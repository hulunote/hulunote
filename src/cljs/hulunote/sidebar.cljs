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

(defn toggle-sidebar! []
  (swap! sidebar-collapsed? not))

(defn generate-note-title
  "Generate a unique note title with date and time"
  []
  (.format (moment.) "YYYY-MM-DD HH:mm:ss"))

(defn get-value
  "Get value from map, trying both keyword and string keys"
  [m k]
  (or (get m k)
      (get m (name k))
      (get m (keyword (clojure.string/replace (name k) "/" "-")))))

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
           [:img {:src "/img/hulunote.webp"
                  :width "24px"
                  :style {:border-radius "50%"}}]
           [:span.sidebar-title.ml2 "HULUNOTE"]]]
         
         ;; New note button
         [:button.new-note-btn
          {:on-click #(create-new-note! database-name)}
          [:span.new-note-btn-icon "+"]
          "New Note"]
         
         ;; Sidebar content
         [:div.sidebar-content
          ;; Menu items
          (sidebar-item "📅" "Diaries" 
                        #(router/go-to-diaries! database-name)
                        (= route-name :diaries))
          
          (sidebar-item "📝" "All Notes" 
                        #(router/go-to-all-notes! database-name)
                        (= route-name :all-notes))
          
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
