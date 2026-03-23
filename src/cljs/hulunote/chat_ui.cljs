(ns hulunote.chat-ui
  "AI Chat page shell."
  (:require [rum.core :as rum]
            [hulunote.chat-components :as chat-components]
            [hulunote.chat-core :as chat-core]
            [hulunote.chat-state :as chat-state]
            [hulunote.mcp :as mcp]
            [hulunote.mcp-state :as mcp-state]
            [hulunote.db :as db]
            [hulunote.sidebar :as sidebar]))

(defn get-current-database-name
  [db]
  (let [{:keys [params]} (db/get-route db)]
    (:database params)))

(defn sync-chat-session!
  [db]
  (let [db-name (get-current-database-name db)
        current-db-name (:database-name @chat-state/chat-state)]
    (when (not= db-name current-db-name)
      (chat-core/set-database-name! db-name))))

(rum/defcs chat-page
  < {:will-mount
     (fn [state]
       (chat-core/init-chat!)
       (when (mcp/mcp-available?)
         (mcp-state/init!))
       state)
     :did-mount
     (fn [state]
       (let [[db] (:rum/args state)]
         (sync-chat-session! db))
       state)
     :did-update
     (fn [state]
       (let [[db] (:rum/args state)]
         (sync-chat-session! db))
       state)}
  rum/reactive
  [state db]
  (let [{:keys [connected-clients]} (rum/react mcp-state/mcp-state)
        connected-count (count connected-clients)
        database-name (get-current-database-name db)
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)
        right-sidebar-open? (rum/react db/right-sidebar-open?)]
    [:div.night-center-boxBg.night-textColor-2
     (sidebar/app-top-bar {:title "AI Chat"})
     [:div.page-wrapper
      (sidebar/left-sidebar db database-name)
     [:div.main-content-area
       {:class (str (when sidebar-collapsed? "sidebar-collapsed")
                    (when right-sidebar-open? " right-sidebar-open"))}
       (chat-components/chat-panel connected-count)]]]))
