(ns hulunote.router
  (:require [reitit.core :as r]
            [reitit.frontend :as rfi]
            [reitit.coercion :as rc]
            [schema.core :as s]
            [reitit.coercion.schema :as rsc]
            [reitit.frontend.easy :as rfe]
            [datascript.core :as d]
            [hulunote.db :as db]))

(def router
  (r/router
    [;; 笔记库列表页面
     ["/" :database]
     ;; 笔记库的列表，详情，图页面
     ["/app/:database/" :home]
     ["/app/:database/show/:page-id/:nav-id" :show]
     ["/app/:database/graph" :graph]
     ["/app/:database/diaries" :diaries]
     ["/app/:database/notes" :all-notes]
     ["/app/:database/note/:note-id" :single-note]
     ;; 首页：登录，主页，价格，下载
     ["/login" :login]
     ["/main" :main]
     ["/price" :price]
     ["/download" :download]]
    {:compile rc/compile-request-coercers
     :data {:coercion rsc/coercion}}))

(defn switch-router! [loc]
  (set! (.-hash js/window.location) (str "#" loc)))

(defn go-to-note! 
  "Navigate to a specific note page"
  [database-name note-id]
  (switch-router! (str "/app/" database-name "/note/" note-id)))

(defn go-to-all-notes!
  "Navigate to all notes page"
  [database-name]
  (switch-router! (str "/app/" database-name "/notes")))

(defn go-to-diaries!
  "Navigate to diaries page"
  [database-name]
  (switch-router! (str "/app/" database-name "/diaries")))

(defn is-route-in-login []
  (= (.-hash js/window.location)
    "#/login"))

(defonce router-uuid (d/squuid))

(defn navigate [route route-params]
  (d/transact! db/dsdb [{:route/id router-uuid
                         :route/name {:route-name route :params route-params}}]))
