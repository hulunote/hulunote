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

;; State to track if we've initialized the daily note
(defonce daily-note-initialized? (atom #{}))

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
  "Read-only note title component for diaries list"
  [note-id note-title database-name]
  [:div.note-title
   {:style {:cursor "pointer"}
    :on-click #(router/go-to-note! database-name note-id)}
   note-title])

;; Lifecycle mixin to initialize daily note when component mounts
(def daily-note-init-mixin
  {:did-mount
   (fn [state]
     (let [[db] (:rum/args state)
           database-name (get-current-database-name db)]
       (when (and database-name (not (@daily-note-initialized? database-name)))
         (swap! daily-note-initialized? conj database-name)
         ;; Ensure today's daily note exists
         (sidebar/ensure-daily-note! database-name
           {:navigate? false
            :on-ready (fn [note-info]
                       (prn "Daily note ready:" note-info))})))
     state)})

(rum/defc diaries-page < rum/reactive daily-note-init-mixin
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
      ;; Back button
      [:div {:style {:padding "8px 16px"}}
       [:button
        {:on-click #(js/history.back)
         :style {:background "transparent"
                 :border "1px solid rgba(255,255,255,0.2)"
                 :color "#fff"
                 :padding "6px 12px"
                 :border-radius "4px"
                 :cursor "pointer"
                 :font-size "13px"}}
        "← Back"]]
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
           "Create First Note"]
          ;; Add quick create today's note button
          [:button
           {:style {:margin-top "16px"
                    :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                    :color "#fff"
                    :border "none"
                    :border-radius "8px"
                    :padding "12px 24px"
                    :font-size "14px"
                    :cursor "pointer"}
            :on-click #(sidebar/ensure-daily-note! database-name {:navigate? true})}
           (str "📅 Create Today's Note (" (sidebar/get-today-title) ")")]]
         
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
              [:div {:style {:padding "calc(var(--space-4xl) + var(--space-lg)) 0 calc(var(--space-xl) + 3px) 0"}}
               [:div {:style {:background "rgba(151, 151, 151, 0.25)"
                              :height "1px" :width "100%"}}]]])))
       
       [:div {:style {:height "100px"}}]]]]))
