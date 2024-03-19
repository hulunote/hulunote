(ns functor-api.state.server
  (:require [mount.core :as mount]
            [taoensso.timbre :as timbre]
            [functor-api.middleware-new :as middleware]
            [functor-api.util :as u]
            [functor-api.service.core :refer [app]]
            [ring.adapter.jetty9 :refer [run-jetty]]))

(mount/defstate server
  :start
  (do
    (u/log-info "Start api server at port: " 6689)
    (run-jetty #'app {:host "0.0.0.0"
                      :port 6689
                      :join? false
                      :ws-max-text-message-size 524288 ;; 512kb的最大消息数(默认为64kb)，再大说明输入会有异常的
                      :websockets {}}))
  :stop
  (.stop server))
