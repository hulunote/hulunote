(ns functor-api.service.navs
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
            [functor-api.db.note :as note]
            [functor-api.db.nav :as nav]))

(defn create-or-update-nav-api
  "原版的/new-hulunote-navs-uuid-v2类似的接口"
  [{:keys [hulunote region]}
   {:keys [database-id database database-name note-id
           id parid content is-delete is-display properties order] :as param}]
  (let [account-id (:hulunote/id hulunote)
        database-id (database/get-database-id-by account-id database-id database database-name)
        database-id (if (empty? database-id) (nav/get-database-id-by-note-id note-id) database-id)]
    (cond
      #_(nil? database-id)
      #_(dict/get-dict-error :error-missing-database-info region)

      (empty? database-id)
      (dict/get-dict-error :error-database-not-found-by-name region)

      (false? (permission/can-access-database? account-id database-id))
      (dict/get-dict-error :error-permission-deny region)
      
      :else
      (nav/create-or-update-nav account-id database-id note-id id param region))))

(defn get-note-navs-api
  "获取笔记节点的接口"
  [{:keys [hulunote region]}
   {:keys [note-id]}]
  (let [account-id (:hulunote/id hulunote)]
    (cond
      (nil? note-id)
      (dict/get-dict-error :error-missing-parameter region)

      (false? (permission/can-access-note? account-id note-id))
      (dict/get-dict-error :error-permission-deny region)
      
      :else
      {:nav-list (nav/get-note-navs account-id note-id)})))

(defn get-database-all-navs-by-page-api
  "获取笔记库所有节点"
  [{:keys [hulunote region]}
   {:keys [database-id database database-name backend-ts page size]}]
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
      (nav/get-database-all-navs-by-page account-id database-id backend-ts page size region))))

(defn get-datbase-all-navs-api
  "获取笔记库所有节点，不分页"
  [{:keys [hulunote region]}
   {:keys [database-id database database-name backend-ts]}]
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
      (nav/get-database-all-navs account-id database-id backend-ts region))))

(def apis
  [["/create-or-update-nav" {:post #'create-or-update-nav-api}]
   ["/new-hulunote-navs-uuid-v2" {:post #'create-or-update-nav-api}]
   ["/get-note-navs" {:post #'get-note-navs-api}]
   ["/get-nav-list-by-id" {:post #'get-note-navs-api}]
   ["/get-all-nav-by-page" {:post #'get-database-all-navs-by-page-api}]
   ["/get-all-navs" {:post #'get-datbase-all-navs-api}]])
