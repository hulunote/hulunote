(ns functor-api.service.login
  (:require [clojure.string :as strings]
            [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]
            [functor-api.dict :as dict] 
            [functor-api.middleware-new :as middleware]
            [buddy.hashers :as hashers]
            [clojure.core.async :as async]
            [clj-http.client :as client]

            [functor-api.db.database :as database]
            [functor-api.service.huluseed :as huluseed]
            [functor-api.service.oauth :as oauth]
            [functor-api.service.hulubot :as hulubot]
            [cheshire.core :as json]))

;; 记录保存注册、登录时的email验证码
(def map-email-and-ack (atom {}))

;; TODO: 验证email和验证码
(defn- verify-email-ack-number [email ack-number]
  (= (get @map-email-and-ack email) 
     ack-number))

(defn- verify-email [email]
  (and (some? email)
       (strings/includes? email "@")))

(defn- check-username-email-exists [username email]
  (let [exists? (-> (sql/select :id)
                    (sql/from :accounts)
                    (sql/where [:or
                                [:= :username username]
                                [:= :mail email]])
                    (u/execute-one!))]
    (nil? exists?)))

(defn- add-invitation-record [account-id invite-account-id]
  (-> (sql/insert-into :invitation-codes)
      (sql/values [{:invite-account invite-account-id
                    :invitee-account account-id}])
      (u/execute-one!)))

(defn rand-int-6-byte []
  (str  (+ (* 100000 (+ 1 (rand-int 9)))
           (rand-int 99999))))

(defn send-email-ack
  "发送邮箱验证码"
  [{:keys [region] :as ctx}
   {:keys [email]}]
  (cond
    (false? (verify-email email))
    (dict/get-dict-error :error-missing-email region)
    
    (some? (get @map-email-and-ack email))
    (dict/get-dict-error :error-email-ack-already-exists region)
    
    :else
    (try
      (let [rand-6-num (rand-int-6-byte)
            text (str "Hi, this is hulunote ack code for verification:\n"
                      "\t" rand-6-num "\n"
                      "Have fun with your note~")
            resp (client/post "https://api.mailgun.net/v3/mg.hulunote.io/messages"
                              {:form-params {:from "Hulunote.io no-reply@mg.hulunote.io"
                                             :to email
                                             :subject "Hulunote verification"
                                             :text text}
                               :basic-auth ["api" "6eb96b32f3c8f274e7ff5141b480a1fc-913a5827-2816e8ee"]
                               :accept :json})
            body (json/parse-string (:body resp))]
        (u/log-info body)
        (async/go
          (swap! map-email-and-ack assoc email (str rand-6-num))
          (async/<! (async/timeout 300000))
          (swap! map-email-and-ack dissoc email))
        {:success true})
      (catch Exception ex
        (u/log-error ex)
        (dict/get-dict-error :error-server region)))))

(comment

  (-> (sql/insert-into :accounts)
    (sql/values [{:username "stevechan"
                  :nickname "stevechan"
                  :password (hashers/derive "123456")
                  :mail "chanshunli@gmail.com"
                  :info ""
                  :invitation-code (u/gen-reg-code)
                  :cell-number (u/uuid)
                  :oauth-key "aaaa"           ;; oauth-key
                  :need-update-password false ;; (boolean need-update-password)
                  }])
    (u/returning :*)
    (u/execute-one!))
  
  )
(defn web-sign-up-and-create-database
  "web端注册"
  [{:keys [region] :as ctx}
   {:keys [username password email ack-number
           ;; 邀请码相关
           invitation-code        ;; 普通邀请码
           ot-invitation-code     ;; 笔记OT邀请码
           db-invitation-code     ;; 笔记库邀请码
           db-invitation-password ;; 笔记库邀请密码
           ;; oauth相关 ;; 记录注册时的平台
           oauth-key platform
           ;; 绑定相关，由绑定的map去决定是哪里的绑定
           binding-code binding-platform
           ;; 是否需要改密码(放在这个参数，有可能可以复用)
           need-update-password]}]
  (try
    (cond
      (false? (verify-email email))
      (dict/get-dict-error :error-missing-email)

      (false? (verify-email-ack-number email ack-number))
      (dict/get-dict-error :error-ack-number region)

      (false? (check-username-email-exists username email))
      (dict/get-dict-error :error-username-email-exists region)

      :else
      (let [username (if username username email)
            {:accounts/keys [id] :as account} (-> (sql/insert-into :accounts)
                                                (sql/values [{:username username
                                                              :nickname username
                                                              :password (hashers/derive password)
                                                              :mail email
                                                              :info platform
                                                              :invitation-code (u/gen-reg-code)
                                                              :cell-number (u/uuid)
                                                              :oauth-key oauth-key
                                                              :need-update-password (boolean need-update-password)}])
                                                (u/returning :*)
                                                (u/execute-one!))
            account-id id
            invite-account (when invitation-code
                             (-> (sql/select :*)
                               (sql/from :accounts)
                               (sql/where [:= :invitation-code invitation-code])
                               (u/execute-one!)))
            database-name (str username "-" (rand-int 9999))]
        ;; 邀请注册的流水记录
        (when invite-account
          (add-invitation-record account-id (:accounts/id invite-account))
          (huluseed/add-hulunote-seed (:accounts/id invite-account)
            10 (dict/get-dict-string :reward-huluseed-by-invition region)
            :extra-text (str "code:" invitation-code)))
        ;; 处理OT邀请来的注册
        (when ot-invitation-code
          (when-let [inviter-id (-> (sql/select :inviter-id)
                                  (sql/from :ot-note-invites)
                                  (sql/where [:and
                                              [:= :invite-code ot-invitation-code]
                                              [:= :is-active false]
                                              [:= :is-delete false]])
                                  (u/execute-one!)
                                  (:ot-note-invites/inviter-id))]
            ;; 激活这个ot邀请码
            (-> (sql/update :ot-note-invites)
              (sql/sset {:invitee-id account-id
                         :is-active true
                         :updated-at (sqlh/call :now)})
              (sql/where [:= :invite-code ot-invitation-code])
              (u/execute-one!))
            (huluseed/add-hulunote-seed inviter-id 10 (dict/get-dict-string :reward-huluseed-by-ot-invition region)
              :extra-text (str "code: " ot-invitation-code))))
        ;; 处理笔记库邀请来的注册
        (when db-invitation-code
          (database/add-user-permission-by-invitaion account-id db-invitation-code db-invitation-password region))

        ;; 处理绑定相关
        (when (and oauth-key platform)
          (oauth/add-oauth-user oauth-key platform region))
        
        ;; 处理机器人绑定
        (when (and binding-code binding-platform)
          (hulubot/add-bot-binding account-id binding-code binding-platform region))

        ;; 创建默认笔记库
        (database/create-default-database account-id database-name region)

        ;; 返回token等信息
        (assoc ctx
          :database database-name
          :hulunote (dissoc account :accounts/password)
          :token (u/make-user-jwt-token account-id))))

    (catch Exception ex
      (u/log-error ex)
      (dict/get-dict-error :error-server region))))

(defn web-login
  "web端登录"
  [{:keys [region] :as ctx}
   {:keys [username email password
           ;; 绑定相关，由绑定的map去决定是哪里的绑定
           binding-code binding-platform]}]
  (cond
    (and (or username email)
         (nil? password))
    (dict/get-dict-error :error-missing-password region)
    
    (or username email)
    (let [account (-> (sql/select :*)
                      (sql/from :accounts)
                      (sql/where [:or
                                  [:= :username username]
                                  [:= :mail email]])
                      (u/execute-one!))]
      (cond
        (empty? account)
        (dict/get-dict-error :error-username-or-email-wrong region)

        (not (u/check-password password (:accounts/password account)))
        (dict/get-dict-error :error-password region)
        
        :else
        (do
          ;; 处理机器人绑定
          (when (and binding-code binding-platform)
            (hulubot/add-bot-binding (:accounts/id account) binding-code binding-platform region))
          (assoc ctx
                 :hulunote (dissoc account :accounts/password)
                 :token (u/make-user-jwt-token (:accounts/id account))))))
    
    :else
    (dict/get-dict-error :error-missing-username-or-email region)))

(def apis
  ["/login" {:middleware middleware/common-middlewares-without-auth}
   ["/send-ack-msg" {:post #'send-email-ack}]
   ["/web-login" {:post #'web-login}]
   ["/web-signup" {:post #'web-sign-up-and-create-database}]])
