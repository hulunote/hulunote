(ns hulunote.ws
  "WebSocket connection for real-time AI integration.
   Receives events when notes/navs are created via MCP server
   and auto-navigates to newly created notes."
  (:require [hulunote.storage :as storage]
            [hulunote.router :as router]
            [hulunote.db :as db]
            [hulunote.http :as http]
            [datascript.core :as d]
            [clojure.string :as str]))

(defonce ws-conn (atom nil))
(defonce reconnect-timer (atom nil))

(defn- get-ws-url []
  (let [base http/API_BASE_URL
        ;; Convert http(s) to ws(s)
        ws-base (-> base
                    (str/replace #"^https://" "wss://")
                    (str/replace #"^http://" "ws://"))
        token (:token @storage/jwt-auth)]
    (when (seq token)
      (str ws-base "/ws?token=" (js/encodeURIComponent token)))))

(defn- get-current-database
  "Get the current database name from the URL hash"
  []
  (let [hash (.-hash js/window.location)
        match (re-find #"#/app/([^/]+)" hash)]
    (when match
      (js/decodeURIComponent (second match)))))

(defn- find-database-name-by-id
  "Look up database name from database UUID in DataScript"
  [database-id]
  (let [result (d/q '[:find ?name .
                       :in $ ?id
                       :where
                       [?e :hulunote-databases/id ?id]
                       [?e :hulunote-databases/name ?name]]
                    (d/db db/dsdb)
                    database-id)]
    result))

(defn- handle-note-created
  "Handle note_created event - add note to DataScript and navigate to it"
  [{:strs [note-id database-id title root-nav-id]}]
  (prn "[WS] Note created:" title "id:" note-id)
  ;; Add the new note to DataScript so sidebar can show it
  (d/transact! db/dsdb
    [{:hulunote-notes/id note-id
      :hulunote-notes/title title
      :hulunote-notes/database-id database-id
      :hulunote-notes/root-nav-id root-nav-id
      :hulunote-notes/is-delete false
      :hulunote-notes/is-public false
      :hulunote-notes/is-shortcut false}])
  ;; Navigate to the new note
  (let [current-db (get-current-database)
        db-name (or current-db (find-database-name-by-id database-id))]
    (when db-name
      (prn "[WS] Navigating to note:" note-id "in database:" db-name)
      (router/go-to-note! db-name note-id))))

(defn- handle-nav-updated
  "Handle nav_updated event - update nav in DataScript"
  [{:strs [nav-id note-id database-id content]}]
  (prn "[WS] Nav updated:" nav-id "content:" (subs content 0 (min 50 (count content)))))

(defn- handle-message [event]
  (let [data (js/JSON.parse (.-data event))
        msg (js->clj data)
        msg-type (get msg "type")]
    (case msg-type
      "connected" (prn "[WS] Connected to server")
      "note_created" (handle-note-created msg)
      "nav_updated" (handle-nav-updated msg)
      (prn "[WS] Unknown message type:" msg-type))))

(defn- clear-reconnect-timer! []
  (when-let [timer @reconnect-timer]
    (js/clearTimeout timer)
    (reset! reconnect-timer nil)))

(declare connect!)

(defn- schedule-reconnect! []
  (clear-reconnect-timer!)
  (reset! reconnect-timer
    (js/setTimeout
      (fn []
        (prn "[WS] Reconnecting...")
        (connect!))
      5000)))

(defn disconnect! []
  (clear-reconnect-timer!)
  (when-let [ws @ws-conn]
    (.close ws)
    (reset! ws-conn nil)))

(defn connect! []
  (disconnect!)
  (when-let [url (get-ws-url)]
    (try
      (let [ws (js/WebSocket. url)]
        (set! (.-onopen ws) (fn [_] (prn "[WS] Connection opened")))
        (set! (.-onmessage ws) handle-message)
        (set! (.-onclose ws) (fn [event]
                               (prn "[WS] Connection closed, code:" (.-code event))
                               (reset! ws-conn nil)
                               ;; Auto-reconnect unless intentionally closed
                               (when (not= (.-code event) 1000)
                                 (schedule-reconnect!))))
        (set! (.-onerror ws) (fn [_]
                               (prn "[WS] Connection error")))
        (reset! ws-conn ws))
      (catch :default e
        (prn "[WS] Failed to connect:" e)
        (schedule-reconnect!)))))

(defn init!
  "Initialize WebSocket connection. Call after login/auth."
  []
  (when (seq (:token @storage/jwt-auth))
    (connect!)))
