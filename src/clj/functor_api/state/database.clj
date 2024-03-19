(ns functor-api.state.database
  (:require [functor-api.config :as config]
            [next.jdbc :as jdbc]
            [mount.core :as mount]
            [taoensso.timbre :as log]))

(defn start-datasource []
  (log/info "Start datasource.")
  (let [db-spec (-> @config/functor-api-conf :database :main)
        ds (jdbc/get-datasource db-spec)]
    ;; ensure datasource is avaiable
    (jdbc/execute! ds ["select 1"])
    ds))

(defn stop-datasource [ds])

(mount/defstate ^:dynamic *main-datasource*
  :start
  (start-datasource)
  :stop
  (stop-datasource *main-datasource*))

(comment
  (mount/start #'*main-datasource*)
  (mount/stop #'*main-datasource*))
