(ns functor-api.service.payment
  (:require [clojure.string :as strings]
            [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [functor-api.dict :as dict]))

(def pay-in-process 0)
(def pay-ok 1)
(def pay-fail 2)

(def user-vip 1)
(def user-free 0)

(defn- create-or-update-user-vip-state [account-id kind days]
  (let [state (-> (sql/select :*)
                  (sql/from :vip-states)
                  (sql/where [:= :account-id account-id])
                  (u/execute-one!))]
    (cond
      ;; 创建免费用户信息
      (and (empty? state)
           (= kind :free))
      (-> (sql/insert-into :vip-states)
          (sql/values [{:account-id account-id
                        :state user-free}])
          (u/execute-one!))

      ;; 创建VIP用户信息
      (and (empty? state)
           (= kind :vip))
      (let [now (System/currentTimeMillis)
            expired-time (+ (* 1000 86400 days) now)
            now-ts (java.sql.Timestamp. now)
            expired-ts (java.sql.Timestamp. expired-time)]
        (-> (sql/insert-into :vip-states)
            (sql/values [{:account-id account-id
                          :state user-vip
                          :start-at now-ts
                          :expired-at expired-ts}])
            (u/execute-one!)))

      ;; 更新为免费用户
      (= kind :free)
      (-> (sql/update :vip-states)
          (sql/sset {:state user-free})
          (sql/where [:= :account-id account-id])
          (u/execute-one!))
 
      ;; 更新vip
      (= kind :vip)
      (if (= (:vip-states/state state) user-free)
        ;; 原本是免费用户
        (let [now (System/currentTimeMillis)
              expired-time (+ (* 1000 86400 days) now)
              now-ts (java.sql.Timestamp. now)
              expired-ts (java.sql.Timestamp. expired-time)]
          (-> (sql/update :vip-states)
              (sql/sset {:state user-vip
                         :start-at now-ts
                         :expired-at expired-ts})
              (sql/where [:= :account-id account-id])
              (u/execute-one!)))
        ;; 原本是vip，增加过期时间
        (let [expired-ts (:vip-states/expired-at state)
              new-expired-time (+ (.getTime expired-ts)
                                  (* 1000 86400 days))
              new-expired-ts (java.sql.Timestamp. new-expired-time)]
          (-> (sql/update :vip-states)
              (sql/sset {:state user-vip
                         :expired-at new-expired-ts})
              (sql/where [:= :account-id account-id])
              (u/execute-one!)))))))

;; 这里不再使用从头计算payments的方式了，直接从用户的会员表里取和检查
;; 每次发生payment交易、葫芦籽兑换时，同步添加这里的会员表信息
(defn get-user-payments-state 
  "通过用户id获取用户的付费情况（VIP时间）"
  ([account-id] (get-user-payments-state account-id nil))
  ([account-id region]
   (let [state (-> (sql/select :*)
                   (sql/from :vip-states)
                   (sql/where [:= :account-id account-id])
                   (u/execute-one!))
         now (System/currentTimeMillis)
         free-result {:ok "success"
                      :kind "free"
                      :type (dict/get-dict-string :user-type-free region)}]
     (cond 
       (empty? state)
       free-result
       
       (= (:vip-states/state state) user-free)
       free-result
       
       (> now (.getTime (:vip-states/expired-at state)))
       (do
         (create-or-update-user-vip-state account-id :free 0)
         free-result)
       
       :else
       (let [expired-at (:vip-states/expired-at state)
             formatter (java.text.SimpleDateFormat. "yyyy-MM-dd")
             expired-str (.format formatter expired-at)
             days (int (/ (- now (.getTime expired-at))
                          (* 1000 86400)))
             type (str (dict/get-dict-string :user-type-vip region)
                       ", have " days " days remaining, expired at " expired-str)]
         {:ok "success"
          :kind "vip"
          :type type})))))

(defn is-vip?
  "检查用户是否是VIP"
  [account-id]
  (= (:kind (get-user-payments-state account-id)) "vip"))

(defn get-or-take-vip-trail-chance
  "获取用户的专业版状态，或者是消耗试用次数"
  ([account-id kind] (get-or-take-vip-trail-chance account-id kind 50))
  ([account-id kind default-chance]
   (if (is-vip? account-id)
     true
     (let [record (-> (sql/select :*)
                      (sql/from :vip-trial-chances)
                      (sql/where [:and
                                  [:= :account-id account-id]
                                  [:= :kind kind]])
                      (u/execute-one!))]
       (cond
         ;; 创建试用的记录
         (empty? record)
         (do (-> (sql/insert-into :vip-trial-chances)
                 (sql/values [{:account-id account-id
                               :kind kind
                               :chances (dec default-chance)}])
                 (u/execute-one!))
             true)
         
         ;; 扣减使用次数
         (>= (:vip-trial-chances/chances record) 1)
         (do (-> (sql/update :vip-trial-chances)
                 (sql/sset {:chances (dec (:vip-trial-chances/chances record))
                            :updated-at (sqlh/call :now)})
                 (sql/where [:= :id (:vip-trial-chances/id record)])
                 (u/execute-one!))
             true)
         
         ;; 次数不足
         :else false)))))
