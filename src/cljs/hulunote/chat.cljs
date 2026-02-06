(ns hulunote.chat
  "Chat API 封装 - 与 OpenRouter 通信"
  (:require [cljs.core.async :as a :refer [chan put! close!]]))

;; ==================== 环境检测 ====================

(defn electron?
  []
  (and (exists? js/window)
       (exists? js/window.electronAPI)
       (.-isElectron js/window.electronAPI)))

(defn chat-available?
  []
  (and (electron?)
       (exists? (.-chat js/window.electronAPI))))

;; ==================== Promise 处理 ====================

(defn promise->chan
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

;; ==================== Chat API ====================

(defn set-api-key!
  "设置 OpenRouter API Key"
  [api-key]
  (when (chat-available?)
    (promise->chan (.setApiKey (.-chat js/window.electronAPI) api-key))))

(defn get-api-key!
  "获取当前 API Key"
  []
  (when (chat-available?)
    (promise->chan (.getApiKey (.-chat js/window.electronAPI)))))

(defn set-model!
  "设置模型"
  [model]
  (when (chat-available?)
    (promise->chan (.setModel (.-chat js/window.electronAPI) model))))

(defn get-model!
  "获取当前模型"
  []
  (when (chat-available?)
    (promise->chan (.getModel (.-chat js/window.electronAPI)))))

(defn get-models!
  "获取可用模型列表"
  []
  (when (chat-available?)
    (promise->chan (.getModels (.-chat js/window.electronAPI)))))

(defn send-message!
  "发送聊天消息
   params: {:messages [{:role :content}] :useTools bool}"
  [{:keys [messages use-tools] :as params}]
  (when (chat-available?)
    (let [js-params (clj->js {:messages messages
                              :useTools (boolean use-tools)})]
      (promise->chan (.sendMessage (.-chat js/window.electronAPI) js-params)))))
