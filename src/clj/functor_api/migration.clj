(ns functor-api.migration
  (:require [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]
            [functor-api.config :as config]
            [mount.core :as mount]))

(mount/defstate mg
  :start
  {:datastore (jdbc/sql-database (-> @config/functor-api-conf :database :main))
   :migrations (jdbc/load-directory "migrations")}
  :stop
  nil)

;;; Migration operations
(comment
  (mount/stop #'mg)
  (mount/start #'mg)
  (repl/migrate mg)
  (repl/rollback mg))
