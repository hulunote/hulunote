(ns hulunote.settings.preferences
  (:require [rum.core :as rum]
            [hulunote.select :as select]
            [hulunote.theme :as theme]))

(def theme-options
  [{:value "dark" :label "Dark"}
   {:value "auto" :label "Auto"}
   {:value "light" :label "Light"}])

(rum/defc page < rum/reactive []
  (let [current-theme-mode (rum/react theme/current-theme-mode)]
    [:div {:style {:padding "24px 0"}}
     [:div {:style {:display "flex"
                    :align-items "center"
                    :justify-content "space-between"
                    :gap "24px"
                    :padding "8px 0"}}
      [:div {:style {:min-width "0"
                     :flex "1 1 auto"}}
       [:div {:style {:font-size "14px"
                      :font-weight "600"
                      :color "var(--ui-text-strong)"
                      :margin-bottom "4px"}}
        "Theme"]
       [:div {:style {:font-size "12px"
                      :line-height "1.5"
                      :color "var(--ui-text-muted)"}}
        "Choose how Hulunote follows dark and light theme on this device."]]
       (select/select-dropdown
         {:options theme-options
          :value current-theme-mode
          :on-change theme/set-theme!
          :style {:flex-shrink 0}})]]))
