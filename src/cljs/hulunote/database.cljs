(ns hulunote.database
  (:require [datascript.core :as d]
            [hulunote.db :as db]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.storage :as storage]
            [hulunote.components :as comps]
            [hulunote.http :as http]
            [hulunote.settings :as settings]
            [re-frame.core :as re-frame]
            [clojure.string :as str]))

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

(defonce import-state (atom {:importing false
                              :result nil}))

(defonce user-menu-open? (atom false))

;; Holds the database-id to import into (set before opening file picker)
(defonce import-target-db-id (atom nil))

;; ==================== Import Helper ====================
(defn import-notes-to-database!
  "Upload multiple JSON files to import notes into a database via fetch API"
  [database-id files]
  (reset! import-state {:importing true :result nil})
  (let [form-data (js/FormData.)]
    (.append form-data "database-id" database-id)
    (doseq [file files]
      (.append form-data "files" file))
    (-> (js/fetch (http/http-uri "/hulunote/import-notes")
          (clj->js {:method "POST"
                    :headers {"X-FUNCTOR-API-TOKEN" (:token @storage/jwt-auth)}
                    :body form-data}))
        (.then (fn [resp] (.json resp)))
        (.then (fn [data]
                 (let [result (js->clj data :keywordize-keys true)]
                   (reset! import-state {:importing false :result result})
                   (if (:success result)
                     (u/alert (str "Import complete: " (:imported-count result) " note(s) imported"
                                   (when (> (:error-count result) 0)
                                     (str ", " (:error-count result) " error(s)"))))
                     (u/alert (str "Import failed: " (:error result)))))))
        (.catch (fn [err]
                  (reset! import-state {:importing false :result nil})
                  (u/alert (str "Import failed: " err)))))))

;; ==================== Context Menu Component ====================
(rum/defc context-menu < rum/reactive []
  (let [{:keys [visible x y database-name database-id]} (rum/react context-menu-state)
        {:keys [importing]} (rum/react import-state)]
    (when visible
      [:div.context-menu
       {:style {:position "fixed"
                :left (str x "px")
                :top (str y "px")
                :background "#fff"
                :border-radius "8px"
                :box-shadow "0 4px 12px rgba(0,0,0,0.15)"
                :padding "8px 0"
                :min-width "180px"
                :z-index 10000}
        :on-mouse-leave #(swap! context-menu-state assoc :visible false)}

       ;; Import JSON option
       [:div.context-menu-item.pointer
        {:style {:padding "10px 16px"
                 :display "flex"
                 :align-items "center"
                 :gap "8px"
                 :transition "background 0.2s"
                 :opacity (if importing 0.5 1)}
         :on-mouse-enter #(set! (.. % -target -style -background) "#f5f5f5")
         :on-mouse-leave #(set! (.. % -target -style -background) "transparent")
         :on-click (fn [e]
                     (.stopPropagation e)
                     (when-not importing
                       (reset! import-target-db-id database-id)
                       (swap! context-menu-state assoc :visible false)
                       (js/setTimeout
                         (fn []
                           (when-let [input (.getElementById js/document "import-json-input")]
                             (.click input)))
                         100)))}
        [:span {:style {:color "#667eea"}} "\uD83D\uDCE5"]
        [:span {:style {:color "#333"}} (if importing "Importing..." "Import JSON")]]

       ;; Import ZIP option
       [:div.context-menu-item.pointer
        {:style {:padding "10px 16px"
                 :display "flex"
                 :align-items "center"
                 :gap "8px"
                 :transition "background 0.2s"
                 :opacity (if importing 0.5 1)}
         :on-mouse-enter #(set! (.. % -target -style -background) "#f5f5f5")
         :on-mouse-leave #(set! (.. % -target -style -background) "transparent")
         :on-click (fn [e]
                     (.stopPropagation e)
                     (when-not importing
                       (reset! import-target-db-id database-id)
                       (swap! context-menu-state assoc :visible false)
                       (js/setTimeout
                         (fn []
                           (when-let [input (.getElementById js/document "import-zip-input")]
                             (.click input)))
                         100)))}
        [:span {:style {:color "#764ba2"}} "\uD83D\uDDDC\uFE0F"]
        [:span {:style {:color "#333"}} (if importing "Importing..." "Import ZIP")]]

       ;; Divider
       [:div {:style {:height "1px"
                      :background "#eee"
                      :margin "4px 0"}}]

       ;; Delete Database option
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
        [:span {:style {:color "#ff4d4f"}} "\uD83D\uDDD1\uFE0F"]
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
        _ (rum/react create-modal-state)  ;; Subscribe to create modal state
        _ (rum/react user-menu-open?)]    ;; Subscribe to user menu state
    [:div.flex.flex-column
     {:style {:min-height "100vh"
              :background "#f8f9fa"}
      :on-click (fn [_]
                  (swap! context-menu-state assoc :visible false)
                  (reset! user-menu-open? false))}
     
     ;; Header
     [:div.td-navbar
      {:style {:display "flex"
               :align-items "center"
               :justify-content "space-between"
               :padding "0 32px"
               :height "60px"
               :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"}}
      [:div.flex.items-center
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
         (let [hulunote-info (:hulunote @storage/jwt-auth)
               avatar-url (:accounts/avatar hulunote-info)
               username (or (:accounts/nickname hulunote-info)
                            (first (clojure.string/split
                                     (or (:accounts/mail hulunote-info) "") "@")))
               menu-open? (rum/react user-menu-open?)]
           [:div {:style {:position "relative"}}
            [:div.pointer.flex.items-center
             {:on-click (fn [e]
                          (.stopPropagation e)
                          (swap! user-menu-open? not))
              :style {:gap "8px"}}
             ;; Avatar circle
             [:div {:style {:width "32px" :height "32px" :border-radius "50%"
                            :background "rgba(255,255,255,0.3)"
                            :display "flex" :align-items "center" :justify-content "center"
                            :overflow "hidden" :border "2px solid rgba(255,255,255,0.5)"}}
              (if avatar-url
                [:img {:src (if (clojure.string/starts-with? (or avatar-url "") "http")
                              avatar-url
                              (str (http/http-uri "") avatar-url))
                       :style {:width "100%" :height "100%" :object-fit "cover"}}]
                [:span {:style {:color "#fff" :font-size "14px" :font-weight "600"}}
                 (-> (or username "U") first clojure.string/upper-case)])]
             [:span {:style {:color "#fff" :font-weight "500"}} username]
             ;; Dropdown arrow
             [:svg {:width "12" :height "12" :viewBox "0 0 24 24" :fill "#fff"
                    :style {:transition "transform 0.2s"
                            :transform (if menu-open? "rotate(180deg)" "rotate(0)")}}
              [:path {:d "M7 10l5 5 5-5z"}]]]
            ;; Dropdown menu
            (when menu-open?
              [:div {:style {:position "absolute" :top "calc(100% + 8px)" :right 0
                             :background "#fff" :border-radius "8px"
                             :box-shadow "0 4px 16px rgba(0,0,0,0.15)"
                             :min-width "180px" :z-index 10000
                             :padding "8px 0"
                             :overflow "hidden"}}
               ;; Settings
               [:div.pointer
                {:style {:padding "10px 16px" :display "flex" :align-items "center"
                         :gap "10px" :transition "background 0.15s" :color "#333"}
                 :on-mouse-enter #(set! (.. % -currentTarget -style -background) "#f5f5f5")
                 :on-mouse-leave #(set! (.. % -currentTarget -style -background) "transparent")
                 :on-click (fn [e]
                             (.stopPropagation e)
                             (reset! user-menu-open? false)
                             (settings/open-settings!))}
                [:svg {:width "16" :height "16" :viewBox "0 0 24 24" :fill "none"
                       :stroke "currentColor" :stroke-width "2"
                       :stroke-linecap "round" :stroke-linejoin "round"}
                 [:circle {:cx "12" :cy "12" :r "3"}]
                 [:path {:d "M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"}]]
                [:span "Settings"]]
               ;; Divider
               [:div {:style {:height "1px" :background "#f0f0f0" :margin "4px 0"}}]
               ;; Logout
               [:div.pointer
                {:style {:padding "10px 16px" :display "flex" :align-items "center"
                         :gap "10px" :transition "background 0.15s" :color "#ff4d4f"}
                 :on-mouse-enter #(set! (.. % -currentTarget -style -background) "#fff1f0")
                 :on-mouse-leave #(set! (.. % -currentTarget -style -background) "transparent")
                 :on-click (fn [e]
                             (.stopPropagation e)
                             (reset! user-menu-open? false)
                             (reset! storage/jwt-auth {})
                             (router/switch-router! "/login"))}
                [:svg {:width "16" :height "16" :viewBox "0 0 24 24" :fill "none"
                       :stroke "currentColor" :stroke-width "2"
                       :stroke-linecap "round" :stroke-linejoin "round"}
                 [:path {:d "M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"}]
                 [:polyline {:points "16 17 21 12 16 7"}]
                 [:line {:x1 "21" :y1 "12" :x2 "9" :y2 "12"}]]
                [:span "Logout"]]])]))]]
     
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
     
     ;; Hidden file inputs for import (must live outside context-menu so they persist)
     [:input
      {:id "import-json-input"
       :type "file"
       :multiple true
       :accept ".json"
       :style {:display "none"}
       :on-change (fn [e]
                    (let [files (.. e -target -files)
                          db-id @import-target-db-id]
                      (when (and db-id (> (.-length files) 0))
                        (import-notes-to-database! db-id files))
                      (set! (.. e -target -value) "")))}]
     [:input
      {:id "import-zip-input"
       :type "file"
       :accept ".zip"
       :style {:display "none"}
       :on-change (fn [e]
                    (let [files (.. e -target -files)
                          db-id @import-target-db-id]
                      (when (and db-id (> (.-length files) 0))
                        (import-notes-to-database! db-id files))
                      (set! (.. e -target -value) "")))}]

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
       "© 2026 Hulunote - MIT License"]]]))
