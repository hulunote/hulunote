(ns hulunote.database
  (:require [datascript.core :as d]
            [hulunote.db :as db]
            [hulunote.icon :as icon]
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
                                   :database-id nil
                                   :is-default false}))

(defonce create-modal-state (atom {:visible false
                                   :database-name ""}))

(defonce rename-modal-state (atom {:visible false
                                   :database-id nil
                                   :database-name ""}))

(defonce import-state (atom {:importing false
                              :result nil}))

(defonce user-menu-open? (atom false))

;; Holds the database-id to import into (set before opening file picker)
(defonce import-target-db-id (atom nil))

(defn sync-local-database!
  [database-id attrs]
  (d/transact! db/dsdb
    [(merge {:hulunote-databases/id database-id}
       attrs)]))

(defn set-default-database!
  [database-id database-name]
  (let [current-defaults (->> (db/get-database @db/dsdb)
                           (map first)
                           (filter :hulunote-databases/is-default)
                           (remove #(= (:hulunote-databases/id %) database-id))
                           vec)]
    (doseq [item current-defaults]
      (re-frame/dispatch
        [:update-database
         {:database-id (:hulunote-databases/id item)
          :is-default false}]))
    (re-frame/dispatch
      [:update-database
       {:database-id database-id
        :is-default true
        :op-fn (fn [_]
                 (doseq [item current-defaults]
                   (sync-local-database! (:hulunote-databases/id item)
                     {:hulunote-databases/is-default false}))
                 (sync-local-database! database-id
                   {:hulunote-databases/is-default true})
                 (u/alert (str "Database \"" database-name "\" set as default")))}])))

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
  (let [{:keys [visible x y database-name database-id is-default]} (rum/react context-menu-state)
        {:keys [importing]} (rum/react import-state)]
    (when visible
      [:div.context-menu
       {:style {:position "fixed"
                :left (str x "px")
                :top (str y "px")
                :background "var(--light-surface)"
                :border-radius "8px"
                :box-shadow "var(--light-shadow-popover)"
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
         :on-mouse-enter #(set! (.. % -target -style -background) "var(--light-surface-hover)")
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
        [:span {:style {:color "var(--theme-accent)"}} "\uD83D\uDCE5"]
        [:span {:style {:color "var(--light-text-primary)"}} (if importing "Importing..." "Import JSON")]]

       ;; Import ZIP option
       [:div.context-menu-item.pointer
        {:style {:padding "10px 16px"
                 :display "flex"
                 :align-items "center"
                 :gap "8px"
                 :transition "background 0.2s"
                 :opacity (if importing 0.5 1)}
         :on-mouse-enter #(set! (.. % -target -style -background) "var(--light-surface-hover)")
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
        [:span {:style {:color "var(--graph-node-active)"}} "\uD83D\uDDDC\uFE0F"]
        [:span {:style {:color "var(--light-text-primary)"}} (if importing "Importing..." "Import ZIP")]]

       ;; Divider
       [:div {:style {:height "1px"
                      :background "var(--light-border-subtle)"
                      :margin "4px 0"}}]

       ;; Rename Database option
       [:div.context-menu-item.pointer
        {:style {:padding "10px 16px"
                 :display "flex"
                 :align-items "center"
                 :gap "8px"
                 :transition "background 0.2s"}
         :on-mouse-enter #(set! (.. % -target -style -background) "var(--light-surface-hover)")
         :on-mouse-leave #(set! (.. % -target -style -background) "transparent")
         :on-click (fn [e]
                     (.stopPropagation e)
                     (reset! rename-modal-state {:visible true
                                                 :database-id database-id
                                                 :database-name database-name})
                     (swap! context-menu-state assoc :visible false))}
        [:span {:style {:color "var(--theme-accent)"}} "✏️"]
        [:span {:style {:color "var(--light-text-primary)"}} "Rename Database"]]

       ;; Set as default option
       [:div.context-menu-item.pointer
        {:style {:padding "10px 16px"
                 :display "flex"
                 :align-items "center"
                 :gap "8px"
                 :transition "background 0.2s"
                 :opacity (if is-default 0.55 1)}
         :on-mouse-enter #(when-not is-default
                            (set! (.. % -target -style -background) "var(--light-surface-hover)"))
         :on-mouse-leave #(set! (.. % -target -style -background) "transparent")
         :on-click (fn [e]
                     (.stopPropagation e)
                     (when-not is-default
                       (set-default-database! database-id database-name))
                     (swap! context-menu-state assoc :visible false))}
        [:span {:style {:color "var(--theme-warning)"}} "★"]
        [:span {:style {:color "var(--light-text-primary)"}} (if is-default "Default Database" "Set as Default")]]

       ;; Divider
       [:div {:style {:height "1px"
                      :background "var(--light-border-subtle)"
                      :margin "4px 0"}}]

       ;; Delete Database option
       [:div.context-menu-item.pointer
        {:style {:padding "10px 16px"
                 :display "flex"
                 :align-items "center"
                 :gap "8px"
                 :transition "background 0.2s"}
         :on-mouse-enter #(set! (.. % -target -style -background) "var(--light-surface-hover)")
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
        [:span {:style {:color "var(--app-danger-soft)"}} "\uD83D\uDDD1\uFE0F"]
        [:span {:style {:color "var(--app-danger-soft)"}} "Delete Database"]]])))

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
                :background "var(--light-overlay)"
                :display "flex"
                :align-items "center"
                :justify-content "center"
                :z-index 10000}
        :on-click #(swap! create-modal-state assoc :visible false)}
       [:div.modal-content
        {:style {:background "var(--light-surface)"
                 :border-radius "16px"
                 :padding "32px"
                 :min-width "400px"
                 :box-shadow "var(--light-shadow-popover-strong)"}
         :on-click #(.stopPropagation %)}
        [:h2 {:style {:margin "0 0 24px 0"
                      :font-size "24px"
                      :font-weight "600"
                      :color "var(--light-text-primary)"}}
         "Create New Database"]
        [:div {:style {:margin-bottom "24px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "var(--light-text-secondary)"}}
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
                   :border "2px solid var(--light-border)"
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
                   :border "2px solid var(--light-border)"
                   :border-radius "8px"
                   :background "var(--light-surface)"
                   :font-size "16px"
                   :font-weight "500"
                   :color "var(--light-text-secondary)"
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
                   :background "var(--theme-accent-gradient)"
                   :font-size "16px"
                   :font-weight "600"
                   :color "var(--theme-accent-text)"
                   :cursor "pointer"}}
         "Create"]]]])))

(rum/defc rename-modal < rum/reactive []
  (let [{:keys [visible database-id database-name]} (rum/react rename-modal-state)]
    (when visible
      [:div.modal-overlay
       {:style {:position "fixed"
                :top 0
                :left 0
                :right 0
                :bottom 0
                :background "var(--light-overlay)"
                :display "flex"
                :align-items "center"
                :justify-content "center"
                :z-index 10000}
        :on-click #(reset! rename-modal-state {:visible false
                                               :database-id nil
                                               :database-name ""})}
       [:div.modal-content
        {:style {:background "var(--light-surface)"
                 :border-radius "16px"
                 :padding "32px"
                 :min-width "400px"
                 :box-shadow "var(--light-shadow-popover-strong)"}
         :on-click #(.stopPropagation %)}
        [:h2 {:style {:margin "0 0 24px 0"
                      :font-size "24px"
                      :font-weight "600"
                      :color "var(--light-text-primary)"}}
         "Rename Database"]
        [:div {:style {:margin-bottom "24px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "var(--light-text-secondary)"}}
          "Database Name"]
         [:input
          {:type "text"
           :placeholder "Enter database name..."
           :value database-name
           :on-change #(swap! rename-modal-state assoc :database-name (.. % -target -value))
           :on-key-down (fn [e]
                          (when (= (.-key e) "Enter")
                            (let [name (clojure.string/trim database-name)]
                              (when (and database-id (not (empty? name)))
                                (re-frame/dispatch
                                  [:update-database
                                   {:database-id database-id
                                    :db-name name
                                    :op-fn (fn [_]
                                             (sync-local-database! database-id
                                               {:hulunote-databases/name name})
                                             (reset! rename-modal-state {:visible false
                                                                         :database-id nil
                                                                         :database-name ""})
                                             (u/alert (str "Database renamed to \"" name "\"")))}])))))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "2px solid var(--light-border)"
                   :border-radius "8px"
                   :font-size "16px"
                   :outline "none"
                   :transition "border-color 0.2s"}}]]
        [:div {:style {:display "flex"
                       :justify-content "flex-end"
                       :gap "12px"}}
         [:button.pointer
          {:on-click #(reset! rename-modal-state {:visible false
                                                  :database-id nil
                                                  :database-name ""})
           :style {:padding "12px 24px"
                   :border "2px solid var(--light-border)"
                   :border-radius "8px"
                   :background "var(--light-surface)"
                   :font-size "16px"
                   :font-weight "500"
                   :color "var(--light-text-secondary)"
                   :cursor "pointer"}}
          "Cancel"]
         [:button.pointer
          {:on-click (fn []
                       (let [name (clojure.string/trim database-name)]
                         (when (and database-id (not (empty? name)))
                           (re-frame/dispatch
                             [:update-database
                              {:database-id database-id
                               :db-name name
                               :op-fn (fn [_]
                                        (sync-local-database! database-id
                                          {:hulunote-databases/name name})
                                        (reset! rename-modal-state {:visible false
                                                                    :database-id nil
                                                                    :database-name ""})
                                        (u/alert (str "Database renamed to \"" name "\"")))}]))))
           :style {:padding "12px 24px"
                   :border "none"
                   :border-radius "8px"
                   :background "var(--theme-accent-gradient)"
                   :font-size "16px"
                   :font-weight "600"
                   :color "var(--theme-accent-text)"
                   :cursor "pointer"}}
          "Save"]]]])))

;; ==================== Database Card Component ====================
(rum/defc database-card [name database-id is-default on-click]
  [:div.flex.pointer.database-card
   {:on-click on-click
    :on-context-menu (fn [e]
                       (.preventDefault e)
                       (.stopPropagation e)
                       (swap! context-menu-state assoc
                              :visible true
                              :x (.-clientX e)
                              :y (.-clientY e)
                              :database-name name
                              :database-id database-id
                              :is-default (boolean is-default)))
    :style {:background "var(--light-surface)"
            :position "relative"
            :border-radius "12px"
            :padding "32px 24px"
            :box-shadow (if is-default
                          "0 10px 30px var(--theme-accent-15), 0 0 0 1px var(--theme-accent-20)"
                          "var(--light-shadow-soft)")
            :transition "all 0.3s ease"
            :border "2px solid transparent"
            :min-width "200px"}}
   (when is-default
     [:div
      {:style {:position "absolute"
               :top "18px"
               :right "18px"
                :padding "6px 12px"
                :border-radius "999px"
                :background "var(--theme-accent-15)"
                :border "1px solid var(--theme-accent-20)"
                :font-size "11px"
                :font-weight "700"
                :letter-spacing "0.02em"
                :line-height "1"
               :color "var(--theme-accent-strong)"}}
      "Default"])
   [:div.flex.flex-column.items-center.w-100
    [:div {:style {:font-size "40px"
                   :margin-bottom "16px"}}
     "📚"]
    [:div {:style {:font-size "18px"
                   :font-weight "600"
                   :color "var(--light-text-primary)"
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
                 :color "var(--light-text-primary)"
                 :margin "0 0 12px 0"}}
    "No Databases Yet"]
   [:p {:style {:font-size "16px"
                :color "var(--light-text-secondary)"
                :margin "0 0 32px 0"}}
    (if (u/is-expired?)
      "Login to create your first note database"
      "Click the \"+ New Database\" button to create your first database")]
   (if (u/is-expired?)
     [:button.pointer
      {:on-click #(router/switch-router! "/login")
       :style {:background "var(--theme-accent-gradient)"
               :color "var(--theme-accent-text)"
               :border "none"
               :padding "12px 28px"
               :border-radius "25px"
               :font-size "16px"
               :font-weight "600"
               :cursor "pointer"}}
      "Login Now"]
     [:button.pointer
      {:on-click #(swap! create-modal-state assoc :visible true)
       :style {:background "var(--theme-accent-gradient)"
               :color "var(--theme-accent-text)"
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
        _ (rum/react rename-modal-state)  ;; Subscribe to rename modal state
        _ (rum/react user-menu-open?)]    ;; Subscribe to user menu state
    [:div.flex.flex-column
     {:style {:min-height "100vh"
              :background "var(--light-page-bg)"}
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
               :background "var(--theme-accent-gradient)"}}
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
                 :color "var(--theme-accent-text)"}}
        "HULUNOTE"]]
      [:div.flex.items-center
       (if (u/is-expired?)
         [:button.pointer
          {:on-click #(router/switch-router! "/login")
           :style {:background "var(--light-surface)"
                   :color "var(--theme-accent)"
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
                            :background "var(--app-control-bg)"
                            :display "flex" :align-items "center" :justify-content "center"
                            :overflow "hidden" :border "2px solid var(--app-control-border)"}} 
              (if avatar-url
                [:img {:src (if (clojure.string/starts-with? (or avatar-url "") "http")
                              avatar-url
                              (str (http/http-uri "") avatar-url))
                       :style {:width "100%" :height "100%" :object-fit "cover"}}]
                [:span {:style {:color "var(--theme-accent-text)" :font-size "14px" :font-weight "600"}}
                 (-> (or username "U") first clojure.string/upper-case)])]
             [:span {:style {:color "var(--theme-accent-text)" :font-weight "500"}} username]
             ;; Dropdown arrow
             (icon/svg-icon
               {:name "keyboard_arrow_down"
                :style {:width "12px"
                        :height "12px"
                        :color "var(--theme-accent-text)"
                        :transition "transform 0.2s"
                        :transform (if menu-open? "rotate(180deg)" "rotate(0)")}})]
            ;; Dropdown menu
            (when menu-open?
              [:div {:style {:position "absolute" :top "calc(100% + 8px)" :right 0
                             :background "var(--light-surface)" :border-radius "8px"
                             :box-shadow "var(--light-shadow-popover)"
                             :min-width "180px" :z-index 10000
                             :padding "8px 0"
                             :overflow "hidden"}}
               ;; Logout
               [:div.pointer
               {:style {:padding "10px 16px" :display "flex" :align-items "center"
                        :gap "10px" :transition "background 0.15s" :color "var(--app-danger-soft)"}
                :on-mouse-enter #(set! (.. % -currentTarget -style -background) "var(--light-danger-hover)")
                :on-mouse-leave #(set! (.. % -currentTarget -style -background) "transparent")
                :on-click (fn [e]
                            (.stopPropagation e)
                            (reset! user-menu-open? false)
                            (reset! storage/jwt-auth {})
                            (router/switch-router! "/login"))}
                (icon/svg-icon
                  {:name "logout"
                   :style {:width "16px"
                           :height "16px"}})
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
                      :color "var(--light-text-primary)"
                      :margin "0"}}
         "My Databases"]
        [:div {:style {:color "var(--light-text-secondary)"
                       :font-size "14px"
                       :margin-top "8px"}}
         (str (count database-list) " database(s)")]]
       
       ;; Create button (only show when logged in)
       (when-not (u/is-expired?)
         [:button.pointer
         {:on-click #(swap! create-modal-state assoc :visible true)
           :style {:background "var(--theme-accent-gradient)"
                   :color "var(--theme-accent-text)"
                   :border "none"
                   :padding "12px 24px"
                   :border-radius "25px"
                   :font-size "16px"
                   :font-weight "600"
                   :cursor "pointer"
                   :display "flex"
                   :align-items "center"
                   :gap "8px"
                   :box-shadow "var(--light-shadow-primary)"
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
                 db-id (:hulunote-databases/id db-item)
                 is-default (:hulunote-databases/is-default db-item)]
             (rum/with-key
               (database-card
                 db-name
                 db-id
                 is-default
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

     ;; Rename modal
     (rename-modal)

     ;; Footer
     [:div
      {:style {:background "var(--light-text-primary)"
               :padding "24px 20px"
               :text-align "center"}}
      [:div {:style {:color "var(--app-text-soft)"
                     :font-size "14px"}}
       "© 2026 Hulunote - MIT License"]]]))
