(ns hulunote.storage
  (:require [reagent.core :as reagent]
            [alandipert.storage-atom :refer [local-storage]]
            [datascript.transit :as dt]
            [cognitect.transit :as t]))

(def jwt-auth (local-storage (atom {}) :jwt-auth))

;; (async-save-db @db/dsdb)
(defn async-save-db [dsdb]
  (info "start write db " (js/Date.))
  (lc/set-item "dsdb"
    (dt/write-transit-str dsdb)
    (fn [v]
      (info "end write db" (js/Date.)))))

