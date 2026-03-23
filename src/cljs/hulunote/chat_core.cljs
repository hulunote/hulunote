(ns hulunote.chat-core
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<! go]]
            [datascript.core :as d]
            [hulunote.chat :as chat]
            [hulunote.chat-state :as chat-state]
            [hulunote.db :as db]))

(def valid-llm-roles
  #{"system" "assistant" "user" "function" "tool" "developer"})

(defonce note-event-listener-registered? (atom false))
(defonce persistence-watch-registered? (atom false))

(def session-storage-prefix "hulunote.ai-chat.session")

(declare load-models!)

(defn local-storage []
  (some-> js/window .-localStorage))

(defn session-storage-key
  [database-name]
  (str session-storage-prefix ":" (or database-name "global")))

(defn session-payload
  [{:keys [messages input use-tools?]}]
  {:messages (vec messages)
   :input (or input "")
   :use-tools? (boolean use-tools?)})

(defn normalize-thinking-log
  [progress-log]
  (->> progress-log
       (remove str/blank?)
       (remove #{"--- Tool Calls ---" "--- End ---"})
       (str/join "\n")
       (str/trim)))

(defn persist-session!
  [{:keys [database-name] :as state}]
  (when-let [storage (local-storage)]
    (.setItem storage
      (session-storage-key database-name)
      (js/JSON.stringify (clj->js (session-payload state))))))

(defn restore-session!
  [database-name]
  (let [fallback {:messages []
                  :input ""
                  :use-tools? true}]
    (if-not (local-storage)
      (swap! chat-state/chat-state merge fallback {:database-name database-name
                                                   :loading? false
                                                   :error nil
                                                   :show-settings? false})
      (let [raw (.getItem (local-storage) (session-storage-key database-name))]
        (if (str/blank? raw)
          (swap! chat-state/chat-state merge fallback {:database-name database-name
                                                       :loading? false
                                                       :error nil
                                                       :show-settings? false})
          (try
            (let [payload (js->clj (.parse js/JSON raw) :keywordize-keys true)]
              (swap! chat-state/chat-state merge fallback payload {:database-name database-name
                                                                   :loading? false
                                                                   :error nil
                                                                   :show-settings? false}))
            (catch js/Error _
              (swap! chat-state/chat-state merge fallback {:database-name database-name
                                                           :loading? false
                                                           :error nil
                                                           :show-settings? false}))))))))

(defn register-session-persistence!
  []
  (when-not @persistence-watch-registered?
    (add-watch chat-state/chat-state ::persist-session
      (fn [_ _ _ new-state]
        (persist-session! new-state)))
    (reset! persistence-watch-registered? true)))

(defn js->clj-safe [obj]
  (if (object? obj)
    (js->clj obj :keywordize-keys true)
    obj))

(defn llm-message?
  [{:keys [role content]}]
  (and (contains? valid-llm-roles role)
       (string? content)))

(defn add-message! [role content]
  (swap! chat-state/chat-state update :messages conj {:role role :content content}))

(defn clear-messages! []
  (swap! chat-state/chat-state assoc :messages []))

(defn start-new-conversation! []
  (swap! chat-state/chat-state assoc
    :messages []
    :input ""
    :loading? false
    :error nil
    :show-settings? false))

(defn set-input! [input]
  (swap! chat-state/chat-state assoc :input input))

(defn set-database-name! [database-name]
  (restore-session! database-name))

(defn toggle-use-tools! []
  (swap! chat-state/chat-state update :use-tools? not))

(defn open-settings! []
  (swap! chat-state/chat-state assoc :show-settings? true)
  (load-models!))

(defn handle-note-event!
  "Handle note/nav creation events from AI agent.
   Opens created notes in the right sidebar and updates DataScript in real-time."
  [event-data]
  (let [event-type (unchecked-get event-data "type")]
    (prn "[chat-ui] note event:" event-type)
    (cond
      (= event-type "note_created")
      (let [note-id (unchecked-get event-data "noteId")
            root-nav-id (unchecked-get event-data "rootNavId")
            database-name (unchecked-get event-data "databaseName")
            title (unchecked-get event-data "title")]
        (prn "[chat-ui] note_created - opening in right sidebar:" title)
        (d/transact! db/dsdb
          [{:hulunote-notes/id note-id
            :hulunote-notes/title title
            :hulunote-notes/root-nav-id root-nav-id
            :hulunote-notes/database-id database-name
            :hulunote-notes/is-delete false
            :hulunote-notes/is-public false
            :hulunote-notes/is-shortcut false
            :hulunote-notes/updated-at (.toISOString (js/Date.))}])
        (d/transact! db/dsdb
          [{:id root-nav-id
            :content "ROOT"
            :hulunote-note note-id
            :same-deep-order 0
            :is-display true
            :origin-parid db/root-id}])
        (db/open-note-in-right-sidebar! note-id title root-nav-id database-name))

      (= event-type "nav_created")
      (let [note-id (unchecked-get event-data "noteId")
            nav-id (unchecked-get event-data "navId")
            content (unchecked-get event-data "content")
            parid (unchecked-get event-data "parid")
            order (unchecked-get event-data "order")]
        (prn "[chat-ui] nav_created - updating DataScript:" nav-id)
        (d/transact! db/dsdb
          [{:id nav-id
            :content (or content "")
            :hulunote-note note-id
            :same-deep-order (or order 0)
            :is-display true
            :origin-parid parid}])
        (d/transact! db/dsdb
          [[:db/add [:id parid] :parid [:id nav-id]]])))))

(defn register-note-event-listener!
  "Register a global window callback for note events from the AI agent."
  []
  (when-not @note-event-listener-registered?
    (prn "[chat-ui] Registering note event listener on window.__hulunoteNoteEvent")
    (set! (.-__hulunoteNoteEvent js/window)
      (fn [event-data]
        (handle-note-event! event-data)))
    (reset! note-event-listener-registered? true)))

(defn init-chat! []
  (register-session-persistence!)
  (when (chat/chat-available?)
    (go
      (when-let [ch (chat/get-api-key!)]
        (let [result (js->clj-safe (<! ch))]
          (when (:success result)
            (let [api-key (:apiKey result)]
              (swap! chat-state/chat-state assoc
                :api-key api-key
                :api-key-set? (not (str/blank? api-key))))))))
    (go
      (when-let [ch (chat/get-model!)]
        (let [result (js->clj-safe (<! ch))]
          (when (:success result)
            (swap! chat-state/chat-state assoc :model (:model result))))))
    (register-note-event-listener!)))

(defn send-message! []
  (let [{:keys [input messages use-tools? api-key-set? database-name]} @chat-state/chat-state]
    (when (and (not (str/blank? input)) api-key-set?)
      (let [user-message {:role "user" :content input}
            request-messages (conj (vec (filter llm-message? messages)) user-message)
            all-messages (conj messages user-message)]
        (swap! chat-state/chat-state assoc
          :messages all-messages
          :input ""
          :loading? true
          :error nil)
        (go
          (when-let [ch (chat/send-message! {:messages request-messages
                                             :use-tools use-tools?
                                             :database-name database-name})]
            (let [raw (<! ch)
                  raw-progress-log (when (object? raw)
                                     (unchecked-get raw "progressLog"))
                  _ (js/console.log "[DEBUG] raw IPC result:" raw)
                  _ (js/console.log "[DEBUG] raw type:" (type raw))
                  _ (js/console.log "[DEBUG] raw.progressLog:" raw-progress-log)
                  result (js->clj-safe raw)]
              (js/console.log "[DEBUG] result keys:" (pr-str (keys result)))
              (js/console.log "[DEBUG] :progressLog =" (pr-str (:progressLog result)))
              (js/console.log "[DEBUG] :success =" (pr-str (:success result)))
              (swap! chat-state/chat-state assoc :loading? false)
              (if (:success result)
                (let [response (:response result)
                      assistant-content (get-in response [:choices 0 :message :content] "")
                      progress-log (normalize-thinking-log (:progressLog result))]
                  (js/console.log "[DEBUG] progress-log value:" (pr-str progress-log))
                  (js/console.log "[DEBUG] sequential?:" (sequential? progress-log))
                  (js/console.log "[DEBUG] seq:" (boolean (seq progress-log)))
                  (when (not (str/blank? progress-log))
                    (add-message! "thinking" progress-log))
                  (when (not (str/blank? assistant-content))
                    (add-message! "assistant" assistant-content)))
                (do
                  (swap! chat-state/chat-state assoc :error (:error result))
                  (add-message! "error" (str "Error: " (:error result))))))))))))

(defn stop-message! []
  (swap! chat-state/chat-state assoc :loading? false))

(defn load-models! []
  (when (and (chat/chat-available?)
             (not (:models-loading? @chat-state/chat-state)))
    (swap! chat-state/chat-state assoc :models-loading? true)
    (go
      (when-let [ch (chat/get-models!)]
        (let [result (js->clj-safe (<! ch))]
          (swap! chat-state/chat-state assoc :models-loading? false)
          (when (:success result)
            (let [models (:models result)
                  preferred-providers ["anthropic" "openai" "google" "meta-llama" "deepseek" "mistralai"]
                  provider-rank (into {} (map-indexed (fn [i p] [p i]) preferred-providers))
                  sorted-models (->> models
                                     (filter #(:id %))
                                     (sort-by (fn [m]
                                                (let [id (:id m)
                                                      provider (first (str/split id #"/"))]
                                                  [(get provider-rank provider 99) id]))))]
              (swap! chat-state/chat-state assoc :available-models sorted-models))))))))

(defn save-api-key! [api-key]
  (go
    (when-let [ch (chat/set-api-key! api-key)]
      (let [result (js->clj-safe (<! ch))]
        (when (:success result)
          (swap! chat-state/chat-state assoc
            :api-key api-key
            :api-key-set? (not (str/blank? api-key))
            :show-settings? false))))))

(defn save-model! [model]
  (go
    (when-let [ch (chat/set-model! model)]
      (let [result (js->clj-safe (<! ch))]
        (when (:success result)
          (swap! chat-state/chat-state assoc :model model))))))
