(ns functor-api.tools.auto-lru
  (:require [functor-api.util :as u]
            [clojure.core.async :as async :refer [<!! <! go go-loop timeout]]))

;; 自动根据时间清理缓存的lru，以节省空间

(defn- lru-remove-expired-items! [lru]
  (let [expired-millis (:expired-millis lru)
        expired-keys (reduce #(let [kv %2
                                    k (first kv)
                                    v (second kv)
                                    {:keys [at-time]} v
                                    expired-at (+ at-time expired-millis)
                                    now (System/currentTimeMillis)]
                                (if (< expired-at now)
                                  (conj %1 k)
                                  %1)) 
                             [] (seq (:items lru)))
        queue (:queue lru)
        new-queue (loop [q queue
                         acc []]
                    (if (empty? q)
                      acc
                      (let [k (first q)]
                        (if (u/in? k expired-keys)
                          (recur (rest q) acc)
                          (recur (rest q) (conj acc k))))))
        new-items (reduce #(dissoc %1 %2) (:items lru) expired-keys)]
    (assoc lru
           :queue new-queue
           :items new-items)))

(defn create-lru-cache 
  ([name size expired-millis]
   (let [lru (atom {:size size
                    :queue []
                    :items {} ;; { key -> {:at-time xxx, :data xxx} }
                    :expired-millis expired-millis
                    :state :ok ;;:stop
                    })]
     (go-loop []
              (when (= (:state @lru) :ok)
                ;; println出来，因为会有太多的embedding
                ;;(println "LRU<" name "> cleaning..")
                (swap! lru lru-remove-expired-items!)
                #_(println "LRU<" name "> cleaned: " )
                (<! (timeout 10000))
                (recur)))
     lru))
  ([size expired-millis]
   (create-lru-cache "?" size expired-millis)))

(defn- add-and-remove-queue [queue delete-key new-key]
  (loop [queue queue
         acc [new-key]] 
    (if (empty? queue)
      acc
      (let [old-key (first queue)
            new-acc (if (= old-key delete-key)
                      acc
                      (conj acc old-key))]
        (recur (rest queue) new-acc)))))

(defn lru-set [lru key value]
  (swap! lru #(let [lru %1
                    key %2
                    value %3]
                (cond 
                  ;; 有缓存，直接更新缓存
                  (get (:items lru) key)
                  (assoc lru
                         :queue (add-and-remove-queue (:queue lru) key key)
                         :items (let [item (get (:items lru) key)
                                      new-item (assoc item
                                                      :at-time (System/currentTimeMillis)
                                                      :data value)]
                                  (assoc (:items lru) key new-item)))
                  ;; 没有缓存，没超出size，直接存
                  (< (count (:queue lru)) (:size lru))
                  (assoc lru
                         :queue (vec (concat [key] (:queue lru)))
                         :items (assoc (:items lru)
                                       key {:at-time (System/currentTimeMillis)
                                            :data value}))

                  ;; 没有缓存，size超出，删除最后没访问的
                  :else
                  (let [last-key (last (:queue lru))
                        new-queue (add-and-remove-queue (:queue lru) last-key key)
                        new-items (-> (:items lru)
                                      (dissoc last-key)
                                      (assoc key {:at-time (System/currentTimeMillis)
                                                  :data value}))]
                    (assoc lru
                           :state :ok
                           :queue new-queue
                           :items new-items))))
         key value))

(defn lru-get [lru key]
  (let [item (get (:items @lru) key)]
    (when item
      (swap! lru #(let [lru %1
                        key %2
                        new-item (assoc item :at-time (System/currentTimeMillis))]
                    (assoc lru 
                           :queue (add-and-remove-queue (:queue lru) key key)
                           :items (assoc (:items lru)
                                         key new-item))) key))
    (:data item)))
