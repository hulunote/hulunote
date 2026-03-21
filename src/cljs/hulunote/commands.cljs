(ns hulunote.commands
  (:require [hulunote.db :as db]
            [hulunote.router :as router]
            [hulunote.settings-state :as settings-state]
            [hulunote.sidebar :as sidebar]
            [hulunote.util :as u]))

(defonce command-registry (atom {}))

(defn register-command!
  [{:keys [id] :as command}]
  (when id
    (swap! command-registry assoc id command)))

(defn unregister-command!
  [command-id]
  (swap! command-registry dissoc command-id))

(defn get-command
  [command-id]
  (get @command-registry command-id))

(defn all-commands
  []
  (vals @command-registry))

(defn execute-command!
  ([command-id]
   (execute-command! command-id nil))
  ([command-id context]
   (when-let [handler (:handler (get-command command-id))]
     (handler context))))

(defn authenticated?
  []
  (not (u/is-expired?)))

(defn route-name
  []
  (:route-name (db/get-route @db/dsdb)))

(defn in-database-context?
  []
  (boolean (get-in (db/get-route @db/dsdb) [:params :database])))

(def core-commands
  [{:id "search.toggle"
    :title "Search"
    :description "Open or close the global note search."
    :category :general
    :source :core
    :customizable? true
    :default-shortcut "Mod+U"
    :scopes #{:global :modal :input}
    :allow-in-input? true
    :enabled? (fn [_]
                (and (authenticated?)
                     (in-database-context?)))
    :handler (fn [_]
               (sidebar/toggle-search!))}
   {:id "settings.open"
    :title "Settings"
    :description "Open the settings center modal."
    :category :general
    :source :core
    :customizable? true
    :default-shortcut "Mod+,"
    :scopes #{:global :modal :input}
    :allow-in-input? true
    :enabled? (fn [_]
                (authenticated?))
    :handler (fn [_]
               (settings-state/open-settings!))}
   {:id "sidebar.toggle"
    :title "Left Sidebar"
    :description "Collapse or expand the left sidebar."
    :category :navigation
    :source :core
    :customizable? true
    :default-shortcut "Mod+\\"
    :scopes #{:global}
    :allow-in-input? false
    :enabled? (fn [_]
                (contains? #{:show :graph :diaries :all-notes :single-note :mcp-settings :mcp-chat}
                           (route-name)))
    :handler (fn [_]
               (sidebar/toggle-sidebar!))}
   {:id "diaries.open"
    :title "Diaries"
    :description "Open the Diaries page for the current database."
    :category :navigation
    :source :core
    :customizable? true
    :default-shortcut "Mod+J"
    :scopes #{:global}
    :allow-in-input? false
    :enabled? (fn [_]
                (in-database-context?))
    :handler (fn [_]
               (when-let [database-name (get-in (db/get-route @db/dsdb) [:params :database])]
                 (router/go-to-diaries! database-name)))}
   {:id "all-notes.open"
    :title "All Notes"
    :description "Open the All Notes page for the current database."
    :category :navigation
    :source :core
    :customizable? true
    :default-shortcut nil
    :scopes #{:global}
    :allow-in-input? false
    :enabled? (fn [_]
                (in-database-context?))
    :handler (fn [_]
               (when-let [database-name (get-in (db/get-route @db/dsdb) [:params :database])]
                 (router/go-to-all-notes! database-name)))}
   {:id "graph.open"
    :title "Graph"
    :description "Open the Graph page for the current database."
    :category :navigation
    :source :core
    :customizable? true
    :default-shortcut nil
    :scopes #{:global}
    :allow-in-input? false
    :enabled? (fn [_]
                (in-database-context?))
    :handler (fn [_]
               (when-let [database-name (get-in (db/get-route @db/dsdb) [:params :database])]
                 (router/go-to-graph! database-name)))}
   {:id "note.new"
    :title "New Note"
    :description "Create a new note in the current database."
    :category :notes
    :source :core
    :customizable? true
    :default-shortcut "Mod+N"
    :scopes #{:global}
    :allow-in-input? false
    :enabled? (fn [_]
                (in-database-context?))
    :handler (fn [_]
               (when-let [database-name (get-in (db/get-route @db/dsdb) [:params :database])]
                 (sidebar/create-new-note! database-name)))}
   {:id "right-sidebar.toggle"
    :title "Right Sidebar"
    :description "Show or hide the right sidebar."
    :category :navigation
    :source :core
    :customizable? true
    :default-shortcut "Mod+/"
    :scopes #{:global}
    :allow-in-input? false
    :enabled? (fn [_]
                (contains? #{:show :graph :diaries :all-notes :single-note :mcp-settings :mcp-chat}
                           (route-name)))
    :handler (fn [_]
               (db/toggle-right-sidebar-visibility!))}
   ])

(defn register-core-commands!
  []
  (doseq [command core-commands]
    (register-command! command)))
