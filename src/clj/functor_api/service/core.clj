(ns functor-api.service.core
  (:require [reitit.ring :as ring]
            [functor-api.middleware-new :as middleware] 
            [reitit.core :as r]
            [functor-api.service.login :as login]
            [functor-api.service.database :as database]
            [functor-api.service.notes :as notes]
            [functor-api.service.navs :as navs]
            [functor-api.service.hulubot :as hulubot]
            [functor-api.service.files :as files]
            [functor-api.service.huluseed :as huluseed]
            [functor-api.layout :as layout]))

(defn home-page [request]
  (layout/render request "public/html/hulunote.html" {}))

(def app
  (ring/ring-handler
    (ring/router
      [["/" {:get home-page}]
       ;; 登录注册
       login/apis
       ["/hulunote" {:middleware middleware/common-middlewares}
        ;; 笔记库操作
        database/apis
        ;; 笔记操作
        notes/apis
        ;; 笔记节点的操作
        navs/apis]
       ;; 文件上传下载
       files/apis
       ;; 机器人功能接口
       hulubot/apis
       ;; 葫芦籽接口
       huluseed/apis

       ["/*" (ring/create-resource-handler)]]
      {:conflicts (constantly nil)})
    (ring/create-default-handler)))
