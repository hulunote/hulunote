(ns hulunote.settings.mcp-servers
  (:require [rum.core :as rum]
            [hulunote.mcp :as mcp]
            [hulunote.mcp-state :as mcp-state]
            [hulunote.mcp-ui :as mcp-ui]
            [hulunote.settings.shared :as shared]))

(rum/defc page < rum/reactive []
  (let [_ (rum/react mcp-ui/ui-state)
        _ (rum/react mcp-state/mcp-state)]
    [:div {:style {:padding "24px 0"}}
     [:div
      {:style {:display "flex"
               :justify-content "space-between"
               :align-items "flex-start"
               :gap "20px"
               :padding-right "74px"
               :margin-bottom "24px"}}
      [:div
       [:p {:style {:color "var(--app-text-soft)"
                    :margin 0
                    :font-size "14px"}}
        "Configure Model Context Protocol servers for AI integration"]]
      (shared/action-button
        {:on-click #(swap! mcp-ui/ui-state assoc :show-add-form true)}
        "Add Server")]

     (when-not (mcp/electron?)
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
          "MCP is only available in Hulunote PC App"]
         [:div {:style {:color "var(--theme-warning-soft-text)"
                        :font-size "13px"}}
          "Please use the Hulunote desktop application to configure MCP servers."]]])

     (mcp-ui/server-list)
     (mcp-ui/tools-panel)
     (mcp-ui/add-server-form)
     (mcp-ui/tool-modal)]))
