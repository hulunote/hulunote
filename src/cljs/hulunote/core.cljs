(ns hulunote.core
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [datascript.core :as d]
   [hulunote.db :as db]
   [rum.core :as rum]
   [datascript.transit :as dt]
   [hulunote.dom :as dom]
   [hulunote.util :as u]
   [hulunote.http :as http]
   [hulunote.router :as router]
   [hulunote.sidebar :as sidebar]
   [hulunote.commands :as commands]
   [hulunote.shortcuts :as shortcuts]
   [goog.events :as events]
   [goog.history.EventType :as HistoryEventType]
   [reitit.core :as reitit]
   [hulunote.render :as render]
   [hulunote.database :as database]
   [hulunote.home :as home]
   [hulunote.show :as show]
   [hulunote.graph :as graph]
   [hulunote.diaries :as diaries]
   [hulunote.all-notes :as all-notes]
   [hulunote.single-note :as single-note]
   [hulunote.login :as login]
   [hulunote.components :as comps]
   [hulunote.mcp-ui :as mcp-ui]
   [hulunote.chat-ui :as chat-ui]
   [hulunote.right-sidebar :as right-sidebar]
   [hulunote.settings :as settings]
   [hulunote.plugin :as plugin]
   [hulunote.ws :as ws])
  (:require-macros
   [hulunote.share :refer [profile]])
  (:import goog.History))

(defn all-notes [db]
  (->> (d/datoms db :aevt :hulunote-notes/title)
    (map :e)
    (d/pull-many db [:*])))

(rum/defc main-page [db]
  [:div "main page"])

(rum/defc price-page [db]
  [:div "price page"])

(rum/defc download-page [db]
  [:div "download page"])

(rum/defc not-found-component []
  [:div "not-found page"])

(rum/defc app < rum/reactive
  [conn]
  (let [db (rum/react conn)
        {:keys [route-name params]} (db/get-route db)
        right-sidebar-open? (rum/react db/right-sidebar-open?)]
    [:div
     {:class (when right-sidebar-open? "right-sidebar-active")
      :on-click (fn [e]
                  ;; Close context menu when clicking outside
                  (render/hide-context-menu!)
                  (sidebar/hide-topbar-more-menu!)
                  (sidebar/hide-sidebar-user-menu!))}
     (case route-name
       ;; Root path: show home page if not logged in or expired, otherwise show database list
       :database (if (u/is-expired?)
                   (home/home-page db)
                   (database/database-page db))
       ;; App pages (notebook list, detail, graph)
       :home (home/home-page db)
       :show (show/show-page db)
       :graph (graph/graph-page db)
       :diaries (diaries/diaries-page db)
       :all-notes (all-notes/all-notes-page db)
       :single-note (single-note/single-note-page db)
       ;; Settings page (unified)
       :settings (settings/settings-page db)
       ;; MCP settings pages (redirect to unified settings)
       :mcp-settings (settings/settings-page db {:initial-tab :mcp-servers})
       :mcp-settings-global (settings/settings-page db {:initial-tab :mcp-servers})
       ;; MCP chat pages
       :mcp-chat (chat-ui/chat-page db)
       :mcp-chat-global (chat-ui/chat-page db)
       ;; Public pages: login, main, price, download
       :login (login/login-page db)
       :main (home/home-page db)
       :price (price-page db)
       :download (download-page db)
       (not-found-component))
     ;; Right sidebar for multi-note editing
     (right-sidebar/right-sidebar db)
     ;; Global context menu - rendered at app level
     (render/global-context-menu)
     ;; Slash command dropdown menu
     (render/slash-command-menu)
     (render/page-link-suggestion-menu)
     (settings/settings-modal)
     (comps/confirm-dialog)
     (comps/toast db)
     ;; Search modal - available globally
     (sidebar/search-modal)]))

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      HistoryEventType/NAVIGATE
      (fn [event]
        (do
          (let [uri (or (not-empty (str/replace (.-token ^js event) #"^.*#" "")) "/")
                match (reitit/match-by-path router/router uri)
                current-page (:name (:data match))
                route-params (:path-params match)
                {:keys [page-id database]} route-params]
            (when current-page
              (router/navigate current-page route-params))))))
    (.setEnabled true)))

(defn render []
  (rum/mount (app db/dsdb) (js/document.getElementById "app")))

(defn init! []
  (db/init-db)
  (plugin/init-plugin-system!)
  (commands/register-core-commands!)
  (shortcuts/install-global-shortcuts!)
  ;; http://127.0.0.1:6689/#/app/JackyWong-5721/diaries
  (if (re-find
        #"#/app/(.*)"
        (.-hash js/window.location))
    (let [db
          (js/decodeURIComponent
            (nth
              (clojure.string/split (.-hash js/window.location)
                #"/")
              2))]
      (http/database-data-load db)))
  ;; Send stored auth token to Electron to start built-in MCP server
  (when-let [token (:token @hulunote.storage/jwt-auth)]
    (when (and (seq token)
               (exists? js/window.electronAPI)
               (.-setAuthToken js/window.electronAPI))
      (.setAuthToken js/window.electronAPI token)))
  ;; Initialize WebSocket for AI integration (real-time note sync)
  (ws/init!)
  (hook-browser-navigation!)
  (render))

(defn after-load []
  (prn "reload")
  (render))
