(ns functor-api.core
  (:require [functor-api.nrepl :as nrepl]
            [functor-api.config :refer [env]]
            [functor-api.util :as u]
            [functor-api.state.server :as server]
            [functor-api.state.database :as database]
            [functor-api.state.schedule :as schedule]
            [mount.core :as mount])
  (:gen-class))

(mount/defstate ^{:on-reload :noop} repl-server
  :start
  (nrepl/start {:port 8888})
  :stop
  (when repl-server
    (nrepl/stop repl-server)))

(defn -main [& _args]
  (u/log-info "Server is starting...")

  ;; async的线程池加到10
  (System/setProperty "clojure.core.async.pool-size" "10")
  ;; 环境配置
  (mount/start #'env)
  ;; 数据源
  (mount/start #'database/*main-datasource*)
  ;; http服务
  (mount/start #'server/server)
  ;; nrepl服务
  (mount/start #'repl-server)
  ;; 定时调度任务
  (mount/start #'schedule/schedule-jobs))

(comment
  (-main))
