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

(defn note-in-database?
  "Return true when the note belongs to the current database.
   Handles both legacy local values (database name) and synced values (database id)."
  [conn database-name note]
  (let [current-database-id (when database-name
                              (get-database-id-by-name conn database-name))
        note-database-id (:hulunote-notes/database-id note)]
    (or (nil? database-name)
        (= note-database-id database-name)
        (= note-database-id current-database-id)
        (nil? current-database-id))))

(defn search-page-links
  "Search note titles for page-link suggestions inside the current database.
   Blank query falls back to recent note titles."
  ([conn database-name query] (search-page-links conn database-name query 8))
  ([conn database-name query limit]
   (let [q-lower (some-> query clojure.string/lower-case)
         note-eids (d/q '[:find [?e ...]
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
          (filter #(note-in-database? conn database-name %))
          (filter (fn [note]
                    (let [title (:hulunote-notes/title note)]
                      (and (string? title)
                           (if (clojure.string/blank? query)
                             true
                             (clojure.string/includes?
                               (clojure.string/lower-case title)
                               q-lower))))))
          (map (fn [note]
                 {:note-id (:hulunote-notes/id note)
                  :note-title (:hulunote-notes/title note)
                  :root-nav-id (:hulunote-notes/root-nav-id note)
                  :updated-at (or (:hulunote-notes/updated-at note)
                                  (:hulunote-notes/created-at note)
                                  "1970-01-01")}))
          (sort-by :updated-at #(compare %2 %1))
          (take limit)
          vec))))

(defn find-backlinks
  "Find all navs that reference the given note title via [[title]] or #title or #[[title]].
   Returns a vector of backlink entries ordered by most recent reference first.
   For backlinks we prefer nav created-at over updated-at, so later child edits
   do not incorrectly bubble an older reference to the top."
  [conn title]
  (when (and title (not (empty? title)))
    (->> (d/q '[:find ?nav-content ?nav-id ?source-note-id ?source-title ?parent-id ?updated-at ?created-at
                :in $ ?title ?match-fn
                :where
                [?nav :content ?nav-content]
                [?nav :id ?nav-id]
                [?nav :hulunote-note ?source-note-id]
                [?source :hulunote-notes/id ?source-note-id]
                [?source :hulunote-notes/title ?source-title]
                [?nav :origin-parid ?parent-id]
                [(get-else $ ?nav :updated-at "") ?updated-at]
                [(get-else $ ?nav :created-at "") ?created-at]
                [(?match-fn ?nav-content ?title)]]
          conn title
          (fn [content title]
            (when (and (string? content) (not= content "ROOT"))
              (or (clojure.string/includes? content (str "[[" title "]]"))
                  (clojure.string/includes? content (str "#[[" title "]]"))
                  (clojure.string/includes? content (str "#" title))))))
         (map (fn [[nav-content nav-id source-note-id source-title parent-id updated-at created-at]]
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
                                       parent-content)
                      normalize-sort-date (fn [v]
                                            (cond
                                              (nil? v) nil
                                              (and (string? v) (clojure.string/blank? v)) nil
                                              (string? v) v
                                              (instance? js/Date v) (.toISOString v)
                                              :else (str v)))
                      updated-at (normalize-sort-date updated-at)
                      created-at (normalize-sort-date created-at)
                      sort-date (or created-at updated-at "1970-01-01T00:00:00.000Z")]
                  {:content nav-content
                   :id nav-id
                   :source-note-id source-note-id
                   :source-title source-title
                   :parent-content parent-content
                   :updated-at updated-at
                   :created-at created-at
                   :sort-date sort-date})))
         (sort-by :sort-date)
         reverse
         vec)))

(defn group-backlinks-by-note
  "Group backlink results by source note.
   Returns an ordered vector of [source-note-id {:title ... :navs ...}] sorted by the
   latest backlink reference timestamp in each group, newest first. Navs inside each group are
   also sorted newest first."
  [backlinks]
  (->> backlinks
       (reduce
         (fn [acc {:keys [content id source-note-id source-title parent-content updated-at created-at sort-date] :as backlink}]
           (update acc source-note-id
             (fn [existing]
               (let [existing-navs (or (:navs existing) [])
                     next-navs (->> (conj existing-navs
                                      {:content content
                                       :id id
                                       :parent-content parent-content
                                       :updated-at updated-at
                                       :created-at created-at
                                       :sort-date sort-date})
                                    (sort-by :sort-date)
                                    reverse
                                    vec)
                     latest-sort-date (or (:latest-sort-date existing) sort-date)]
                 {:title source-title
                  :note-id source-note-id
                  :latest-sort-date (if (pos? (compare latest-sort-date sort-date))
                                      latest-sort-date
                                      sort-date)
                  :navs next-navs}))))
         {})
       (sort-by (fn [[_ {:keys [latest-sort-date]}]]
                  latest-sort-date))
       reverse
       vec))

(defn search-notes
  "Search notes by title and nav content. Returns up to `limit` results.
   Each result has :match-type (:title or :content) and optional :match-content snippet."
  ([conn query] (search-notes conn query 30))
  ([conn query limit]
   (if (or (nil? query) (empty? query))
     []
     (let [q-lower (clojure.string/lower-case query)
           ;; --- Title matches ---
           note-eids (d/q '[:find [?e ...]
                             :where [?e :hulunote-notes/id]]
                       conn)
           notes (d/pull-many conn
                   '[:hulunote-notes/id
                     :hulunote-notes/title
                     :hulunote-notes/root-nav-id
                     :hulunote-notes/database-id
                     :hulunote-notes/updated-at
                     :hulunote-notes/created-at]
                   note-eids)
           title-matches
           (->> notes
                (filter (fn [note]
                          (when-let [title (:hulunote-notes/title note)]
                            (clojure.string/includes?
                              (clojure.string/lower-case title) q-lower))))
                (map (fn [note]
                       {:note-id (:hulunote-notes/id note)
                        :note-title (:hulunote-notes/title note)
                        :root-nav-id (:hulunote-notes/root-nav-id note)
                        :database-id (:hulunote-notes/database-id note)
                        :updated-at (or (:hulunote-notes/updated-at note)
                                        (:hulunote-notes/created-at note)
                                        "1970-01-01")
                        :match-type :title})))
           title-note-ids (set (map :note-id title-matches))
           ;; --- Content matches (nav nodes) ---
           nav-hits (d/q '[:find ?nav-content ?nav-id ?note-id
                            :in $ ?match-fn
                            :where
                            [?nav :content ?nav-content]
                            [?nav :id ?nav-id]
                            [?nav :hulunote-note ?note-id]
                            [(?match-fn ?nav-content)]]
                      conn
                      (fn [content]
                        (and (string? content)
                             (not= content "ROOT")
                             (> (count content) 0)
                             (clojure.string/includes?
                               (clojure.string/lower-case content) q-lower))))
           ;; Group by note-id, keep first matching nav per note
           nav-by-note (reduce
                         (fn [acc [nav-content nav-id note-id]]
                           (if (contains? acc note-id)
                             acc
                             (assoc acc note-id {:nav-content nav-content :nav-id nav-id})))
                         {} nav-hits)
           ;; Build note info map for content-matched notes
           content-note-ids (remove title-note-ids (keys nav-by-note))
           content-matches
           (when (seq content-note-ids)
             (let [note-info-map (into {}
                                   (map (fn [note]
                                          [(:hulunote-notes/id note) note]))
                                   notes)]
               (->> content-note-ids
                    (map (fn [nid]
                           (let [info (get note-info-map nid)
                                 nav (get nav-by-note nid)]
                             (when info
                               {:note-id nid
                                :note-title (or (:hulunote-notes/title info) "Untitled")
                                :root-nav-id (:hulunote-notes/root-nav-id info)
                                :database-id (:hulunote-notes/database-id info)
                                :updated-at (or (:hulunote-notes/updated-at info)
                                                (:hulunote-notes/created-at info)
                                                "1970-01-01")
                                :match-type :content
                                :match-content (:nav-content nav)}))))
                    (remove nil?))))
           ;; Also add match-content to title matches if they have content hits
           title-matches-enriched
           (map (fn [m]
                  (if-let [nav (get nav-by-note (:note-id m))]
                    (assoc m :match-content (:nav-content nav))
                    m))
                title-matches)]
       (->> (concat title-matches-enriched content-matches)
            (sort-by :updated-at #(compare %2 %1))
            (take limit)
            vec)))))

;; ==================== Right Sidebar State ====================

(defonce right-sidebar-open? (atom false))
(defonce right-sidebar-notes (atom []))
(defonce right-sidebar-width (atom 420))

(defn clamp-right-sidebar-width
  [width]
  (let [viewport-width (or (some-> js/window .-innerWidth) 1520)
        max-width (max 320 (js/Math.floor (/ viewport-width 2)))]
    (-> width
        (max 320)
        (min max-width))))

(defn set-right-sidebar-width!
  [width]
  (reset! right-sidebar-width (clamp-right-sidebar-width width)))

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
