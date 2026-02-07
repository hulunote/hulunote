(ns hulunote.database
  (:require [datascript.core :as d]
            [hulunote.db :as db]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.storage :as storage]
            [hulunote.components :as comps]
            [hulunote.http :as http]
            [re-frame.core :as re-frame]))

;; ==================== Helper Functions ====================
(defn remove-nil-values
  "Remove nil values from a map to avoid datascript errors"
  [m]
  (into {} (remove (fn [[k v]] (nil? v)) m)))

;; ==================== State ====================
(defonce context-menu-state (atom {:visible false
                                   :x 0
                                   :y 0
                                   :database-name nil
                                   :database-id nil}))

(defonce create-modal-state (atom {:visible false
                                   :database-name ""}))

;; ==================== Context Menu Component ====================
(rum/defc context-menu < rum/reactive []
  (let [{:keys [visible x y database-name database-id]} (rum/react context-menu-state)]
    (when visible
      [:div.context-menu
       {:style {:position "fixed"
                :left (str x "px")
                :top (str y "px")
                :background "#fff"
                :border-radius "8px"
                :box-shadow "0 4px 12px rgba(0,0,0,0.15)"
                :padding "8px 0"
                :min-width "150px"
                :z-index 10000}
        :on-mouse-leave #(swap! context-menu-state assoc :visible false)}
       [:div.context-menu-item.pointer
        {:style {:padding "10px 16px"
                 :display "flex"
                 :align-items "center"
                 :gap "8px"
                 :transition "background 0.2s"}
         :on-mouse-enter #(set! (.. % -target -style -background) "#f5f5f5")
         :on-mouse-leave #(set! (.. % -target -style -background) "transparent")
         :on-click (fn [e]
                     (when (js/confirm (str "Are you sure you want to delete \"" database-name "\"? This action cannot be undone."))
                       (re-frame/dispatch
                         [:delete-database
                          {:database-id database-id
                           :database-name database-name
                           :op-fn (fn [_]
                                    ;; Remove from local datascript
                                    (d/transact! db/dsdb
                                      [[:db/retractEntity [:hulunote-databases/id database-id]]])
                                    (u/alert (str "Database \"" database-name "\" deleted successfully")))}]))
                     (swap! context-menu-state assoc :visible false))}
        [:span {:style {:color "#ff4d4f"}} "🗑️"]
        [:span {:style {:color "#ff4d4f"}} "Delete Database"]]])))

;; ==================== Create Modal Component ====================
(rum/defc create-modal < rum/reactive []
  (let [{:keys [visible database-name]} (rum/react create-modal-state)]
    (when visible
      [:div.modal-overlay
       {:style {:position "fixed"
                :top 0
                :left 0
                :right 0
                :bottom 0
                :background "rgba(0,0,0,0.5)"
                :display "flex"
                :align-items "center"
                :justify-content "center"
                :z-index 10000}
        :on-click #(swap! create-modal-state assoc :visible false)}
       [:div.modal-content
        {:style {:background "#fff"
                 :border-radius "16px"
                 :padding "32px"
                 :min-width "400px"
                 :box-shadow "0 8px 32px rgba(0,0,0,0.2)"}
         :on-click #(.stopPropagation %)}
        [:h2 {:style {:margin "0 0 24px 0"
                      :font-size "24px"
                      :font-weight "600"
                      :color "#1a1a2e"}}
         "Create New Database"]
        [:div {:style {:margin-bottom "24px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "#666"}}
          "Database Name"]
         [:input
          {:type "text"
           :placeholder "Enter database name..."
           :value database-name
           :on-change #(swap! create-modal-state assoc :database-name (.. % -target -value))
           :on-key-down (fn [e]
                          (when (= (.-key e) "Enter")
                            (let [name (clojure.string/trim database-name)]
                              (when (not (empty? name))
                                (re-frame/dispatch
                                  [:create-database
                                   {:database-name name
                                    :op-fn (fn [data]
                                             (when-let [db-info (:database data)]
                                               ;; Remove nil values before storing in datascript
                                               (d/transact! db/dsdb [(remove-nil-values db-info)]))
                                             (swap! create-modal-state assoc :visible false :database-name "")
                                             (u/alert (str "Database \"" name "\" created successfully")))}])))))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "2px solid #e0e0e0"
                   :border-radius "8px"
                   :font-size "16px"
                   :outline "none"
                   :transition "border-color 0.2s"}}]]
        [:div {:style {:display "flex"
                       :justify-content "flex-end"
                       :gap "12px"}}
         [:button.pointer
          {:on-click #(swap! create-modal-state assoc :visible false :database-name "")
           :style {:padding "12px 24px"
                   :border "2px solid #e0e0e0"
                   :border-radius "8px"
                   :background "#fff"
                   :font-size "16px"
                   :font-weight "500"
                   :color "#666"
                   :cursor "pointer"}}
          "Cancel"]
         [:button.pointer
          {:on-click (fn []
                       (let [name (clojure.string/trim database-name)]
                         (when (not (empty? name))
                           (re-frame/dispatch
                             [:create-database
                              {:database-name name
                               :op-fn (fn [data]
                                        (when-let [db-info (:database data)]
                                          ;; Remove nil values before storing in datascript
                                          (d/transact! db/dsdb [(remove-nil-values db-info)]))
                                        (swap! create-modal-state assoc :visible false :database-name "")
                                        (u/alert (str "Database \"" name "\" created successfully")))}]))))
           :style {:padding "12px 24px"
                   :border "none"
                   :border-radius "8px"
                   :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                   :font-size "16px"
                   :font-weight "600"
                   :color "#fff"
                   :cursor "pointer"}}
          "Create"]]]])))

;; ==================== Database Card Component ====================
(rum/defc database-card [name database-id on-click]
  [:div.flex.pointer.database-card
   {:on-click on-click
    :on-context-menu (fn [e]
                       (.preventDefault e)
                       (swap! context-menu-state assoc
                              :visible true
                              :x (.-clientX e)
                              :y (.-clientY e)
                              :database-name name
                              :database-id database-id))
    :style {:background "#fff"
            :border-radius "12px"
            :padding "32px 24px"
            :box-shadow "0 2px 12px rgba(0,0,0,0.08)"
            :transition "all 0.3s ease"
            :border "2px solid transparent"
            :min-width "200px"}}
   [:div.flex.flex-column.items-center.w-100
    [:div {:style {:font-size "40px"
                   :margin-bottom "16px"}}
     "📚"]
    [:div {:style {:font-size "18px"
                   :font-weight "600"
                   :color "#1a1a2e"
                   :text-align "center"
                   :word-break "break-word"}}
     name]]])

;; ==================== Empty State Component ====================
(rum/defc empty-state []
  [:div.flex.flex-column.items-center
   {:style {:padding "60px 20px"
            :text-align "center"}}
   [:div {:style {:font-size "64px"
                  :margin-bottom "24px"}}
    "📝"]
   [:h3 {:style {:font-size "24px"
                 :font-weight "600"
                 :color "#1a1a2e"
                 :margin "0 0 12px 0"}}
    "No Databases Yet"]
   [:p {:style {:font-size "16px"
                :color "#666"
                :margin "0 0 32px 0"}}
    (if (u/is-expired?)
      "Login to create your first note database"
      "Click the \"+ New Database\" button to create your first database")]
   (if (u/is-expired?)
     [:button.pointer
      {:on-click #(router/switch-router! "/login")
       :style {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
               :color "#fff"
               :border "none"
               :padding "12px 28px"
               :border-radius "25px"
               :font-size "16px"
               :font-weight "600"
               :cursor "pointer"}}
      "Login Now"]
     [:button.pointer
      {:on-click #(swap! create-modal-state assoc :visible true)
       :style {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
               :color "#fff"
               :border "none"
               :padding "12px 28px"
               :border-radius "25px"
               :font-size "16px"
               :font-weight "600"
               :cursor "pointer"}}
      "+ Create Database"])])

;; ==================== Main Database List Page ====================
(rum/defcs database-page
  < {:will-mount
     (fn [state]
       (re-frame/dispatch-sync
         [:get-database-list
          {:op-fn
           (fn [{:keys [database-list settings]}]
             (doseq [item database-list]
               ;; Remove nil values before storing in datascript
               (d/transact! db/dsdb [(remove-nil-values item)])))}])
       state)}
  rum/reactive
  [state db]
  (let [database-list (db/get-database db)
        _ (rum/react context-menu-state)  ;; Subscribe to context menu state
        _ (rum/react create-modal-state)] ;; Subscribe to create modal state
    [:div.flex.flex-column
     {:style {:min-height "100vh"
              :background "#f8f9fa"}
      :on-click #(swap! context-menu-state assoc :visible false)}
     
     ;; Header (with macOS traffic light padding)
     [:div.td-navbar
      {:style {:display "flex"
               :align-items "center"
               :justify-content "space-between"
               :padding "0 32px"
               :padding-top "28px"
               :height "60px"
               :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
               :-webkit-app-region "drag"}}
      [:div.flex.items-center
       {:style {:-webkit-app-region "no-drag"}}
       [:img.pointer
        {:on-click #(router/switch-router! "/main")
         :width "36px"
         :style {:border-radius "50%"}
         :src (u/asset-path "/img/hulunote.webp")}]
       [:div.pl3.pointer
        {:on-click #(router/switch-router! "/main")
         :style {:font-size "22px"
                 :font-weight "700"
                 :color "#fff"}}
        "HULUNOTE"]]
      [:div.flex.items-center
       (if (u/is-expired?)
         [:button.pointer
          {:on-click #(router/switch-router! "/login")
           :style {:background "#fff"
                   :color "#667eea"
                   :border "none"
                   :padding "8px 20px"
                   :border-radius "20px"
                   :font-weight "600"
                   :cursor "pointer"}}
          "Login"]
         [:div
          {:style {:color "#fff"}}
          (first (clojure.string/split (:accounts/mail (:hulunote @storage/jwt-auth)) "@"))])]]
     
     ;; Main content
     [:div.flex.flex-column
      {:style {:flex "1"
               :padding "40px 20px"
               :max-width "1200px"
               :margin "0 auto"
               :width "100%"}}
      
      ;; Title row with create button
      [:div.flex.flex-row.items-center.justify-between
       {:style {:margin-bottom "32px"
                :margin-top "60px"}}
       [:div.flex.flex-column
        [:h1 {:style {:font-size "32px"
                      :font-weight "700"
                      :color "#1a1a2e"
                      :margin "0"}}
         "My Databases"]
        [:div {:style {:color "#666"
                       :font-size "14px"
                       :margin-top "8px"}}
         (str (count database-list) " database(s)")]]
       
       ;; Create button (only show when logged in)
       (when-not (u/is-expired?)
         [:button.pointer
          {:on-click #(swap! create-modal-state assoc :visible true)
           :style {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                   :color "#fff"
                   :border "none"
                   :padding "12px 24px"
                   :border-radius "25px"
                   :font-size "16px"
                   :font-weight "600"
                   :cursor "pointer"
                   :display "flex"
                   :align-items "center"
                   :gap "8px"
                   :box-shadow "0 4px 12px rgba(102, 126, 234, 0.4)"
                   :transition "all 0.3s ease"}}
          [:span {:style {:font-size "20px"}} "+"]
          [:span "New Database"]])]
      
      ;; Database list or empty state
      (if (empty? database-list)
        (empty-state)
        [:div
         {:style {:display "grid"
                  :grid-template-columns "repeat(auto-fill, minmax(220px, 1fr))"
                  :gap "20px"}}
         (for [item database-list]
           (let [db-item (first item)
                 db-name (:hulunote-databases/name db-item)
                 db-id (:hulunote-databases/id db-item)]
             (rum/with-key
               (database-card
                 db-name
                 db-id
                 (fn []
                   (http/database-data-load db-name)
                   (router/go-to-diaries! db-name)))
               db-id)))])]
     
     ;; Context menu
     (context-menu)
     
     ;; Create modal
     (create-modal)
     
     ;; Footer
     [:div
      {:style {:background "#1a1a2e"
               :padding "24px 20px"
               :text-align "center"}}
      [:div {:style {:color "rgba(255,255,255,0.5)"
                     :font-size "14px"}}
       "© 2024 Hulunote - MIT License"]]]))
