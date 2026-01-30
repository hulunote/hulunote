(ns hulunote.all-notes
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.db :as db]
            [hulunote.sidebar :as sidebar]
            [hulunote.router :as router]
            [re-frame.core :as re-frame]))

;; Pagination state
(defonce current-page (atom 1))
(defonce page-size 20)

;; State for note menu
(defonce note-menu-state (atom {:visible false
                                 :x 0
                                 :y 0
                                 :note-id nil
                                 :note-title nil
                                 :database-name nil}))

(defn get-current-database-name
  "Get current database name from route params"
  [db]
  (let [{:keys [params]} (db/get-route db)]
    (:database params)))

(defn get-all-notes-sorted
  "Get all notes sorted by updated-at desc"
  [db]
  (let [notes (d/q
                '[:find ?note-id ?note-title ?root-nav-id
                  :where
                  [?e :hulunote-notes/id ?note-id]
                  [?e :hulunote-notes/title ?note-title]
                  [?e :hulunote-notes/root-nav-id ?root-nav-id]]
                db)
        ;; Get updated-at separately since it might not exist
        notes-with-dates (map (fn [[note-id note-title root-nav-id]]
                                (let [updated-at (first (first (d/q
                                                                 '[:find ?updated-at
                                                                   :in $ ?note-id
                                                                   :where
                                                                   [?e :hulunote-notes/id ?note-id]
                                                                   [?e :hulunote-notes/updated-at ?updated-at]]
                                                                 db note-id)))]
                                  [note-id note-title root-nav-id (or updated-at "1970-01-01")]))
                              notes)]
    (->> notes-with-dates
         (sort-by (fn [[_ _ _ updated-at]] updated-at))
         reverse
         vec)))

(defn get-paginated-notes
  "Get notes for current page"
  [all-notes page]
  (let [start (* (dec page) page-size)
        end (+ start page-size)]
    (if (empty? all-notes)
      []
      (subvec all-notes 
              (min start (count all-notes))
              (min end (count all-notes))))))

(defn total-pages
  "Calculate total number of pages"
  [total-count]
  (max 1 (int (Math/ceil (/ total-count page-size)))))

(defn go-to-page! [page]
  (reset! current-page page))

;; ==================== Note Menu Functions ====================

(defn show-note-menu!
  "Show note menu at specified position"
  [e note-id note-title database-name]
  (.preventDefault e)
  (.stopPropagation e)
  (reset! note-menu-state
    {:visible true
     :x (.-clientX e)
     :y (.-clientY e)
     :note-id note-id
     :note-title note-title
     :database-name database-name}))

(defn hide-note-menu!
  "Hide note menu"
  []
  (swap! note-menu-state assoc :visible false))

(defn delete-note!
  "Delete a note by setting is-delete to true"
  [note-id]
  ;; Update backend
  (re-frame/dispatch-sync
    [:update-note
     {:note-id note-id
      :is-delete true
      :op-fn (fn [data]
               (prn "Note deleted:" data)
               ;; Remove from local datascript
               (d/transact! db/dsdb
                 [[:db/retractEntity [:hulunote-notes/id note-id]]]))}])
  ;; Also remove from local datascript immediately for UI responsiveness
  (d/transact! db/dsdb
    [[:db/retractEntity [:hulunote-notes/id note-id]]]))

(rum/defc note-menu < rum/reactive
  "Context menu component for note card"
  []
  (let [{:keys [visible x y note-id note-title database-name]} (rum/react note-menu-state)]
    (when visible
      [:div.note-context-menu
       {:style {:position "fixed"
                :left (str x "px")
                :top (str y "px")
                :background "#2a2f3a"
                :border "1px solid #444"
                :border-radius "6px"
                :box-shadow "0 4px 12px rgba(0,0,0,0.3)"
                :z-index 10000
                :min-width "150px"
                :padding "4px 0"}
        :on-mouse-leave hide-note-menu!}
       ;; Note title info
       [:div.context-menu-header
        {:style {:padding "8px 12px"
                 :color "#888"
                 :font-size "11px"
                 :border-bottom "1px solid #444"
                 :max-width "200px"
                 :overflow "hidden"
                 :text-overflow "ellipsis"
                 :white-space "nowrap"}}
        note-title]
       ;; Open note option
       [:div.context-menu-item
        {:style {:padding "8px 12px"
                 :cursor "pointer"
                 :color "#fff"
                 :font-size "13px"}
         :on-mouse-over #(set! (-> % .-target .-style .-background) "#3a4555")
         :on-mouse-out #(set! (-> % .-target .-style .-background) "transparent")
         :on-click (fn [e]
                     (.stopPropagation e)
                     (router/go-to-note! database-name note-id)
                     (hide-note-menu!))}
        "📄 Open Note"]
       ;; Delete note option
       [:div.context-menu-item
        {:style {:padding "8px 12px"
                 :cursor "pointer"
                 :color "#ff6b6b"
                 :font-size "13px"}
         :on-mouse-over #(set! (-> % .-target .-style .-background) "#3a4555")
         :on-mouse-out #(set! (-> % .-target .-style .-background) "transparent")
         :on-click (fn [e]
                     (.stopPropagation e)
                     (when (js/confirm (str "Are you sure you want to delete \"" note-title "\"?"))
                       (delete-note! note-id))
                     (hide-note-menu!))}
        "🗑️ Delete Note"]])))

(rum/defc pagination-controls < rum/reactive
  [total-count]
  (let [page (rum/react current-page)
        pages (total-pages total-count)]
    (when (> pages 1)
      [:div.pagination
       {:style {:display "flex"
                :justify-content "center"
                :align-items "center"
                :gap "8px"
                :padding "20px 0"}}
       
       ;; Previous button
       [:button.pagination-btn
        {:disabled (= page 1)
         :on-click #(go-to-page! (dec page))
         :style {:padding "8px 16px"
                 :background (if (= page 1) "#3d4455" "#4a90d9")
                 :color "#fff"
                 :border "none"
                 :border-radius "4px"
                 :cursor (if (= page 1) "not-allowed" "pointer")
                 :opacity (if (= page 1) 0.5 1)}}
        "← Prev"]
       
       ;; Page numbers
       [:div {:style {:display "flex" :gap "4px"}}
        (for [p (range 1 (inc pages))]
          [:button.pagination-num
           {:key p
            :on-click #(go-to-page! p)
            :style {:padding "8px 12px"
                    :background (if (= p page) "#4a90d9" "transparent")
                    :color "#fff"
                    :border (if (= p page) "none" "1px solid rgba(255,255,255,0.2)")
                    :border-radius "4px"
                    :cursor "pointer"}}
           p])]
       
       ;; Next button
       [:button.pagination-btn
        {:disabled (= page pages)
         :on-click #(go-to-page! (inc page))
         :style {:padding "8px 16px"
                 :background (if (= page pages) "#3d4455" "#4a90d9")
                 :color "#fff"
                 :border "none"
                 :border-radius "4px"
                 :cursor (if (= page pages) "not-allowed" "pointer")
                 :opacity (if (= page pages) 0.5 1)}}
        "Next →"]])))

(rum/defc note-card
  [note-id note-title updated-at database-name]
  [:div.note-card
   {:on-click #(router/go-to-note! database-name note-id)
    :style {:padding "16px 20px"
            :margin-bottom "12px"
            :background "rgba(255,255,255,0.05)"
            :border-radius "8px"
            :cursor "pointer"
            :transition "all 0.2s ease"
            :border "1px solid rgba(255,255,255,0.1)"
            :display "flex"
            :justify-content "space-between"
            :align-items "center"}}
   [:div {:style {:flex 1}}
    [:div {:style {:font-size "16px" 
                   :font-weight "500"
                   :margin-bottom "8px"}}
     note-title]
    [:div {:style {:font-size "12px"
                   :color "rgba(255,255,255,0.5)"}}
     (str "Updated: " (if (and updated-at (seq updated-at)) 
                        (subs updated-at 0 (min 19 (count updated-at)))
                        "Unknown"))]]
   ;; More options button (...)
   [:div.note-more-btn
    {:on-click (fn [e]
                 (.stopPropagation e)
                 (show-note-menu! e note-id note-title database-name))
     :style {:padding "8px"
             :cursor "pointer"
             :border-radius "4px"
             :color "rgba(255,255,255,0.5)"
             :font-size "18px"
             :font-weight "bold"
             :line-height "1"
             :transition "all 0.2s ease"}
     :on-mouse-over #(set! (-> % .-currentTarget .-style .-background) "rgba(255,255,255,0.1)")
     :on-mouse-out #(set! (-> % .-currentTarget .-style .-background) "transparent")}
    "⋯"]])

(rum/defc all-notes-page < rum/reactive
  [db]
  (let [database-name (get-current-database-name db)
        all-notes (get-all-notes-sorted db)
        page (rum/react current-page)
        paginated-notes (get-paginated-notes all-notes page)
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)]
    [:div.page-wrapper.night-center-boxBg.night-textColor-2
     
     ;; Left sidebar
     (sidebar/left-sidebar db database-name)
     
     ;; Main content area
     [:div.main-content-area
      {:class (when sidebar-collapsed? "sidebar-collapsed")}
      [:div.flex.flex-column
       {:style {:padding "20px"
                :max-width "900px"
                :margin "0 auto"}}
       
       ;; Page header
       [:div {:style {:margin-bottom "24px"}}
        [:h1 {:style {:font-size "24px" 
                      :font-weight "600"
                      :margin "0 0 8px 0"}}
         "All Notes"]
        [:div {:style {:color "rgba(255,255,255,0.6)"
                       :font-size "14px"}}
         (str "Total: " (count all-notes) " notes")]]
       
       ;; Notes list
       (if (empty? all-notes)
         [:div.flex.flex-column.items-center.justify-center
          {:style {:height "50vh"}}
          [:div {:style {:font-size "18px" :margin-bottom "16px"}} 
           "No notes yet"]
          [:button.new-note-btn
           {:on-click #(sidebar/create-new-note! database-name)}
           [:span.new-note-btn-icon "+"]
           "Create First Note"]]
         
         [:div
          ;; Note cards
          (for [[note-id note-title root-nav-id updated-at] paginated-notes]
            (rum/with-key
              (note-card note-id note-title updated-at database-name)
              note-id))
          
          ;; Pagination
          (pagination-controls (count all-notes))])
       
       [:div {:style {:height "50px"}}]]]
     
     ;; Global note menu
     (note-menu)]))
