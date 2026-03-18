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

(defn get-database-id-by-name
  [conn database-name]
  (d/q '[:find ?database-id .
         :in $ ?database-name
         :where
         [?e :hulunote-databases/name ?database-name]
         [?e :hulunote-databases/id ?database-id]]
    conn
    database-name))

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

(defn get-recent-notes
  "Get notes sorted by updated-at desc.
   Falls back to created-at when updated-at is missing."
  [conn]
  (let [note-eids (d/q
                    '[:find [?e ...]
                      :where
                      [?e :hulunote-notes/id]]
                    conn)
        notes (d/pull-many conn
                '[:hulunote-notes/id
                  :hulunote-notes/title
                  :hulunote-notes/root-nav-id
                  :hulunote-notes/database-id
                  :hulunote-notes/updated-at
                  :hulunote-notes/created-at]
                note-eids)]
    (->> notes
         (map (fn [note]
                (let [updated-at (or (:hulunote-notes/updated-at note)
                                     (:updated-at note))
                      created-at (or (:hulunote-notes/created-at note)
                                     (:created-at note))
                      sort-date (or updated-at created-at "1970-01-01")]
                  {:note-id (:hulunote-notes/id note)
                   :note-title (:hulunote-notes/title note)
                   :root-nav-id (:hulunote-notes/root-nav-id note)
                   :updated-at updated-at
                   :created-at created-at
                   :sort-date sort-date})))
         (sort-by :sort-date)
         reverse
         vec)))

(defn get-starred-notes
  "Get starred notes sorted by updated-at desc.
   Falls back to created-at when updated-at is missing."
  [conn database-name]
  (let [note-eids (d/q
                    '[:find [?e ...]
                      :where
                      [?e :hulunote-notes/id]
                      [?e :hulunote-notes/is-shortcut true]]
                    conn)
        current-database-id (when database-name
                              (get-database-id-by-name conn database-name))
        notes (d/pull-many conn
                '[:hulunote-notes/id
                  :hulunote-notes/title
                  :hulunote-notes/root-nav-id
                  :hulunote-notes/database-id
                  :hulunote-notes/updated-at
                  :hulunote-notes/created-at]
                note-eids)]
    (->> notes
         (filter (fn [note]
                   (let [note-database-id (:hulunote-notes/database-id note)]
                     (or (nil? database-name)
                         (= note-database-id database-name)
                         (= note-database-id current-database-id)
                         (nil? current-database-id)))))
         (map (fn [note]
                (let [updated-at (or (:hulunote-notes/updated-at note)
                                     (:updated-at note))
                      created-at (or (:hulunote-notes/created-at note)
                                     (:created-at note))
                      sort-date (or updated-at created-at "1970-01-01")]
                  {:note-id (:hulunote-notes/id note)
                   :note-title (:hulunote-notes/title note)
                   :root-nav-id (:hulunote-notes/root-nav-id note)
                   :updated-at updated-at
                   :created-at created-at
                   :sort-date sort-date})))
         (sort-by :sort-date)
         reverse
         vec)))

(defn find-backlinks
  "Find all navs that reference the given note title via [[title]] or #title or #[[title]].
   Returns a set of [nav-content nav-id source-note-id source-title parent-content]."
  [conn title]
  (when (and title (not (empty? title)))
    (->> (d/q '[:find ?nav-content ?nav-id ?source-note-id ?source-title ?parent-id
                :in $ ?title ?match-fn
                :where
                [?nav :content ?nav-content]
                [?nav :id ?nav-id]
                [?nav :hulunote-note ?source-note-id]
                [?source :hulunote-notes/id ?source-note-id]
                [?source :hulunote-notes/title ?source-title]
                [?nav :origin-parid ?parent-id]
                [(?match-fn ?nav-content ?title)]]
          conn title
          (fn [content title]
            (when (and (string? content) (not= content "ROOT"))
              (or (clojure.string/includes? content (str "[[" title "]]"))
                  (clojure.string/includes? content (str "#[[" title "]]"))
                  (clojure.string/includes? content (str "#" title))))))
         (map (fn [[nav-content nav-id source-note-id source-title parent-id]]
                (let [parent-content (when parent-id
                                       (d/q '[:find ?parent-content .
                                              :in $ ?parent-id
                                              :where
                                              [?parent :id ?parent-id]
                                              [?parent :content ?parent-content]]
                                         conn parent-id))
                      parent-content (when (and (string? parent-content)
                                                (not (clojure.string/blank? parent-content))
                                                (not= parent-content "ROOT"))
                                       parent-content)]
                  [nav-content nav-id source-note-id source-title parent-content])))
         set)))

(defn group-backlinks-by-note
  "Group backlink results by source note. Returns a map of
   {source-note-id {:title source-title :navs [{:content ... :id ...}]}}"
  [backlinks]
  (reduce
    (fn [acc [nav-content nav-id source-note-id source-title parent-content]]
      (update acc source-note-id
        (fn [existing]
          {:title source-title
           :note-id source-note-id
           :navs (conj (or (:navs existing) [])
                   {:content nav-content
                    :id nav-id
                    :parent-content parent-content})})))
    {}
    backlinks))

;; ==================== Right Sidebar State ====================

(defonce right-sidebar-open? (atom false))
(defonce right-sidebar-notes (atom []))

(defn open-note-in-right-sidebar!
  "Open a note in the right sidebar"
  [note-id note-title root-nav-id database-name]
  (prn "[right-sidebar] open-note-in-right-sidebar! note-id:" note-id
       "title:" note-title "root-nav-id:" root-nav-id "db:" database-name)
  (when-not (some #(= (:note-id %) note-id) @right-sidebar-notes)
    (swap! right-sidebar-notes conj
      {:note-id note-id
       :note-title note-title
       :root-nav-id root-nav-id
       :database-name database-name}))
  (reset! right-sidebar-open? true)
  (prn "[right-sidebar] state after open - open?:" @right-sidebar-open?
       "notes count:" (count @right-sidebar-notes)))

(defn close-note-in-right-sidebar!
  "Remove a note from the right sidebar"
  [note-id]
  (swap! right-sidebar-notes
    (fn [notes] (vec (remove #(= (:note-id %) note-id) notes))))
  (when (empty? @right-sidebar-notes)
    (reset! right-sidebar-open? false)))

(defn close-right-sidebar! []
  (reset! right-sidebar-open? false)
  (reset! right-sidebar-notes []))

(defn toggle-right-sidebar-visibility!
  "Toggle right sidebar visibility without clearing loaded notes."
  []
  (swap! right-sidebar-open? not))

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
