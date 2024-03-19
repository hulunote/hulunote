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
     ;; 首页：登录，主页，价格，下载
     ["/login" :login]
     ["/main" :main]
     ["/price" :price]
     ["/download" :download]]
    {:compile rc/compile-request-coercers
     :data {:coercion rsc/coercion}}))

(comment
  (switch-router! "/")

  (switch-router! "/login")

  (switch-router! "/app/help/diaries")
  (switch-router! "/app/xxxx/diaries")
  (switch-router! "/app/yyyy/diaries")
  (switch-router! "/app/zzzz/diaries")
  (switch-router! "/app/1111/diaries")
  (switch-router! "/app/22222/diaries")

  )
(defn switch-router! [loc]
  (set! (.-hash js/window.location) (str "#" loc)))

(defn is-route-in-login []
  (= (.-hash js/window.location)
    "#/login"))

(defonce router-uuid (d/squuid))

(defn navigate [route route-params]
  (d/transact! db/dsdb [{:route/id router-uuid ;;:db/id (d/tempid :db.part/user)
                         :route/name {:route-name route :params route-params}}]))
