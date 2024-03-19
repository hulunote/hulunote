(ns hulunote.import
  (:require [datascript.core :as d]
            [hulunote.db :as db]
            [hulunote.util :as u]))

(defn nav-list->db-add
  "从init-nav-list->dsdb抽象出来可复用的函数"
  [nav-list]
  (let [db-ids
        (into {}
          (map-indexed
            (fn [db-id item]
              {(:id item) (db/get-dsdb-id)})
            nav-list))
        trans-data
        (map
          (fn [item]
            (if-not (:parid item)
              {}
              (let [item (assoc item  :last-account-id (if (:last-account-id item)
                                                         (:last-account-id item)
                                                         -1))
                    output (if (nil? (db-ids (:parid item)))
                             (-> item
                               (assoc :db/id (db-ids (:id item)))
                               (dissoc item :parid))
                             (assoc item :db/id
                               (db-ids (:id item))
                               :parid
                               (db-ids (:parid item))))]
                (assoc output :origin-parid (:parid item)
                  ;; TODO: 加载`:load true` 第一次刷新出来的nav才有这个字段, 更新后的字段没有这个字段 => 增量计算引用数量和第一次计算引用数量相互隔离
                  :load true))))
          nav-list)]
    trans-data))

(defn separate-attribute-and-relation-data-for-reactnative
  "ReactNative分离关系数据和属性数据"
  ([trans-data]
   (separate-attribute-and-relation-data-for-reactnative trans-data nil))
  ([trans-data retract-atom]
   (let [attr-data
         (mapv
           (fn [item]
             (-> (dissoc item
                   :parid
                   :reference-notes
                   :reference-navs
                   :updated-at-unix
                   :created-at
                   :created-at-unix
                   :is-public)
               (update :parser-content u/transit-read)
               (#(if (:parser-content %)
                   %
                   (dissoc % :parser-content)))
               (#(if (nil? (:is-display %))
                   (assoc % :is-display true)
                   %))))
           trans-data)
         relation-data
         (filterv
           (fn [item]
             (not (nil? (nth item 1))))
           (mapv
             (fn [item]
               (try
                 (when retract-atom
                   (when (= (get @retract-atom
                              (:id item))
                           (:parid item))
                     (swap! retract-atom dissoc (:id item))))
                 (catch :default e
                   nil))
               [:db/add (:parid item)  :parid  (:db/id item)])
             trans-data))
         to-be-retract
         (try
           (when retract-atom
             (for [ids  @retract-atom]
               (let [[id oid] ids]
                 [:db/retract [:id oid]
                  :parid [:id id]])))
           (catch :default e
             []))]
     (concat  to-be-retract attr-data relation-data ))))

(defn separate-attribute-and-relation-data
  "分离关系数据和属性数据"
  [trans-data]
  (let [attr-data
        (mapv
          (fn [item]
            (-> (dissoc item
                  :parid
                  :reference-notes
                  :reference-navs
                  :is-public)
              (update :parser-content u/transit-read)
              (#(if (:parser-content %)
                  %
                  (dissoc % :parser-content)))
              (#(if (nil? (:is-display %))
                  (assoc % :is-display true)
                  %))))
          trans-data)
        relation-data
        (mapv
          (fn [item]
            (if (not= (:origin-parid item)
                  db/root-id)
              [:db/add [:id (:origin-parid item)]  :parid  [:id  (:id item)]]
              {}))
          trans-data)]
    [attr-data relation-data] ))
