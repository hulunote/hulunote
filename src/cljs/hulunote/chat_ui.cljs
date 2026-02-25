(ns hulunote.chat-ui
  "MCP Chat UI 组件"
  (:require [rum.core :as rum]
            [hulunote.chat :as chat]
            [hulunote.mcp :as mcp]
            [hulunote.mcp-state :as mcp-state]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [cljs.core.async :as a :refer [<! go]]
            [clojure.string :as str]))

;; ==================== State ====================

(defonce chat-state
  (atom {:messages []           ; [{:role "user"/"assistant" :content "..."}]
         :input ""              ; 当前输入
         :loading? false        ; 是否正在等待回复
         :api-key ""            ; API Key
         :api-key-set? false    ; API Key 是否已设置
         :model "anthropic/claude-3.5-sonnet"
         :use-tools? true       ; 是否使用 MCP 工具
         :show-settings? false  ; 是否显示设置面板
         :error nil}))

;; ==================== 辅助函数 ====================

(defn js->clj-safe [obj]
  (if (object? obj)
    (js->clj obj :keywordize-keys true)
    obj))

(defn add-message! [role content]
  (swap! chat-state update :messages conj {:role role :content content}))

(defn clear-messages! []
  (swap! chat-state assoc :messages []))

;; ==================== 初始化 ====================

(defn init-chat! []
  (when (chat/chat-available?)
    ;; 加载 API Key
    (go
      (when-let [ch (chat/get-api-key!)]
        (let [result (js->clj-safe (<! ch))]
          (when (:success result)
            (let [api-key (:apiKey result)]
              (swap! chat-state assoc
                     :api-key api-key
                     :api-key-set? (not (str/blank? api-key))))))))
    ;; 加载模型
    (go
      (when-let [ch (chat/get-model!)]
        (let [result (js->clj-safe (<! ch))]
          (when (:success result)
            (swap! chat-state assoc :model (:model result))))))))

;; ==================== 发送消息 ====================

(defn send-message! []
  (let [{:keys [input messages use-tools? api-key-set?]} @chat-state]
    (when (and (not (str/blank? input)) api-key-set?)
      (let [user-message {:role "user" :content input}
            all-messages (conj messages user-message)]
        ;; 更新状态
        (swap! chat-state assoc
               :messages all-messages
               :input ""
               :loading? true
               :error nil)
        ;; 发送请求
        (go
          (when-let [ch (chat/send-message! {:messages all-messages
                                             :use-tools use-tools?})]
            (let [result (js->clj-safe (<! ch))]
              (swap! chat-state assoc :loading? false)
              (if (:success result)
                (let [response (:response result)
                      assistant-content (get-in response [:choices 0 :message :content] "")]
                  ;; 添加助手回复
                  (when (not (str/blank? assistant-content))
                    (add-message! "assistant" assistant-content))
                  ;; 如果有工具调用，显示信息
                  (when-let [tool-calls (:toolCalls result)]
                    (add-message! "system"
                                  (str "Used tools: "
                                       (str/join ", "
                                                 (map #(get-in % [:function :name]) tool-calls))))))
                (do
                  (swap! chat-state assoc :error (:error result))
                  (add-message! "error" (str "Error: " (:error result))))))))))))

;; ==================== 保存设置 ====================

(defn save-api-key! [api-key]
  (go
    (when-let [ch (chat/set-api-key! api-key)]
      (let [result (js->clj-safe (<! ch))]
        (when (:success result)
          (swap! chat-state assoc
                 :api-key api-key
                 :api-key-set? (not (str/blank? api-key))
                 :show-settings? false))))))

(defn save-model! [model]
  (go
    (when-let [ch (chat/set-model! model)]
      (let [result (js->clj-safe (<! ch))]
        (when (:success result)
          (swap! chat-state assoc :model model))))))

;; ==================== UI 组件 ====================

(rum/defc message-bubble [msg]
  (let [{:keys [role content]} msg
        is-user (= role "user")
        is-error (= role "error")
        is-system (= role "system")]
    [:div.message
     {:style {:display "flex"
              :justify-content (if is-user "flex-end" "flex-start")
              :margin-bottom "12px"}}
     [:div.message-bubble
      {:style {:max-width "80%"
               :padding "12px 16px"
               :border-radius (if is-user "16px 16px 4px 16px" "16px 16px 16px 4px")
               :background (cond
                             is-user "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                             is-error "#fff2f0"
                             is-system "#f0f5ff"
                             :else "#f5f5f5")
               :color (cond
                        is-user "#fff"
                        is-error "#ff4d4f"
                        is-system "#1890ff"
                        :else "#333")
               :white-space "pre-wrap"
               :word-break "break-word"
               :font-size "14px"
               :line-height "1.5"}}
      content]]))

(rum/defc settings-modal < rum/reactive []
  (let [{:keys [show-settings? api-key model]} (rum/react chat-state)]
    (when show-settings?
      [:div.modal-overlay
       {:style {:position "fixed"
                :top 0 :left 0 :right 0 :bottom 0
                :background "rgba(0,0,0,0.5)"
                :display "flex"
                :align-items "center"
                :justify-content "center"
                :z-index 10000}
        :on-click #(swap! chat-state assoc :show-settings? false)}
       [:div.modal-content
        {:style {:background "#fff"
                 :border-radius "16px"
                 :padding "32px"
                 :min-width "450px"
                 :box-shadow "0 8px 32px rgba(0,0,0,0.2)"}
         :on-click #(.stopPropagation %)}

        [:h2 {:style {:margin "0 0 24px 0"
                      :font-size "24px"
                      :font-weight "600"
                      :color "#1a1a2e"}}
         "Chat Settings"]

        ;; API Key
        [:div {:style {:margin-bottom "20px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "#666"}}
          "OpenRouter API Key"]
         [:input
          {:type "password"
           :placeholder "sk-or-..."
           :value api-key
           :on-change #(swap! chat-state assoc :api-key (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "2px solid #e0e0e0"
                   :border-radius "8px"
                   :font-size "14px"
                   :outline "none"
                   :box-sizing "border-box"}}]
         [:div {:style {:font-size "12px"
                        :color "#999"
                        :margin-top "6px"}}
          "Get your API key from "
          [:a {:href "https://openrouter.ai/keys"
               :target "_blank"
               :style {:color "#667eea"}}
           "openrouter.ai/keys"]]]

        ;; Model
        [:div {:style {:margin-bottom "24px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "#666"}}
          "Model"]
         [:select
          {:value model
           :on-change #(swap! chat-state assoc :model (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "2px solid #e0e0e0"
                   :border-radius "8px"
                   :font-size "14px"
                   :outline "none"
                   :box-sizing "border-box"
                   :background "#fff"}}
          [:option {:value "anthropic/claude-3.5-sonnet"} "Claude 3.5 Sonnet"]
          [:option {:value "anthropic/claude-3-opus"} "Claude 3 Opus"]
          [:option {:value "openai/gpt-4-turbo"} "GPT-4 Turbo"]
          [:option {:value "openai/gpt-4o"} "GPT-4o"]
          [:option {:value "google/gemini-pro-1.5"} "Gemini Pro 1.5"]
          [:option {:value "meta-llama/llama-3.1-405b-instruct"} "Llama 3.1 405B"]]]

        ;; Buttons
        [:div {:style {:display "flex"
                       :justify-content "flex-end"
                       :gap "12px"}}
         [:button.pointer
          {:on-click #(swap! chat-state assoc :show-settings? false)
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
          {:on-click #(do
                        (save-api-key! api-key)
                        (save-model! model))
           :style {:padding "12px 24px"
                   :border "none"
                   :border-radius "8px"
                   :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                   :font-size "16px"
                   :font-weight "600"
                   :color "#fff"
                   :cursor "pointer"}}
          "Save"]]]])))

(rum/defcs chat-page
  < {:will-mount
     (fn [state]
       (init-chat!)
       (when (mcp/mcp-available?)
         (mcp-state/init!))
       state)}
  rum/reactive
  [state database-name]
  (let [{:keys [messages input loading? api-key-set? use-tools? error model]}
        (rum/react chat-state)
        {:keys [servers connected-clients]} (rum/react mcp-state/mcp-state)
        connected-count (count connected-clients)]
    [:div.flex.flex-column
     {:style {:min-height "100vh"
              :background "#f8f9fa"}}

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
       ;; Back button
       [:button.pointer
        {:on-click #(js/history.back)
         :style {:background "rgba(255,255,255,0.2)"
                 :border "none"
                 :padding "6px 12px"
                 :border-radius "6px"
                 :color "#fff"
                 :font-size "18px"
                 :cursor "pointer"
                 :margin-right "12px"}}
        "\u2190"]
       [:img.pointer
        {:on-click #(router/switch-router! "/")
         :width "36px"
         :style {:border-radius "50%"}
         :src (u/asset-path "/img/hulunote.webp")}]
       [:div.pl3.pointer
        {:on-click #(router/switch-router! "/")
         :style {:font-size "22px"
                 :font-weight "700"
                 :color "#fff"}}
        "HULUNOTE"]]
      [:div.flex.items-center {:style {:gap "16px"
                                       :-webkit-app-region "no-drag"}}
       ;; MCP status
       [:div {:style {:color "#fff"
                      :font-size "13px"
                      :display "flex"
                      :align-items "center"
                      :gap "6px"}}
        [:span {:style {:width "8px"
                        :height "8px"
                        :border-radius "50%"
                        :background (if (pos? connected-count) "#52c41a" "#ff4d4f")}}]
        (str connected-count " MCP connected")]
       ;; Settings button
       [:button.pointer
        {:on-click #(swap! chat-state assoc :show-settings? true)
         :style {:background "rgba(255,255,255,0.2)"
                 :border "none"
                 :padding "8px 16px"
                 :border-radius "8px"
                 :color "#fff"
                 :font-size "14px"
                 :cursor "pointer"}}
        "Settings"]]]

     ;; Main content
     [:div.flex.flex-column
      {:style {:flex 1
               :max-width "900px"
               :margin "0 auto"
               :width "100%"
               :padding "20px"
               :margin-top "88px"}}

      ;; API Key warning
      (when-not api-key-set?
        [:div {:style {:background "#fff7e6"
                       :border "1px solid #ffd591"
                       :border-radius "8px"
                       :padding "16px 20px"
                       :margin-bottom "20px"
                       :display "flex"
                       :align-items "center"
                       :gap "12px"}}
         [:span {:style {:font-size "24px"}} "⚠️"]
         [:div
          [:div {:style {:font-weight "600"
                         :color "#d46b08"}}
           "API Key Required"]
          [:div {:style {:color "#ad6800"
                         :font-size "13px"}}
           "Please configure your OpenRouter API key in Settings to start chatting."]]])

      ;; Messages area
      [:div.messages-container
       {:style {:flex 1
                :background "#fff"
                :border-radius "16px"
                :padding "20px"
                :margin-bottom "20px"
                :min-height "400px"
                :max-height "calc(100vh - 350px)"
                :overflow-y "auto"
                :box-shadow "0 2px 12px rgba(0,0,0,0.08)"}}
       (if (empty? messages)
         [:div {:style {:text-align "center"
                        :padding "60px 20px"
                        :color "#999"}}
          [:div {:style {:font-size "48px"
                         :margin-bottom "16px"}}
           "💬"]
          [:div {:style {:font-size "18px"
                         :margin-bottom "8px"}}
           "Start a conversation"]
          [:div {:style {:font-size "14px"}}
           (if (pos? connected-count)
             (str "You have " connected-count " MCP server(s) connected. AI can use their tools.")
             "Connect MCP servers to enable AI tool use.")]]
         (for [[idx msg] (map-indexed vector messages)]
           (rum/with-key (message-bubble msg) idx)))

       ;; Loading indicator
       (when loading?
         [:div {:style {:display "flex"
                        :justify-content "flex-start"
                        :margin-top "12px"}}
          [:div {:style {:padding "12px 16px"
                         :background "#f5f5f5"
                         :border-radius "16px"
                         :color "#666"
                         :font-size "14px"}}
           "Thinking..."]])]

      ;; Input area
      [:div {:style {:display "flex"
                     :gap "12px"
                     :align-items "flex-end"}}
       ;; Use tools toggle
       [:div {:style {:display "flex"
                      :flex-direction "column"
                      :gap "4px"}}
        [:label {:style {:font-size "11px"
                         :color "#999"}}
         "Use MCP"]
        [:button.pointer
         {:on-click #(swap! chat-state update :use-tools? not)
          :style {:padding "10px 12px"
                  :border (if use-tools? "2px solid #667eea" "2px solid #e0e0e0")
                  :border-radius "8px"
                  :background (if use-tools? "#f0f5ff" "#fff")
                  :color (if use-tools? "#667eea" "#999")
                  :font-size "16px"
                  :cursor "pointer"}}
         "🔧"]]

       ;; Text input
       [:input
        {:type "text"
         :placeholder (if api-key-set?
                        "Type your message..."
                        "Configure API key first...")
         :value input
         :disabled (or (not api-key-set?) loading?)
         :on-change #(swap! chat-state assoc :input (.. % -target -value))
         :on-key-down #(when (and (= (.-key %) "Enter") (not (.-shiftKey %)))
                         (.preventDefault %)
                         (send-message!))
         :style {:flex 1
                 :padding "14px 20px"
                 :border "2px solid #e0e0e0"
                 :border-radius "12px"
                 :font-size "16px"
                 :outline "none"}}]

       ;; Send button
       [:button.pointer
        {:on-click send-message!
         :disabled (or (str/blank? input) (not api-key-set?) loading?)
         :style {:padding "14px 24px"
                 :border "none"
                 :border-radius "12px"
                 :background (if (and (not (str/blank? input)) api-key-set? (not loading?))
                               "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                               "#e0e0e0")
                 :color "#fff"
                 :font-size "16px"
                 :font-weight "600"
                 :cursor (if (and (not (str/blank? input)) api-key-set? (not loading?))
                           "pointer"
                           "not-allowed")}}
        "Send"]]

      ;; Clear button
      (when (seq messages)
        [:div {:style {:text-align "center"
                       :margin-top "12px"}}
         [:button.pointer
          {:on-click clear-messages!
           :style {:padding "8px 16px"
                   :border "1px solid #e0e0e0"
                   :border-radius "8px"
                   :background "#fff"
                   :color "#999"
                   :font-size "13px"
                   :cursor "pointer"}}
          "Clear conversation"]])]

     ;; Settings modal
     (settings-modal)

     ;; Footer
     [:div
      {:style {:background "#1a1a2e"
               :padding "24px 20px"
               :text-align "center"}}
      [:div {:style {:color "rgba(255,255,255,0.5)"
                     :font-size "14px"}}
       "© 2026  Hulunote - MIT License"]]]))
