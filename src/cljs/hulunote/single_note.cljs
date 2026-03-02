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

;; State for note title context menu
(defonce title-menu-state (atom {:visible false
                                  :x 0
                                  :y 0
                                  :note-id nil
                                  :note-title nil
                                  :root-nav-id nil
                                  :database-name nil}))

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

;; ==================== Title Context Menu Functions ====================

(defn show-title-menu!
  "Show title context menu at specified position"
  [e note-id note-title root-nav-id database-name]
  (.preventDefault e)
  (.stopPropagation e)
  (reset! title-menu-state
    {:visible true
     :x (.-clientX e)
     :y (.-clientY e)
     :note-id note-id
     :note-title note-title
     :root-nav-id root-nav-id
     :database-name database-name}))

(defn hide-title-menu!
  "Hide title context menu"
  []
  (swap! title-menu-state assoc :visible false))

(defn get-all-navs-content
  "Recursively get all nav content as markdown with proper indentation"
  [db nav-id depth]
  (let [nav (u/get-nav-sub-navs-sorted db nav-id)
        children (:parid nav)
        nav-info (u/get-nav-by-id db nav-id)
        content (:content nav-info)
        indent (apply str (repeat depth "  "))]
    (if (seq children)
      (str 
        (when (and content (not= content "ROOT"))
          (str indent "- " content "\n"))
        (apply str 
          (map #(get-all-navs-content db (:id %) (if (= content "ROOT") depth (inc depth))) 
               children)))
      (when (and content (not= content "ROOT"))
        (str indent "- " content "\n")))))

(defn copy-note-as-markdown!
  "Copy entire note content as markdown to clipboard"
  [note-title root-nav-id]
  (let [content (get-all-navs-content @db/dsdb root-nav-id 0)
        markdown (str "# " note-title "\n\n" content)
        textarea (.createElement js/document "textarea")]
    (set! (.-value textarea) markdown)
    (set! (.-style textarea) "position: fixed; left: -9999px;")
    (.appendChild (.-body js/document) textarea)
    (.select textarea)
    (.execCommand js/document "copy")
    (.removeChild (.-body js/document) textarea)
    (u/alert "Note copied as Markdown!")))

(defn delete-note!
  "Delete a note and navigate back"
  [note-id database-name]
  ;; Update backend
  (re-frame/dispatch-sync
    [:update-note
     {:note-id note-id
      :is-delete true
      :op-fn (fn [data]
               (prn "Note deleted:" data))}])
  ;; Remove from local datascript
  (d/transact! db/dsdb
    [[:db/retractEntity [:hulunote-notes/id note-id]]])
  ;; Navigate back to diaries/all notes
  (router/go-to-all-notes! database-name))

(rum/defc title-context-menu < rum/reactive
  "Context menu component for note title"
  []
  (let [{:keys [visible x y note-id note-title root-nav-id database-name]} (rum/react title-menu-state)]
    (when visible
      [:div.title-context-menu
       {:style {:position "fixed"
                :left (str x "px")
                :top (str y "px")
                :background "#2a2f3a"
                :border "1px solid #444"
                :border-radius "6px"
                :box-shadow "0 4px 12px rgba(0,0,0,0.3)"
                :z-index 10000
                :min-width "180px"
                :padding "4px 0"}
        :on-mouse-leave hide-title-menu!}
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
       ;; Edit title option
       [:div.context-menu-item
        {:style {:padding "8px 12px"
                 :cursor "pointer"
                 :color "#fff"
                 :font-size "13px"}
         :on-mouse-over #(set! (-> % .-target .-style .-background) "#3a4555")
         :on-mouse-out #(set! (-> % .-target .-style .-background) "transparent")
         :on-click (fn [e]
                     (.stopPropagation e)
                     (start-editing-title! note-id note-title)
                     (hide-title-menu!))}
        "✏️ Edit Title"]
       ;; Copy as markdown option
       [:div.context-menu-item
        {:style {:padding "8px 12px"
                 :cursor "pointer"
                 :color "#fff"
                 :font-size "13px"}
         :on-mouse-over #(set! (-> % .-target .-style .-background) "#3a4555")
         :on-mouse-out #(set! (-> % .-target .-style .-background) "transparent")
         :on-click (fn [e]
                     (.stopPropagation e)
                     (copy-note-as-markdown! note-title root-nav-id)
                     (hide-title-menu!))}
        "📋 Copy as Markdown"]
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
                       (delete-note! note-id database-name))
                     (hide-title-menu!))}
        "🗑️ Delete Note"]])))

(rum/defc note-title-editor < rum/reactive
  "Editable note title component with right-click context menu"
  [note-id note-title root-nav-id database-name]
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
       {:on-click #(start-editing-title! note-id note-title)
        :on-context-menu (fn [e]
                           (show-title-menu! e note-id note-title root-nav-id database-name))
        :style {:cursor "pointer"}}
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
      ;; Back button
      [:div {:style {:padding "8px 16px"}}
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
      [:div.flex.flex-column.overflow-scroll-new
       {:style {:padding "20px"
                :max-width "900px"
                :margin "0 auto"
                :min-height "100vh"}}

       (if note-info
         (let [[note-title root-nav-id] note-info]
           [:div
            ;; Editable note title with context menu
            [:div.note-title-wrapper
             {:style {:margin-bottom "24px"}}
             (note-title-editor note-id note-title root-nav-id database)]
            
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
            :style {:background "var(--theme-accent)"
                    :border "none"
                    :color "#fff"
                    :padding "10px 20px"
                    :border-radius "6px"
                    :cursor "pointer"}}
           "Go to Diaries"]])
       
       [:div {:style {:height "100px"}}]]]
     
     ;; Global title context menu
     (title-context-menu)
     
     ;; Global nav context menu (from render.cljs)
     (render/global-context-menu)]))
