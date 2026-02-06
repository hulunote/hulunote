(ns hulunote.mcp-state
  "MCP 状态管理
   使用 atom 管理 MCP 相关状态，提供 re-frame 事件和订阅"
  (:require [hulunote.mcp :as mcp]
            [re-frame.core :as re-frame]
            [cljs.core.async :as a :refer [<! go]]))

;; ==================== 状态定义 ====================

(defonce mcp-state
  (atom {:servers []           ; 服务器配置列表 [{:name :command :args :connected}]
         :connected-clients #{} ; 已连接的客户端名称集合
         :tools {}             ; 客户端工具 {client-id [{:name :description}]}
         :resources {}         ; 客户端资源 {client-id [...]}
         :loading? false       ; 是否正在加载
         :error nil            ; 错误信息
         :selected-server nil  ; 当前选中的服务器
         :tool-results []}))   ; 工具执行结果历史

;; ==================== 辅助函数 ====================

(defn js->clj-safe
  "安全地将 JS 对象转换为 Clojure 数据结构"
  [obj]
  (if (object? obj)
    (js->clj obj :keywordize-keys true)
    obj))

;; ==================== 状态更新函数 ====================

(defn set-loading! [loading?]
  (swap! mcp-state assoc :loading? loading?))

(defn set-error! [error]
  (swap! mcp-state assoc :error error))

(defn clear-error! []
  (swap! mcp-state assoc :error nil))

(defn set-servers! [servers]
  (swap! mcp-state assoc :servers servers))

(defn add-connected-client! [client-id]
  (swap! mcp-state update :connected-clients conj client-id))

(defn remove-connected-client! [client-id]
  (swap! mcp-state update :connected-clients disj client-id))

(defn set-tools! [client-id tools]
  (swap! mcp-state assoc-in [:tools client-id] tools))

(defn set-resources! [client-id resources]
  (swap! mcp-state assoc-in [:resources client-id] resources))

(defn add-tool-result! [result]
  (swap! mcp-state update :tool-results
         (fn [results]
           (take 50 (cons result results))))) ; 保留最近 50 条

(defn select-server! [server-name]
  (swap! mcp-state assoc :selected-server server-name))

;; ==================== 异步操作 ====================

(defn load-servers!
  "从 Electron 加载服务器列表"
  []
  (when (mcp/mcp-available?)
    (set-loading! true)
    (clear-error!)
    (go
      (when-let [ch (mcp/get-servers!)]
        (let [raw-result (<! ch)
              result (js->clj-safe raw-result)]
          (set-loading! false)
          (if (:success result)
            (let [servers (or (:data result) [])]
              (set-servers! servers)
              ;; 更新已连接客户端集合
              (swap! mcp-state assoc :connected-clients
                     (set (map :name (filter :connected servers)))))
            (set-error! (or (:error result) "Failed to load servers"))))))))

(defn add-server!
  "添加新服务器配置"
  [server-config callback]
  (when (mcp/mcp-available?)
    (set-loading! true)
    (clear-error!)
    (go
      (when-let [ch (mcp/add-server! server-config)]
        (let [raw-result (<! ch)
              result (js->clj-safe raw-result)]
          (set-loading! false)
          (if (:success result)
            (do
              (load-servers!) ; 重新加载服务器列表
              (when callback (callback {:success true})))
            (do
              (set-error! (or (:error result) "Failed to add server"))
              (when callback (callback {:success false :error (:error result)})))))))))

(defn remove-server!
  "删除服务器配置"
  [server-name callback]
  (when (mcp/mcp-available?)
    (set-loading! true)
    (clear-error!)
    (go
      (when-let [ch (mcp/remove-server! server-name)]
        (let [raw-result (<! ch)
              result (js->clj-safe raw-result)]
          (set-loading! false)
          (if (:success result)
            (do
              (load-servers!)
              (when callback (callback {:success true})))
            (do
              (set-error! (or (:error result) "Failed to remove server"))
              (when callback (callback {:success false :error (:error result)})))))))))

(defn connect-server!
  "连接到服务器"
  [server-config callback]
  (when (mcp/mcp-available?)
    (set-loading! true)
    (clear-error!)
    (go
      (when-let [ch (mcp/create-client! server-config)]
        (let [raw-result (<! ch)
              result (js->clj-safe raw-result)]
          (set-loading! false)
          (if (:success result)
            (do
              (add-connected-client! (:name server-config))
              (load-servers!) ; 刷新列表以更新连接状态
              (when callback (callback {:success true})))
            (do
              (set-error! (or (:error result) "Failed to connect"))
              (when callback (callback {:success false :error (:error result)})))))))))

(defn disconnect-server!
  "断开服务器连接"
  [server-name callback]
  (when (mcp/mcp-available?)
    (set-loading! true)
    (clear-error!)
    (go
      (when-let [ch (mcp/disconnect-client! server-name)]
        (let [raw-result (<! ch)
              result (js->clj-safe raw-result)]
          (set-loading! false)
          (if (:success result)
            (do
              (remove-connected-client! server-name)
              ;; 清除该客户端的工具和资源缓存
              (swap! mcp-state update :tools dissoc server-name)
              (swap! mcp-state update :resources dissoc server-name)
              (load-servers!)
              (when callback (callback {:success true})))
            (do
              (set-error! (or (:error result) "Failed to disconnect"))
              (when callback (callback {:success false :error (:error result)})))))))))

(defn load-tools!
  "加载客户端可用的工具"
  [client-id callback]
  (when (mcp/mcp-available?)
    (go
      (when-let [ch (mcp/list-tools! client-id)]
        (let [raw-result (<! ch)
              result (js->clj-safe raw-result)]
          (if (:success result)
            (do
              (set-tools! client-id (or (:tools result) []))
              (when callback (callback {:success true :tools (:tools result)})))
            (when callback (callback {:success false :error (:error result)}))))))))

(defn load-resources!
  "加载客户端可用的资源"
  [client-id callback]
  (when (mcp/mcp-available?)
    (go
      (when-let [ch (mcp/list-resources! client-id)]
        (let [raw-result (<! ch)
              result (js->clj-safe raw-result)]
          (if (:success result)
            (do
              (set-resources! client-id (or (:resources result) []))
              (when callback (callback {:success true :resources (:resources result)})))
            (when callback (callback {:success false :error (:error result)}))))))))

(defn execute-tool!
  "执行工具"
  [client-id tool-name args callback]
  (when (mcp/mcp-available?)
    (go
      (when-let [ch (mcp/call-tool! {:client-id client-id
                                     :tool-name tool-name
                                     :args args})]
        (let [raw-result (<! ch)
              result (js->clj-safe raw-result)]
          ;; 记录执行结果
          (add-tool-result! {:client-id client-id
                             :tool-name tool-name
                             :args args
                             :result result
                             :timestamp (js/Date.)})
          (when callback (callback result)))))))

;; ==================== 初始化 ====================

(defn init!
  "初始化 MCP 状态管理
   - 加载服务器列表
   - 设置事件监听"
  []
  (when (mcp/mcp-available?)
    ;; 加载服务器列表
    (load-servers!)

    ;; 设置连接/断开事件监听
    (mcp/on-server-connected!
     (fn [data]
       (println "MCP Server connected:" (:name data))
       (add-connected-client! (:name data))
       (load-servers!)))

    (mcp/on-server-disconnected!
     (fn [data]
       (println "MCP Server disconnected:" (:name data))
       (remove-connected-client! (:name data))
       (load-servers!)))))

(defn cleanup!
  "清理 MCP 状态管理
   - 移除事件监听"
  []
  (mcp/remove-listeners!))

;; ==================== re-frame 事件（可选） ====================

(re-frame/reg-event-fx
  :mcp/init
  (fn [{:keys [db]} _]
    (init!)
    {:db db}))

(re-frame/reg-event-fx
  :mcp/load-servers
  (fn [{:keys [db]} _]
    (load-servers!)
    {:db db}))

(re-frame/reg-event-fx
  :mcp/add-server
  (fn [{:keys [db]} [_ server-config callback]]
    (add-server! server-config callback)
    {:db db}))

(re-frame/reg-event-fx
  :mcp/remove-server
  (fn [{:keys [db]} [_ server-name callback]]
    (remove-server! server-name callback)
    {:db db}))

(re-frame/reg-event-fx
  :mcp/connect
  (fn [{:keys [db]} [_ server-config callback]]
    (connect-server! server-config callback)
    {:db db}))

(re-frame/reg-event-fx
  :mcp/disconnect
  (fn [{:keys [db]} [_ server-name callback]]
    (disconnect-server! server-name callback)
    {:db db}))

(re-frame/reg-event-fx
  :mcp/load-tools
  (fn [{:keys [db]} [_ client-id callback]]
    (load-tools! client-id callback)
    {:db db}))

(re-frame/reg-event-fx
  :mcp/execute-tool
  (fn [{:keys [db]} [_ {:keys [client-id tool-name args callback]}]]
    (execute-tool! client-id tool-name args callback)
    {:db db}))
