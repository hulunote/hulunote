(ns functor-api.transform-helper.note-to-markdown
  (:require [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [clojure.string :as strings]))

(declare patch-content
         navs->markdown)

(defn- get-navs-by-parid [navs note-id parid]
  (->> navs
       (filter #(= (:hulunote-navs/parid %) parid))
       (map #(do [(str (:hulunote-navs/id %))
                  (:hulunote-navs/content %)
                  (:hulunote-navs/same-deep-order %)]))
       (sort-by #(nth % 2))))

(defn- patch-ref [content ref-str]
  (let [ref-id (subs ref-str 2 38)
        next-ref-content (-> (sql/select :content)
                             (sql/from :hulunote-navs)
                             (sql/where [:and
                                         [:= :id ref-id]
                                         [:= :is-delete false]])
                             (u/execute-one!)
                             (:hulunote-navs/content))
        patched (patch-content next-ref-content)]
    (strings/replace content ref-str (str " " patched))))

(defn patch-content [content]
  (let [get-ref (re-find #"\(\([0-9A-Za-z|-]{36}\)\)" content)]
    (if get-ref
      (patch-ref content get-ref)
      content)))

(defn- nav-children->markdown [note-navs depth note-id parid]
  (let [children (get-navs-by-parid note-navs note-id parid)
        children-mds (navs->markdown note-navs depth note-id children "")]
    children-mds))

(defn- navs->markdown [note-navs depth note-id navs acc]
  (if (empty? navs)
    acc
    (let [nav (first navs)
          id (first nav)
          content (second nav)
          patched-content (patch-content content)
          prefix-t (strings/join
                    (repeat (dec depth) "\t"))
          markdown-row (str prefix-t "- " patched-content "\n")
          children-markdown (nav-children->markdown note-navs (inc depth) note-id id)
          new-acc (str acc markdown-row "\n" children-markdown)]
      (recur note-navs depth note-id (rest navs) new-acc))))

(defn note->markdown
  "笔记转成完成的文章markdown"
  [note-id]
  (let [note (-> (sql/select :title :root-nav-id)
                 (sql/from :hulunote-notes)
                 (sql/where [:= :id note-id])
                 (u/execute-one!))
        note-navs (-> (sql/select :*)
                      (sql/from :hulunote-navs)
                      (sql/where [:and
                                  [:= :note-id note-id]
                                  [:= :is-delete false]])
                      (u/execute!))
        title (:hulunote-notes/title note)
        root-id (:hulunote-notes/root-nav-id note)
        md-title (str "# " title "\n")
        db-navs (get-navs-by-parid note-navs note-id root-id)
        children-mds (navs->markdown note-navs 1 note-id db-navs "")]
    (str md-title "\n"
         children-mds)))
