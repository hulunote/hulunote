(ns hulunote.settings.chat
  (:require [rum.core :as rum]
            [hulunote.chat :as chat]
            [hulunote.settings.shared :as shared]))

(rum/defc page < rum/reactive []
  (let [{:keys [api-key model available-models models-loading? saved?]}
        (rum/react shared/chat-settings-state)]
    [:div {:style {:padding "24px 0"}}
     (when-not (chat/chat-available?)
       [:div {:style {:background "var(--theme-warning-soft)"
                      :border "1px solid var(--theme-warning-border)"
                      :border-radius "8px"
                      :padding "16px 20px"
                      :margin-bottom "24px"
                      :display "flex"
                      :align-items "center"
                      :gap "12px"}}
        [:span {:style {:font-size "24px"}} "!"]
        [:div
         [:div {:style {:font-weight "600"
                        :color "var(--theme-warning)"
                        :margin-bottom "4px"}}
          "Chat is only available in Hulunote PC App"]
         [:div {:style {:color "var(--theme-warning-soft-text)"
                        :font-size "13px"}}
          "Please use the Hulunote desktop application to configure chat settings."]]])

     [:div {:style {:margin-bottom "20px"}}
      [:label {:style shared/label-style} "OpenRouter API Key"]
      [:input
       {:type "password"
        :placeholder "sk-or-..."
        :value api-key
        :on-change #(swap! shared/chat-settings-state assoc :api-key (.. % -target -value))
        :style shared/input-style}]
      [:div {:style {:font-size "12px"
                     :color "var(--app-text-faint)"
                     :margin-top "6px"}}
       "Get your API key from "
       [:a {:href "https://openrouter.ai/keys"
            :target "_blank"
            :style {:color "var(--theme-accent)"}}
        "openrouter.ai/keys"]]]

     [:div {:style {:margin-bottom "24px"}}
      [:label {:style shared/label-style}
       "Model"
       (when models-loading?
         [:span {:style {:margin-left "8px"
                         :font-size "12px"
                         :color "var(--app-text-faint)"}}
          "Loading models..."])]
      [:select
       {:value model
        :on-change #(swap! shared/chat-settings-state assoc :model (.. % -target -value))
        :style shared/input-style}
       (if (seq available-models)
         (for [m available-models]
           (let [id (:id m)
                 mname (or (:name m) id)]
             [:option {:key id :value id} mname]))
         (list
           [:option {:key "anthropic/claude-sonnet-4.6" :value "anthropic/claude-sonnet-4.6"} "Claude Sonnet 4"]
           [:option {:key "anthropic/claude-haiku-4.5" :value "anthropic/claude-haiku-4.5"} "Claude Haiku 4"]
           [:option {:key "openai/gpt-4o" :value "openai/gpt-4o"} "GPT-4o"]
           [:option {:key "google/gemini-3.1-pro-preview" :value "google/gemini-3.1-pro-preview"} "Gemini 3 Pro"]
           [:option {:key "deepseek/deepseek-chat-v3-0324" :value "deepseek/deepseek-chat-v3-0324"} "DeepSeek V3"]))]
      (when (seq available-models)
        [:div {:style {:font-size "12px"
                       :color "var(--app-text-faint)"
                       :margin-top "6px"}}
         (str (count available-models) " models available from OpenRouter")])]

     [:div {:style {:display "flex"
                    :justify-content "flex-end"}}
      (shared/action-button
        {:on-click shared/save-chat-settings!
         :tone (when saved? :success)}
        (if saved? "Saved!" "Save Chat Settings"))]]))
