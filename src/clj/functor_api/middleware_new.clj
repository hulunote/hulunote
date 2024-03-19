(ns functor-api.middleware-new
  (:require [buddy.sign.jwt :as jwt]
            [ring.middleware.gzip :as gzip]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [taoensso.timbre :refer [error debug info]]
            [ring.util.http-response :as resp]
            [ring.util.response :refer [redirect]]
            [ring.util.io :as ring-io]
            [functor-api.util :as u]
            [functor-api.config :as config]
            [clojure.walk :as walk]
            [functor-api.service.admin-states :as admin-states]
            [functor-api.dict :as dict]))

(defn auth->user
  "Translate auth data to user or hulunote format."
  [auth]
  (case (:role auth)
    "jarvis" {:jarvis {:jarvis/id (:id auth)}}
    "hulunote" {:hulunote {:hulunote/id (:id auth)
                           :hulunote/openid (:openid auth)}}
    "admin" {:admin {:admin/id (:id auth)}}
    nil))

(defn <-camel-case-coll [coll]
  (walk/postwalk
   (fn [v]
     (if (keyword? v)
       (keyword (u/<-camelCase (name v)))
       v))
   coll))

(defn ->camel-case-coll [coll]
  (walk/postwalk
   (fn [v]
     (if (keyword? v)
       (u/->camelCase (name v))
       v))
   coll))

(defn wrap-handler 
  "封装handler"
  [handler service-name] 
  (fn [request]
    (let [ctx (try
                (let [body-params (:params request)
                      origin-query-params (:query-params request)
                      region (keyword (get-in request [:headers "region"]))
                      params (reduce #(assoc %1 (keyword (first %2)) (second %2))
                                     body-params
                                     (seq origin-query-params))
                      new-ctx (-> request 
                                  :auth 
                                  auth->user
                                  (assoc :region region))]
                  (u/log-debug
                   "[" service-name "]"
                   "\nURI:" (:uri request) 
                   "\nPARAMS: " (if (:password params)
                                  (dissoc params :password)
                                  params))
                  (let [resp (handler new-ctx params)]
                    resp))
                (catch clojure.lang.ExceptionInfo ex
                  (let [msg (ex-message ex)]
                    (u/log-error msg)
                    {:error msg}))
                (catch Exception ex
                  (u/log-error ex)
                  ;; 新注册的用户直接进来,创建笔记库和note,就会出错,先暴力解决先 & ws的状态也是红色的 => TODO: 不需要刷新
                  (dict/get-dict-error :error-server)))]
      (if (:is-redirect* ctx)
        (redirect (:redirect-url* ctx))
        (resp/ok (dissoc ctx :request))))))

(defn wrap-stream-handler 
  "封装stream返回的handler"
  [handler service-name]
  (fn [request]
    (let [ctx (try
                (let [body-params (:params request)
                      origin-query-params (:query-params request)
                      region (keyword (get-in request [:headers "region"]))
                      params (reduce #(assoc %1 (keyword (first %2)) (second %2))
                                     body-params
                                     (seq origin-query-params))
                      new-ctx (-> request
                                  :auth
                                  auth->user
                                  (assoc :region region))]
                  (u/log-debug
                   "[" service-name "]"
                   "\nURI:" (:uri request)
                   "\nPARAMS: " (if (:password params)
                                  (dissoc params :password)
                                  params))
                  (let [resp (handler new-ctx params)]
                    resp))
                (catch clojure.lang.ExceptionInfo ex
                  (let [msg (ex-message ex)]
                    (u/log-error msg)
                    {:error msg}))
                (catch Exception ex
                  (u/log-error ex)
                  ;; 新注册的用户直接进来,创建笔记库和note,就会出错,先暴力解决先 & ws的状态也是红色的 => TODO: 不需要刷新
                  (dict/get-dict-error :error-server)))]
      (if (:stream ctx)
        (let [main-fn (get-in ctx [:stream :main-fn])
              content-type (get-in ctx [:stream :content-type])
              filename (get-in ctx [:stream :filename])
              headers (cond-> {}
                              content-type (assoc "Content-Type" content-type)
                              filename (assoc "Content-Disposition" (str "attachment; filename=\"" filename "\"")))]
          (-> (ring.util.response/response
               (ring-io/piped-input-stream main-fn))
              (assoc :headers headers)))
        (resp/ok (dissoc ctx :request))))))

(defn wrap-handler-auth-wxbot [handler bot-id]
  (fn [ctx params]
    (let [id (get-in ctx [:hulunote :hulunote/id])
          region (:region ctx)]
      (if-not (= id bot-id)
        (resp/unauthorized 
         (dict/get-dict-error :error-api-bot-unauth region))
        (handler ctx params)))))

(defn wrap-handler-auth-need [handler user-id]
  (fn [ctx params]
    (let [id (get-in ctx [:hulunote :hulunote/id])
          region (:region ctx)]
      (if-not (= id user-id)
        (resp/unauthorized 
         (dict/get-dict-error :error-api-designate-unauth region))
        (handler ctx params)))))

(defn token-verifier
  [token]
  (jwt/unsign token (:functor-api-jwt-key @config/functor-api-conf)))

(defn auth-token
  [handler]
  (fn [request]
    (let [token (get-in request [:headers "x-functor-api-token"])
          region (keyword (get-in request [:headers "region"]))
          auth (try
                 (token-verifier token)
                 (catch Throwable _))]
      (if auth
        (let [account-id (:id auth)]
          (if (contains? @admin-states/today-banned-user account-id)
            (resp/unauthorized 
             (dict/get-dict-error :error-auth-too-many region))
            (handler (assoc request :auth auth))))
        (resp/unauthorized 
         (dict/get-dict-error :error-unauthorized region))))))

(defn auth-token-if-exist
  "若有token则解析，若没有则跳过，不阻止"
  [handler]
  (fn [request]
    (let [token (get-in request [:headers "x-functor-api-token"])
          auth (try
                 (token-verifier token)
                 (catch Throwable _))]
      (if auth
        (handler (assoc request :auth auth))
        (handler (assoc request :auth {}))))))

(defn auth-token-from-query
  [handler]
  (fn [request]
    (let [token (-> request :query-params (get "x-functor-api-token"))
          auth (try
                 (token-verifier token)
                 (catch Throwable _))]
      (if auth
        (handler (assoc request :auth auth))
        (do
          (u/log-info (str "url query token:" token "验证失败"))
          (redirect "/"))))))

(def common-middlewares-without-auth
  [[wrap-multipart-params]
   [wrap-restful-format]
   [wrap-params]
   [wrap-keyword-params]
   [wrap-handler "API"]])

(def common-middlewares
  [[wrap-multipart-params]
   [wrap-restful-format] 
   [wrap-params]
   [wrap-keyword-params]
   [auth-token]
   [wrap-stream-handler "API"]])
