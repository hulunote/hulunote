(ns hulunote.db
  (:require
   [datascript.core :as d]
   [datascript.transit :as dt]
   ;;[hulunote.data :as data]
   [reitit.frontend.easy :as rfe]
   ["moment" :as moment]))

(def schema
  {:id                    {:db/unique :db.unique/identity}
   :parid                 {:db/cardinality :db.cardinality/many
                           :db/valueType   :db.type/ref}
   :hulunote-note         {:db/index true}
   :origin-parid          {:db/index true}
   :hulunote-notes/id     {:db/unique :db.unique/identity}
   :hulunote-notes/title  {:db/unique :db.unique/identity}
   ;; ds描述路由
   :route/id              {:db/unique :db.unique/identity}
   ;; => 数据库列表和设置
   :hulunote-databases/id {:db/unique :db.unique/identity}
   ;; :settings {}
   :message/id            {:db/unique :db.unique/identity}})

(defonce dsdb (d/create-conn schema))

(def root-id "00000000-0000-0000-0000-000000000000")

(defn init-db []
  ;; (d/transact! dsdb [[:db/add -1 :id root-id]
  ;;                    ;;
  ;;                    {:db/id -2
  ;;                     :route/name {:route-name :home :params {}}}
  ;;                    {:db/id -3
  ;;                     :route/name {:route-name :database :params {}}}
  ;;                    {:db/id -4
  ;;                     :route/name {:route-name :login :params {}}}])
  )

(comment
  ;;
  (dt/read-transit-str (str data/eg1))

  (reset-db! data/eg1)

  )
(defn reset-db! [db]
  (d/reset-conn! dsdb db))

(defn clear-dsdb
  []
  (d/reset-conn! dsdb (d/empty-db schema))
  (init-db))

(defn get-dsdb-id
  "获取dsdb的自增id"
  []
  (d/tempid :hulunote))

(defn select
  "select by any entity key and value in datomic"
  ;;select all by any entity id
  ;;(select :database/id "5f38e6b6-ddc9-4e6b-a341-e72a45e14a22")
  ;;(select :database/name "landing")
  ;; select :all by key
  ;;(select :database/id)
  ([entity-key]
   (d/q
     (assoc-in '[:find (pull ?e [*]) :in $ :where [?e k ?id]]
       [5 1]
       entity-key)
     (d/db dsdb)))

  ([entity-key id]
   (d/q
     (assoc-in '[:find (pull ?e [*]) :in $ ?id :where [?e k ?id]]
       [6 1]
       entity-key)
     (d/db dsdb)
     id)))

(defn get-route
  [conn]
  (:v (first (d/datoms conn :aevt :route/name)))
  ;; (last
  ;;   (last
  ;;     (sort-by first
  ;;       (d/q '[:find ?e ?name
  ;;              :where [?e :route/name ?name]]
  ;;         conn))))
  )

(comment
  (get-message @dsdb)
  (last
    (first
      (d/q '[:find ?e ?name
             :where [?e :message/name ?name]]
        @dsdb)))

  ;; (:v (first (d/datoms @dsdb :aevt :message/name)))

  )
(defn get-message
  [conn]
  (:v (first (d/datoms conn :aevt :message/name))) ;; 性能最好的方式，直接datom取
  ;; (last
  ;;   (first
  ;;     (d/q '[:find ?e ?name
  ;;            :where [?e :message/name ?name]]
  ;;       conn)))
  )

(comment
  (get-database @dsdb)
  )
(defn get-database
  [conn]
  (d/q '[:find (pull ?e [*])  ;; ?e ?name
         :where [?e :hulunote-databases/id ?name]]
    conn))

(defn is-daily-title
  "解决(= (u/get-time-now-stri-day) title): 今日笔记的标题不可修改 , 过去的标题就能修改了"
  [title]
  (re-find #"^\d{4}-\d{2}-\d{2}$" title))

(defn daily-title->unix
  "解析2020-09-01为unix的时间"
  [day]
  (.unix (moment. day "YYYY-MM-DD")))

(comment
  (->>

    (sort-by
      (fn [item]
        ;; (prn item)
        (daily-title->unix (first item))
        )
      >
      (get-daily-list @dsdb)
      )
    )
  )
(defn get-daily-list
  [conn]
  (d/q
    '[:find  ?note-title ?note-id ?root-nav
      :in $ ?daily-title
      :where
      [?note :hulunote-notes/title ?note-title]
      [?note :hulunote-notes/root-nav-id ?root-nav]
      [?note :hulunote-notes/id ?note-id]
      [_ :hulunote-notes/id ?note-id]
      [(?daily-title ?note-title)]]
    conn
    is-daily-title))

(defn sort-daily-list
  [daily-list]
  (sort-by
    (fn [item]
      (daily-title->unix (first item)))
    >
    daily-list))

(comment
  (defn get-note-list
    [conn]
    (d/q
      '[:find ?note-id  ?note-title ?root-nav
        ;; ?note-id ?root-nav
        ;; :in $ ?daily-title
        :where
        [?note :hulunote-notes/title ?note-title]
        ;; [?note :hulunote-navs/root-nav-id ?root-nav]
        [?note :hulunote-notes/root-nav-id ?root-nav]
        [?note :hulunote-notes/id ?note-id]
        [_ :hulunote-notes/id ?note-id]]
      ;; conn
      @dsdb
      )))
