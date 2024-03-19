(ns hulunote.util
  (:require
   [datascript.core :as d]
   [hulunote.db :as db]
   [cognitect.transit :as transit]
   ["./jshelp/jsutil" :as jsutil :refer (parseJwt)]
   [hulunote.storage :as storage]
   ["moment" :as moment]
   ["file-saver" :as file-saver])
  (:import (goog.date DateTime Interval)))

(defn remove-vals [f m]
  (reduce-kv (fn [m k v] (if (f v) m (assoc m k v))) (empty m) m))

(defn find-prev [xs pred]
  (last (take-while #(not (pred %)) xs)))

(defn find-next [xs pred]
  (fnext (drop-while #(not (pred %)) xs)))

(defn drop-tail [xs pred]
  (loop [acc []
         xs  xs]
    (let [x (first xs)]
      (cond
        (nil? x) acc
        (pred x) (conj acc x)
        :else  (recur (conj acc x) (next xs))))))

(defn trim-head [xs n]
  (vec (drop (- (count xs) n) xs)))

(defn index [xs]
  (map vector xs (range)))

(defn e-by-av [db a v]
  (-> (d/datoms db :avet a v) first :e))

(defn date->month [date]
  [(.getFullYear date)
   (inc (.getMonth date))])

(defn format-month [month year]
  (str (get ["January"
             "February"
             "March"
             "April"
             "May"
             "June"
             "July"
             "August"
             "September"
             "October"
             "November"
             "December"] (dec month))
       " " year))

(defn month-start [month year]
  (js/Date. year (dec month) 1))

(defn month-end [month year]
  (let [[month year] (if (< month 12)
                       [(inc month) year]
                       [1 (inc year)])]
    (-> (js/Date. year (dec month) 1)
        .getTime
        dec
        js/Date.
        )))

(defn transit-read
  [s]
  (let [r (transit/reader :json {})]
    (transit/read r s)))

;; (info (transit-write {:aa 11 :b 32231 :annn "a啊啊啊"}))
(defn transit-write
  [s]
  (let [w (transit/writer :json {})]
    (transit/write w s)))

(defn get-nav-by-id
  "根据uuid来获取任意属性属性的信息值"
  [ds id]
  (d/q '[:find (pull ?nav [*]) .
         :in $ ?id
         :where [?nav :id ?id]]
    ds id))

(defn sort-navs
  "递归程序: 每一层map 排序一遍"
  [navs]
  (if-let [children (seq (:parid navs))]
    (assoc navs :parid
      (sort-by :same-deep-order (map sort-navs children)))
    navs))

(defn get-nav-sub-navs-sorted
  [ds id]
  (sort-navs
    (d/q '[:find (pull ?e [:id :content :is-display :same-deep-order
                           :origin-parid
                           {:parid ...}]) .
           :in $ ?nav-id
           :where
           [?e :id ?nav-id]]
      ds id)))

(defn get-ele [div-id]
  (.getElementById js/document div-id))

(defn get-class [class]
  (first
    (array-seq
      (.getElementsByClassName js/document class))))

(defn is-dev?
  "你或者127就是开发者"
  []
  (re-find
    #"127.0.0.1"
    (-> js/location .-host)))

(defn get-params
  []
  (last (clojure.string/split (.-hash js/location) "?"))
  ;; (subs
  ;;   (str (.-search js/location)) 1)
  )

(defn parse-query-string
  [query-string]
  (if (empty? query-string)
    {}
    (let [params
          (-> query-string
            (clojure.string/split #"=|&"))]
      (->> params
        (partition 2)
        (map (fn [item] [(keyword (first item))
                         (last item)]))
        (into {})))))

(defn parse-jwt-token
  [token]
  (try
    (js->clj (parseJwt token))
    (catch js/Object err
      {})))

(defn jwt-token-is-expired?
  [token]
  (if (empty? token)
    true
    (>
      (/ (js/Date.now) 1000)
      (get  (parse-jwt-token token) "exp"))))

(defn is-expired?
  []
  (let [{:keys [token]} @storage/jwt-auth]
    (jwt-token-is-expired? token)))

(defn rest-join
  [vecs]
  (clojure.string/join "" (rest vecs)))

(defn stop-click-bubble
  "阻止默认事件以及事件冒泡: https://www.cnblogs.com/zhaozhipeng/p/7417947.html"
  [evt]
  (do
    (if (.-preventDefault evt)
      (.preventDefault evt)
      (set! (.-returnValue evt) false))
    (if (.-stopPropagation evt)
      (.stopPropagation evt)
      (set! (.-cancelBubble evt) true))))

(defn open-url
  [url]
  (.open js/window url "_blank"))

(comment
  (alert "321321312xxx")
  (alert "3213213122222xxx")


  ;; Lookup ref attribute should be marked as :db/unique: [:message/id "644e55d0-1ed0-4dff-b43b-50fbff4f052f"]
  (d/transact! db/dsdb [[:db/retractEntity
                         [:message/id "644e5706-f2d8-4b06-a166-09139211451f"]]])


  )
(defn alert [text]
  (let [did (str (d/squuid))
        _ (prn did)]
    (d/transact! db/dsdb
      [{:message/id did
        :message/name
        {:text text
         :date
         (.getTime
           (doto (DateTime.)
             (.add (Interval. Interval.SECONDS 5))))}}])
    ;; 删除掉，就不用sort-by多个了
    (js/setTimeout #(d/transact! db/dsdb [[:db/retractEntity
                                           [:message/id did]]]) 5000)))

(comment
  (daily-title->unix "2020-09-01")

  )
(defn daily-title->unix
  "解析2020-09-01为unix的时间"
  [day]
  (.unix (moment. day "YYYY-MM-DD")))

(comment
  (daily-title->en "2020-09-01")
  ;; => "September 1, 2020"
  )
(defn daily-title->en
  [day]
  (.format (moment. day "YYYY-MM-DD") "LL"))

(comment

  (.format
    (moment. (js/Date.)) "YYYY-MM-DD T HH.mm")
  ;; => "2021-10-20 T 10.50"

  ;; => "2020-11-30"
  )
(defn moment-format
  [date]
  (.format
    (moment. date) "YYYY-MM-DD"))


(declare parid->text)

(defn parid2->text
  "互相递归函数和parid->text"
  [{:keys [id content parid] :as parid} level nav-ids]
  (do
    (when-not (and (get @nav-ids id) id)
      (swap! nav-ids assoc id id))
    (let [parid-text (parid->text parid (inc level) nav-ids)
          level-pattern (str (apply str (repeat level "\t"))
                          (if (zero? level)
                            "-"
                            " -"))]
      (if content
        (str level-pattern " " (clojure.string/triml content) "\n"
          parid-text)
        parid-text))))

;;
(defn parid->text
  "转换Markdown的递归程序"
  [parid level nav-ids]
  (->> (map #(parid2->text % level nav-ids) parid)
    (interpose "\n")
    (apply str)))

(defn tree->md
  "用户批量导出Markdown,多选复制md等"
  [tree-data]
  (let [nav-ids (atom {})]
    (parid->text tree-data 0 nav-ids)))

(comment
  (download-text-file "a32132321111" "aaa11.txt")
  )
(defn download-text-file
  "下载txt文件"
  [stri file]
  ((.-saveAs file-saver)
   (js/Blob. #js [stri]
     #js {"type" "text/plain;charset=utf-8"})
   file))
