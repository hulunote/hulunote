(ns functor-api.redis
  (:require
   [taoensso.carmine :as car :refer (wcar)]
   [functor-api.config :as config]))

;; 服务端OT: id 是由服务端 Redis 生成的自增 id，客户端会根据这个判断历史是否是新的。prev_id 用来操作转换时记录所需要进行转换操作的历史队列。

(defn get-redis-connect
  []
  {:pool {} :spec (:redis @config/functor-api-conf)})

(defmacro wcar* [& body] `(car/wcar (get-redis-connect) ~@body))

(defn get-keys [str]
  (wcar* (car/keys str)))

(defn del-keys [& args]
  (wcar* (apply car/del args)))

;;; String
(defn get-string [k]
  (wcar* (car/get k)))

(defn set-string [k v]
  (wcar* (car/set k v)))

(defn set-expire-string [k v t]
  (wcar*
   (car/set k v)
   (car/expire k t)))

;;; Hashes
(defn hgetall [k]
  (apply hash-map (wcar* (car/hgetall k))))

(defmacro wcar1 [& body] `(car/wcar {:pool {} :spec (:redis @config/functor-api-conf)} ~@body))

(defn get-string1 [k]
  (wcar1 (car/get k)))

(def redis-conn {:pool {} :spec (:redis @config/functor-api-conf)})
(defmacro wcar2 [& body] `(car/wcar redis-conn ~@body))

(defn get-string2 [k]
  (wcar2 (car/get k)))

;;; all kind types
;; taoensso.carmine会存储二进制的格式，它自己会进行序列化/反序列化，所以不需要手动转类型
(defn get-val [k]
  (wcar* (car/get k)))

(defn set-val [k v]
  (wcar* (car/set k v)))

(defn set-val-expire [k v t]
  (wcar*
   (car/set k v)
   (car/expire k t)))

;; 获取key所占用的内存大小（bytes），包括key和value
(defn get-mem [k]
  (let [res (wcar* (car/memory-usage k))]
    (if res res 0)))

;; 队列相关
(defn push-queue [k v]
  (wcar*
   (car/lpush k v)))

(defn bpop-queue
  "阻塞pop"
  ([k]
   (bpop-queue k 0))
  ([k expire]
   (let [[_k val] (wcar* (car/brpop k expire))]
     val)))
