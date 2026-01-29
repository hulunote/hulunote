(ns hulunote.diaries
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.render :as render]
            [hulunote.db :as db]
            [hulunote.components :as comps]
            [hulunote.sidebar :as sidebar]
            [hulunote.router :as router]
            [re-frame.core :as re-frame]))

;; State for editing note title
(defonce editing-note-id (atom nil))
(defonce editing-note-title (atom ""))

(defn get-current-database-name
  "Get current database name from route params"
  [db]
  (let [{:keys [params]} (db/get-route db)]
    (:database params)))

(defn start-editing-title!
  "Start editing a note title"
  [note-id title]
  (reset! editing-note-id note-id)
  (reset! editing-note-title (or title "")))

(defn cancel-editing-title!
  "Cancel editing title"
  []
  (reset! editing-note-id nil)
  (reset! editing-note-title ""))

(defn save-note-title!
  "Save the edited note title"
  [note-id database-name]
  (let [new-title @editing-note-title]
    (when (not (empty? new-title))
      ;; Update local datascript
      (d/transact! db/dsdb
        [[:db/add [:hulunote-notes/id note-id] :hulunote-notes/title new-title]])
      ;; Sync to backend
      (re-frame/dispatch-sync
        [:update-note
         {:note-id note-id
          :title new-title}]))
    ;; Clear editing state
    (cancel-editing-title!)))

(defn handle-title-key-down
  "Handle keyboard events for title editing"
  [e note-id database-name]
  (let [key-code (.-keyCode e)]
    (cond
      ;; Enter key - save
      (= key-code 13)
      (do
        (.preventDefault e)
        (save-note-title! note-id database-name))
      ;; Escape key - cancel
      (= key-code 27)
      (cancel-editing-title!))))

(rum/defc note-title-editor < rum/reactive
  "Editable note title component"
  [note-id note-title database-name]
  (let [is-editing (= note-id (rum/react editing-note-id))]
    (if is-editing
      [:input.note-title-input
       {:type "text"
        :auto-focus true
        :value (rum/react editing-note-title)
        :on-change #(reset! editing-note-title (.. % -target -value))
        :on-key-down #(handle-title-key-down % note-id database-name)
        :on-blur #(save-note-title! note-id database-name)}]
      [:div.note-title
       {:on-click #(start-editing-title! note-id note-title)}
       note-title])))

(rum/defc diaries-page < rum/reactive
  [db]
  (let [daily-list (db/sort-daily-list (db/get-daily-list db))
        database-name (get-current-database-name db)
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)]
    [:div.page-wrapper.night-center-boxBg.night-textColor-2
     
     ;; Left sidebar
     (sidebar/left-sidebar db database-name)
     
     ;; Main content area
     [:div.main-content-area
      {:class (when sidebar-collapsed? "sidebar-collapsed")}
      [:div.flex.flex-column.overflow-scroll-new
       {:style {:padding "20px"
                :max-width "900px"
                :margin "0 auto"}}
       
       (if (empty? daily-list)
         ;; Empty state - show message and create first note button
         [:div.flex.flex-column.items-center.justify-center
          {:style {:height "80vh"}}
          [:div {:style {:font-size "24px" :margin-bottom "20px"}} 
           "No notes yet"]
          [:div {:style {:color "rgba(255,255,255,0.6)" :margin-bottom "30px"}}
           "Create your first note to get started"]
          [:button.new-note-btn
           {:on-click #(sidebar/create-new-note! database-name)}
           [:span.new-note-btn-icon "+"]
           "Create First Note"]]
         
         ;; Show existing notes
         (for [item daily-list]
           (let [[note-title note-id root-nav-id] item]
             [:div {:key note-id
                    :style {:margin-bottom "40px"}}
              ;; Editable note title
              [:div.note-title-wrapper
               (note-title-editor note-id note-title database-name)]
              
              ;; Nav outline
              [:div {:style {:padding-left "12px"}}
               (render/render-navs db root-nav-id note-id database-name)]
              
              ;; Separator
              [:div {:style {:padding "35px 0"}}
               [:div {:style {:background "rgba(151, 151, 151, 0.25)"
                              :height "1px" :width "100%"}}]]])))
       
       [:div {:style {:height "100px"}}]]]]))
