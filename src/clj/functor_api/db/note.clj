(ns functor-api.db.note
  (:require [clojure.string :as strings]
            [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [honeysql-postgres.helpers :as pg-sqlh]
            [cheshire.core :as json]
            [functor-api.dict :as dict]
            [functor-api.db.nav :as nav]
            [functor-api.service.payment :as payment]
            [functor-api.state.database :as database]))

(defn- get-database-id-by-name [account-id database database-name]
  (let [db-name (or database database-name)]
    (-> (sql/select :id)
        (sql/from :hulunote-databases)
        (sql/where [:and
                    [:= :account-id account-id]
                    [:= :name db-name]])
        (u/execute-one!)
        (:hulunote-databases/id)
        (str))))

(defn- check-note-title-access [database-id title]
  (let [note-id (-> (sql/select :id)
                    (sql/from :hulunote-notes)
                    (sql/where [:and
                                [:= :database-id database-id]
                                [:= :title title]])
                    (u/execute-one!)
                    (:hulunote-notes/id)
                    (str))]
     (empty? note-id)))

(defn create-new-note-core
  "创建笔记（联动创建根节点）"
  ([account-id database-id title] 
   (create-new-note-core account-id database-id title (u/uuid) (u/uuid)))
  ([account-id database-id title note-id root-id]
   (try
     (u/with-transaction
      (let [note-value {:id note-id
                        :title title
                        :database-id database-id
                        :root-nav-id root-id
                        :account-id account-id}
            note-result (-> (sql/insert-into :hulunote-notes)
                            (sql/values [note-value])
                            (u/returning :*)
                            (u/execute-one!))
            nav-result (nav/create-new-nav-core-with-db-id account-id database-id note-id root-id
                                                           "00000000-0000-0000-0000-000000000000" "ROOT" 0)] 
        (assoc note-result :hulunote-notes/nav nav-result)))
     (catch Exception ex
       (u/log-error ex)
       (dict/get-dict-error :error-server)))))

(defn create-new-note
  "创建新笔记" 
  [account-id db-id title region]
   (if (check-note-title-access db-id title)
     (create-new-note-core account-id db-id title)
     (dict/get-dict-error :error-note-title-already-exists region)))

(defn get-note-list-by-page
  "分页获取笔记列表"
  [account-id database-id page size region]
  (if (> size 1000)
    (dict/get-dict-error :error-size-too-big region) 
    (let [db-id database-id
          offset (* (dec page) size)
          all-count (-> (sql/select :%count.*)
                        (sql/from :hulunote-notes)
                        (sql/where [:and
                                    [:= :database-id db-id]
                                    [:= :is-delete false]])
                        (u/execute-one!)
                        (:count))
          all-pages (int (Math/ceil (/ all-count size)))
          result (-> (sql/select :*)
                     (sql/from :hulunote-notes)
                     (sql/where [:and
                                 [:= :database-id db-id]
                                 [:= :is-delete false]])
                     (sql/offset offset)
                     (sql/limit size)
                     (sql/order-by [:updated-at :desc])
                     (u/execute!)
                     (u/flip-map #(assoc % 
                                         :created-at-unix (.getTime (:hulunote-notes/created-at %))
                                         :updated-at-unix (.getTime (:hulunote-notes/updated-at %)))))]
      {:all-pages all-pages
       :note-list result})))

(defn get-all-note-count
  "获取笔记库的笔记数量"
  [account-id database-id]
  (-> (sql/select :%count.*)
      (sql/from :hulunote-notes)
      (sql/where [:and
                  [:= :database-id database-id]
                  [:= :is-delete false]])
      (u/execute-one!)
      (:count)))

(defn get-all-note-list
  "获取全部笔记列表"
  [account-id database-id region]
  (let [db-id database-id
        result (-> (sql/select :*)
                   (sql/from :hulunote-notes)
                   (sql/where [:and
                               [:= :database-id db-id]
                               [:= :is-delete false]])
                   (sql/order-by [:updated-at :desc])
                   (u/execute!)
                   (u/flip-map #(assoc % 
                                         :created-at-unix (.getTime (:hulunote-notes/created-at %))
                                         :updated-at-unix (.getTime (:hulunote-notes/updated-at %)))))]
    {:note-list result}))

(defn update-note
  "更新笔记（包括删除）"
  [account-id note-id {:keys [title is-delete is-public is-shortcut]} region]
  (let [note (-> (sql/select :*)
                 (sql/from :hulunote-notes)
                 (sql/where [:= :id note-id])
                 (u/execute-one!))
        database-id (:hulunote-notes/database-id note)
        is-title-conflict? (if title
                             (-> (sql/select :id)
                                 (sql/from :hulunote-notes)
                                 (sql/where [:and
                                             [:= :database-id database-id]
                                             [:= :is-delete false]
                                             [:= :title title]])
                                 (u/execute-one!)
                                 (:hulunote-notes/id)
                                 (str))
                             false)
        base (cond-> {:updated-at (java.sql.Timestamp. (System/currentTimeMillis))}
               title (assoc :title title)
               is-public (assoc :is-public is-public)
               is-shortcut (assoc :is-shortcut is-shortcut)
               (true? is-delete) (assoc :is-delete true
                                        :title (str (:hulunote-notes/title note) "-DELETE-" (u/uuid))))]
    (if (and (not= title (:hulunote-notes/title note))
             is-title-conflict?)
      (dict/get-dict-error :error-note-title-already-exists region)
      (u/with-transaction
        ;; 更新笔记 
        (-> (sql/update :hulunote-notes)
            (sql/sset base)
            (sql/where [:= :id note-id])
            (u/execute-one!))
        ;; 更新笔记的双链信息
        (when (and (not= title (:hulunote-notes/title note))
                   (false? is-title-conflict?))
          (nav/update-double-link account-id database-id (:hulunote-notes/title note) title))
        {:success true}))))

(defn get-shortcuts-note-list
  "获取笔记库的收藏的笔记信息"
  [account-id database-id]
  (let [result (-> (sql/select :*)
                   (sql/from :hulunote-notes)
                   (sql/where [:and
                               [:= :database-id database-id]
                               [:= :is-delete false]
                               [:= :is-shortcut true]])
                   (u/execute!))]
    {:note-list result}))

(defn get-or-create-daynote
  "获取或创建每日笔记"
  [account-id database-id]
  (let [daystr (u/local-today-str)
        note (-> (sql/select :*)
                 (sql/from :hulunote-notes)
                 (sql/where [:and
                             [:= :account-id account-id]
                             [:= :database-id database-id]
                             [:= :title daystr]
                             [:= :is-delete false]])
                 (u/execute-one!))]
    (if (empty? note)
      ;; 没有每日笔记，创建
      (create-new-note-core account-id database-id daystr)
      note)))

(defn import-or-update-note!
  "导入或更新笔记及节点信息，已再事务中，无需再开启"
  [database-id account-id note]
  (let [navs (:children note)
        the-title (:title note)
        now-ts (java.sql.Timestamp. (System/currentTimeMillis))
        
        {:hulunote-notes/keys [title id root-nav-id] :as db-note} 
        (-> (sql/select :id :title :root-nav-id)
            (sql/from :hulunote-notes)
            (sql/where [:and 
                        [:= :database-id database-id]
                        [:= :title the-title]])
            (u/execute-one! ))
        root-nav-id (if root-nav-id root-nav-id (u/uuid))
        id (if id id (u/uuid))]

    (if db-note
      (-> (sql/update :hulunote-notes)
          (sql/sset {:title the-title
                     :updated-at now-ts})
          (sql/where [:= :id id])
          (u/execute-one! ))
      (do 
        (-> (sql/insert-into :hulunote-notes)
            (sql/values [{:id id
                          :title the-title
                          :database-id database-id
                          :root-nav-id root-nav-id
                          :account-id account-id}])
            (u/execute-one! )) 
        (-> (sql/insert-into :hulunote-navs)
            (sql/values [{:id root-nav-id
                          :parid "00000000-0000-0000-0000-000000000000"
                          :same-deep-order 0
                          :content "ROOT"
                          :account-id account-id
                          :note-id id
                          :database-id database-id
                          :is-delete false
                          :updated-at now-ts}])
            (u/execute-one! ))))
    ;; 存节点信息
    (nav/import-or-update-navs! database-id id account-id root-nav-id navs)))
