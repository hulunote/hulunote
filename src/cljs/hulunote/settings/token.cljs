(ns hulunote.settings.token
  (:require [rum.core :as rum]
            [hulunote.settings.shared :as shared]))

(rum/defc page < rum/reactive []
  (let [{:keys [generating new-token expires-at copied]} (rum/react shared/token-state)
        current-info (shared/get-current-token-info)]
    [:div {:style {:padding "24px 0"}}
     [:div {:style shared/section-style}
      [:h3 {:style {:margin "0 0 12px 0" :font-size "16px"
                    :font-weight "600" :color "#fdfeffc4"}}
       "Current Token Status"]
      (if current-info
        [:div
         [:div {:style {:display "flex" :align-items "center" :gap "8px" :margin-bottom "8px"}}
          [:div {:style {:width "10px" :height "10px" :border-radius "50%"
                         :background (if (:is-expired current-info) "#ff4d4f" "#52c41a")}}]
          [:span {:style {:font-size "14px" :color "rgba(255,255,255,0.6)"}}
           (if (:is-expired current-info) "Token Expired" "Token Active")]]
         [:div {:style {:font-size "14px" :color "rgba(255,255,255,0.5)" :margin-bottom "4px"}}
          (str "Remaining: " (:remaining-days current-info) " days")]
         [:div {:style {:font-size "13px" :color "rgba(255,255,255,0.3)"}}
          (str "Expires: "
               (.toLocaleDateString
                 (js/Date. (* (:expires-at current-info) 1000))))]]
        [:div {:style {:font-size "14px" :color "rgba(255,255,255,0.4)"}}
         "No active token"])]

     [:div {:style shared/section-style}
     [:h3 {:style {:margin "0 0 8px 0" :font-size "16px"
                    :font-weight "600" :color "#fdfeffc4"}}
       "Generate New Token"]
      [:p {:style {:margin "0 0 16px 0" :font-size "14px" :color "rgba(255,255,255,0.5)"}}
       "Generate a new JWT token with 3-month (90 days) validity. The new token will replace your current one."]

      [:div {:style {:display "flex"
                     :justify-content "flex-end"}}
       (shared/action-button
         {:on-click shared/generate-token!
          :disabled? generating}
         (if generating "Generating..." "Generate 3-Month Token"))]]

     (when new-token
       [:div {:style {:background "rgba(82,196,26,0.1)" :border "1px solid rgba(82,196,26,0.3)"
                      :border-radius "12px" :padding "20px" :margin-bottom "24px"}}
        [:h3 {:style {:margin "0 0 8px 0" :font-size "16px"
                      :font-weight "600" :color "#52c41a"}}
         "New Token Generated"]
        (when expires-at
          [:div {:style {:font-size "13px" :color "#52c41a" :margin-bottom "12px"}}
           (str "Valid until: " (.toLocaleDateString (js/Date. expires-at)))])
        [:div {:style {:display "flex" :gap "8px" :align-items "stretch"}}
         [:input {:type "text"
                  :value new-token
                  :read-only true
                  :style (merge shared/input-style
                                {:flex "1" :font-size "12px" :font-family "monospace"})}]
         [:button
          {:on-click #(shared/copy-token! new-token)
           :style {:padding "10px 16px"
                   :border "none"
                   :border-radius "6px"
                   :background (if copied "#52c41a" "#667eea")
                   :color "#fff"
                   :font-size "13px"
                   :font-weight "500"
                   :cursor "pointer"
                   :white-space "nowrap"}}
          (if copied "Copied!" "Copy")]]])]))
