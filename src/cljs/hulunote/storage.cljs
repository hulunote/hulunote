(ns hulunote.storage
  (:require [reagent.core :as reagent]
            ["localforage" :as lcf]
            [alandipert.storage-atom :refer [local-storage]]
            [datascript.transit :as dt]
            [cognitect.transit :as t]))

(def jwt-auth (local-storage (atom {}) :jwt-auth))

(defn info [& args] (js/console.log args))


(defonce local-db (atom nil))

(defn get-item [k cb]
  (-> (.getItem @local-db k)
      (.then cb)))

(defn set-item [k v cb]
  (-> (.setItem @local-db k v)
      (.then cb)))

(defn item-keys [cb]
  (-> (.keys @local-db)
      (.then cb)))

(defn create-db
  [dbname]
  (let [db (lcf/createInstance (clj->js {:name dbname}))]
    (reset! local-db db)))

;; Save: (async-save-db @db/dsdb)  => Load: (get-item "dsdb" (fn [v] (reset-db! (dt/read-transit-str v)) ))
(defn async-save-db [dsdb]
  (info "start write db " (js/Date.))
  (set-item "dsdb"
    (dt/write-transit-str dsdb)
    (fn [v]
      (info "end write db" (js/Date.)))))

