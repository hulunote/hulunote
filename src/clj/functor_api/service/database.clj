(ns functor-api.service.database
  (:require [clojure.string :as strings]
            [clojure.java.io :as io]
            [functor-api.util :as u]
            [cheshire.core :as json]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [functor-api.dict :as dict]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [functor-api.middleware-new :as middleware]
            [buddy.hashers :as hashers]
            [functor-api.service.payment :as payment]
            [functor-api.db.database :as db]
            [functor-api.db.note :as note]))

(comment
  (create-database-api
    {:hulunote  {:hulunote/id 1}}
    {:database-name "hulunotedb"})
  ;; so , you can request: http://127.0.0.1:6689/#/app/hulunotedb/diaries
  )
(defn create-database-api
  [{:keys [hulunote region]}
   {:keys [database-name description]}]
  (let [account-id (:hulunote/id hulunote)
        db-count (db/get-database-count account-id)]
    (if (and (payment/is-vip? account-id)
          (>= db-count 5))
      (dict/get-dict-error :error-too-database region)
      (db/create-database account-id database-name description region))))

(defn get-database-list-api
  [{:keys [hulunote region]}
   {:keys []}]
  (let [account-id (:hulunote/id hulunote)
        database-list (db/get-all-database-list account-id)
        user-setting (db/get-database-setting account-id)]
    {:database-list database-list
     :settings user-setting}))

(defn update-database-api
  [{:keys [hulunote region]}
   {:keys [database-id id is-public is-ws-daily is-default is-delete db-name] :as param}]
  (let [account-id (:hulunote/id hulunote)
        database-id (or database-id id)]
    (cond
      (nil? database-id)
      (dict/get-dict-error :error-missing-parameter region)
      
      (false? (db/is-database-account-match? account-id database-id))
      (dict/get-dict-error :error-cant-update-other-database region)
      
      :else
      (db/update-database database-id account-id param region))))

(defn import-database-json
  "导入database的json文件"
  [{:keys [hulunote region]}
   {:keys [database-id file]}]
  (let [account-id (:hulunote/id hulunote)]
    (try
      (with-open [in (io/reader (:tempfile file))]
        (let [lst (json/parse-stream in true)]
          (u/with-transaction 
            (doseq [item lst]
              (note/import-or-update-note! database-id account-id item)))
          {:success true}))      
      (catch Exception ex
        (u/log-error ex)
        (dict/get-dict-error :error-server region)))
    ))

;; 上层是 /hulunote，这里直接给vector
(def apis
  [["/new-database" {:post #'create-database-api}]
   ["/get-database-list" {:post #'get-database-list-api}]
   ["/update-database" {:post #'update-database-api}]
   ["/import-database-json" {:post #'import-database-json}]])
