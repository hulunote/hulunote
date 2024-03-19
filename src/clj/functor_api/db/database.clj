(ns functor-api.db.database
  (:require [clojure.string :as strings]
            [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [cheshire.core :as json]
            [functor-api.dict :as dict]
            [functor-api.service.payment :as payment])
  (:import (java.sql Timestamp)))


(defn get-database-id-by
  "通过几个参数key来获取笔记id"
  [account-id database-id database database-name]
  (if database-id database-id
      (let [db-name (or database database-name)]
        (-> (sql/select :id)
            (sql/from :hulunote-databases)
            (sql/where [:and
                        [:= :account-id account-id]
                        [:= :name db-name]])
            (u/execute-one!)
            (:hulunote-databases/id)
            (str)))))

(defn get-database-count
  "获取笔记库数量"
  [account-id]
  (-> (sql/select :%count.*)
      (sql/from :hulunote-databases)
      (sql/where [:and
                  [:= :account-id account-id]
                  [:= :is-delete false]])
      (u/execute-one!)
      (:count)))

(defn- calc-database-name-when-conflict [account-id db-name]
  (loop [c 0]
    (let [new-name (if (= c 0)
                     db-name
                     (str db-name "(" c ")"))
          exists? (-> (sql/select :%count.*)
                      (sql/from :hulunote-databases)
                      (sql/where [:and
                                  [:= :account-id account-id]
                                  [:= :name new-name]
                                  [:= :is-delete false]])
                      (u/execute-one!)
                      (:count 0)
                      (> 0))]
      (if exists?
        (recur (inc c))
        new-name))))

(defn create-database
  "创建笔记库"
  [account-id database-name description region]
  (let [database-name (calc-database-name-when-conflict account-id database-name)
        id (u/uuid)
        value {:id id
               :name database-name
               :account-id account-id
               :description description}]
    (try
      (-> (sql/insert-into :hulunote-databases)
          (sql/values [value])
          (u/execute-one!))
      {:success true
       :database-name database-name
       :database-id id}
      (catch Exception ex
        (u/log-error ex)
        (dict/get-dict-error :error-server region)))))

(defn create-bot-database
  "创建机器人绑定的笔记库"
  [account-id database-name description platform region]
  (let [database-name (calc-database-name-when-conflict account-id database-name)
        id (u/uuid)
        value {:id id
               :name database-name
               :account-id account-id
               :description description
               :bot-group-platform platform}]
    (try
      (-> (sql/insert-into :hulunote-databases)
          (sql/values [value])
          (u/execute-one!))
      {:success true
       :database-name database-name
       :database-id id}
      (catch Exception ex
        (u/log-error ex)
        (dict/get-dict-error :error-server region)))))

;; 创建默认的笔记数据
(defn- create-default-notes [database-id account-id region]
  (u/log-todo "创建默认的笔记数据"))

(defn create-default-database
  "创建默认笔记库，在注册帐号后创建的"
  [account-id database-name region]
  (let [id (u/uuid)
        value {:id id
               :name database-name
               :description "Default and guide database"
               :is-default true
               :account-id account-id}]
    (try
      (-> (sql/insert-into :hulunote-databases)
          (sql/values [value])
          (u/execute-one!))
      (create-default-notes id account-id region)
      (catch Exception ex
        (u/log-error ex)
        (dict/get-dict-error :error-server region)))))

(defn add-user-permission-by-invitaion
  "因为邀请时而添加笔记库权限"
  [account-id db-invitation-code db-invitation-password region]
  (u/log-todo "邀请时而添加笔记库权限"))

(defn- get-database-note-count [db]
  (let [id (:hulunote-databases/id db)
        note-count (-> (sql/select :%count.*)
                       (sql/from :hulunote-notes)
                       (sql/where [:and
                                   [:= :database-id id]
                                   [:= :is-delete false]])
                       (u/execute-one!)
                       (:count))]
    (assoc db :count-notes note-count
           :account (:hulunote-databases/account-id db))))

(defn get-all-database-list
  "获取用户的笔记库列表，包括有权限的"
  [account-id]
  (let [databases (-> (sql/select :*)
                      (sql/from :hulunote-databases)
                      (sql/where [:and
                                  [:= :account-id account-id]
                                  [:= :is-delete false]])
                      (u/execute!)
                      (u/flip-map get-database-note-count))
        joined-databases (-> (sql/select :db.*)
                             (sql/from [:hulunote-databases :db])
                             (sql/join [:cancans :c] [:= :db.id :c.database-id])
                             (sql/where [:and
                                         [:= :c.account-id account-id]
                                         [:= :c.is-delete false]])
                             (u/execute!)
                             (u/flip-map get-database-note-count))]
    (concat databases joined-databases)))


(defn get-database-setting
  "获取用的笔记库设置"
  [account-id]
  (let [setting (-> (sql/select :*)
                    (sql/from :user-settings)
                    (sql/where [:= :account-id account-id])
                    (u/execute-one!))]
    (if setting
      {:settings/auto-suggest-note (:user-settings/auto-suggest-note setting)
       :settings/default-theme-mode (:user-settings/default-theme-mode setting)
       :settings/my-api-key (:user-settings/my-api-key setting)
       :settings/offine-code (:user-settings/offline-code setting)
       :settings/offline-code (:user-settings/offline-code setting)
       :settings/openai-key (-> setting
                                (:user-settings/extra-json)
                                (json/parse-string true)
                                (:openai-key))}
      (let [setting {:account-id account-id
                     :my-api-key ""
                     :default-theme-mode "light"
                     :auto-suggest-note false
                     :offline-code ""}]
        (-> (sql/insert-into :user-settings)
            (sql/values [setting])
            (u/execute-one!))
        {:settings/auto-suggest-note false
         :settings/default-theme-mode "light"
         :settings/my-api-key ""
         :settings/offine-code ""
         :settings/offline-code ""
         :settings/openai-key ""}))))

(defn export-and-public-database
  "公开并导出笔记库信息"
  [database-id]
  (u/log-todo "公开并导出笔记库信息"))

(defn- clear-default-database [account-id]
  (-> (sql/update :hulunote-databases)
      (sql/sset {:is-default false})
      (sql/where [:= :account-id account-id])
      (u/execute-one!)))

(defn is-database-account-match? 
  [account-id database-id]
  (let [db-account-id (-> (sql/select :account-id)
                          (sql/from :hulunote-databases)
                          (sql/where [:= :id database-id])
                          (u/execute-one!)
                          (:hulunote-databases/account-id))]
    (= db-account-id account-id)))

(defn update-database
  "更新笔记库设置，包括删除"
  [database-id account-id {:keys [is-public is-ws-daily is-default is-delete db-name]} region]
  (let [database (-> (sql/select :*)
                     (sql/from :hulunote-databases)
                     (sql/where [:= :id database-id])
                     (u/execute-one!))
        is-default (cond
                     (some? is-ws-daily) is-ws-daily
                     (some? is-default) is-default
                     :else nil)]
    (cond 
      (and (or (false? is-default)
               (true? is-delete))
           (:hulunote-databases/is-default database))
      (dict/get-dict-error :error-must-have-one-default-db region) 

      (and (some? db-name)
           (strings/includes? db-name "/"))
      (dict/get-dict-error :error-unsupport-param region) 
      
      :else
      (u/with-transaction
       (let [base (cond-> {:updated-at (java.sql.Timestamp. (System/currentTimeMillis))}
                    (some? is-public) (assoc :is-public is-public)
                    (some? is-default) (assoc :is-default is-default)
                    (some? db-name) (assoc :name (calc-database-name-when-conflict account-id db-name))
                    (true? is-delete) (assoc :name (str (:hulunote-databases/name database) "-DELETE-" (u/uuid))
                                             :is-delete true))]
         ;; 处理公开的导出
         (when (true? is-public)
           (export-and-public-database database-id))

         ;; 处理默认库
         (when (true? is-default)
           (clear-default-database account-id)) 
         
         (-> (sql/update :hulunote-databases)
             (sql/sset base)
             (sql/where [:= :id (:hulunote-databases/id database)])
             (u/execute-one!))
         {:success true
          :ok (dict/get-dict-string :success-op region)})))))
