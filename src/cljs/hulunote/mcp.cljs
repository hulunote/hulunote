(ns hulunote.mcp
  "MCP (Model Context Protocol) API 封装
   提供与 Electron 主进程 MCP 功能的交互接口"
  (:require [cljs.core.async :as a :refer [chan put! close!]]))

;; ==================== 环境检测 ====================

(defn electron?
  "检查是否在 Electron 环境中运行"
  []
  (and (exists? js/window)
       (exists? js/window.electronAPI)
       (.-isElectron js/window.electronAPI)))

(defn mcp-available?
  "检查 MCP API 是否可用"
  []
  (and (electron?)
       (exists? (.-mcp js/window.electronAPI))))

;; ==================== Promise 处理 ====================

(defn promise->chan
  "将 JavaScript Promise 转换为 core.async channel"
  [promise]
  (let [ch (chan 1)]
    (-> promise
        (.then (fn [result]
                 (put! ch result)
                 (close! ch)))
        (.catch (fn [error]
                  (put! ch {:success false :error (.-message error)})
                  (close! ch))))
    ch))

(defn js->clj-result
  "转换 JS 结果为 Clojure 数据结构"
  [result]
  (js->clj result :keywordize-keys true))

;; ==================== MCP 配置管理 API ====================

(defn load-settings!
  "加载 MCP 配置
   返回 channel，结果格式: {:success bool :data {:mcpServers [...]}}"
  []
  (when (mcp-available?)
    (promise->chan (.loadSettings (.-mcp js/window.electronAPI)))))

(defn save-settings!
  "保存 MCP 配置
   settings: {:mcpServers [...]}"
  [settings]
  (when (mcp-available?)
    (promise->chan (.saveSettings (.-mcp js/window.electronAPI) (clj->js settings)))))

(defn get-servers!
  "获取 MCP 服务器列表
   返回 channel，结果格式: {:success bool :data [{:name ... :command ... :args [...] :connected bool}]}"
  []
  (when (mcp-available?)
    (promise->chan (.getServers (.-mcp js/window.electronAPI)))))

(defn add-server!
  "添加 MCP 服务器
   server-config: {:name string :command string :args [string] :env {}}
   返回 channel"
  [server-config]
  (when (mcp-available?)
    (promise->chan (.addServer (.-mcp js/window.electronAPI) (clj->js server-config)))))

(defn remove-server!
  "删除 MCP 服务器
   server-name: string"
  [server-name]
  (when (mcp-available?)
    (promise->chan (.removeServer (.-mcp js/window.electronAPI) server-name))))

;; ==================== MCP 客户端操作 API ====================

(defn create-client!
  "创建并连接 MCP 客户端
   server-config: {:name string :command string :args [string] :env {}}"
  [server-config]
  (when (mcp-available?)
    (promise->chan (.createClient (.-mcp js/window.electronAPI) (clj->js server-config)))))

(defn disconnect-client!
  "断开 MCP 客户端连接
   client-id: string (通常是 server name)"
  [client-id]
  (when (mcp-available?)
    (promise->chan (.disconnectClient (.-mcp js/window.electronAPI) client-id))))

(defn is-connected?
  "检查客户端是否已连接"
  [client-id]
  (when (mcp-available?)
    (promise->chan (.isConnected (.-mcp js/window.electronAPI) client-id))))

;; ==================== MCP 工具操作 API ====================

(defn list-tools!
  "列出客户端可用的工具
   client-id: string
   返回 channel，结果格式: {:success bool :tools [{:name :description :inputSchema}]}"
  [client-id]
  (when (mcp-available?)
    (promise->chan (.listTools (.-mcp js/window.electronAPI) client-id))))

(defn call-tool!
  "调用 MCP 工具
   params: {:clientId string :toolName string :args {}}
   返回 channel，结果包含工具执行结果"
  [{:keys [client-id tool-name args] :as params}]
  (when (mcp-available?)
    (let [js-params (clj->js {:clientId client-id
                              :toolName tool-name
                              :args (or args {})})]
      (promise->chan (.callTool (.-mcp js/window.electronAPI) js-params)))))

;; ==================== MCP 资源操作 API ====================

(defn list-resources!
  "列出客户端可用的资源
   client-id: string
   返回 channel，结果格式: {:success bool :resources [...]}"
  [client-id]
  (when (mcp-available?)
    (promise->chan (.listResources (.-mcp js/window.electronAPI) client-id))))

(defn read-resource!
  "读取 MCP 资源
   params: {:client-id string :uri string}"
  [{:keys [client-id uri]}]
  (when (mcp-available?)
    (let [js-params (clj->js {:clientId client-id :uri uri})]
      (promise->chan (.readResource (.-mcp js/window.electronAPI) js-params)))))

;; ==================== 事件监听 ====================

(defn on-server-connected!
  "监听服务器连接事件
   callback: (fn [data] ...) data 格式 {:name string ...}"
  [callback]
  (when (mcp-available?)
    (.onServerConnected (.-mcp js/window.electronAPI)
                        (fn [data]
                          (callback (js->clj-result data))))))

(defn on-server-disconnected!
  "监听服务器断开事件
   callback: (fn [data] ...) data 格式 {:name string}"
  [callback]
  (when (mcp-available?)
    (.onServerDisconnected (.-mcp js/window.electronAPI)
                           (fn [data]
                             (callback (js->clj-result data))))))

(defn remove-listeners!
  "移除所有 MCP 事件监听器"
  []
  (when (mcp-available?)
    (.removeServerConnectedListener (.-mcp js/window.electronAPI))
    (.removeServerDisconnectedListener (.-mcp js/window.electronAPI))))

;; ==================== 便捷函数 ====================

(defn connect-server!
  "便捷函数：根据服务器配置连接
   等同于 create-client!"
  [server-config]
  (create-client! server-config))

(defn disconnect-server!
  "便捷函数：根据服务器名称断开连接
   等同于 disconnect-client!"
  [server-name]
  (disconnect-client! server-name))
