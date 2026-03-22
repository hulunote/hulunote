(ns hulunote.mcp-ui
  "MCP UI 组件
   提供 MCP 服务器管理和工具调用的用户界面"
  (:require [rum.core :as rum]
            [hulunote.mcp :as mcp]
            [hulunote.mcp-state :as mcp-state]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.db :as db]
            [hulunote.sidebar :as sidebar]
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
                :background "var(--app-modal-backdrop-strong)"
                :display "flex"
                :align-items "center"
                :justify-content "center"
                :z-index 10000}
        :on-click #(swap! ui-state assoc :show-add-form false)}
       [:div.modal-content
        {:style {:background "var(--app-modal-surface)"
                 :border-radius "16px"
                 :padding "32px"
                 :min-width "500px"
                 :max-width "600px"
                 :border "1px solid var(--surface-border)"
                 :box-shadow "var(--app-modal-shadow)"}
         :on-click #(.stopPropagation %)}

        [:h2 {:style {:margin "0 0 24px 0"
                      :font-size "24px"
                      :font-weight "600"
                      :color "var(--app-text-primary)"}}
         "Add MCP Server"]

        ;; Server Name
        [:div {:style {:margin-bottom "20px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "var(--app-text-muted)"}}
          "Server Name *"]
         [:input
          {:type "text"
           :placeholder "e.g., filesystem"
           :value name
           :on-change #(swap! ui-state assoc-in [:form-data :name] (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "1px solid var(--surface-border-strong)"
                   :border-radius "8px"
                   :font-size "16px"
                   :outline "none"
                   :box-sizing "border-box"
                   :background "var(--app-modal-input-bg)"
                   :color "var(--app-text-primary)"}}]]

        ;; Command
        [:div {:style {:margin-bottom "20px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "var(--app-text-muted)"}}
          "Command *"]
         [:input
          {:type "text"
           :placeholder "e.g., npx"
           :value command
           :on-change #(swap! ui-state assoc-in [:form-data :command] (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "1px solid var(--surface-border-strong)"
                   :border-radius "8px"
                   :font-size "16px"
                   :outline "none"
                   :box-sizing "border-box"
                   :background "var(--app-modal-input-bg)"
                   :color "var(--app-text-primary)"}}]]

        ;; Arguments
        [:div {:style {:margin-bottom "20px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "var(--app-text-muted)"}}
          "Arguments (space separated)"]
         [:input
          {:type "text"
           :placeholder "e.g., -y @modelcontextprotocol/server-filesystem /path/to/dir"
           :value args
           :on-change #(swap! ui-state assoc-in [:form-data :args] (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "1px solid var(--surface-border-strong)"
                   :border-radius "8px"
                   :font-size "16px"
                   :outline "none"
                   :box-sizing "border-box"
                   :background "var(--app-modal-input-bg)"
                   :color "var(--app-text-primary)"}}]]

        ;; Environment Variables
        [:div {:style {:margin-bottom "24px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "var(--app-text-muted)"}}
          "Environment Variables (KEY=VALUE, one per line)"]
         [:textarea
          {:placeholder "GITHUB_TOKEN=xxx\nAPI_KEY=yyy"
           :value env
           :on-change #(swap! ui-state assoc-in [:form-data :env] (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "1px solid var(--surface-border-strong)"
                   :border-radius "8px"
                   :font-size "14px"
                   :font-family "monospace"
                   :outline "none"
                   :min-height "80px"
                   :resize "vertical"
                   :box-sizing "border-box"
                   :background "var(--app-modal-input-bg)"
                   :color "var(--app-text-primary)"}}]]

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
                   :border "1px solid var(--app-control-border)"
                   :border-radius "8px"
                   :background "transparent"
                   :font-size "16px"
                   :font-weight "500"
                   :color "var(--app-control-text)"
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
                   :background "var(--theme-accent-gradient)"
                   :font-size "16px"
                   :font-weight "600"
                   :color "var(--theme-accent-text)"
                   :cursor "pointer"}}
          "Add Server"]]]])))

;; ==================== Server Card ====================

(rum/defc server-card < rum/reactive
  [server]
  (let [{:keys [name command args connected]} server
        {:keys [loading?]} (rum/react mcp-state/mcp-state)]
    [:div.server-card
     {:style {:background "var(--app-overlay-soft-strong)"
              :border-radius "12px"
              :padding "20px"
              :border (if connected
                        "1px solid var(--theme-success-border)"
                        "1px solid var(--app-divider)")}}

     ;; Header
     [:div {:style {:display "flex"
                    :justify-content "space-between"
                    :align-items "flex-start"
                    :margin-bottom "12px"}}
      [:div
       [:div {:style {:font-size "18px"
                      :font-weight "600"
                      :margin-bottom "4px"}}
        name]
       [:div {:style {:font-size "12px"
                      :color "var(--app-text-subtle)"
                      :font-family "monospace"}}
        (str command " " (str/join " " args))]]

      ;; Status badge
      [:div {:style {:padding "4px 12px"
                     :border-radius "12px"
                     :font-size "12px"
                     :font-weight "500"
                     :background (if connected "var(--theme-success-soft)" "var(--theme-warning-soft)")
                     :color (if connected "var(--theme-success)" "var(--theme-warning)")}}
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
                   :border "1px solid var(--theme-error-border-strong)"
                   :border-radius "6px"
                   :background "transparent"
                   :font-size "14px"
                   :font-weight "500"
                   :color "var(--app-danger-soft)"
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
                   :background "var(--theme-accent-gradient)"
                   :font-size "14px"
                   :font-weight "500"
                   :color "var(--theme-accent-text)"
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
                  :background "var(--theme-success-gradient)"
                  :font-size "14px"
                  :font-weight "500"
                  :color "var(--theme-accent-text)"
                  :cursor (if loading? "not-allowed" "pointer")
                  :opacity (if loading? 0.6 1)}}
         "Connect"])

      ;; Delete button
      [:button.pointer
       {:on-click #(when (js/confirm (str "Are you sure you want to delete \"" name "\"?"))
                     (mcp-state/remove-server! name nil))
        :style {:padding "8px 12px"
                :border "1px solid var(--theme-error-border-strong)"
                :border-radius "6px"
                :background "transparent"
                :font-size "14px"
                :color "var(--app-danger-soft)"
                :cursor "pointer"}}
       "Delete"]]]))

;; ==================== Server List ====================

(rum/defc server-list < rum/reactive
  []
  (let [{:keys [servers loading? error]} (rum/react mcp-state/mcp-state)]
    [:div
     ;; Error message
     (when error
       [:div {:style {:background "var(--theme-error-soft)"
                      :border "1px solid var(--theme-error-border)"
                      :border-radius "8px"
                      :padding "12px 16px"
                      :margin-bottom "20px"
                      :color "var(--app-danger)"}}
        error])

     ;; Loading indicator
     (when loading?
       [:div {:style {:text-align "center"
                      :padding "20px"
                      :color "var(--app-text-muted)"}}
        "Loading..."])

     ;; Server grid
     (if (empty? servers)
       [:div {:style {:text-align "center"
                      :padding "60px 20px"
                      :color "var(--app-text-subtle)"}}
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
     {:style {:background "var(--app-overlay-soft-strong)"
              :border-radius "8px"
              :padding "16px"
              :border "1px solid var(--app-divider)"
              :cursor "pointer"
              :transition "all 0.2s"}
      :on-click #(swap! ui-state assoc
                        :selected-tool tool
                        :tool-args {}
                        :show-tool-modal true
                        :current-client client-id)}
     [:div {:style {:font-weight "600"
                    :margin-bottom "4px"}}
      name]
     (when description
       [:div {:style {:font-size "13px"
                      :color "var(--app-text-soft)"
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
                      :font-size "20px"}}
         (str "Tools - " selected-server)]
        [:button.pointer
         {:on-click #(mcp-state/load-tools! selected-server nil)
          :style {:padding "6px 12px"
                  :border "1px solid var(--app-control-border)"
                  :border-radius "6px"
                  :background "transparent"
                  :font-size "13px"
                  :color "var(--app-control-text)"
                  :cursor "pointer"}}
         "Refresh"]]

       (if (empty? client-tools)
         [:div {:style {:text-align "center"
                        :padding "40px"
                        :color "var(--app-text-subtle)"
                        :background "var(--app-row-hover)"
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
                  :background "var(--app-modal-backdrop-strong)"
                  :display "flex"
                  :align-items "center"
                  :justify-content "center"
                  :z-index 10000}
          :on-click #(swap! ui-state assoc :show-tool-modal false)}
         [:div.modal-content
          {:style {:background "var(--app-modal-surface)"
                   :border-radius "16px"
                   :padding "32px"
                   :min-width "500px"
                   :max-width "700px"
                   :max-height "80vh"
                   :overflow-y "auto"
                   :border "1px solid var(--surface-border)"
                   :box-shadow "var(--app-modal-shadow)"}
           :on-click #(.stopPropagation %)}

          [:h2 {:style {:margin "0 0 8px 0"
                        :font-size "24px"
                        :font-weight "600"
                        :color "var(--app-text-primary)"}}
           (str "Execute: " name)]

          (when description
            [:p {:style {:margin "0 0 24px 0"
                         :color "var(--app-text-soft)"
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
                                   :color "var(--app-text-muted)"}}
                   (str prop-name (when is-required " *"))]
                  (when-let [desc (:description prop-schema)]
                    [:div {:style {:font-size "12px"
                                   :color "var(--app-text-subtle)"
                                   :margin-bottom "6px"}}
                     desc])
                  [:input
                   {:type "text"
                    :placeholder (or (:default prop-schema) "")
                    :value (get tool-args prop-key "")
                    :on-change #(swap! ui-state assoc-in [:tool-args prop-key] (.. % -target -value))
                    :style {:width "100%"
                            :padding "12px 16px"
                            :border "1px solid var(--surface-border-strong)"
                            :border-radius "8px"
                            :font-size "14px"
                            :outline "none"
                            :box-sizing "border-box"
                            :background "var(--app-modal-input-bg)"
                            :color "var(--app-text-primary)"}}]]))])

          ;; Buttons
          [:div {:style {:display "flex"
                         :justify-content "flex-end"
                         :gap "12px"}}
           [:button.pointer
           {:on-click #(swap! ui-state assoc :show-tool-modal false)
             :style {:padding "12px 24px"
                     :border "1px solid var(--app-control-border)"
                     :border-radius "8px"
                     :background "transparent"
                     :font-size "16px"
                     :font-weight "500"
                     :color "var(--app-control-text)"
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
                     :background "var(--theme-accent-gradient)"
                     :font-size "16px"
                     :font-weight "600"
                     :color "var(--theme-accent-text)"
                     :cursor "pointer"}}
            "Execute"]]]]))))

;; ==================== Main Settings Panel ====================

(defn get-current-database-name
  [db]
  (let [{:keys [params]} (db/get-route db)]
    (:database params)))

(rum/defcs mcp-settings-panel
  < {:will-mount
     (fn [state]
       (when (mcp/mcp-available?)
         (mcp-state/init!))
       state)
     :will-unmount
     (fn [state]
       (mcp-state/cleanup!)
       state)}
  rum/reactive
  [state db]
  (let [_ (rum/react ui-state)
        _ (rum/react mcp-state/mcp-state)
        database-name (get-current-database-name db)
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)]
    [:div.night-center-boxBg.night-textColor-2
     (sidebar/app-top-bar {:title "MCP Settings"})
     [:div.page-wrapper
      ;; Left sidebar
      (sidebar/left-sidebar db database-name)
      ;; Main content area
      [:div.main-content-area
       {:class (when sidebar-collapsed? "sidebar-collapsed")}
       [:div.flex.flex-column
        {:style {:padding "20px"
                 :max-width "900px"
                 :margin "0 auto"}}

        ;; Title and Add button
        [:div {:style {:display "flex"
                       :justify-content "space-between"
                       :align-items "center"
                       :margin-bottom "32px"}}
         [:div
          [:h1 {:style {:font-size "24px"
                        :font-weight "600"
                        :margin "0 0 8px 0"}}
           "MCP Server Settings"]
          [:p {:style {:color "var(--app-text-muted)"
                       :margin 0
                       :font-size "14px"}}
           "Configure Model Context Protocol servers for AI integration"]]

         [:button.pointer
          {:on-click #(swap! ui-state assoc :show-add-form true)
           :style {:background "var(--theme-accent-gradient)"
                   :color "var(--theme-accent-text)"
                   :border "none"
                   :padding "10px 20px"
                   :border-radius "8px"
                   :font-size "14px"
                   :font-weight "600"
                   :cursor "pointer"
                   :display "flex"
                   :align-items "center"
                   :gap "8px"}}
          [:span {:style {:font-size "18px"}} "+"]
          [:span "Add Server"]]]

        ;; Not in Electron warning
        (when-not (mcp/electron?)
          [:div {:style {:background "var(--theme-warning-soft)"
                         :border "1px solid var(--theme-warning-border)"
                         :border-radius "8px"
                         :padding "16px 20px"
                         :margin-bottom "24px"
                         :display "flex"
                         :align-items "center"
                         :gap "12px"}}
           [:span {:style {:font-size "24px"}} "⚠️"]
           [:div
            [:div {:style {:font-weight "600"
                           :color "var(--theme-warning)"
                           :margin-bottom "4px"}}
             "MCP is only available in Hulunote PC App"]
            [:div {:style {:color "var(--theme-warning-soft-text)"
                           :font-size "13px"}}
             "Please use the Hulunote desktop application to configure MCP servers."]]])

        ;; Server list
        (server-list)

        ;; Tools panel
        (tools-panel)

        [:div {:style {:height "50px"}}]]]]

     ;; Modals
     (add-server-form)
     (tool-modal)]))
