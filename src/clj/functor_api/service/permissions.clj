(ns functor-api.service.permissions
  (:require [clojure.string :as strings]
            [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [functor-api.dict :as dict]
            [functor-api.service.payment :as payment]))

(defn can-access-database?
  "检查访问笔记库权限"
  [account-id database-id]
  (let [owner-id (-> (sql/select :account-id)
                     (sql/from :hulunote-databases)
                     (sql/where [:and
                                 [:= :id database-id]
                                 [:= :is-delete false]])
                     (u/execute-one!)
                     (:hulunote-databases/account-id))]
    (boolean
     (or
      (= account-id owner-id)
     ;; TODO: 暂时不用检查读与写，只要检查是否存在即可
      (-> (sql/select :id)
          (sql/from :cancans)
          (sql/where [:and
                      [:= :account-id account-id]
                      [:= :database-id database-id]
                      [:= :is-delete false]])
          (u/execute-one!)
          (:cancans/id)
          (str))))))

(defn can-access-note?
  "检查访问笔记权限"
  [account-id note-id]
  (let [database-id (-> (sql/select :database-id)
                        (sql/from :hulunote-notes)
                        (sql/where [:and
                                    [:= :id note-id]
                                    [:= :is-delete false]])
                        (u/execute-one!)
                        (:hulunote-notes/database-id))]
    (boolean (and database-id
                  (can-access-database? account-id database-id)))))

(defn add-permission-to-database
  ([account-id database-id] (add-permission-to-database account-id database-id true true)) 
  ([account-id database-id can-read? can-write?]
   (if-let [id (-> (sql/select :id)
                   (sql/from :cancans)
                   (sql/where [:and
                               [:= :account-id account-id]
                               [:= :database-id database-id]])
                   (u/execute-one!)
                   (:cancans/id)
                   (str))]
    ;; 原来有这个权限记录，更新
     (-> (sql/update :cancans)
         (sql/sset {:can-read (Boolean/valueOf can-read?)
                    :can-write (Boolean/valueOf can-write?)
                    :is-delete false
                    :updated-at (sqlh/call :now)})
         (sql/where [:= :id id])
         (u/execute-one!))
    ;; 原来没有这个记录，新增
     (-> (sql/insert-into :cancans)
         (sql/values [{:id (u/uuid)
                       :database-id database-id
                       :account-id account-id
                       :can-read (Boolean/valueOf can-read?)
                       :can-write (Boolean/valueOf can-write?)}])
         (u/execute-one!)))))
