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

(defn get-current-database-name
  "Get current database name from route params"
  [db]
  (let [{:keys [params]} (db/get-route db)]
    (:database params)))

(defn get-all-notes-sorted
  "Get all notes sorted by updated-at desc"
  [db]
  (let [note-eids (d/q
                    '[:find [?e ...]
                      :where
                      [?e :hulunote-notes/id]]
                    db)
        notes (d/pull-many db
                '[:hulunote-notes/id
                  :hulunote-notes/title
                  :hulunote-notes/root-nav-id
                  :hulunote-notes/updated-at
                  :hulunote-notes/created-at]
                note-eids)]
    (->> notes
         (map (fn [note]
                (let [updated-at (or (:hulunote-notes/updated-at note)
                                     (:updated-at note)
                                     "1970-01-01")
                      created-at (or (:hulunote-notes/created-at note)
                                     (:created-at note)
                                     updated-at
                                     "1970-01-01")]
                  {:note-id (:hulunote-notes/id note)
                   :note-title (:hulunote-notes/title note)
                   :root-nav-id (:hulunote-notes/root-nav-id note)
                   :updated-at updated-at
                   :created-at created-at})))
         (sort-by :updated-at)
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

(defn format-note-date
  [date-value]
  (if (and date-value (seq date-value))
    (u/moment-format date-value)
    "Unknown"))

(defn go-to-page! [page]
  (reset! current-page page))

(defn toggle-note-selection!
  [selected-note-ids note-id checked?]
  (swap! selected-note-ids
    (fn [selected]
      (if checked?
        (conj selected note-id)
        (disj selected note-id)))))

(defn toggle-page-selection!
  [selected-note-ids notes checked?]
  (let [note-ids (set (map :note-id notes))]
    (swap! selected-note-ids
      (fn [selected]
        (if checked?
          (into selected note-ids)
          (reduce disj selected note-ids))))))

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

(defn delete-selected-notes!
  [selected-note-ids]
  (let [selected-ids (vec @selected-note-ids)
        selected-count (count selected-ids)]
    (when (and (pos? selected-count)
               (js/confirm (str "Delete " selected-count " selected note"
                                (when (> selected-count 1) "s")
                                "?")))
      (doseq [note-id selected-ids]
        (delete-note! note-id))
      (reset! selected-note-ids #{}))))

(defn trash-icon
  [color]
  [:svg {:width "15"
         :height "15"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke color
         :stroke-width "1.9"
         :stroke-linecap "round"
         :stroke-linejoin "round"}
   [:path {:d "M3 6h18"}]
   [:path {:d "M8 6V4h8v2"}]
   [:path {:d "M19 6l-1 14H6L5 6"}]
   [:path {:d "M10 11v5"}]
   [:path {:d "M14 11v5"}]])

(rum/defc all-notes-sticky-header < rum/reactive
  [selected-note-ids paginated-notes]
  (let [selected (rum/react selected-note-ids)
        note-ids (map :note-id paginated-notes)
        all-selected? (and (seq note-ids)
                           (every? selected note-ids))
        selected-count (count selected)]
    [:div
     {:style {:position "sticky"
              :top "0"
              :z-index 20
              :margin-bottom "8px"}}
     [:div
      {:style {:display "grid"
               :grid-template-columns "44px minmax(0, 1fr) 140px 140px"
               :align-items "center"
               :min-height "48px"
               :margin-bottom "10px"}}
      [:div
       {:style {:grid-column "1 / span 2"
                :display "flex"
                :align-items "center"
                :gap "10px"}}
       [:button
        {:disabled (zero? selected-count)
         :title (if (pos? selected-count)
                  (str "Delete " selected-count " selected note"
                       (when (> selected-count 1) "s"))
                  "Delete")
         :on-click #(delete-selected-notes! selected-note-ids)
         :style {:display "inline-flex"
                 :align-items "center"
                 :justify-content "center"
                 :width "30px"
                 :height "30px"
                 :padding 0
                 :border-radius "8px"
                 :border "1px solid rgba(255,255,255,0.12)"
                 :background (if (zero? selected-count)
                               "rgba(255,255,255,0.04)"
                               "rgba(255,107,107,0.14)")
                 :color (if (zero? selected-count)
                          "rgba(255,255,255,0.35)"
                          "#ff9b9b")
                 :cursor (if (zero? selected-count) "not-allowed" "pointer")}}
        (trash-icon (if (zero? selected-count)
                      "rgba(255,255,255,0.35)"
                      "#ff9b9b"))]]
      [:div
       {:style {:grid-column "3 / span 2"
                :display "flex"
                :align-items "center"
                :justify-content "flex-end"
                :gap "10px"}}
       [:div
        {:style {:display "flex"
                 :align-items "center"
                 :gap "8px"
                 :width "220px"
                 :height "32px"
                 :padding "0 10px"
                 :border-radius "8px"
                 :background "rgba(255,255,255,0.06)"
                 :border "1px solid rgba(255,255,255,0.1)"}}
        [:img {:src (u/asset-path "/img/icons/search.svg")
               :width "15px"
               :height "15px"
               :style {:opacity 0.72
                       :filter "invert(1) brightness(0.82)"}}]
        [:input
         {:type "text"
          :placeholder "Search All Pages"
          :readOnly true
          :style {:width "100%"
                  :background "transparent"
                  :border "none"
                  :outline "none"
                  :padding 0
                  :color "rgba(255,255,255,0.72)"
                  :font-size "13px"}}]]
       [:button
        {:title "Calendar"
         :style {:display "inline-flex"
                 :align-items "center"
                 :justify-content "center"
                 :width "32px"
                 :height "32px"
                 :padding 0
                 :border-radius "8px"
                 :border "1px solid rgba(255,255,255,0.1)"
                 :background "rgba(255,255,255,0.04)"
                 :cursor "default"}}
        [:img {:src (u/asset-path "/img/icons/calendar_month.svg")
               :width "16px"
               :height "16px"
               :style {:opacity 0.78
                       :filter "invert(1) brightness(0.82)"}}]]]]
     [:div
      {:style {:display "grid"
               :grid-template-columns "44px minmax(0, 1fr) 140px 140px"
               :align-items "center"
               :gap "0"
               :min-height "44px"
               :padding "0 14px"
               :background "rgba(47, 53, 66, 0.95)"
               :backdrop-filter "blur(8px)"
               :border "1px solid rgba(255,255,255,0.08)"
               :border-radius "10px"
               :font-size "12px"
               :font-weight "700"
               :letter-spacing "0.02em"
               :color "rgba(255,255,255,0.68)"
               :text-transform "uppercase"}}
      [:div {:style {:display "flex" :justify-content "center"}}
       [:input {:type "checkbox"
                :checked all-selected?
                :title "Select current page"
                :on-click #(.stopPropagation %)
                :on-change #(toggle-page-selection! selected-note-ids paginated-notes (.. % -target -checked))}]]
      [:div "Title"]
      [:div "Updated"]
      [:div "Created"]]]))

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
                 :background (if (= page 1) "#3d4455" "var(--theme-accent)")
                 :color "#fff"
                 :border "none"
                 :border-radius "4px"
                 :cursor (if (= page 1) "not-allowed" "pointer")
                 :opacity (if (= page 1) 0.5 1)}}
        "Prev"]

       ;; Page numbers
       [:div {:style {:display "flex" :gap "4px"}}
        (for [p (range 1 (inc pages))]
          [:button.pagination-num
           {:key p
            :on-click #(go-to-page! p)
            :style {:padding "8px 12px"
                    :background (if (= p page) "var(--theme-accent)" "transparent")
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
                 :background (if (= page pages) "#3d4455" "var(--theme-accent)")
                 :color "#fff"
                 :border "none"
                 :border-radius "4px"
                 :cursor (if (= page pages) "not-allowed" "pointer")
                 :opacity (if (= page pages) 0.5 1)}}
        "Next"]])))

(rum/defc note-row < rum/reactive
  [selected-note-ids {:keys [note-id note-title root-nav-id updated-at created-at]} database-name]
  (let [selected (rum/react selected-note-ids)
        checked? (contains? selected note-id)]
    [:div.note-card
   {:style {:display "grid"
            :grid-template-columns "44px minmax(0, 1fr) 140px 140px"
            :align-items "center"
            :gap "0"
            :padding "0 14px"
            :min-height "56px"
            :margin-bottom "8px"
            :background (if checked?
                          "rgba(102,126,234,0.12)"
                          "rgba(255,255,255,0.04)")
            :border-radius "10px"
            :cursor "default"
            :transition "all 0.2s ease"
            :border (if checked?
                      "1px solid rgba(102,126,234,0.35)"
                      "1px solid rgba(255,255,255,0.08)")}}
     [:div {:style {:display "flex" :justify-content "center"}}
      [:input {:type "checkbox"
               :checked checked?
               :on-click #(.stopPropagation %)
               :on-change #(toggle-note-selection! selected-note-ids note-id (.. % -target -checked))}]]
     [:div
      {:style {:min-width 0
               :display "flex"
               :align-items "center"}}
      [:div
       {:style {:min-width 0
                :font-size "15px"
                :font-weight "500"
                :cursor "pointer"
                :white-space "nowrap"
                :overflow "hidden"
                :text-overflow "ellipsis"}
        :title note-title
        :on-mouse-down (fn [e]
                         (when (.-shiftKey e)
                           (.preventDefault e)))
        :on-click (fn [e]
                    (.stopPropagation e)
                    (if (.-shiftKey e)
                      (db/open-note-in-right-sidebar! note-id note-title root-nav-id database-name)
                      (router/go-to-note! database-name note-id)))}
       note-title]]
     [:div {:style {:font-size "13px"
                    :color "rgba(255,255,255,0.72)"}}
      (format-note-date updated-at)]
     [:div {:style {:font-size "13px"
                    :color "rgba(255,255,255,0.58)"}}
      (format-note-date created-at)]]))

(rum/defcs all-notes-page < rum/reactive
  (rum/local #{} ::selected-note-ids)
  (rum/local nil ::selected-database)
  [state db]
  (let [selected-note-ids (::selected-note-ids state)
        selected-database (::selected-database state)
        database-name (get-current-database-name db)
        all-notes (get-all-notes-sorted db)
        page (rum/react current-page)
        paginated-notes (get-paginated-notes all-notes page)
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)
        right-sidebar-open? (rum/react db/right-sidebar-open?)]
    (when (not= @selected-database database-name)
      (reset! selected-note-ids #{})
      (reset! selected-database database-name)
      (reset! current-page 1))
    [:div.night-center-boxBg.night-textColor-2
     (sidebar/app-top-bar {:title "All Notes"})
     [:div.page-wrapper
      ;; Left sidebar
      (sidebar/left-sidebar db database-name)
      ;; Main content area
      [:div.main-content-area
       {:class (str (when sidebar-collapsed? "sidebar-collapsed")
                    (when right-sidebar-open? " right-sidebar-open"))}
       [:div.flex.flex-column
        {:style {:padding "10px 20px 20px"
                 :max-width "900px"
                 :margin "0 auto"}}

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
           (all-notes-sticky-header selected-note-ids paginated-notes)
           ;; Note cards
           (for [note paginated-notes]
             (rum/with-key
               (note-row selected-note-ids note database-name)
               (:note-id note)))

           ;; Pagination
           (pagination-controls (count all-notes))])

        [:div {:style {:height "50px"}}]]]]]))
