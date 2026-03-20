(ns hulunote.sidebar
  (:require [clojure.string :as str]
            [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.db :as db]
            [hulunote.http :as http]
            [hulunote.menu :as menu]
            [hulunote.settings-state :as settings-state]
            [hulunote.storage :as storage]
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
(defonce sidebar-user-menu-open? (atom false))
(defonce database-list-loading? (atom false))

;; State to track if we've already created today's note this session
(defonce daily-note-created? (atom #{}))

;; ==================== Search Modal State ====================
(defonce search-state (atom {:visible false
                              :query ""
                              :selected-index 0}))

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

(defn hide-sidebar-user-menu! []
  (reset! sidebar-user-menu-open? false))

(defn toggle-sidebar-user-menu! []
  (swap! sidebar-user-menu-open? not))

(defn toggle-topbar-more-menu! []
  (hide-sidebar-user-menu!)
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

(defn remove-nil-values
  [m]
  (into {} (remove (fn [[_ v]] (nil? v)) m)))

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

(defn star-menu-icon-filled []
  [:svg {:viewBox "0 0 24 24"
         :fill "currentColor"}
   [:path {:d "M12 3l2.9 5.8 6.4.9-4.7 4.5 1.1 6.3L12 17.5 6.3 20.5l1.1-6.3-4.7-4.5 6.4-.9L12 3z"}]])

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

(defn settings-menu-icon []
  [:svg {:viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "1.8"
         :stroke-linecap "round"
         :stroke-linejoin "round"}
   [:circle {:cx "12" :cy "12" :r "3"}]
   [:path {:d "M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"}]])

(defn logout-menu-icon []
  [:svg {:viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "1.8"
         :stroke-linecap "round"
         :stroke-linejoin "round"}
   [:path {:d "M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"}]
   [:polyline {:points "16 17 21 12 16 7"}]
   [:line {:x1 "21" :y1 "12" :x2 "9" :y2 "12"}]])

(defn available-database-names
  [conn]
  (->> (db/get-database conn)
       (map first)
       (keep :hulunote-databases/name)
       (sort-by str/lower-case)))

(defn load-database-list!
  []
  (reset! database-list-loading? true)
  (re-frame/dispatch
    [:get-database-list
     {:op-fn (fn [{:keys [database-list]}]
               (doseq [item (or database-list [])]
                 (d/transact!
                   db/dsdb
                   [(remove-nil-values
                      {:hulunote-databases/id (or (get-value item :hulunote-databases/id)
                                                  (get-value item :database-id)
                                                  (:database-id item))
                       :hulunote-databases/name (or (get-value item :hulunote-databases/name)
                                                    (get-value item :database-name)
                                                    (:database-name item))
                       :hulunote-databases/description (or (get-value item :hulunote-databases/description)
                                                           (get-value item :database-description)
                                                           (:database-description item))
                       :hulunote-databases/is-public (or (get-value item :hulunote-databases/is-public)
                                                         (get-value item :is-public)
                                                         (:is-public item))})]))
               (reset! database-list-loading? false))}]))

(defn ensure-database-list-loaded!
  [conn]
  (when (and (empty? (available-database-names conn))
             (not @database-list-loading?))
    (load-database-list!)))

(defn navigate-to-database!
  [route-name database-name]
  (case route-name
    :all-notes (router/go-to-all-notes! database-name)
    :graph (router/go-to-graph! database-name)
    :mcp-settings (router/go-to-settings! database-name)
    :settings (router/go-to-settings! database-name)
    :mcp-chat (router/go-to-mcp-chat! database-name)
    :diaries (router/go-to-diaries! database-name)
    ;; single-note/show cannot be preserved across databases reliably.
    (router/go-to-diaries! database-name)))

(defn switch-database!
  [route-name database-name]
  (hide-sidebar-user-menu!)
  (http/database-data-load database-name)
  (navigate-to-database! route-name database-name))

(defn user-menu-items [database-name]
  [{:label "Settings"
    :icon (settings-menu-icon)
    :on-click (fn [_]
                (hide-sidebar-user-menu!)
                (settings-state/open-settings!))}
   {:label "Logout"
    :icon (logout-menu-icon)
    :danger? true
    :on-click (fn [_]
                (hide-sidebar-user-menu!)
                (reset! storage/jwt-auth {})
                (router/switch-router! "/login"))}])

(rum/defc sidebar-user-trigger < rum/reactive
  [{:keys [database-name class]}]
  (let [menu-open? (rum/react sidebar-user-menu-open?)
        app-db (rum/react db/dsdb)
        {:keys [route-name]} (db/get-route app-db)
        database-names (available-database-names app-db)
        hulunote-info (:hulunote @storage/jwt-auth)
        avatar-url (:accounts/avatar hulunote-info)
        username (or (:accounts/nickname hulunote-info)
                     (first (str/split (or (:accounts/mail hulunote-info) "") #"@"))
                     "User")
        avatar-src (when avatar-url
                     (if (str/starts-with? avatar-url "http")
                       avatar-url
                       (str (http/http-uri "") avatar-url)))]
    (ensure-database-list-loaded! app-db)
    [:div.sidebar-user-menu-anchor
     {:class class
      :on-click u/stop-click-bubble}
     [:button.sidebar-user-trigger
      {:class (when menu-open? "active")
       :title database-name
       :on-click (fn [e]
                   (u/stop-click-bubble e)
                   (hide-topbar-more-menu!)
                   (toggle-sidebar-user-menu!))}
      [:span.sidebar-user-avatar
       (if avatar-src
         [:img {:src avatar-src
                :style {:width "100%" :height "100%" :object-fit "cover"}}]
         [:span.sidebar-user-avatar-fallback
          (-> username first str/upper-case)])]
      [:span.sidebar-user-meta
       [:span.sidebar-user-database-name database-name]]
      [:svg.sidebar-user-chevron
       {:viewBox "0 0 24 24"
        :fill "currentColor"}
       [:path {:d "M7 10l5 5 5-5z"}]]]
     (when menu-open?
       (into
         (menu/menu-popover
           {:class "sidebar-user-menu"
            :style {:position "absolute"
                    :top "calc(100% + 8px)"
                    :left "0"
                    :min-width "236px"}})
         (concat
           [(menu/menu-header "Databases")]
           (for [db-name database-names]
             (menu/menu-item
               {:class (str "sidebar-user-menu-database-item"
                            (when (= db-name database-name) " active"))
                :on-click (fn [_]
                            (if (= db-name database-name)
                              (hide-sidebar-user-menu!)
                              (switch-database! route-name db-name)))}
               db-name))
           [[:div {:style {:height "1px"
                           :background "var(--surface-border-strong)"
                           :margin "6px 0"}}]]
           (for [{:keys [label icon danger? on-click]} (user-menu-items database-name)]
             (menu/menu-item
               {:icon icon
                :danger? danger?
                :on-click on-click}
               label)))))]))

(rum/defc sidebar-footer-brand []
  [:div.sidebar-footer-brand
   [:img {:src (u/asset-path "/img/hulunote.webp")
          :width "28px"
          :height "28px"
          :style {:border-radius "50%"}}]
   [:span.sidebar-footer-brand-text "HULUNOTE"]])

(rum/defc app-top-bar < rum/reactive
  "Global top bar for app pages."
   [{:keys [more-menu-items]}]
  (let [collapsed? (rum/react sidebar-collapsed?)
        right-sidebar-open? (rum/react db/right-sidebar-open?)
        current-route (db/get-route (rum/react db/dsdb))
        database-name (get-in current-route [:params :database])
        more-menu-open? (rum/react topbar-more-menu-open?)
        more-menu-items (or more-menu-items [])]
    ;; Set topbar height on :root so layout (sidebar, page-wrapper) adapts
    (.setProperty (.-style (.-documentElement js/document)) "--app-topbar-height" "44px")
    [:div.app-topbar
     {:class (when-not collapsed? "with-sidebar")}
     (when-not collapsed?
       [:div.app-topbar-brand
        (sidebar-user-trigger {:database-name database-name
                               :class "sidebar-user-trigger-topbar"})])
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
     {:title "Search (Cmd+K)"
      :on-click #(show-search!)}
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
        starred-notes (db/get-starred-notes db database-name)
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
        ;; Sidebar header is only shown when peeking the collapsed sidebar.
        (when collapsed?
          [:div.sidebar-header
           (sidebar-user-trigger {:database-name database-name
                                  :class "sidebar-user-trigger-sidebar"})])

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
                  :display "none"
                  :align-items "center"
                  :justify-content "center"
                  :margin-bottom "16px"
                  :transition "all 0.2s ease"}
          :on-click #(ensure-daily-note! database-name {:navigate? true})}
         [:span {:style {:margin-right "8px"}} "📅"]
         (str "Today: " (get-today-title))]

        [:div.sidebar-content
         [:div.sidebar-nav-primary
          (sidebar-item [:img.sidebar-symbol-icon {:src (u/asset-path "/img/icons/calendar_month.svg")}] "Diaries"
                        #(router/go-to-diaries! database-name)
                        (= route-name :diaries))

          (sidebar-item [:img.sidebar-symbol-icon {:src (u/asset-path "/img/icons/description.svg")}] "All Notes"
                        #(router/go-to-all-notes! database-name)
                        (= route-name :all-notes))

          (sidebar-item [:img.sidebar-symbol-icon {:src (u/asset-path "/img/icons/graph.svg")}] "Graph"
                        #(router/go-to-graph! database-name)
                        (= route-name :graph))

          #_(sidebar-item [:img.sidebar-symbol-icon {:src (u/asset-path "/img/icons/tune.svg")}] "Settings"
                        #(router/go-to-settings! database-name)
                        (= route-name :settings))

          (sidebar-item [:img.sidebar-symbol-icon {:src (u/asset-path "/img/icons/chat_bubble.svg")}] "AI Chat"
                        #(router/go-to-mcp-chat! database-name)
                        (= route-name :mcp-chat))

          [:div {:style {:padding "8px 16px 4px 16px"}}
           [:button.new-note-btn.sidebar-new-note-btn
            {:style {:margin 0
                     :width "100%"}
             :on-click #(create-new-note! database-name)}
            "New Note"]]

          [:div.sidebar-section-title "Shortcuts"]

          [:div.note-list
           (for [{:keys [note-title note-id root-nav-id]} (take 15 starred-notes)]
             [:div.note-list-item
              {:key note-id
               :on-click (fn [e]
                           (if (.-shiftKey e)
                             (do
                               (.stopPropagation e)
                               (db/open-note-in-right-sidebar! note-id note-title root-nav-id database-name))
                             (router/go-to-note! database-name note-id)))
               :title (str note-title " (Shift+click to open in sidebar)")}
              note-title])]]]

        [:div.sidebar-bottom-slot
         (sidebar-footer-brand)]])]))

;; ==================== Search Modal ====================

(defn show-search! []
  (reset! search-state {:visible true :query "" :selected-index 0}))

(defn hide-search! []
  (reset! search-state {:visible false :query "" :selected-index 0}))

(defn search-navigate! [database-name note-id]
  (when (and database-name note-id)
    (router/go-to-note! database-name note-id)
    (hide-search!)))

(rum/defc search-modal < rum/reactive
  {:did-mount (fn [state]
                ;; Add global Cmd+K / Ctrl+K listener
                (let [handler (fn [e]
                                (when (and (or (.-metaKey e) (.-ctrlKey e))
                                           (= (.-key e) "k"))
                                  (.preventDefault e)
                                  (if (:visible @search-state)
                                    (hide-search!)
                                    (show-search!))))]
                  (.addEventListener js/document "keydown" handler)
                  (assoc state ::global-handler handler)))
   :will-unmount (fn [state]
                   (when-let [handler (::global-handler state)]
                     (.removeEventListener js/document "keydown" handler))
                   state)}
  []
  (let [{:keys [visible query selected-index]} (rum/react search-state)
        db (rum/react db/dsdb)
        current-route (db/get-route db)
        database-name (get-in current-route [:params :database])
        results (when (and visible (not (str/blank? query)))
                  (db/search-notes db query 20))
        results (or results [])]
    (when visible
      [:div.search-modal-overlay
       {:on-click (fn [e]
                    (when (= (.-target e) (.-currentTarget e))
                      (hide-search!)))}
       [:div.search-modal
        [:div.search-modal-input-wrapper
         [:img.search-modal-icon {:src (u/asset-path "/img/icons/search.svg")}]
         [:input.search-modal-input
          {:type "text"
           :placeholder "Search notes..."
           :auto-focus true
           :value query
           :on-change (fn [e]
                        (let [v (.. e -target -value)]
                          (swap! search-state assoc :query v :selected-index 0)))
           :on-key-down (fn [e]
                          (case (.-key e)
                            "Escape" (hide-search!)
                            "ArrowDown" (do (.preventDefault e)
                                            (swap! search-state update :selected-index
                                              (fn [i] (min (inc i) (max 0 (dec (count results)))))))
                            "ArrowUp" (do (.preventDefault e)
                                          (swap! search-state update :selected-index
                                            (fn [i] (max (dec i) 0))))
                            "Enter" (when-let [item (get results selected-index)]
                                      (search-navigate! database-name (:note-id item)))
                            nil))}]
         [:span.search-modal-shortcut "ESC"]]
        [:div.search-modal-results
         (if (str/blank? query)
           [:div.search-modal-hint "Type to search notes by title or content"]
           (if (empty? results)
             [:div.search-modal-hint "No results found"]
             (map-indexed
               (fn [idx {:keys [note-id note-title match-type match-content]}]
                 [:div.search-modal-item
                  {:key (str note-id "-" idx)
                   :class (when (= idx selected-index) "search-modal-item-selected")
                   :ref (fn [el]
                          (when (and el (= idx selected-index))
                            (.scrollIntoView el #js {:block "nearest"})))
                   :on-mouse-enter #(swap! search-state assoc :selected-index idx)
                   :on-click #(search-navigate! database-name note-id)}
                  [:div.search-modal-item-left
                   [:div.search-modal-item-title note-title]
                   (when match-content
                     [:div.search-modal-item-snippet
                      (let [content (str match-content)
                            max-len 80]
                        (if (> (count content) max-len)
                          (str (subs content 0 max-len) "...")
                          content))])]
                  [:div.search-modal-item-badge
                   (if (= match-type :title) "Title" "Content")]])
               results)))]]])))
