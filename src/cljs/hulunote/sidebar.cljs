(ns hulunote.sidebar
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.db :as db]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.render :as render]
            [re-frame.core :as re-frame]
            ["moment" :as moment]))

;; State for sidebar collapse
(defonce sidebar-collapsed? (atom false))

;; State to track if we've already created today's note this session
(defonce daily-note-created? (atom #{}))

(defn toggle-sidebar! []
  (swap! sidebar-collapsed? not))

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

(rum/defc left-sidebar < rum/reactive
  [db database-name]
  (let [collapsed? (rum/react sidebar-collapsed?)
        daily-list (db/sort-daily-list (db/get-daily-list db))
        {:keys [route-name]} (db/get-route db)]
    [:<>
     ;; Sidebar container
     [:div.left-sidebar
      {:class (when collapsed? "collapsed")}
      
      (when-not collapsed?
        [:<>
         ;; Sidebar header with logo
         [:div.sidebar-header
          [:div.flex.items-center
           [:img {:src (u/asset-path "/img/hulunote.webp")
                  :width "24px"
                  :style {:border-radius "50%"}}]
           [:span.sidebar-title.ml2 "HULUNOTE"]]]
         
         ;; New note button
         [:button.new-note-btn
          {:on-click #(create-new-note! database-name)}
          [:span.new-note-btn-icon "+"]
          "New Note"]
         
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
          (sidebar-item "📅" "Diaries" 
                        #(router/go-to-diaries! database-name)
                        (= route-name :diaries))
          
          (sidebar-item "📝" "All Notes"
                        #(router/go-to-all-notes! database-name)
                        (= route-name :all-notes))

          (sidebar-item "🔌" "MCP Settings"
                        #(router/go-to-mcp-settings! database-name)
                        (= route-name :mcp-settings))

          ;; Note list section
          [:div.sidebar-section-title "Recent Notes"]
          
          [:div.note-list
           (for [[note-title note-id root-nav-id] (take 15 daily-list)]
             [:div.note-list-item
              {:key note-id
               :on-click #(router/go-to-note! database-name note-id)
               :title note-title}
              note-title])]]])]
     
     ;; Toggle button - always visible, positioned at edge of sidebar
     [:div.sidebar-toggle-btn
      {:class (when collapsed? "collapsed")
       :on-click toggle-sidebar!
       :title (if collapsed? "展开侧边栏" "收起侧边栏")}
      (if collapsed? "☰" "✕")]]))
