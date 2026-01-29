(ns hulunote.single-note
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.render :as render]
            [hulunote.db :as db]
            [hulunote.sidebar :as sidebar]
            [hulunote.router :as router]
            [re-frame.core :as re-frame]))

;; State for editing note title
(defonce editing-note-id (atom nil))
(defonce editing-note-title (atom ""))

(defn get-route-params
  "Get route params from db"
  [db]
  (let [{:keys [params]} (db/get-route db)]
    params))

(defn get-note-by-id
  "Get note by id from datascript"
  [db note-id]
  (let [result (d/q
                 '[:find ?title ?root-nav-id
                   :in $ ?note-id
                   :where
                   [?e :hulunote-notes/id ?note-id]
                   [?e :hulunote-notes/title ?title]
                   [?e :hulunote-notes/root-nav-id ?root-nav-id]]
                 db note-id)]
    (first result)))

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
  [note-id]
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
  [e note-id]
  (let [key-code (.-keyCode e)]
    (cond
      ;; Enter key - save
      (= key-code 13)
      (do
        (.preventDefault e)
        (save-note-title! note-id))
      ;; Escape key - cancel
      (= key-code 27)
      (cancel-editing-title!))))

(rum/defc note-title-editor < rum/reactive
  "Editable note title component"
  [note-id note-title]
  (let [is-editing (= note-id (rum/react editing-note-id))]
    (if is-editing
      [:input.note-title-input
       {:type "text"
        :auto-focus true
        :value (rum/react editing-note-title)
        :on-change #(reset! editing-note-title (.. % -target -value))
        :on-key-down #(handle-title-key-down % note-id)
        :on-blur #(save-note-title! note-id)}]
      [:div.note-title
       {:on-click #(start-editing-title! note-id note-title)}
       note-title])))

(rum/defc single-note-page < rum/reactive
  [db]
  (let [{:keys [database note-id]} (get-route-params db)
        note-info (when note-id (get-note-by-id db note-id))
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)]
    [:div.page-wrapper.night-center-boxBg.night-textColor-2
     
     ;; Left sidebar
     (sidebar/left-sidebar db database)
     
     ;; Main content area
     [:div.main-content-area
      {:class (when sidebar-collapsed? "sidebar-collapsed")}
      [:div.flex.flex-column.overflow-scroll-new
       {:style {:padding "20px"
                :max-width "900px"
                :margin "0 auto"
                :min-height "100vh"}}
       
       (if note-info
         (let [[note-title root-nav-id] note-info]
           [:div
            ;; Back button
            [:div {:style {:margin-bottom "16px"}}
             [:button
              {:on-click #(router/go-to-diaries! database)
               :style {:background "transparent"
                       :border "1px solid rgba(255,255,255,0.2)"
                       :color "#fff"
                       :padding "6px 12px"
                       :border-radius "4px"
                       :cursor "pointer"
                       :font-size "13px"}}
              "← Back to Diaries"]]
            
            ;; Editable note title
            [:div.note-title-wrapper
             {:style {:margin-bottom "24px"}}
             (note-title-editor note-id note-title)]
            
            ;; Nav outline
            [:div {:style {:padding-left "12px"}}
             (render/render-navs db root-nav-id note-id database)]])
         
         ;; Note not found
         [:div.flex.flex-column.items-center.justify-center
          {:style {:height "50vh"}}
          [:div {:style {:font-size "18px" :margin-bottom "16px"}} 
           "Note not found"]
          [:div {:style {:color "rgba(255,255,255,0.5)" :margin-bottom "20px"}}
           (str "Note ID: " note-id)]
          [:button
           {:on-click #(router/go-to-diaries! database)
            :style {:background "#4a90d9"
                    :border "none"
                    :color "#fff"
                    :padding "10px 20px"
                    :border-radius "6px"
                    :cursor "pointer"}}
           "Go to Diaries"]])
       
       [:div {:style {:height "100px"}}]]]]))
