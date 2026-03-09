(ns hulunote.chat-ui
  "MCP Chat UI 组件"
  (:require [rum.core :as rum]
            [hulunote.chat :as chat]
            [hulunote.mcp :as mcp]
            [hulunote.mcp-state :as mcp-state]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.db :as db]
            [hulunote.sidebar :as sidebar]
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
            (swap! chat-state assoc :model (:model result))))))
))

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
            (let [raw (<! ch)
                  _ (js/console.log "[DEBUG] raw IPC result:" raw)
                  _ (js/console.log "[DEBUG] raw type:" (type raw))
                  _ (js/console.log "[DEBUG] raw.progressLog:" (.-progressLog raw))
                  result (js->clj-safe raw)]
              (js/console.log "[DEBUG] result keys:" (pr-str (keys result)))
              (js/console.log "[DEBUG] :progressLog =" (pr-str (:progressLog result)))
              (js/console.log "[DEBUG] :success =" (pr-str (:success result)))
              (swap! chat-state assoc :loading? false)
              (if (:success result)
                (let [response (:response result)
                      assistant-content (get-in response [:choices 0 :message :content] "")
                      progress-log (:progressLog result)]
                  (js/console.log "[DEBUG] progress-log value:" (pr-str progress-log))
                  (js/console.log "[DEBUG] sequential?:" (sequential? progress-log))
                  (js/console.log "[DEBUG] seq:" (boolean (seq progress-log)))
                  ;; 显示工具调用活动日志
                  (when (and (sequential? progress-log) (seq progress-log))
                    (js/console.log "[DEBUG] Adding system message with" (count progress-log) "lines")
                    (add-message! "system" (str/join "\n" progress-log)))
                  ;; 添加助手回复
                  (when (not (str/blank? assistant-content))
                    (add-message! "assistant" assistant-content)))
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
                             is-error "rgba(255,77,79,0.1)"
                             is-system "rgba(102,126,234,0.1)"
                             :else "rgba(255,255,255,0.08)")
               :color (cond
                        is-user "#fff"
                        is-error "#ff6b6b"
                        is-system "#8da4ef"
                        :else "#fdfeffc4")
               :white-space "pre-wrap"
               :word-break "break-word"
               :font-size (if is-system "12px" "14px")
               :font-family (when is-system "monospace")
               :line-height "1.5"
               :border (cond
                         is-system "1px solid rgba(102,126,234,0.2)"
                         is-error "1px solid rgba(255,77,79,0.2)"
                         :else "none")}}
      content]]))

(rum/defc settings-modal < rum/reactive []
  (let [{:keys [show-settings? api-key model]} (rum/react chat-state)]
    (when show-settings?
      [:div.modal-overlay
       {:style {:position "fixed"
                :top 0 :left 0 :right 0 :bottom 0
                :background "rgba(0,0,0,0.6)"
                :display "flex"
                :align-items "center"
                :justify-content "center"
                :z-index 10000}
        :on-click #(swap! chat-state assoc :show-settings? false)}
       [:div.modal-content
        {:style {:background "#2a2f3a"
                 :border-radius "16px"
                 :padding "32px"
                 :min-width "450px"
                 :border "1px solid rgba(255,255,255,0.1)"
                 :box-shadow "0 8px 32px rgba(0,0,0,0.4)"}
         :on-click #(.stopPropagation %)}

        [:h2 {:style {:margin "0 0 24px 0"
                      :font-size "24px"
                      :font-weight "600"
                      :color "#fdfeffc4"}}
         "Chat Settings"]

        ;; API Key
        [:div {:style {:margin-bottom "20px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "rgba(255,255,255,0.6)"}}
          "OpenRouter API Key"]
         [:input
          {:type "password"
           :placeholder "sk-or-..."
           :value api-key
           :on-change #(swap! chat-state assoc :api-key (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "1px solid rgba(255,255,255,0.15)"
                   :border-radius "8px"
                   :font-size "14px"
                   :outline "none"
                   :box-sizing "border-box"
                   :background "#363b48"
                   :color "#fdfeffc4"}}]
         [:div {:style {:font-size "12px"
                        :color "rgba(255,255,255,0.4)"
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
                          :color "rgba(255,255,255,0.6)"}}
          "Model"]
         [:select
          {:value model
           :on-change #(swap! chat-state assoc :model (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "1px solid rgba(255,255,255,0.15)"
                   :border-radius "8px"
                   :font-size "14px"
                   :outline "none"
                   :box-sizing "border-box"
                   :background "#363b48"
                   :color "#fdfeffc4"}}
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
                   :border "1px solid rgba(255,255,255,0.2)"
                   :border-radius "8px"
                   :background "transparent"
                   :font-size "16px"
                   :font-weight "500"
                   :color "rgba(255,255,255,0.7)"
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

(defn get-current-database-name
  [db]
  (let [{:keys [params]} (db/get-route db)]
    (:database params)))

(rum/defcs chat-page
  < {:will-mount
     (fn [state]
       (init-chat!)
       (when (mcp/mcp-available?)
         (mcp-state/init!))
       state)}
  rum/reactive
  [state db]
  (let [{:keys [messages input loading? api-key-set? use-tools? error model]}
        (rum/react chat-state)
        {:keys [servers connected-clients]} (rum/react mcp-state/mcp-state)
        connected-count (count connected-clients)
        database-name (get-current-database-name db)
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)]
    [:div.night-center-boxBg.night-textColor-2
     (sidebar/app-top-bar {:title "MCP Chat"})
     [:div.page-wrapper
      ;; Left sidebar
      (sidebar/left-sidebar db database-name)
      ;; Main content area
      [:div.main-content-area
       {:class (when sidebar-collapsed? "sidebar-collapsed")}
       [:div.flex.flex-column
        {:style {:padding "20px"
                 :max-width "900px"
                 :margin "0 auto"
                 :height "calc(100vh - var(--app-topbar-height, 0px) - 40px)"}}

        ;; Header bar with MCP status and settings
        [:div {:style {:display "flex"
                       :justify-content "space-between"
                       :align-items "center"
                       :margin-bottom "16px"}}
         [:h1 {:style {:font-size "24px"
                       :font-weight "600"
                       :margin 0}}
          "MCP Chat"]
         [:div.flex.items-center {:style {:gap "12px"}}
          ;; MCP status
          [:div {:style {:font-size "13px"
                         :display "flex"
                         :align-items "center"
                         :gap "6px"
                         :color "rgba(255,255,255,0.6)"}}
           [:span {:style {:width "8px"
                           :height "8px"
                           :border-radius "50%"
                           :background (if (pos? connected-count) "#52c41a" "#ff4d4f")}}]
           (str connected-count " MCP connected")]
          ;; Settings button
          [:button.pointer
           {:on-click #(swap! chat-state assoc :show-settings? true)
            :style {:background "rgba(255,255,255,0.1)"
                    :border "1px solid rgba(255,255,255,0.2)"
                    :padding "6px 14px"
                    :border-radius "6px"
                    :color "rgba(255,255,255,0.7)"
                    :font-size "13px"
                    :cursor "pointer"}}
           "Settings"]]]

        ;; API Key warning
        (when-not api-key-set?
          [:div {:style {:background "rgba(250,140,22,0.1)"
                         :border "1px solid rgba(250,140,22,0.3)"
                         :border-radius "8px"
                         :padding "16px 20px"
                         :margin-bottom "16px"
                         :display "flex"
                         :align-items "center"
                         :gap "12px"}}
           [:span {:style {:font-size "24px"}} "⚠️"]
           [:div
            [:div {:style {:font-weight "600"
                           :color "#fa8c16"}}
             "API Key Required"]
            [:div {:style {:color "rgba(250,140,22,0.8)"
                           :font-size "13px"}}
             "Please configure your OpenRouter API key in Settings to start chatting."]]])

        ;; Messages area
        [:div.messages-container
         {:style {:flex 1
                  :background "rgba(255,255,255,0.03)"
                  :border-radius "12px"
                  :border "1px solid rgba(255,255,255,0.08)"
                  :padding "20px"
                  :margin-bottom "16px"
                  :min-height "300px"
                  :overflow-y "auto"}}
         (if (empty? messages)
           [:div {:style {:text-align "center"
                          :padding "60px 20px"
                          :color "rgba(255,255,255,0.4)"}}
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
                           :background "rgba(255,255,255,0.05)"
                           :border-radius "16px"
                           :color "rgba(255,255,255,0.6)"
                           :font-size "14px"
                           :display "flex"
                           :align-items "center"
                           :gap "8px"}}
             [:span {:style {:display "inline-block"
                             :width "8px"
                             :height "8px"
                             :border-radius "50%"
                             :background "#667eea"
                             :animation "pulse 1.5s ease-in-out infinite"}}]
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
                           :color "rgba(255,255,255,0.4)"}}
           "Use MCP"]
          [:button.pointer
           {:on-click #(swap! chat-state update :use-tools? not)
            :style {:padding "10px 12px"
                    :border (if use-tools?
                              "1px solid rgba(102,126,234,0.5)"
                              "1px solid rgba(255,255,255,0.15)")
                    :border-radius "8px"
                    :background (if use-tools? "rgba(102,126,234,0.15)" "transparent")
                    :color (if use-tools? "#667eea" "rgba(255,255,255,0.4)")
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
                   :border "1px solid rgba(255,255,255,0.15)"
                   :border-radius "12px"
                   :font-size "16px"
                   :outline "none"
                   :background "#363b48"
                   :color "#fdfeffc4"}}]

         ;; Send button
         [:button.pointer
          {:on-click send-message!
           :disabled (or (str/blank? input) (not api-key-set?) loading?)
           :style {:padding "14px 24px"
                   :border "none"
                   :border-radius "12px"
                   :background (if (and (not (str/blank? input)) api-key-set? (not loading?))
                                 "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                                 "rgba(255,255,255,0.1)")
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
                     :border "1px solid rgba(255,255,255,0.15)"
                     :border-radius "8px"
                     :background "transparent"
                     :color "rgba(255,255,255,0.4)"
                     :font-size "13px"
                     :cursor "pointer"}}
            "Clear conversation"]])]]]

     ;; Settings modal
     (settings-modal)]))
