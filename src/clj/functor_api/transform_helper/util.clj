(ns functor-api.transform-helper.util
  (:require [functor-api.db.note :as note]
            [functor-api.db.nav :as nav]
            [functor-api.util :as u]))

(defn create-note-in-database!
  [db-uuid account-id note-title]
  (let [note-id (u/uuid)
        nav-id (u/uuid)
        result (note/create-new-note-core account-id db-uuid note-title note-id nav-id)]
    (if (:error result)
      (throw Exception. "Create note failed:" (:error result))
      [note-id nav-id])))

(defn create-note-nav!
  [account-id note-id parid content]
  (let [id (u/uuid)
        last-order (nav/get-parent-last-order parid)
        order (+ last-order 100)]
    (nav/create-new-nav-core account-id note-id id parid content order)
    id))
