(ns functor-api.service.openai
  (:require [clojure.core.async :as async]
            [clj-http.client :as client]
            [clojure.string :as strings]
            [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [functor-api.dict :as dict]
            [functor-api.middleware-new :as middleware]
            [cheshire.core :as json]
            [functor-api.db.note :as note]
            [functor-api.db.nav :as nav]))

(def hulunote-openai-key "sk-xxas321312312321")

(defn chatgpt-request-core [message region]
  (try
    (loop [n 0]
      (if (> n 2)
        (dict/get-dict-error :error-chatgpt-request region)
        (let [body (json/generate-string {:openai-key hulunote-openai-key
                                          :content message})
              resp (client/post "http://3.143.215.166:5568/v2/embedding-qa/qa"
                                {:body body :content-type :json :accept :json})
              resp-body (json/parse-string (:body resp) true)
              result (reduce #(str %1 "\n" (get-in %2 [:message :content])) "" (:choices resp-body))
              result (if (strings/starts-with? result "\n\n\n")
                       (subs result 3)
                       result)]
          (if (empty? (strings/trim result))
            (recur (inc n))
            {:result result}))))
    (catch Exception ex
      (u/log-error ex)
      (dict/get-dict-error :error-chatgpt-request region))))

(defn chatgpt-request-write-note
  "请求chatgpt，并把结果写到笔记中"
  [account-id note-id speaker-name message region]
  (let [result (chatgpt-request-core message region)]
    (if (:error result)
      result
      (let [parent-nav-content (str (when speaker-name 
                                      (str speaker-name ":: "))
                                    "QA request:"
                                    message)
            nav-content (:result result)
            parent-nav-id (u/uuid)
            nav-id (u/uuid)
            root-id (-> (sql/select :root-nav-id)
                        (sql/from :hulunote-notes)
                        (sql/where [:= :id note-id])
                        (u/execute-one!)
                        (:hulunote-notes/root-nav-id))]
        (u/with-transaction
          (nav/create-new-nav-auto-order account-id note-id parent-nav-id root-id parent-nav-content)
          (nav/create-new-nav-auto-order account-id note-id nav-id parent-nav-id nav-content))
        result))))

(defn chatgpt-request-summary-core [full-text command region]
  (try 
    (let [body (json/generate-string {:openai-key hulunote-openai-key
                                      :text full-text
                                      :command command})
          resp (client/post "http://3.143.215.166:5568/v3/summary"
                            {:body body :content-type :json :accept :json})
          resp-body (json/parse-string (:body resp) true)
          error (:error resp-body)]
      (if error
        (do
          (u/log-error error)
          (dict/get-dict-error :error-chatgpt-request region))
        resp-body))))
