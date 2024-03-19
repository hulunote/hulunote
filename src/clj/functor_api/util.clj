(ns functor-api.util
  (:require [functor-api.config :as config]
            [functor-api.dict :as dict]
            [functor-api.state.database :refer [*main-datasource*]]
            [buddy.sign.jwt :as jwt]
            [buddy.hashers :as hashers]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [honeysql-postgres.helpers]
            [honeysql-postgres.format]
            [honeysql.helpers :as sqlh]
            [honeysql.core :as sql]
            [honeysql.format :as honeysql-fmt]
            [honeysql.types :as sqlt]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc-sql]
            [next.jdbc.optional :as jdbc-opt]
            [taoensso.timbre :as log]
            [reitit.core :refer [Expand]]
            [next.jdbc.result-set :refer [ReadableColumn]]
            [jsonista.core :as json]
            [mount.core :as mount]
            [ring.adapter.jetty9.websocket :as ws]
            [clojure.java.shell :as shell]
            [clj-time.format]
            [clj-time.coerce]
            [clj-http.client :as client]
            [functor-api.redis :as redis]
            [clojure.set :as sets]
            [clojure.java.io :as io]
            [clojure.string :as strings])
  (:import (org.postgresql.util PGobject)
           (java.time LocalDateTime LocalDate LocalTime Instant Duration Period)
           (java.time.format DateTimeFormatter)
           (java.time ZoneId)
           (java.sql Timestamp Time Array)
           (java.util Date TimeZone)
           (java.util Base64)))


(if true ;; (:release config)
  (extend-protocol Expand
    clojure.lang.Var
    (expand [this opts]
      {:handler (var-get this)}))
  (extend-protocol Expand
    clojure.lang.Var
    (expand [this opts]
      {:handler (fn [& args] (apply (var-get this) args))})))

(def ^:dynamic *lein-type*
  "FRONTEND")

(def ^:dynamic *mqtt*
  "false")

(def ^:dynamic *db-dry-run*
  "Bind to true to prevent write to database."
  false)

(defn in?
  "检查元素是否在列表内"
  [elem coll]
  (boolean (some #(= elem %) coll)))

(defn sql->location-sql-time [date]
  (if false ;; (:release config)
    (Timestamp.
     (- (.getTime date)
        (*  46800 1000)))
    date))

(defn sql-time-now
  "同步服务器上面的时间错位"
  []
  (if false ;; (:release config)
    (.addTo (Duration/ofSeconds 46800)
            (LocalDateTime/now))
    (LocalDateTime/now)))

(defn sql-time-now-today-begin
  []
  (if false ;; (:release config)
    (.addTo (Duration/ofSeconds 46800)
            (.atTime
             (LocalDate/now) 0 0))
    (LocalDate/now)))

(defn sql-time-now-today-end
  []
  (if false ;; (:release config)
    (.addTo (Duration/ofSeconds (+ 46800 86400))
            (.atTime
             (LocalDate/now) 0 0))
    (.plusDays (LocalDate/now) 1)))

(def mapper (json/object-mapper {:decode-key-fn keyword}))
(def ->json json/write-value-as-string)
(def <-json #(json/read-value % mapper))

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (->json x)))))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^org.postgresql.util.PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (with-meta (<-json value) {:pgtype type})
      value)))

(extend-protocol ReadableColumn
  ;; PGobject对象
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v))
  ;; PGArray
  org.postgresql.jdbc.PgArray
  (read-column-by-label [^org.postgresql.jdbc.PgArray v _]
    (vec (.getArray v)))
  (read-column-by-index [^org.postgresql.jdbc.PgArray v _2 _3]
    (vec (.getArray v)))
  ;; 时间戳
  java.sql.Date
  (read-column-by-label ^java.time.LocalDate [^java.sql.Date v _]
    (.toLocalDate v))
  (read-column-by-index ^java.time.LocalDate [^java.sql.Date v _2 _3]
    (.toLocalDate v))
  java.sql.Timestamp
  (read-column-by-label ^java.time.Instant [^java.sql.Timestamp v _]
    v)
  (read-column-by-index ^java.time.Instant [^java.sql.Timestamp v _2 _3]
    ;; (Timestamp. (.getTime (Date.)))
    (sql->location-sql-time v)))

;;; Logging

(defmacro log-info [& args]
  `(log/info ~@args))

(defmacro log-debug [& args]
  `(log/debug ~@args))

(declare save-error-info-db)

(defmacro log-error [& args]
  `(do
     (log/error ~@args) 
     (apply save-error-info-db [~@args])
     #_(save-error-info-db ~@args)))

(defn log-todo [s]
  (log-info "\n\n\t TODO: " s "\n\n"))

;;; Database Utilities

(defmacro with-transaction [& body]
  `(jdbc/with-transaction [tx# functor-api.state.database/*main-datasource* {:isolation :serializable}]
     (binding [functor-api.state.database/*main-datasource* tx#]
       (try
         (log-info "Transaction Begin")
         (let [ret# (do ~@body)]
           (log-info "Transaction Commit")
           ret#)
         (catch RuntimeException ex#
           (log-info "Transaction Rollback!")
           (throw ex#))))))

(defn timestamp
  "Return the timestamp in seconds."
  []
  (quot (System/currentTimeMillis) 1000))

(defn ->snake_case [s]
  (-> s
      (str/replace #"-" "_")))

;;; kebab -> snake
(defn <-snake_case [s]
  (-> s
      (str/replace #"_" "-")
      (str/replace #"bf-" "")))

;;; kebab -> camel
(defn ->camelCase [s]
  (let [ws (str/split s #"-")]
    (apply str (first ws) (map str/capitalize (next ws)))))

;;; camel -> kebab
(defn <-camelCase [s]
  (->> (re-seq #"(?:[A-Z]|^)[^A-Z]*" s)
       (map str/lower-case)
       (str/join "-")))

(defn as-kebab-case-map
  [rs opts]
  (jdbc-opt/as-modified-maps rs (assoc opts
                                       :qualifier-fn <-snake_case
                                       :label-fn <-snake_case)))

(def ^{:doc "imported from `honeysql-postgres.helpers`"} returning honeysql-postgres.helpers/returning)

(defn sql-format
  "Format SQL with some default setup.
  Currently,
  1. treat namespace as table name."
  [sqlmap]
  (sql/format sqlmap
              :namespace-as-table? true
              :quoting :postgres))

(defn- sqlmap-is-mutate? [sqlmap]
  (or (:insert sqlmap)
      (:update sqlmap)
      (:delete sqlmap)))

(defn execute-one!
  "Format the `sqlmap` into sql and immediately execute it on `datasource`.
  Return the first resultset.

  If caller don't provide the `datasource`,
  `functor-api.state.datasource/main-datasource` will be used."
  ([sqlmap]
   (execute-one! *main-datasource* sqlmap))
  ([datasource sqlmap]
   (let [sqlvec (sql-format sqlmap)]
     (if (and (sqlmap-is-mutate? sqlmap) *db-dry-run*)
       (log-debug "*DRYRUN* DB Execute: " sqlvec)
       (jdbc/execute-one! datasource sqlvec
                          {:builder-fn as-kebab-case-map})))))

(defn execute-sql-vec-one!
  "honeysql的新版本中有pg on-conflict的功能，为了保证稳定，暂不升级。
   因此这里使用直接提交sql-vec的方式"
  ([sqlvec]
   (execute-sql-vec-one! *main-datasource* sqlvec))
  ([datasource sqlvec]
   (jdbc/execute-one! datasource sqlvec
                      {:builder-fn as-kebab-case-map})))

(defn execute-raw!
  "执行raw的sql操作"
  ([raw]
   (execute-raw! *main-datasource* raw))
  ([datasource raw]
   (jdbc/execute-one! datasource raw
                      {:builder-fn as-kebab-case-map})))

(defn pgvector-holder [] {:pgvector nil})

(defn sql-pgvector-replace!
  "因技术问题，这里去替换pgvector的实现，需要按顺序:
  (-> (sql/insert-into :vector-dev-test)
      (sql/values [{:embedding (u/pgvector-holder)
                    :embedding2 (u/pgvector-holder)
                    :aaa \"abcd\"
                    :bbb 1234}])
      (u/sql-format)
      (u/sql-pgvector-replace! [1.1 2.2 3.3])
      (u/sql-pgvector-replace! [2.3 3.4 4.5])
      (execute-raw!))"
  [sql-raw pgvector-val]
  (if (empty? pgvector-val)
    sql-raw
    (let [pgvector-val (reduce #(str %1 "," %2)
                               (str (first pgvector-val))
                               (rest pgvector-val))
          pgvector-val (str "'[" pgvector-val "]'")
          raw (first sql-raw)
          start (str/index-of raw "()")
          end (when start
                (+ start 2))]
      (if end
        (let [head (subs raw 0 start)
              tail (subs raw end (count raw))
              new-raw (str head pgvector-val tail)]
          (concat [new-raw] (rest sql-raw)))
        sql-raw))))

(defn execute!
  "Format the `sqlmap` into sql and immediately execute it on `datasource`.
  Return the resultset in a seq.

  If caller don't provide the `datasource`,
  `functor-api.state.datasource/main-datasource` will be used."
  ([sqlmap]
   (execute! *main-datasource* sqlmap))
  ([datasource sqlmap]
   (let [sqlvec (sql-format sqlmap)]
     (log-debug sqlvec)
     (if (and (sqlmap-is-mutate? sqlmap) *db-dry-run*)
       (log-debug "*DRYRUN* DB Execute: " sqlvec)
       (jdbc/execute! *main-datasource* sqlvec
                      {:builder-fn as-kebab-case-map})))))

(defn execute-update!
  "Format the `sqlmap` into sql and immediately execute it on `datasource`.
  Return the affected row count.

  If caller don't provide the `datasource`,
  `functor-api.state.datasource/main-datasource` will be used."
  ([sqlmap]
   (execute-update! *main-datasource* sqlmap))
  ([datasource sqlmap]
   (let [sqlvec (sql-format sqlmap)]
     (if (and (sqlmap-is-mutate? sqlmap) *db-dry-run*)
       (log-debug "*DRYRUN* DB Execute: " sqlvec)
       (-> (jdbc/execute-one! *main-datasource* sqlvec
                              {:builder-fn as-kebab-case-map})
           ::jdbc/update-count)))))

(defn sql-insert!
  "Friendly SQL helper for insertion."
  ([table entity]
   (sql-insert! *main-datasource* table entity))
  ([datasource table entity]
   (if *db-dry-run*
     (log-debug (format "*DRYRUN* DB Execute: Insertion on table %s, entity: %s" table entity))
     (jdbc-sql/insert! datasource
                       table
                       entity
                       {:builder-fn as-kebab-case-map
                        :table-fn #(->snake_case (name %))
                        :column-fn #(->snake_case (name %))}))))

(defn sql-find-by-keys
  ([table cond-map]
   (sql-find-by-keys *main-datasource* table cond-map))
  ([datasource table cond-map]
   (jdbc-sql/find-by-keys datasource
                          table
                          cond-map
                          {:builder-fn as-kebab-case-map
                           :table-fn #(->snake_case (name %))
                           :column-fn #(->snake_case (name %))})))

(defn sql-get-by-id
  ([table-pk-name pk]
   (sql-get-by-id *main-datasource* table-pk-name pk))
  ([datasource table-pk-name pk]
   (jdbc-sql/get-by-id datasource
                       (namespace table-pk-name)
                       pk
                       (name table-pk-name)
                       {:builder-fn as-kebab-case-map
                        :table-fn #(->snake_case %)
                        :column-fn #(->snake_case %)})))

;; 记录错误信息到数据库中
(defn save-error-info-db 
  ([] ())
  ([& args]
   (doseq [arg args]
     (when (instance? Exception arg)
       (let [msg (.getMessage arg)
             traces (.getStackTrace arg)
             traces-str (reduce #(str %1 "\n" (str %2)) traces)
             filter-msgs (vals (:error-ws-unauthorized dict/dict))] 
         (when-not (and msg
                        (or (in? msg filter-msgs)
                            (str/includes? msg "functor-api.util/save-error-info-db/fn")))
           ;; 存错误信息，这里要try，失败了也没办法
           (try
             (-> (sqlh/insert-into :hulunote-error-records)
                 (sqlh/values [{:err-message msg
                                :err-stack traces-str}])
                 (execute-one!))
             (catch Exception e
               (log/error e)))))))))

;;; Error Utilities

(defmacro def-err [err-sym err-dict-key]
  (let [arglists '(quote ([& err-msg-format-args]))]
    `(defn ~err-sym
       {:arglists ~arglists}
       [& args#]
       (let [msg# (get-in functor-api.dict/dict [:err ~err-dict-key])]
         (throw (ex-info
                 (if (seq args#)
                   (apply format msg# args#)
                   msg#)
                 {}))))))

;;; Token Utilities

(defn gen-reg-code
  "生成注册的代码"
  []
  (first
   (str/split
    (str (java.util.UUID/randomUUID))
    #"-")))

(defn gen-token []
  (str/replace (str (java.util.UUID/randomUUID))
               #"-" ""))

(defn uuid []
  (str (java.util.UUID/randomUUID)))

;;; Web Server

(defn make-jwt-token
  [{:keys [role id]}]
  (jwt/sign
   {:role role
    :id   id 
    :exp  (.addTo (Period/ofDays 7) (Instant/now))}
   (:functor-api-jwt-key @config/functor-api-conf)))

(defn make-jwt-token-forever
  [{:keys [role id]}]
  (jwt/sign
   {:role role
    :id   id 
    :exp  (.addTo (Period/ofDays 9999) (Instant/now))}
   (:functor-api-jwt-key @config/functor-api-conf)))

(defn make-user-jwt-token [id]
  (jwt/sign {:role :hulunote
             :id id
             :exp (.addTo (Period/ofDays 7) (Instant/now))}
            (:functor-api-jwt-key @config/functor-api-conf)))

(defn get-accounts-by-identity-key-value
  [id-key id-val]
  (-> (sqlh/select :*)
      (sqlh/from :accounts)
      (sqlh/where [:= id-key id-val])
      (sqlh/limit 1)
      (execute-one!)))

;;; Geo calculation

(def earth-radius-km 6372.795477598)

(defn distance
  "Calculate the distance from latlng1 to latlng2.
  Both arguments have structure of: {:lat, :lng}."
  [latlng1 latlng2]
  (let [lat1 (Math/toRadians (:lat latlng1))
        lng1 (Math/toRadians (:lng latlng1))
        lat2 (Math/toRadians (:lat latlng2))
        lng2 (Math/toRadians (:lng latlng2))
        half-dlat (/ (- lat2 lat1) 2)
        half-dlng (/ (- lng2 lng1) 2)
        sin2 (fn [x] (Math/pow (Math/sin x) 2))
        a (+ (sin2 half-dlat) (* (Math/cos lat1) (Math/cos lat2) (sin2 half-dlng)))]
    (* 2000 earth-radius-km (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))))

;; 时区选择
(def zid (ZoneId/of "UTC-5"))
(def time-zone (TimeZone/getTimeZone "UTC-5"))

(defn local-date-string []
  (.format DateTimeFormatter/ISO_DATE (LocalDateTime/now zid)))
(defn local-date-time-string []
  (.format DateTimeFormatter/ISO_DATE_TIME (LocalDateTime/now zid)))

(defn ->epoch-second [^LocalDateTime ldt]
  (.toEpochSecond (.atZone ldt (ZoneId/systemDefault))))

(defn ->date-time [v]
  (cond
    (and (string? v) (= 19 (count v)))
    , (let [fmt (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")]
        (LocalDateTime/parse v fmt))
    (and (string? v) (= 10 (count v)))
    , (let [fmt (DateTimeFormatter/ofPattern "yyyy-MM-dd")]
        (-> (LocalDate/parse v fmt)
            (.atStartOfDay)))
    (number? v)
    , (LocalDateTime/ofInstant (java.time.Instant/ofEpochMilli v) (ZoneId/systemDefault))))

(defn date-end->unix-time [date]
  (if false ;; (:release config)
    (->epoch-second
     (.addTo (Duration/ofSeconds (+ 46800 86400))
             (->date-time date)))
    (->epoch-second
     (.plusDays
      (->date-time date)
      1))))

(defn date-str->util-date
  [stri]
  (java.util.Date.
   (*
    (date-end->unix-time stri)
    1000)))

(def date-formater 
  (let [formatter (java.text.SimpleDateFormat. "yyyy-MM-dd")]
    (.setTimeZone formatter time-zone)
    formatter))

(defn now-date []
  (LocalDateTime/now zid))

(defn date->format
  [date]
  (.format date-formater date))

(defn util-date-compare
  [date1 date2]
  (.compareTo date1 date2))

(defn util-date-compare2
  [date1 date2]
  (if (>= (.compareTo date1 date2) 0)
    true
    false))

(defn date-from-format
  "根据foramt创建时间日期"
  [format datestr]
  (let [formatter (java.text.SimpleDateFormat. format)]
    (.setTimeZone formatter time-zone)
    (.parse formatter datestr)))

(defn timestamp-from-format
  "根据format创建sql的时间戳"
  [format datestr]
  (let [date (date-from-format format datestr)]
    (java.sql.Timestamp. (.getTime date))))

(defn str-from-date
  "根据format格式化时间日期"
  [format dt]
  (let [formatter (java.text.SimpleDateFormat. format)]
    (.setTimeZone formatter time-zone)
    (.format formatter dt)))

(defn str-from-timestamp
  "根据format格式化时间戳"
  [format ts]
  (let [t (.getTime ts)
        #_t #_(if (= (.getID (java.util.TimeZone/getDefault)) "Etc/UTC")
            (- t (* 1000 3600 5))
            t)]
    (str-from-date format (java.util.Date. t))))

(defn flip-str-from-timestamp
  [ts format]
  (str-from-timestamp format ts))

(defn local-today-str []
  (str-from-date "yyyy-MM-dd" (java.util.Date.)))

(defn util-date-compare-range
  "查找处于某范围的时间"
  [max-d min-d date]
  (and
   (>= (.compareTo max-d date) 0)
   (>= (.compareTo date min-d) 0)))

(defn check-password
  [text digest]
  (when (and text digest)
    (hashers/check text digest)))

(defn remove-hash-namespace
  [data]
  (->> data
       (map (fn [[k v]] [(keyword (name k)) v]))
       (into {})))

(defn get-params
  "query的参数解析为hash"
  [query]
  (->>
   (clojure.string/split query #"\?|\&")
   (filter not-empty)
   (map #(clojure.string/split % #"="))
   (into {})))

(defn pandoc-html->md
  "将html转为markdown"
  [stream]
  (shell/sh
   "pandoc" "-f" "html" "-t" "markdown"
   :in stream))

(defn pandoc-md->docx
  "将md转为docx"
  [stream file]
  (shell/sh
   "pandoc" "-f" "markdown" "-t" "docx"
   "-o" file
   :in stream))

(defn html->word
  [stream]
  (try
    (let [file (str "resources/public/img/uploads/export-word" (uuid) ".docx")
          content (pandoc-md->docx
                   (:out (pandoc-html->md stream))
                   file)]
      {:out file})
    (catch Exception ex
      (log-info "导出word失败: " ex)
      (dict/get-dict-error :error-export-failed))))

(defn pandoc-md->html
  "将markdown转为html"
  [stream]
  (shell/sh
   "pandoc" "-f" "markdown" "-t"  "html"
   :in stream))

(defn pandoc-docx->md
  "将word docx转为md"
  [file]
  (try
    (let [res
          (:out (shell/sh
                 "pandoc"  "-s"
                 (str "resources/public/img/uploads/" file)
                 "-t" "markdown"
                 :out-enc "UTF-8"))]
      {:out res})
    (catch Exception e
      {:out ""
       :error (str e)})))

(defn pandoc-docx->md2
  "将word docx转为md,直接由file指定的位置而定"
  [file]
  (try
    (let [stream (io/input-stream file)
          res (:out (shell/sh
                     "pandoc" "-f" "docx" "-t" "markdown"
                     :in stream
                     :out-enc "UTF-8"))]
      {:out res})
    (catch Exception e
      (log-error e)
      {:error (str e)})))


(defn pandoc-html->pdf
  "将html转为pdf"
  [stream]
  (println "stream: " stream)
  (try
    (let [file-name (str (java.util.UUID/randomUUID))
          r (shell/sh
             "pandoc" "--latex-engine=xelatex" "-f" "html" "-t" "latex"
             "-o" (str "resources/public/img/uploads/" file-name ".pdf")
             :in stream)]
      {:out (str "/img/uploads/" file-name ".pdf")
       :succ (str r)})
    (catch Exception e
      {:out (str "/img/uploads/uuid-uuid.pdf")
       :error (str e)})))

(defn pandoc-html->latex
  "将html转为latex"
  [stream]
  (shell/sh
   "pandoc" "-f" "html" "-t" "latex"
   :in stream))

(defn pandoc-md->latex
  "将md转为latex"
  [stream]
  (shell/sh
   "pandoc" "-f" "markdown" "-t" "latex"
   :in stream))

(defn pandoc-md->pdf
  "将md转为pdf"
  [stream]
  (println "stream: " stream)
  (try
    (let [file-name (str (java.util.UUID/randomUUID))]
      (let [r
            (shell/sh
             "pandoc" "--latex-engine=xelatex" "-f" "markdown" "-t" "latex"
             "-o" (str "resources/public/img/uploads/" file-name ".pdf")
             :in stream)]
        {:out (str "/img/uploads/" file-name ".pdf")
         :succ (str r)}))
    (catch Exception e
      {:out (str "/img/uploads/uuid-uuid.pdf")
       :error (str e)})))

(defn pandoc-md->docx2
  "将md转为docx"
  [stream]
  (let [file (str "resources/public/img/uploads/export-word" (uuid) ".docx")]
    (shell/sh
     "pandoc" "-f" "markdown" "-t" "docx"
     "-o" file
     :in stream)
    {:out file}))

(defn is-uuid-str?
  "判断是否为uuid"
  [stri]
  (re-find
   #"^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$"
   stri))

(defn generate-rand-stri
  "生成12位字符: 用于做分享的端链接"
  ([] (generate-rand-stri 12))
  ([n]
   (let [chars-between #(map char (range (int %1) (inc (int %2))))
         chars (concat (chars-between \0 \9)
                       (chars-between \a \z)
                       (chars-between \A \Z)
                       [\_])
         password (take n (repeatedly #(rand-nth chars)))]
     (reduce str password))))

(defn has-part-of?
  "检查列表是否包含一部分或者全部另一个列表"
  [coll1 coll2]
  (let [set1 (set coll1)
        set2 (set coll2)
        intersection (sets/intersection set1 set2)]
    (not (empty? intersection))))

(defn different-elems-from
  "获取本列表对于另一个列表的不同部分"
  [coll1 coll2]
  (let [set1 (set coll1)
        set2 (set coll2)
        diff (sets/difference set1 set2)]
    (vec diff)))

(defn not-includes?
  "检查字符串不包含"
  [str substrings]
  (every? #(not (clojure.string/includes? str %)) substrings))

(defn rand-string
  "生成随机字符串"
  [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn na-or-false? [item]
  (or (= item false)
      (= item "N/A")))

(defn flip-rows
  "以0，0翻转一个二维表"
  [rows]
  (let [max-count (apply max (map #(count %) rows))
        fixed-rows (map #(let [self-count (count %)
                               less (- max-count self-count)]
                           (if (> less 0)
                             (vec (concat % (repeat less "")))
                             %))
                        rows)]
    (loop [i 0
           acc []]
      (if (= i max-count)
        acc
        (let [row (map #(nth % i) fixed-rows)]
          (recur (inc i) (conj acc row)))))))

(defn instant-in-x-days?
  "时间instant是否在x天内"
  [instant x]
  (let [now (System/currentTimeMillis)
        interval (* x 86400 1000)
        start (- now interval)
        target (.getTime instant)]
    (and
     (>= target start)
     (<= target now))))

(defn flip
  "反转函数参数的调用"
  [function]
  (fn
    ([] (function))
    ([x] (function x))
    ([x y] (function y x))
    ([x y z] (function z y x))
    ([a b c d] (function d c b a))
    ([a b c d & rest]
     (->> rest
          (concat [a b c d])
          reverse
          (apply function)))))

(defn flip-map
  "反向的map，方便给->使用"
  [coll f]
  (map f coll))

(defn flip-filter
  "反向的filter，方便给->使用"
  [coll f]
  (filter f coll))

(defn flip-reduce
  "反向的reduce，方便给->使用"
  [coll z f]
  (reduce f z coll))

(defn flip-sort-by
  "反向的sort-by，方便给->使用"
  ([coll f] (sort-by f coll))
  ([coll f comp] (sort-by f comp coll)))

(defn fix-digital-str-with-zero
  "数字字符串前补0"
  [s len]
  (let [str-len (count s)]
    (if (>= str-len len)
      s
      (let [num (- len str-len)]
        (str (str/join (repeat num "0"))
             s)))))

;; 分组并map
(defn partition-and-map
  [n f coll]
  (loop [rest coll
         acc []]
    (if (empty? rest)
      acc
      (let [[head tail] (split-at n rest)
            mapped-head (map f head)
            new-acc (conj acc mapped-head)]
        (recur tail new-acc)))))

(defn encode-string->base64 [s]
  (.encodeToString (Base64/getEncoder) (.getBytes s)))

(defn encode-bytes->base64 [bs]
  (.encodeToString (Base64/getEncoder) bs))

(defn decode-base64->string [s]
  (String. (.decode (Base64/getDecoder) s)))

(defn decode-base64->bytes [s]
  (.decode (Base64/getDecoder) s))
