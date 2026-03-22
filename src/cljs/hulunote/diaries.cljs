(ns hulunote.diaries
  (:require [rum.core :as rum]
            [hulunote.render :as render]
            [hulunote.db :as db]
            [hulunote.http :as http]
            [hulunote.sidebar :as sidebar]
            [hulunote.router :as router]))

;; State to track if we've initialized the daily note
(defonce daily-note-initialized? (atom #{}))

(declare get-current-database-name)

(defn- maybe-initialize-daily-note!
  [db]
  (let [database-name (get-current-database-name db)]
    (when (and database-name
               (http/note-list-loaded? database-name)
               (not (@daily-note-initialized? database-name)))
      (swap! daily-note-initialized? conj database-name)
      (sidebar/ensure-daily-note! database-name
        {:navigate? false
         :on-ready (fn [note-info]
                     (prn "Daily note ready:" note-info))}))))

(defn get-current-database-name
  "Get current database name from route params"
  [db]
  (let [{:keys [params]} (db/get-route db)]
    (:database params)))

(rum/defc note-title-link
  "Read-only note title that opens the single note page"
  [note-id note-title database-name]
  [:div.note-title
   {:on-click #(router/go-to-note! database-name note-id)}
   note-title])

;; Lifecycle mixin to initialize daily note when component mounts
(def daily-note-init-mixin
  {:did-mount
   (fn [state]
     (let [[db] (:rum/args state)]
       (maybe-initialize-daily-note! db))
     state)
   :did-update
   (fn [state]
     (let [[db] (:rum/args state)]
       (maybe-initialize-daily-note! db))
     state)})

(rum/defc diaries-page < rum/reactive daily-note-init-mixin
  [db]
  (let [daily-list (db/sort-daily-list (db/get-daily-list db))
        database-name (get-current-database-name db)
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)
        right-sidebar-open? (rum/react db/right-sidebar-open?)]
    [:div.night-center-boxBg.night-textColor-2
     (sidebar/app-top-bar {:title "Diaries"})
     [:div.page-wrapper
      ;; Left sidebar
      (sidebar/left-sidebar db database-name)
      ;; Main content area
      [:div.main-content-area
       {:class (str (when sidebar-collapsed? "sidebar-collapsed")
                    (when right-sidebar-open? " right-sidebar-open"))
        :style {:overflow-y "auto"
                :height "calc(100vh - var(--app-topbar-height))"
                :min-height 0}}
       [:div.diaries-page-content.flex.flex-column

        (if (empty? daily-list)
          ;; Empty state - show message and create first note button
          [:div.diaries-empty-state.flex.flex-column.items-center.justify-center
           [:div {:style {:font-size "24px" :margin-bottom "20px"}}
            "No notes yet"]
           [:div {:style {:color "var(--app-text-muted)" :margin-bottom "30px"}}
            "Create your first note to get started"]
           [:button.new-note-btn
            {:on-click #(sidebar/create-new-note! database-name)}
            [:span.new-note-btn-icon "+"]
            "Create First Note"]
           ;; Add quick create today's note button
           [:button
            {:style {:margin-top "16px"
                     :background "var(--theme-accent-gradient)"
                     :color "var(--theme-accent-text)"
                     :border "none"
                     :border-radius "8px"
                     :padding "12px 24px"
                     :font-size "14px"
                     :cursor "pointer"}
             :on-click #(sidebar/ensure-daily-note! database-name {:navigate? true})}
            (str "Create Today's Note (" (sidebar/get-today-title) ")")]]

          ;; Show existing notes
          [:div.diaries-list
           (for [item daily-list]
             (let [[note-title note-id root-nav-id] item]
               [:section.diary-entry {:key note-id}
                ;; Clickable note title
                [:div.diary-entry-title
                 (note-title-link note-id note-title database-name)]

                ;; Nav outline
                [:div.diary-entry-body
                 (render/render-navs db root-nav-id note-id database-name)]]))])]]]]))
