(ns hulunote.sidebar
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.db :as db]
            [hulunote.menu :as menu]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.render :as render]
            [re-frame.core :as re-frame]
            ["moment" :as moment]))

;; State for sidebar collapse
(defonce sidebar-collapsed? (atom false))
(defonce sidebar-peek-open? (atom false))
(defonce sidebar-peek-timeout (atom nil))
(defonce topbar-more-menu-open? (atom false))

;; State to track if we've already created today's note this session
(defonce daily-note-created? (atom #{}))

(defn clear-sidebar-peek-timeout! []
  (when-let [timeout-id @sidebar-peek-timeout]
    (js/clearTimeout timeout-id)
    (reset! sidebar-peek-timeout nil)))

(defn open-sidebar-peek! []
  (when @sidebar-collapsed?
    (clear-sidebar-peek-timeout!)
    (reset! sidebar-peek-open? true)))

(defn close-sidebar-peek! []
  (clear-sidebar-peek-timeout!)
  (reset! sidebar-peek-open? false))

(defn schedule-sidebar-peek-close! []
  (when @sidebar-collapsed?
    (clear-sidebar-peek-timeout!)
    (reset! sidebar-peek-timeout
      (js/setTimeout
        (fn []
          (reset! sidebar-peek-open? false)
          (reset! sidebar-peek-timeout nil))
        140))))

(defn toggle-sidebar! []
  (clear-sidebar-peek-timeout!)
  (swap! sidebar-collapsed? not)
  (reset! sidebar-peek-open? false))

(defn hide-topbar-more-menu! []
  (reset! topbar-more-menu-open? false))

(defn toggle-topbar-more-menu! []
  (swap! topbar-more-menu-open? not))

(defn generate-note-title
  "Generate a unique note title with date and time"
  []
  (.format (moment.) "YYYY-MM-DD HH:mm:ss"))

(defn get-today-title
  "Generate today's daily note title in format YYYY-MM-DD"
  []
  (.format (moment.) "YYYY-MM-DD"))

(defn get-value
  "Get value from map, trying both keyword and string keys"
  [m k]
  (or (get m k)
      (get m (name k))
      (get m (keyword (clojure.string/replace (name k) "/" "-")))))

(defn daily-note-exists?
  "Check if a daily note with today's date already exists"
  [database-name]
  (let [today (get-today-title)
        notes (d/q
                '[:find ?note-id
                  :in $ ?title
                  :where
                  [?note :hulunote-notes/title ?title]
                  [?note :hulunote-notes/id ?note-id]]
                @db/dsdb
                today)]
    (seq notes)))

(defn create-daily-note!
  "Create a daily note for today if it doesn't exist.
   Returns the note id if created or existing note id."
  [database-name & [callback]]
  (let [today (get-today-title)]
    ;; Check if note already exists in datascript
    (if-let [existing (daily-note-exists? database-name)]
      ;; Note exists, call callback with existing note info
      (let [note-id (ffirst existing)]
        (when callback (callback {:id note-id :title today :exists true})))
      ;; Create new daily note
      (re-frame/dispatch-sync
        [:create-note
         {:database-name database-name
          :title today
          :op-fn (fn [note-info]
                   (prn "Daily note created response:" note-info)
                   (let [id (or (get-value note-info :hulunote-notes/id)
                                (get-value note-info :id)
                                (:id note-info))
                         root-nav-id (or (get-value note-info :hulunote-notes/root-nav-id)
                                         (get-value note-info :root-nav-id)
                                         (:root_nav_id note-info)
                                         (:root-nav-id note-info))]
                     (prn "Parsed daily note - id:" id "root-nav-id:" root-nav-id)
                     (if (and id root-nav-id)
                       (do
                         ;; Add note to local datascript
                         (d/transact! db/dsdb
                           [{:hulunote-notes/id id
                             :hulunote-notes/title today
                             :hulunote-notes/root-nav-id root-nav-id
                             :hulunote-notes/database-id database-name
                             :hulunote-notes/is-delete false
                             :hulunote-notes/is-public false
                             :hulunote-notes/is-shortcut false
                             :hulunote-notes/updated-at (.toISOString (js/Date.))}])
                         ;; Add root nav to datascript
                         (d/transact! db/dsdb
                           [{:id root-nav-id
                             :content "ROOT"
                             :hulunote-note id
                             :same-deep-order 0
                             :is-display true
                             :origin-parid db/root-id}])
                         ;; Create the first editable nav node
                         (let [first-nav-id (str (d/squuid))]
                           (re-frame/dispatch-sync
                             [:create-nav
                              {:database-name database-name
                               :note-id id
                               :id first-nav-id
                               :parid root-nav-id
                               :content ""
                               :order 0
                               :op-fn (fn [nav-data]
                                        (prn "First nav for daily note created:" nav-data)
                                        ;; Add nav to local datascript
                                        (d/transact! db/dsdb
                                          [{:id first-nav-id
                                            :content ""
                                            :hulunote-note id
                                            :same-deep-order 0
                                            :is-display true
                                            :origin-parid root-nav-id}
                                           [:db/add [:id root-nav-id] :parid [:id first-nav-id]]])
                                        ;; Mark as created for this database
                                        (swap! daily-note-created? conj database-name)
                                        ;; Call callback if provided
                                        (when callback
                                          (callback {:id id :title today :root-nav-id root-nav-id :exists false})))}])))
                       ;; If no root-nav-id returned
                       (when (and id callback)
                         (callback {:id id :title today :exists false})))))}]))))

(defn ensure-daily-note!
  "Ensure today's daily note exists for the database.
   Creates one if it doesn't exist. Can optionally navigate to it."
  [database-name & [{:keys [navigate? on-ready]}]]
  (let [today (get-today-title)]
    ;; Check if we've already handled this database this session
    (if (@daily-note-created? database-name)
      ;; Already created/checked, just find it
      (let [existing (daily-note-exists? database-name)]
        (when (and existing on-ready)
          (let [note-id (ffirst existing)]
            (on-ready {:id note-id :title today :exists true}))))
      ;; Need to create or verify
      (create-daily-note! database-name
        (fn [note-info]
          (swap! daily-note-created? conj database-name)
          (when navigate?
            (router/go-to-note! database-name (:id note-info)))
          (when on-ready
            (on-ready note-info)))))))

(defn create-new-note!
  "Create a new note with a default first nav node.
   Title format: YYYY-MM-DD HH:mm:ss to ensure uniqueness.
   After creation, navigate to the new note page."
  [database-name]
  (let [title (generate-note-title)]
    (re-frame/dispatch-sync
      [:create-note
       {:database-name database-name
        :title title
        :op-fn (fn [note-info]
                 (prn "Note created response:" note-info)
                 ;; Try different possible key formats from backend
                 (let [id (or (get-value note-info :hulunote-notes/id)
                              (get-value note-info :id)
                              (:id note-info))
                       root-nav-id (or (get-value note-info :hulunote-notes/root-nav-id)
                                       (get-value note-info :root-nav-id)
                                       (:root_nav_id note-info)
                                       (:root-nav-id note-info))]
                   (prn "Parsed - id:" id "root-nav-id:" root-nav-id)
                   (if (and id root-nav-id)
                     (do
                       ;; Add note to local datascript
                       (d/transact! db/dsdb
                         [{:hulunote-notes/id id
                           :hulunote-notes/title title
                           :hulunote-notes/root-nav-id root-nav-id
                           :hulunote-notes/database-id database-name
                           :hulunote-notes/is-delete false
                           :hulunote-notes/is-public false
                           :hulunote-notes/is-shortcut false
                           :hulunote-notes/updated-at (.toISOString (js/Date.))}])
                       ;; Add root nav to datascript
                       (d/transact! db/dsdb
                         [{:id root-nav-id
                           :content "ROOT"
                           :hulunote-note id
                           :same-deep-order 0
                           :is-display true
                           :origin-parid db/root-id}])
                       ;; Create the first editable nav node
                       (let [first-nav-id (str (d/squuid))]
                         (re-frame/dispatch-sync
                           [:create-nav
                            {:database-name database-name
                             :note-id id
                             :id first-nav-id
                             :parid root-nav-id
                             :content ""
                             :order 0
                             :op-fn (fn [nav-data]
                                      (prn "First nav created:" nav-data)
                                      ;; Add nav to local datascript
                                      (d/transact! db/dsdb
                                        [{:id first-nav-id
                                          :content ""
                                          :hulunote-note id
                                          :same-deep-order 0
                                          :is-display true
                                          :origin-parid root-nav-id}
                                         [:db/add [:id root-nav-id] :parid [:id first-nav-id]]])
                                      ;; Navigate to the new note page
                                      (router/go-to-note! database-name id)
                                      ;; Start editing the new nav after a short delay
                                      (js/setTimeout
                                        #(render/start-editing! first-nav-id "")
                                        100))}])))
                     ;; If no root-nav-id, just navigate (backend may auto-create)
                     (when id
                       (prn "Warning: No root-nav-id returned, navigating anyway")
                       (router/go-to-note! database-name id)))))}])))

(rum/defc sidebar-item
  [icon text on-click & [active?]]
  [:div.sidebar-item
   {:class (when active? "active")
    :on-click on-click}
   [:div.sidebar-item-icon icon]
   [:div.sidebar-item-text text]])

(defn more-menu-icon []
  [:svg {:width "16"
         :height "16"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"}
   [:circle {:cx "5" :cy "12" :r "1.5"}]
   [:circle {:cx "12" :cy "12" :r "1.5"}]
   [:circle {:cx "19" :cy "12" :r "1.5"}]])

(defn star-menu-icon []
  [:svg {:viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "1.8"
         :stroke-linecap "round"
         :stroke-linejoin "round"}
   [:polygon {:points "12 3 14.9 8.8 21.3 9.7 16.6 14.2 17.7 20.5 12 17.5 6.3 20.5 7.4 14.2 2.7 9.7 9.1 8.8 12 3"}]])

(defn delete-menu-icon []
  [:svg {:viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "1.8"
         :stroke-linecap "round"
         :stroke-linejoin "round"}
   [:path {:d "M3 6h18"}]
   [:path {:d "M8 6V4h8v2"}]
   [:path {:d "M19 6l-1 14H6L5 6"}]
   [:path {:d "M10 11v6"}]
   [:path {:d "M14 11v6"}]])

(rum/defc app-top-bar < rum/reactive
  "Global top bar for app pages."
   [{:keys [more-menu-items]}]
  (let [collapsed? (rum/react sidebar-collapsed?)
        right-sidebar-open? (rum/react db/right-sidebar-open?)
        more-menu-open? (rum/react topbar-more-menu-open?)
        more-menu-items (or more-menu-items [])]
    ;; Set topbar height on :root so layout (sidebar, page-wrapper) adapts
    (.setProperty (.-style (.-documentElement js/document)) "--app-topbar-height" "40px")
    [:div.app-topbar
     {:class (when-not collapsed? "with-sidebar")}
     (when-not collapsed?
       [:div.app-topbar-brand
       [:div.app-topbar-brand-main
         [:img {:src (u/asset-path "/img/hulunote.webp")
                :width "24px"
                :height "24px"
                :style {:border-radius "50%"}}]
         [:span.app-topbar-brand-text "HULUNOTE"]]])
     [:div.app-topbar-left
    [:button.app-topbar-btn
     {:title (if collapsed? "Show Sidebar" "Hide Sidebar")
      :on-click toggle-sidebar!
      :on-mouse-enter open-sidebar-peek!
      :on-mouse-leave schedule-sidebar-peek-close!}
     [:img.app-topbar-icon {:src (u/asset-path "/img/icons/dock_to_right.svg")}]]
    [:button.app-topbar-btn
     {:title "Back"
      :on-click #(js/history.back)}
     [:img.app-topbar-icon {:src (u/asset-path "/img/icons/arrow_back.svg")}]]
    [:button.app-topbar-btn
     {:title "Forward"
      :on-click #(js/history.forward)}
     [:img.app-topbar-icon {:src (u/asset-path "/img/icons/arrow_forward.svg")}]]]
   [:div.app-topbar-center]
   [:div.app-topbar-right
   [:button.app-topbar-btn
     {:title "Search (placeholder)"
      :on-click #()}
     [:img.app-topbar-icon {:src (u/asset-path "/img/icons/search.svg")}]]
    (when (seq more-menu-items)
      [:div.topbar-more-menu-wrapper
       {:on-click u/stop-click-bubble}
       [:button.app-topbar-btn
        {:class (when more-menu-open? "active")
         :title "More"
         :on-click (fn [e]
                     (u/stop-click-bubble e)
                     (toggle-topbar-more-menu!))}
        (more-menu-icon)]
       (when more-menu-open?
         (into
           (menu/menu-popover
             {:class "topbar-more-menu"
              :style {:position "absolute"
                      :top "calc(100% + 8px)"
                      :right "0"
                      :min-width "160px"}})
           (for [{:keys [label icon danger? on-click class style]} more-menu-items]
             (menu/menu-item
               {:class (str "topbar-more-menu-item"
                            (when danger? " topbar-more-menu-item-danger")
                            (when class (str " " class)))
                :danger? danger?
                :icon icon
                :style style
                :on-click (fn [e]
                            (u/stop-click-bubble e)
                            (when on-click
                              (on-click e))
                            (hide-topbar-more-menu!))}
               label))))])
    [:button.app-topbar-btn
     {:class (when right-sidebar-open? "active")
      :title (if right-sidebar-open?
               "Hide Right Sidebar"
               "Show Right Sidebar")
      :on-click #(db/toggle-right-sidebar-visibility!)}
     [:img.app-topbar-icon {:src (u/asset-path "/img/icons/dock_to_left.svg")}]]]]))

(rum/defc left-sidebar < rum/reactive
  [db database-name]
  (let [collapsed? (rum/react sidebar-collapsed?)
        peek-open? (rum/react sidebar-peek-open?)
        visible? (or (not collapsed?) peek-open?)
        recent-notes (db/get-recent-notes db)
        {:keys [route-name]} (db/get-route db)]
    [:div.left-sidebar
     {:class (str
               (when collapsed? " collapsed")
               (when peek-open? " peek-open"))
      :on-mouse-enter #(when collapsed?
                         (open-sidebar-peek!))
      :on-mouse-leave #(when collapsed?
                         (close-sidebar-peek!))}

     (when visible?
        [:<>
         ;; Sidebar header with logo only in temporary peek mode
         (when collapsed?
           [:div.sidebar-header
            [:div.flex.items-center
             [:img {:src (u/asset-path "/img/hulunote.webp")
                    :width "24px"
                    :style {:border-radius "50%"}}]
             [:span.sidebar-title.ml2 "HULUNOTE"]]])

         ;; Today's Daily Note button
         [:button.daily-note-btn
          {:style {:width "100%"
                   :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                   :color "#fff"
                   :border "none"
                   :border-radius "8px"
                   :padding "10px 16px"
                   :font-size "14px"
                   :font-weight "500"
                   :cursor "pointer"
                   :display "none";;"flex"
                   :align-items "center"
                   :justify-content "center"
                   :margin-bottom "16px"
                   :transition "all 0.2s ease"}
           :on-click #(ensure-daily-note! database-name {:navigate? true})}
          [:span {:style {:margin-right "8px"}} "📅"]
          (str "Today: " (get-today-title))]

         ;; Sidebar content
         [:div.sidebar-content
          ;; Menu items
          (sidebar-item [:img.sidebar-symbol-icon {:src (u/asset-path "/img/icons/calendar_month.svg")}] "Diaries"
                        #(router/go-to-diaries! database-name)
                        (= route-name :diaries))

          (sidebar-item [:img.sidebar-symbol-icon {:src (u/asset-path "/img/icons/description.svg")}] "All Notes"
                        #(router/go-to-all-notes! database-name)
                        (= route-name :all-notes))

          (sidebar-item [:img.sidebar-symbol-icon {:src (u/asset-path "/img/icons/graph.svg")}] "Graph"
                        #(router/go-to-graph! database-name)
                        (= route-name :graph))

          (sidebar-item [:img.sidebar-symbol-icon {:src (u/asset-path "/img/icons/tune.svg")}] "MCP Settings"
                        #(router/go-to-mcp-settings! database-name)
                        (= route-name :mcp-settings))

          (sidebar-item [:img.sidebar-symbol-icon {:src (u/asset-path "/img/icons/chat_bubble.svg")}] "MCP Chat"
                        #(router/go-to-mcp-chat! database-name)
                        (= route-name :mcp-chat))

          ;; Note list section
          [:div.sidebar-section-title "Recent Notes"]

          [:div.note-list
           (for [{:keys [note-title note-id root-nav-id]} (take 15 recent-notes)]
             [:div.note-list-item
              {:key note-id
               :on-click (fn [e]
                           (if (.-shiftKey e)
                             (do
                               (.stopPropagation e)
                               (db/open-note-in-right-sidebar! note-id note-title root-nav-id database-name))
                             (router/go-to-note! database-name note-id)))
               :title (str note-title " (Shift+click to open in sidebar)")}
              note-title])]]

         ;; Bottom fixed action - New Note button
         [:div
          {:style {:padding "12px 16px"
                   :border-top "1px solid rgba(255, 255, 255, 0.08)"
                   :flex-shrink 0}}
         [:button.new-note-btn
           {:style {:margin "0"
                    :width "100%"}
            :on-click #(create-new-note! database-name)}
           [:span.new-note-btn-icon "+"]
           "New Note"]]])]))
