(ns hulunote.sidebar
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.db :as db]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.render :as render]
            [re-frame.core :as re-frame]))

;; State for sidebar collapse
(defonce sidebar-collapsed? (atom false))

(defn toggle-sidebar! []
  (swap! sidebar-collapsed? not))

(defn get-all-notes
  "Get all notes from the database"
  [conn]
  (d/q
    '[:find ?note-id ?note-title ?updated-at
      :where
      [?note :hulunote-notes/id ?note-id]
      [?note :hulunote-notes/title ?note-title]
      [?note :hulunote-notes/updated-at ?updated-at]]
    conn))

(defn create-new-note!
  "Create a new note with a default first nav node"
  [database-name]
  (let [today (.toISOString (js/Date.))
        title (subs today 0 10)] ;; Use today's date as default title
    (re-frame/dispatch-sync
      [:create-note
       {:database-name database-name
        :title title
        :op-fn (fn [note-info]
                 (prn "Note created response:" note-info)
                 ;; The response format is: {:hulunote-notes/id "...", :hulunote-notes/title "...", ...}
                 (let [id (or (:hulunote-notes/id note-info) 
                              (get note-info "hulunote-notes/id"))
                       root-nav-id (or (:hulunote-notes/root-nav-id note-info)
                                       (get note-info "hulunote-notes/root-nav-id"))]
                   (when (and id root-nav-id)
                     ;; Add note to local datascript
                     (d/transact! db/dsdb
                       [{:hulunote-notes/id id
                         :hulunote-notes/title title
                         :hulunote-notes/root-nav-id root-nav-id
                         :hulunote-notes/database-id database-name
                         :hulunote-notes/is-delete false
                         :hulunote-notes/is-public false
                         :hulunote-notes/is-shortcut false}])
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
                                    ;; Start editing the new nav
                                    (render/start-editing! first-nav-id ""))}])))))}])))

(rum/defc sidebar-item
  [icon text on-click & [active?]]
  [:div.sidebar-item
   {:class (when active? "active")
    :on-click on-click}
   [:div.sidebar-item-icon icon]
   [:div.sidebar-item-text text]])

(rum/defc note-list-item
  [note-id title on-click & [active?]]
  [:div.note-list-item
   {:class (when active? "active")
    :on-click on-click
    :title title}
   title])

(rum/defc left-sidebar < rum/reactive
  [db database-name]
  (let [collapsed? (rum/react sidebar-collapsed?)
        daily-list (db/sort-daily-list (db/get-daily-list db))]
    [:div.left-sidebar
     {:class (when collapsed? "collapsed")}
     
     ;; Toggle button
     [:div.sidebar-toggle
      {:on-click toggle-sidebar!}
      (if collapsed? "▶" "◀")]
     
     (when-not collapsed?
       [:<>
        ;; Sidebar header
        [:div.sidebar-header
         [:span.sidebar-title "Notes"]]
        
        ;; New note button
        [:button.new-note-btn
         {:on-click #(create-new-note! database-name)}
         [:span.new-note-btn-icon "+"]
         "New Note"]
        
        ;; Sidebar content
        [:div.sidebar-content
         ;; Menu items
         (sidebar-item "📅" "Diaries" 
                       #(router/switch-router! 
                          (str "/app/" database-name "/diaries")))
         
         (sidebar-item "📝" "All Notes" 
                       #(router/switch-router! 
                          (str "/app/" database-name "/diaries")))
         
         ;; Note list section
         [:div {:style {:margin-top "16px"
                        :padding "0 16px"
                        :font-size "12px"
                        :color "rgba(255,255,255,0.5)"
                        :text-transform "uppercase"}}
          "Recent Notes"]
         
         [:div.note-list
          (for [[note-title note-id root-nav-id] (take 20 daily-list)]
            [:div.note-list-item
             {:key note-id
              :on-click #(prn "Navigate to note:" note-id)
              :title note-title}
             (u/daily-title->en note-title)])]]])]))
