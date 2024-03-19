(ns functor-api.service.oauth
  (:require [clojure.string :as strings]
            [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [functor-api.dict :as dict]
            [functor-api.service.payment :as payment]))

(defn add-oauth-user [oauth-key platform region]
  (u/log-todo "添加oauth用户信息"))
