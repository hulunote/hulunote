(ns functor-api.service.huluseed
  (:require [clojure.string :as strings]
            [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [functor-api.dict :as dict]
            [clojure.core.async :as async]
            [functor-api.middleware-new :as middleware]))

(def *income 1)
(def *expenses 2)

(def huluseed-transaction-queue (async/chan 1024))
(def huluseed-transaction-worker-active (atom false))

(defn- get-or-create-huluseed-balance [account-id]
  (let [{:hulunote-seed-balance/keys [balance] :as result} 
           (-> (sql/select :*)
               (sql/from :hulunote-seed-balance)
               (sql/where [:= :account-id account-id])
               (u/execute-one!))]
    (if result
      {:account-id account-id
       :balance balance}
      (let [value {:account-id account-id
                   ;; for first time to use huluseed
                   :balance 20}]
        (-> (sql/insert-into :hulunote-seed-balance)
            (sql/values [value])
            (u/execute-one!))
        value))))

(defn- start-transaction-worker [account-id fee info extras trade-id trade-type]
  ;; put in queueu channel
  (async/>!! huluseed-transaction-queue [account-id fee info extras trade-id trade-type])
  ;; test and start costume
  (when-not @huluseed-transaction-worker-active
    (reset! huluseed-transaction-worker-active true)
    (async/go-loop []
                   (let [v (async/alt! huluseed-transaction-queue ([v] v) 
                                       :default :empty
                                       :priority true)]
                     (if (= v :empty)
                       (reset! huluseed-transaction-worker-active false)
                       (do 
                         (u/with-transaction 
                           (let [[account-id fee info extras trade-id trade-type] v
                                 {:keys [balance]} (get-or-create-huluseed-balance account-id)
                                 new-balance (cond
                                               (= trade-type *income) (+ balance fee)
                                               (= trade-type *expenses) (- balance fee)
                                               :else balance)]
                             (-> (sql/insert-into :hulunote-seed-transaction)
                                 (sql/values [(cond-> {:id trade-id
                                                       :fee fee
                                                       :account-id account-id
                                                       :trade-type trade-type
                                                       :trade-info info} 
                                                (:extra-text extras) (assoc :extra-text (:extra-text extras))
                                                (:extra-int extras) (assoc :extra-int (:extra-int extras)))])
                                 (u/execute-one! ))
                             (-> (sql/update :hulunote-seed-balance)
                                 (sql/sset {:balance new-balance
                                            :updated-at (sqlh/call :now)})
                                 (sql/where [:= :account-id account-id])
                                 (u/execute-one! )))) 
                         (recur))))))) 

(defn add-hulunote-seed
  "添加葫芦籽"
  [account-id fee info & {:as extras}]
  (let [trade-id (u/uuid)]
    (start-transaction-worker account-id fee info extras trade-id *income)
    {:success true :trade-id trade-id}))

(defn remove-hulunote-seed
  "扣除葫芦籽"
  [account-id fee info & {:as extras}]
  (let [{:keys [balance]} (get-or-create-huluseed-balance account-id)]
    (if (< balance fee)
      (dict/get-dict-error :error-insufficient-balance)
      (let [trade-id (u/uuid)]
        (start-transaction-worker account-id fee info extras trade-id *expenses)
        {:success true :trade-id trade-id}))))

(defn get-transaction-list
  "分页获取葫芦籽流水情况"
  [account-id page size]
  (let [offset (* (dec page) size)
        count (-> (sql/select :%count.*)
                  (sql/from :hulunote-seed-transaction)
                  (sql/where [:= :account-id account-id])
                  (u/execute-one!))
        res (-> (sql/select :*)
                (sql/from :hulunote-seed-transaction)
                (sql/where [:= :account-id account-id])
                (sql/order-by [:created-at :desc])
                (sql/offset offset)
                (sql/limit size)
                (u/execute!))]
    {:success true
     :count (:count count)
     :list res}))


;;; apis

(defn get-balance-api
  [{:keys [hulunote]}
   {:keys []}]
  (let [account-id (:hulunote/id hulunote)
        {:keys [balance]} (get-or-create-huluseed-balance account-id)]
    {:success true 
     :balance balance}))

(defn get-transaction-list-api
  [{:keys [hulunote]}
   {:keys [page size]}]
  (let [account-id (:hulunote/id hulunote)]
    (get-transaction-list account-id page size)))

(def apis
  ["/huluseed" {:middleware middleware/common-middlewares}
   ["/get-balance" {:post #'get-balance-api}]
   ["/get-transaction-list" {:post #'get-transaction-list-api}]])

