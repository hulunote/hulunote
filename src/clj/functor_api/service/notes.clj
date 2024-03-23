(ns functor-api.service.notes
  (:require [clojure.string :as strings]
            [clojure.java.io :as io]
            [clojure.core.async :as a]
            [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [cheshire.core :as json]
            [functor-api.dict :as dict]
            [functor-api.service.payment :as payment]
            [functor-api.service.permissions :as permission]
            [functor-api.db.database :as database]
            [functor-api.db.note :as note]))

(comment
  (create-new-note-api {:hulunote  {:hulunote/id 1}}
    {:database-name "hulunotedb" :title "outline example"})
  ;; => #:hulunote-notes{:created-at
  ;;                     #inst "2024-03-23T02:19:36.489370000-00:00",
  ;;                     :root-nav-id "518ec94a-c02e-40de-9a7f-2e05ec5484ee",
  ;;                     :pv 0,
  ;;                     :title "outline example",
  ;;                     :is-delete false,
  ;;                     :account-id 1,
  ;;                     :id #uuid "a462665a-17cd-4243-b7cd-e744c452bd37",
  ;;                     :nav ;; ===>>> create first nav , root nav, but no show
  ;;                     #:hulunote-navs{:note-id
  ;;                                     "a462665a-17cd-4243-b7cd-e744c452bd37",
  ;;                                     :database-id
  ;;                                     "2de0dae6-c710-4383-8339-e0a68d9a3b93",
  ;;                                     :created-at
  ;;                                     #inst "2024-03-23T02:19:36.489370000-00:00",
  ;;                                     :is-delete false,
  ;;                                     :content "ROOT",
  ;;                                     :account-id 1,
  ;;                                     :properties "",
  ;;                                     :same-deep-order 0.0,
  ;;                                     :updated-at
  ;;                                     #inst "2024-03-23T02:19:36.489370000-00:00",
  ;;                                     :parid
  ;;                                     "00000000-0000-0000-0000-000000000000",
  ;;                                     :is-public false,
  ;;                                     :extra-id "",
  ;;                                     :is-display true,
  ;;                                     :id
  ;;                                     #uuid "518ec94a-c02e-40de-9a7f-2e05ec5484ee"},
  ;;                     :database-id "2de0dae6-c710-4383-8339-e0a68d9a3b93",
  ;;                     :is-public false,
  ;;                     :updated-at
  ;;                     #inst "2024-03-23T02:19:36.489370000-00:00",
  ;;                     :is-shortcut false}  
  )
(defn create-new-note-api
  "创建笔记"
  [{:keys [hulunote region]}
   {:keys [database-id database database-name title]}]
  (let [account-id (:hulunote/id hulunote)
        database-id (database/get-database-id-by account-id database-id database database-name)]
    (cond
      (nil? database-id)
      (dict/get-dict-error :error-missing-database-info region)

      (empty? database-id)
      (dict/get-dict-error :error-database-not-found-by-name region)
      
      (false? (permission/can-access-database? account-id database-id))
      (dict/get-dict-error :error-permission-deny region)
      
      :else
      (note/create-new-note account-id database-id title region))))

(defn get-note-list-by-page-api
  "分页获取笔记列表"
  [{:keys [hulunote region]}
   {:keys [database-id database database-name page size]}]
  (let [account-id (:hulunote/id hulunote)
        database-id (database/get-database-id-by account-id database-id database database-name)]
    (cond
      (nil? database-id)
      (dict/get-dict-error :error-missing-database-info region)

      (empty? database-id)
      (dict/get-dict-error :error-database-not-found-by-name region)

      (false? (permission/can-access-database? account-id database-id))
      (dict/get-dict-error :error-permission-deny region)
      
      :else
      (note/get-note-list-by-page account-id database-id page size region))))

(defn get-all-note-list-api
  "获取全部笔记列表
   若笔记超过5000个，就进行stream的流式返回"
  [{:keys [hulunote region]}
   {:keys [database-id database database-name stream]}]
  (let [account-id (:hulunote/id hulunote)
        database-id (database/get-database-id-by account-id database-id database database-name)]
    (cond
      (nil? database-id) 
      (dict/get-dict-error :error-missing-database-info region)

      (empty? database-id)
      (dict/get-dict-error :error-database-not-found-by-name region)

      (false? (permission/can-access-database? account-id database-id))
      (dict/get-dict-error :error-permission-deny region)
      
      :else
      (let [note-count (note/get-all-note-count account-id database-id)]
        (if-not stream
          (note/get-all-note-list account-id database-id region)
          ;; stream返回
          {:stream
           {:main-fn (fn [out-stream]
                       (with-open [writer (io/writer out-stream)]
                         (.write writer "{\"note-list\": [")
                         (loop [page 1]
                           (let [note-list (:note-list
                                            (note/get-note-list-by-page account-id database-id page 1000 region))
                                 partition-count (count note-list)
                                 json (json/generate-string note-list)
                                 json (subs json 1 (dec (count json)))]
                             (.write writer json)
                             (when (= partition-count 1000)
                               (.write writer ",")
                               ;; 这里需要等待来减速，否则CPU会暴涨
                               (a/<!! (a/timeout 300))
                               (recur (inc page)))))
                         (.write writer "]}")))
            :content-type "application/json"}})))))

(defn update-note-api
  "更新笔记信息，包括删除笔记"
  [{:keys [hulunote region]}
   {:keys [note-id title is-delete is-public is-shortcut] :as param}]
  (let [account-id (:hulunote/id hulunote)]
    (cond
      (nil? note-id)
      (dict/get-dict-error :error-missing-parameter region)

      (false? (permission/can-access-note? account-id note-id))
      (dict/get-dict-error :error-permission-deny region)
      
      :else
      (note/update-note account-id note-id param region))))

(defn batch-update-notes-api
  "批量更新笔记信息"
  [{:keys [hulunote region]}
   {:keys [datas] :as param}]
  (let [account-id (:hulunote/id hulunote)]
    (try
      (doseq [data datas]
        (let [note-id (:id data)]
          (when (and (some? note-id)
                     (permission/can-access-note? account-id note-id))
            (note/update-note account-id note-id data region))))
      {:success true}
      (catch Exception ex
        (u/log-error ex)
        (dict/get-dict-error :error-server region)))))

(defn get-shortcuts-note-list-api
  "获取笔记库收藏的笔记"
  [{:keys [hulunote region]}
   {:keys [database-id database database-name]}]
  (let [account-id (:hulunote/id hulunote)
        database-id (database/get-database-id-by account-id database-id database database-name)]
    (cond
      (nil? database-id)
      (dict/get-dict-error :error-missing-database-info region)

      (empty? database-id)
      (dict/get-dict-error :error-database-not-found-by-name region)
      
      (false? (permission/can-access-database? account-id database-id))
      (dict/get-dict-error :error-permission-deny region)
      
      :else
      (note/get-shortcuts-note-list account-id database-id))))

(def apis
  [["/new-note" {:post #'create-new-note-api}]
   ["/get-note-list" {:post #'get-note-list-by-page-api}]
   ["/get-all-note-list" {:post #'get-all-note-list-api}]
   ["/update-hulunote-note" {:post #'update-note-api}]
   ["/update-hulunote-notes" {:post #'batch-update-notes-api}]
   ["/get-shortcuts-note-list" {:post #'get-shortcuts-note-list-api}]])
