(ns functor-api.db.nav
  (:require [clojure.string :as strings]
            [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [honeysql-postgres.helpers :as psqlh]
            [cheshire.core :as json]
            [functor-api.dict :as dict]
            [clojurewerkz.quartzite.scheduler :as qs]))

(defn create-new-nav-core-with-db-id
  "带上db-id创建nav节点"
  [account-id database-id note-id id parid content order]
  (-> (sql/insert-into :hulunote-navs)
      (sql/values [{:id id
                    :parid parid
                    :same-deep-order (double order)
                    :account-id account-id
                    :note-id note-id
                    :database-id database-id
                    :content content}])
      (u/returning :*)
      (u/execute-one!)))

(defn create-new-nav-core-with-db-id-extra
  "带上db-id创建nav节点"
  [account-id database-id note-id id parid content order extra-id]
  (-> (sql/insert-into :hulunote-navs)
      (sql/values [{:id id
                    :parid parid
                    :same-deep-order (double order)
                    :account-id account-id
                    :note-id note-id
                    :database-id database-id
                    :content content
                    :extra-id (or extra-id "")}])
      (u/returning :*)
      (u/execute-one!)))

(defn get-database-id-by-note-id [note-id]
  (-> (sql/select :database-id)
      (sql/from :hulunote-notes)
      (sql/where [:= :id note-id])
      (u/execute-one!)
      (:hulunote-notes/database-id)))

(defn create-new-nav-core
  "创建nav节点" 
  [account-id note-id id parid content order] 
  (let [database-id (get-database-id-by-note-id note-id)]
    (create-new-nav-core-with-db-id
     account-id database-id note-id id parid content order)))

(defn get-parent-last-order [parid]
  (-> (sql/select :%max.same-deep-order)
      (sql/from :hulunote-navs)
      (sql/where [:and
                  [:= :parid parid]
                  [:= :is-delete false]])
      (u/execute-one!)
      (:max -100)))

(defn create-new-nav-auto-order
  "创建nav节点，自动计算order"
  ([account-id note-id id parid content] (create-new-nav-auto-order account-id nil note-id id parid content))
  ([account-id database-id note-id id parid content] 
   (let [database-id (if database-id database-id 
                         (get-database-id-by-note-id note-id)) 
         last-order (get-parent-last-order parid)
         order (+ last-order 100)] 
     (create-new-nav-core-with-db-id 
      account-id database-id note-id id parid content order))))

(defn create-new-nav-auto-order-extra
  "创建nav节点，自动计算order"
  ([account-id note-id id parid content extra-id] (create-new-nav-auto-order-extra account-id nil note-id id parid content extra-id))
  ([account-id database-id note-id id parid content extra-id]
   (let [database-id (if database-id database-id
                         (get-database-id-by-note-id note-id))
         last-order (get-parent-last-order parid)
         order (+ last-order 100)]
     (create-new-nav-core-with-db-id-extra
      account-id database-id note-id id parid content order extra-id))))

;; 直接用sql替换，不用查数据出来
(defn update-double-link
  "更新nav节点的双链"
  [account-id database-id old-title new-title]
  (let [old-link (str "[[" old-title "]]")
        new-link (str "[[" new-title "]]")]
    (-> (sql/update :hulunote-navs)
        (sql/sset {:content (sqlh/call :replace :content old-link new-link)
                   :updated-at (sqlh/call :now)})
        (sql/where [:and
                    [:= :database-id database-id]
                    [:like :content (str "%" old-link "%")]
                    [:= :is-delete false]])
        (u/execute!))))

(defn update-nav
  "更新nav（包括删除）"
  [account-id id 
   {:keys [note-id parid content is-delete is-display properties order]} ]
  (let [base (cond-> {:updated-at (sqlh/call :now)}
                     note-id (assoc :note-id note-id)
                     parid (assoc :parid parid)
                     content (assoc :content content)
                     properties (assoc :properties properties)
                     is-delete (assoc :is-delete (Boolean/valueOf is-delete))
                     is-display (assoc :is-display (Boolean/valueOf is-display))
                     order (assoc :same-deep-order (double order)))]
    (-> (sql/update :hulunote-navs)
        (sql/sset base)
        (sql/where [:and
                    [:= :id id]
                    [:= :is-delete false]])
        (u/execute-one!))
    {:hulunote-navs/id id}))

(defn create-or-update-nav
  "参照原版的new-hulunote-navs-uuid的接口，创建一个新的或更新原来的"
  ([account-id note-id id params region] (create-or-update-nav account-id nil note-id id params region))
  ([account-id database-id note-id id 
    {:keys [parid content is-delete is-display properties order] :as param} region]
   (let [exists-id (-> (sql/select :id)
                       (sql/from :hulunote-navs)
                       (sql/where [:and
                                   [:= :id id]
                                   [:= :is-delete false]])
                       (u/execute-one!)
                       (:hulunote-navs/id)
                       (str))] 
     (cond
       ;; 有id，更新
       (some? exists-id)
       (-> (sql/update :hulunote-navs)
           (sql/sset (cond-> {:updated-at (sqlh/call :now)}
                       parid (assoc :parid parid)
                       content (assoc :content content)
                       properties (assoc :properties properties)
                       is-delete (assoc :is-delete (Boolean/valueOf is-delete))
                       is-display (assoc :is-display (Boolean/valueOf is-display))
                       order (assoc :same-deep-order (double order))))
           (sql/where [:= :id id])
           (u/execute-one!)
           (do {:hulunote-navs/id id}))
       
       ;; 没有id，有笔记信息, 创建
       (some? note-id)
       (let [database-id (if database-id database-id
                             (get-database-id-by-note-id note-id))] 
         (-> (sql/insert-into :hulunote-navs)
             (sql/values [{:id id
                           :parid parid
                           :same-deep-order (double order)
                           :account-id account-id
                           :note-id note-id
                           :database-id database-id
                           :content content}])
             (u/execute-one!)
             (do {:hulunote-navs/id id})))
       
       ;; 没有id，没有笔记信息，报错
       :else
       (dict/get-dict-error :error-missing-parameter region)))))

(defn get-note-navs
  "获取笔记的所有节点"
  [account-id note-id]
  (-> (sql/select :*)
      (sql/from :hulunote-navs)
      (sql/where [:and
                  [:= :note-id note-id]
                  [:= :is-delete false]])
      (u/execute!)
      (u/flip-map #(do {:id (:hulunote-navs/id %)
                        :parid (:hulunote-navs/parid %)
                        :same-deep-order (:hulunote-navs/same-deep-order %)
                        :content (:hulunote-navs/content %)
                        :account-id (:hulunote-navs/account-id %)
                        :last-account-id (:hulunote-navs/account-id %)
                        :note-id (:hulunote-navs/note-id %)
                        :hulunote-note (:hulunote-navs/note-id %)
                        :is-display (:hulunote-navs/is-display %)
                        :is-delete (:hulunote-navs/is-delete %)
                        :properties (:hulunote-navs/properties %)
                        :created-at (:hulunote-navs/created-at %)
                        :updated-at (:hulunote-navs/updated-at %)}))))

(defn get-database-all-navs-by-page
  "分页获取笔记库的所有节点，以backend-ts来区分"
  [account-id database-id backend-ts page size region]
  (if (> size 1000)
    (dict/get-dict-error :error-size-too-big region) 
    (let [offset (* (dec page) size)
          where (if (and backend-ts
                         (not= backend-ts 0))
                  [:and
                   [:= :database-id database-id]
                   [:>= :updated-at (java.sql.Timestamp. backend-ts)]]
                  [:and
                   [:= :database-id database-id]
                   [:= :is-delete false]])
          all-count (-> (sql/select :%count.*)
                        (sql/from :hulunote-navs)
                        (sql/where where)
                        (u/execute-one!)
                        (:count))
          all-pages (int (Math/ceil (/ all-count size)))
          result (-> (sql/select :*)
                     (sql/from :hulunote-navs)
                     (sql/where where)
                     (sql/offset offset)
                     (sql/limit size)
                     (u/execute!)
                     (u/flip-map #(do {:id (:hulunote-navs/id %)
                                       :parid (:hulunote-navs/parid %)
                                       :same-deep-order (:hulunote-navs/same-deep-order %)
                                       :content (:hulunote-navs/content %)
                                       :account-id (:hulunote-navs/account-id %)
                                       :last-account-id (:hulunote-navs/account-id %)
                                       :note-id (:hulunote-navs/note-id %)
                                       :hulunote-note (:hulunote-navs/note-id %)
                                       :is-display (:hulunote-navs/is-display %)
                                       :is-delete (:hulunote-navs/is-delete %)
                                       :properties (:hulunote-navs/properties %)
                                       :created-at (:hulunote-navs/created-at %)
                                       :updated-at (:hulunote-navs/updated-at %)})))]
      {:nav-list result
       :all-pages all-pages
       ;; 当结果数量比参数小再更新backend-ts
       :backend-ts (System/currentTimeMillis)})))

(defn get-database-all-navs
  "获取笔记库的所有节点，以backend-ts来区分，不分页"
  [account-id database-id backend-ts region]
  (let [where (if (and backend-ts
                       (not= backend-ts 0))
                [:and
                 [:= :database-id database-id]
                 [:>= :updated-at (java.sql.Timestamp. backend-ts)]]
                [:and
                 [:= :database-id database-id]
                 [:= :is-delete false]])
        result (-> (sql/select :*)
                   (sql/from :hulunote-navs)
                   (sql/where where)
                   (u/execute!)
                   (u/flip-map #(do {:id (:hulunote-navs/id %)
                                     :parid (:hulunote-navs/parid %)
                                     :same-deep-order (:hulunote-navs/same-deep-order %)
                                     :content (:hulunote-navs/content %)
                                     :account-id (:hulunote-navs/account-id %)
                                     :last-account-id (:hulunote-navs/account-id %)
                                     :note-id (:hulunote-navs/note-id %)
                                     :hulunote-note (:hulunote-navs/note-id %)
                                     :is-display (:hulunote-navs/is-display %)
                                     :is-delete (:hulunote-navs/is-delete %)
                                     :properties (:hulunote-navs/properties %)
                                     :created-at (:hulunote-navs/created-at %)
                                     :updated-at (:hulunote-navs/updated-at %)})))]
    {:nav-list result
     :backend-ts (System/currentTimeMillis)}))

(defn import-or-update-navs!
  "保存或更新navs，在事务中，无需再开启事务"
  [database-id note-id account-id parid navs]
  (loop [items navs
         order 0]
    (when-not (empty? items)
      (let [nav (first items)
            now-ts (java.sql.Timestamp. (System/currentTimeMillis))
            value {:id (:uid nav)
                   :parid parid
                   :same-deep-order order
                   :content (:string nav)
                   :account-id account-id
                   :note-id note-id
                   :database-id database-id
                   :is-delete false
                   :updated-at now-ts}]
        (-> (sql/insert-into :hulunote-navs)
            (sql/values [value])
            (psqlh/upsert (-> (psqlh/on-conflict :id) 
                              (psqlh/do-update-set :parid 
                                                   :same-deep-order 
                                                   :content 
                                                   :account-id 
                                                   :note-id 
                                                   :database-id 
                                                   :is-delete 
                                                   :updated-at)))
            (u/execute-one! ))
        (when (:children nav)
          (import-or-update-navs! database-id note-id account-id (:uid nav) (:children nav)))
        (recur (rest items) (+ order 100))))))
