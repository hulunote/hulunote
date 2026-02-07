(ns hulunote.mcp-ui
  "MCP UI 组件
   提供 MCP 服务器管理和工具调用的用户界面"
  (:require [rum.core :as rum]
            [hulunote.mcp :as mcp]
            [hulunote.mcp-state :as mcp-state]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [clojure.string :as str]))

;; ==================== State ====================

(defonce ui-state
  (atom {:show-add-form false
         :form-data {:name "" :command "" :args "" :env ""}
         :selected-tool nil
         :tool-args {}
         :show-tool-modal false}))

;; ==================== Helper Functions ====================

(defn parse-args
  "将空格分隔的参数字符串转换为数组"
  [args-str]
  (if (str/blank? args-str)
    []
    (vec (remove str/blank? (str/split args-str #"\s+")))))

(defn parse-env
  "解析环境变量字符串 (KEY=VALUE 格式，每行一个)"
  [env-str]
  (if (str/blank? env-str)
    {}
    (->> (str/split-lines env-str)
         (map str/trim)
         (remove str/blank?)
         (map #(str/split % #"=" 2))
         (filter #(= 2 (count %)))
         (into {}))))

;; ==================== Add Server Form ====================

(rum/defcs add-server-form < rum/reactive
  [state]
  (let [{:keys [show-add-form form-data]} (rum/react ui-state)
        {:keys [name command args env]} form-data]
    (when show-add-form
      [:div.modal-overlay
       {:style {:position "fixed"
                :top 0 :left 0 :right 0 :bottom 0
                :background "rgba(0,0,0,0.5)"
                :display "flex"
                :align-items "center"
                :justify-content "center"
                :z-index 10000}
        :on-click #(swap! ui-state assoc :show-add-form false)}
       [:div.modal-content
        {:style {:background "#fff"
                 :border-radius "16px"
                 :padding "32px"
                 :min-width "500px"
                 :max-width "600px"
                 :box-shadow "0 8px 32px rgba(0,0,0,0.2)"}
         :on-click #(.stopPropagation %)}

        [:h2 {:style {:margin "0 0 24px 0"
                      :font-size "24px"
                      :font-weight "600"
                      :color "#1a1a2e"}}
         "Add MCP Server"]

        ;; Server Name
        [:div {:style {:margin-bottom "20px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "#666"}}
          "Server Name *"]
         [:input
          {:type "text"
           :placeholder "e.g., filesystem"
           :value name
           :on-change #(swap! ui-state assoc-in [:form-data :name] (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "2px solid #e0e0e0"
                   :border-radius "8px"
                   :font-size "16px"
                   :outline "none"
                   :box-sizing "border-box"}}]]

        ;; Command
        [:div {:style {:margin-bottom "20px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "#666"}}
          "Command *"]
         [:input
          {:type "text"
           :placeholder "e.g., npx"
           :value command
           :on-change #(swap! ui-state assoc-in [:form-data :command] (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "2px solid #e0e0e0"
                   :border-radius "8px"
                   :font-size "16px"
                   :outline "none"
                   :box-sizing "border-box"}}]]

        ;; Arguments
        [:div {:style {:margin-bottom "20px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "#666"}}
          "Arguments (space separated)"]
         [:input
          {:type "text"
           :placeholder "e.g., -y @modelcontextprotocol/server-filesystem /path/to/dir"
           :value args
           :on-change #(swap! ui-state assoc-in [:form-data :args] (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "2px solid #e0e0e0"
                   :border-radius "8px"
                   :font-size "16px"
                   :outline "none"
                   :box-sizing "border-box"}}]]

        ;; Environment Variables
        [:div {:style {:margin-bottom "24px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "#666"}}
          "Environment Variables (KEY=VALUE, one per line)"]
         [:textarea
          {:placeholder "GITHUB_TOKEN=xxx\nAPI_KEY=yyy"
           :value env
           :on-change #(swap! ui-state assoc-in [:form-data :env] (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "2px solid #e0e0e0"
                   :border-radius "8px"
                   :font-size "14px"
                   :font-family "monospace"
                   :outline "none"
                   :min-height "80px"
                   :resize "vertical"
                   :box-sizing "border-box"}}]]

        ;; Buttons
        [:div {:style {:display "flex"
                       :justify-content "flex-end"
                       :gap "12px"}}
         [:button.pointer
          {:on-click #(do
                        (swap! ui-state assoc
                               :show-add-form false
                               :form-data {:name "" :command "" :args "" :env ""}))
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
          {:on-click #(let [server-config {:name (str/trim name)
                                           :command (str/trim command)
                                           :args (parse-args args)
                                           :env (parse-env env)}]
                        (when (and (not (str/blank? (:name server-config)))
                                   (not (str/blank? (:command server-config))))
                          (mcp-state/add-server!
                           server-config
                           (fn [result]
                             (when (:success result)
                               (swap! ui-state assoc
                                      :show-add-form false
                                      :form-data {:name "" :command "" :args "" :env ""}))))))
           :style {:padding "12px 24px"
                   :border "none"
                   :border-radius "8px"
                   :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                   :font-size "16px"
                   :font-weight "600"
                   :color "#fff"
                   :cursor "pointer"}}
          "Add Server"]]]])))

;; ==================== Server Card ====================

(rum/defc server-card < rum/reactive
  [server]
  (let [{:keys [name command args connected]} server
        {:keys [loading?]} (rum/react mcp-state/mcp-state)]
    [:div.server-card
     {:style {:background "#fff"
              :border-radius "12px"
              :padding "20px"
              :box-shadow "0 2px 12px rgba(0,0,0,0.08)"
              :border (if connected "2px solid #52c41a" "2px solid transparent")}}

     ;; Header
     [:div {:style {:display "flex"
                    :justify-content "space-between"
                    :align-items "flex-start"
                    :margin-bottom "12px"}}
      [:div
       [:div {:style {:font-size "18px"
                      :font-weight "600"
                      :color "#1a1a2e"
                      :margin-bottom "4px"}}
        name]
       [:div {:style {:font-size "12px"
                      :color "#999"
                      :font-family "monospace"}}
        (str command " " (str/join " " args))]]

      ;; Status badge
      [:div {:style {:padding "4px 12px"
                     :border-radius "12px"
                     :font-size "12px"
                     :font-weight "500"
                     :background (if connected "#f6ffed" "#fff7e6")
                     :color (if connected "#52c41a" "#fa8c16")}}
       (if connected "Connected" "Disconnected")]]

     ;; Actions
     [:div {:style {:display "flex"
                    :gap "8px"
                    :margin-top "16px"}}
      (if connected
        ;; Disconnect and Tools buttons
        [:<>
         [:button.pointer
          {:on-click #(mcp-state/disconnect-server! name nil)
           :disabled loading?
           :style {:flex 1
                   :padding "8px 16px"
                   :border "2px solid #ff4d4f"
                   :border-radius "6px"
                   :background "#fff"
                   :font-size "14px"
                   :font-weight "500"
                   :color "#ff4d4f"
                   :cursor (if loading? "not-allowed" "pointer")
                   :opacity (if loading? 0.6 1)}}
          "Disconnect"]
         [:button.pointer
          {:on-click #(do
                        (mcp-state/select-server! name)
                        (mcp-state/load-tools! name nil))
           :style {:flex 1
                   :padding "8px 16px"
                   :border "none"
                   :border-radius "6px"
                   :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                   :font-size "14px"
                   :font-weight "500"
                   :color "#fff"
                   :cursor "pointer"}}
          "Tools"]]

        ;; Connect button
        [:button.pointer
         {:on-click #(mcp-state/connect-server! server nil)
          :disabled loading?
          :style {:flex 1
                  :padding "8px 16px"
                  :border "none"
                  :border-radius "6px"
                  :background "linear-gradient(135deg, #52c41a 0%, #389e0d 100%)"
                  :font-size "14px"
                  :font-weight "500"
                  :color "#fff"
                  :cursor (if loading? "not-allowed" "pointer")
                  :opacity (if loading? 0.6 1)}}
         "Connect"])

      ;; Delete button
      [:button.pointer
       {:on-click #(when (js/confirm (str "Are you sure you want to delete \"" name "\"?"))
                     (mcp-state/remove-server! name nil))
        :style {:padding "8px 12px"
                :border "2px solid #ff4d4f"
                :border-radius "6px"
                :background "#fff"
                :font-size "14px"
                :color "#ff4d4f"
                :cursor "pointer"}}
       "Delete"]]]))

;; ==================== Server List ====================

(rum/defc server-list < rum/reactive
  []
  (let [{:keys [servers loading? error]} (rum/react mcp-state/mcp-state)]
    [:div
     ;; Error message
     (when error
       [:div {:style {:background "#fff2f0"
                      :border "1px solid #ffccc7"
                      :border-radius "8px"
                      :padding "12px 16px"
                      :margin-bottom "20px"
                      :color "#ff4d4f"}}
        error])

     ;; Loading indicator
     (when loading?
       [:div {:style {:text-align "center"
                      :padding "20px"
                      :color "#666"}}
        "Loading..."])

     ;; Server grid
     (if (empty? servers)
       [:div {:style {:text-align "center"
                      :padding "60px 20px"
                      :color "#999"}}
        [:div {:style {:font-size "48px"
                       :margin-bottom "16px"}}
         "🔌"]
        [:div {:style {:font-size "18px"
                       :margin-bottom "8px"}}
         "No MCP Servers Configured"]
        [:div {:style {:font-size "14px"}}
         "Click \"+ Add Server\" to add your first MCP server"]]
       [:div {:style {:display "grid"
                      :grid-template-columns "repeat(auto-fill, minmax(350px, 1fr))"
                      :gap "20px"}}
        (for [server servers]
          (rum/with-key (server-card server) (:name server)))])]))

;; ==================== Tools Panel ====================

(rum/defc tool-item
  [client-id tool]
  (let [{:keys [name description inputSchema]} tool]
    [:div.tool-item
     {:style {:background "#fff"
              :border-radius "8px"
              :padding "16px"
              :border "1px solid #e0e0e0"
              :cursor "pointer"
              :transition "all 0.2s"}
      :on-click #(swap! ui-state assoc
                        :selected-tool tool
                        :tool-args {}
                        :show-tool-modal true
                        :current-client client-id)}
     [:div {:style {:font-weight "600"
                    :color "#1a1a2e"
                    :margin-bottom "4px"}}
      name]
     (when description
       [:div {:style {:font-size "13px"
                      :color "#666"
                      :line-height "1.4"}}
        description])]))

(rum/defc tools-panel < rum/reactive
  []
  (let [{:keys [tools selected-server]} (rum/react mcp-state/mcp-state)
        client-tools (get tools selected-server [])]
    (when selected-server
      [:div {:style {:margin-top "32px"}}
       [:div {:style {:display "flex"
                      :justify-content "space-between"
                      :align-items "center"
                      :margin-bottom "16px"}}
        [:h3 {:style {:margin 0
                      :font-size "20px"
                      :color "#1a1a2e"}}
         (str "Tools - " selected-server)]
        [:button.pointer
         {:on-click #(mcp-state/load-tools! selected-server nil)
          :style {:padding "6px 12px"
                  :border "1px solid #e0e0e0"
                  :border-radius "6px"
                  :background "#fff"
                  :font-size "13px"
                  :cursor "pointer"}}
         "Refresh"]]

       (if (empty? client-tools)
         [:div {:style {:text-align "center"
                        :padding "40px"
                        :color "#999"
                        :background "#f5f5f5"
                        :border-radius "8px"}}
          "No tools available. Click Refresh to load tools."]
         [:div {:style {:display "grid"
                        :grid-template-columns "repeat(auto-fill, minmax(280px, 1fr))"
                        :gap "12px"}}
          (for [tool client-tools]
            (rum/with-key (tool-item selected-server tool) (:name tool)))])])))

;; ==================== Tool Execution Modal ====================

(rum/defc tool-modal < rum/reactive
  []
  (let [{:keys [show-tool-modal selected-tool tool-args current-client]} (rum/react ui-state)
        {:keys [tool-results]} (rum/react mcp-state/mcp-state)]
    (when (and show-tool-modal selected-tool)
      (let [{:keys [name description inputSchema]} selected-tool
            properties (get-in inputSchema [:properties])
            required-fields (set (get inputSchema :required []))]
        [:div.modal-overlay
         {:style {:position "fixed"
                  :top 0 :left 0 :right 0 :bottom 0
                  :background "rgba(0,0,0,0.5)"
                  :display "flex"
                  :align-items "center"
                  :justify-content "center"
                  :z-index 10000}
          :on-click #(swap! ui-state assoc :show-tool-modal false)}
         [:div.modal-content
          {:style {:background "#fff"
                   :border-radius "16px"
                   :padding "32px"
                   :min-width "500px"
                   :max-width "700px"
                   :max-height "80vh"
                   :overflow-y "auto"
                   :box-shadow "0 8px 32px rgba(0,0,0,0.2)"}
           :on-click #(.stopPropagation %)}

          [:h2 {:style {:margin "0 0 8px 0"
                        :font-size "24px"
                        :font-weight "600"
                        :color "#1a1a2e"}}
           (str "Execute: " name)]

          (when description
            [:p {:style {:margin "0 0 24px 0"
                         :color "#666"
                         :font-size "14px"}}
             description])

          ;; Input fields based on schema
          (when (seq properties)
            [:div {:style {:margin-bottom "24px"}}
             (for [[prop-name prop-schema] properties]
               (let [prop-key (keyword prop-name)
                     is-required (contains? required-fields (clj->js prop-name))]
                 [:div {:key prop-name
                        :style {:margin-bottom "16px"}}
                  [:label {:style {:display "block"
                                   :margin-bottom "8px"
                                   :font-size "14px"
                                   :font-weight "500"
                                   :color "#666"}}
                   (str prop-name (when is-required " *"))]
                  (when-let [desc (:description prop-schema)]
                    [:div {:style {:font-size "12px"
                                   :color "#999"
                                   :margin-bottom "6px"}}
                     desc])
                  [:input
                   {:type "text"
                    :placeholder (or (:default prop-schema) "")
                    :value (get tool-args prop-key "")
                    :on-change #(swap! ui-state assoc-in [:tool-args prop-key] (.. % -target -value))
                    :style {:width "100%"
                            :padding "12px 16px"
                            :border "2px solid #e0e0e0"
                            :border-radius "8px"
                            :font-size "14px"
                            :outline "none"
                            :box-sizing "border-box"}}]]))])

          ;; Buttons
          [:div {:style {:display "flex"
                         :justify-content "flex-end"
                         :gap "12px"}}
           [:button.pointer
            {:on-click #(swap! ui-state assoc :show-tool-modal false)
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
            {:on-click #(mcp-state/execute-tool!
                         current-client
                         name
                         tool-args
                         (fn [result]
                           (if (:success result)
                             (u/alert (str "Tool executed successfully!\n\nResult: " (pr-str (:result result))))
                             (u/alert (str "Tool execution failed: " (:error result))))))
             :style {:padding "12px 24px"
                     :border "none"
                     :border-radius "8px"
                     :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                     :font-size "16px"
                     :font-weight "600"
                     :color "#fff"
                     :cursor "pointer"}}
            "Execute"]]]]))))

;; ==================== Main Settings Panel ====================

(rum/defcs mcp-settings-panel
  < {:will-mount
     (fn [state]
       ;; 初始化 MCP 状态
       (when (mcp/mcp-available?)
         (mcp-state/init!))
       state)
     :will-unmount
     (fn [state]
       ;; 清理事件监听
       (mcp-state/cleanup!)
       state)}
  rum/reactive
  [state database-name]
  (let [_ (rum/react ui-state)
        _ (rum/react mcp-state/mcp-state)]
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
      [:div {:style {:color "#fff"
                     :font-size "14px"
                     :-webkit-app-region "no-drag"}}
       "MCP Settings"]]

     ;; Main content
     [:div.flex.flex-column
      {:style {:flex "1"
               :padding "40px 20px"
               :max-width "1200px"
               :margin "0 auto"
               :width "100%"}}

      ;; Title and Add button
      [:div {:style {:display "flex"
                     :justify-content "space-between"
                     :align-items "center"
                     :margin-bottom "32px"
                     :margin-top "88px"}}
       [:div
        [:h1 {:style {:font-size "32px"
                      :font-weight "700"
                      :color "#1a1a2e"
                      :margin "0 0 8px 0"}}
         "MCP Server Settings"]
        [:p {:style {:color "#666"
                     :margin 0
                     :font-size "14px"}}
         "Configure Model Context Protocol servers for AI integration"]]

       [:button.pointer
        {:on-click #(swap! ui-state assoc :show-add-form true)
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
                 :box-shadow "0 4px 12px rgba(102, 126, 234, 0.4)"}}
        [:span {:style {:font-size "20px"}} "+"]
        [:span "Add Server"]]]

      ;; Not in Electron warning
      (when-not (mcp/electron?)
        [:div {:style {:background "#fff7e6"
                       :border "1px solid #ffd591"
                       :border-radius "8px"
                       :padding "16px 20px"
                       :margin-bottom "24px"
                       :display "flex"
                       :align-items "center"
                       :gap "12px"}}
         [:span {:style {:font-size "24px"}} "⚠️"]
         [:div
          [:div {:style {:font-weight "600"
                         :color "#d46b08"
                         :margin-bottom "4px"}}
           "MCP is only available in Electron"]
          [:div {:style {:color "#ad6800"
                         :font-size "13px"}}
           "Please use the Hulunote desktop application to configure MCP servers."]]])

      ;; Server list
      (server-list)

      ;; Tools panel
      (tools-panel)]

     ;; Modals
     (add-server-form)
     (tool-modal)

     ;; Footer
     [:div
      {:style {:background "#1a1a2e"
               :padding "24px 20px"
               :text-align "center"}}
      [:div {:style {:color "rgba(255,255,255,0.5)"
                     :font-size "14px"}}
       "© 2024 Hulunote - MIT License"]]]))
