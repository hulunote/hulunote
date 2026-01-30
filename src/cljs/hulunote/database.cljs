(ns hulunote.database
  (:require [datascript.core :as d]
            [hulunote.db :as db]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.storage :as storage]
            [hulunote.components :as comps]
            [re-frame.core :as re-frame]))

;; Database card component
(rum/defc database-card [name on-click]
  [:div.flex.pointer
   {:on-click on-click
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

;; Empty state component
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
    "Login to create your first note database"]
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
    "Login Now"]])

;; Main database list page
(rum/defcs database-page
  < {:will-mount
     (fn [state]
       (re-frame/dispatch-sync
         [:get-database-list
          {:op-fn
           (fn [{:keys [database-list settings]}]
             (doseq [item database-list]
               (d/transact! db/dsdb [item])))}])
       state)}
  [state db]
  (let [database-list (db/get-database db)]
    [:div.flex.flex-column
     {:style {:min-height "100vh"
              :background "#f8f9fa"}}
     
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
         :src "/img/hulunote.webp"}]
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
      
      ;; Title
      [:div.flex.flex-row.items-center.justify-between
       {:style {:margin-bottom "32px"
                :margin-top "60px"}}
       [:h1 {:style {:font-size "32px"
                     :font-weight "700"
                     :color "#1a1a2e"
                     :margin "0"}}
        "My Databases"]
       [:div {:style {:color "#666"
                      :font-size "14px"}}
        (str (count database-list) " database(s)")]]
      
      ;; Database list or empty state
      (if (empty? database-list)
        (empty-state)
        [:div
         {:style {:display "grid"
                  :grid-template-columns "repeat(auto-fill, minmax(220px, 1fr))"
                  :gap "20px"}}
         (for [item database-list]
           (let [db-name (:hulunote-databases/name (first item))]
             (rum/with-key
               (database-card
                 db-name
                 #(js/open (str "#/app/" db-name "/diaries")))
               db-name)))])]
     
     ;; Footer
     [:div
      {:style {:background "#1a1a2e"
               :padding "24px 20px"
               :text-align "center"}}
      [:div {:style {:color "rgba(255,255,255,0.5)"
                     :font-size "14px"}}
       "© 2024 Hulunote - MIT License"]]]))
