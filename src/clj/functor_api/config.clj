(ns functor-api.config
  (:require [mount.core :refer [defstate]]))

(def functor-api-conf (atom {}))

(defn functor-api-load-conf []
  (reset! functor-api-conf ;; (load-config :file "config/config.clj") ;; 没有加载到:hulunote的key的内容
          (read-string
           (slurp "config/config.clj"))))

(defstate env
  :start
  (functor-api-load-conf))
