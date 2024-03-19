(ns functor-api.service.hulubot
  (:require [clojure.string :as strings]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [functor-api.util :as u]
            [honeysql.helpers :as sql]
            [honeysql-postgres.helpers :as pg-sqlh]
            [honeysql.core :as sqlh]
            [cheshire.core :as json]
            [functor-api.dict :as dict]
            [functor-api.service.payment :as payment]
            [functor-api.service.permissions :as permission]
            [functor-api.db.database :as database]
            [functor-api.db.note :as note]
            [functor-api.db.nav :as nav]
            [functor-api.config :as config]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [functor-api.middleware-new :as middleware]
            [functor-api.transform-helper.core :as transform-helper]
            [clj-http.client :as client]
            [functor-api.service.huluseed :as huluseed]
            [functor-api.tools.auto-lru :as auto-lru]
            [functor-api.service.openai :as openai]))

;; 请求bot接口的固定用户id
(def bot-host-id 2)

;; 绑定的key，不能直接返回绑定的key的链接，安全问题
;; {"binding-code": {:key "bot-uuid" :at time}}}
(def binding-code-map (atom {}))

(defn send-message-to-bot
  ([platform bot-uuid message] (send-message-to-bot platform bot-uuid message nil))
  ([platform bot-uuid message mention]
   (try
     (let [url (cond
              ;; whatsapp的机器人
                 (= platform "whatsapp") "http://localhost:6680/send-message"
              ;; 无意义的占位的，以便后续更改新增
                 (= platform "telegram") "http://localhost:6681/telegram/send-message"
              ;; 不支持的类型，最后会报错返回
                 :else "unsupported platform")
           body {:id bot-uuid
                 :text message
                 :mention mention}
           json (json/generate-string body)
           resp (client/post url {:body json :content-type :json :accept :json})]
       (:body resp))
     (catch Exception ex
       (u/log-error ex)
       (dict/get-dict-error :error-server)))))

(defn check-and-clear-binding-code-map
  "检查并清理绑定码"
  []
  (u/log-info "检查并清理绑定码...")
  (swap! binding-code-map
         (fn [m]
           (let [now (System/currentTimeMillis)]
             (reduce #(let [acc %1
                            [key item] %2
                            at (:at item)]
                      ;; 30分钟过期时间
                        (if (< (- now at)
                               (* 30 60 1000))
                          (assoc acc key item)
                          acc)) {} (seq m))))))

(defn add-bot-binding 
  "添加机器人绑定"
  [account-id binding-code binding-platform region] 
  (let [bot-uuid (:key (get @binding-code-map binding-code))
        exists (when bot-uuid
                 (-> (sql/select :*)
                     (sql/from :hulunote-bot-binding)
                     (sql/where [:and
                                 [:= :account-id account-id]
                                 [:= :platform binding-platform]])
                     (u/execute-one!)))]
    (cond
      (nil? bot-uuid)
      (dict/get-dict-error :error-binding-code-expired region)

      (empty? exists)
      (let [value {:account-id account-id
                   :platform binding-platform
                   :bot-uuid bot-uuid}]
        (-> (sql/insert-into :hulunote-bot-binding)
            (sql/values [value])
            (u/execute-one!))
        {:success true})
      
      (:hulunote-bot-binding/is-delete exists)
      (-> (sql/update :hulunote-bot-binding)
          (sql/sset {:bot-uuid bot-uuid
                     :updated-at (sqlh/call :now)
                     :is-delete false})
          (sql/where [:= :id (:hulunote-bot-binding/id exists)])
          (u/execute-one!)
          (do {:success true}))
      
      :else
      {:success true
       :message (dict/get-dict-error :warn-bot-already-binded region)})))

(defn find-account-by-bot-uuid 
  "通过bot-uuid查找用户"
  [bot-uuid platform]
  (-> (sql/select :account-id)
      (sql/from :hulunote-bot-binding)
      (sql/where [:and 
                  [:= :bot-uuid bot-uuid]
                  [:= :platform platform]
                  [:= :is-delete false]])
      (u/execute-one!)
      (:hulunote-bot-binding/account-id)))

(defn get-or-create-bot-setting 
  "获取或创建机器人的配置信息"
  [type key platform] 
  (let [type (name type)
        res (-> (sql/select :*)
                (sql/from :hulunote-bot-user-setting)
                (sql/where [:and
                            [:= :key-type type]
                            [:= :key-id key]
                            [:= :platform platform]])
                (u/execute-one!))]
    (if-not res
      (do
        (-> (sql/insert-into :hulunote-bot-user-setting)
            (sql/values [{:key-type type
                          :key-id key
                          :platform platform}])
            (u/execute-one!))
        {})
      (let [setting-json (:hulunote-bot-user-setting/setting-context res)]
        (json/parse-string setting-json false)))))

;; 更新机器人用户配置
(defn- update-bot-setting [type key platform new-setting]
  (let [type (name type)
        setting-json (json/generate-string new-setting)
        set-map (if (some? (get new-setting "on-schedule"))
                  {:setting-context setting-json
                   :on_schedule (get new-setting "on-schedule")}
                  {:setting-context setting-json})]
    (-> (sql/update :hulunote-bot-user-setting)
        (sql/sset set-map)
        (sql/where [:and
                    [:= :key-type type]
                    [:= :key-id key]
                    [:= :platform platform]])
        (u/execute-one!))))

(declare *account-id *database-id *note-id *root-nav-id *binder-id)
(defmacro in-binded [bot-uuid platform region & body]
  `(if-let [~'*account-id (find-account-by-bot-uuid ~bot-uuid ~platform)]
     (let [result# (do ~@body)
           setting# (get-or-create-bot-setting :single ~bot-uuid ~platform)]
       (assoc result# :bot-setting setting#))
     (dict/get-dict-error :error-bot-unbind ~region)))

(defn- find-or-create-daynote-by-account! [account-id]
  (when-let [database-id (-> (sql/select :id)
                             (sql/from :hulunote-databases)
                             (sql/where [:and
                                         [:= :account-id account-id]
                                         [:= :is-default true]
                                         [:= :is-delete false]])
                             (u/execute-one!)
                             (:hulunote-databases/id)
                             (str))]
    (let [note (note/get-or-create-daynote account-id database-id)]
      [(str database-id) 
       (str (:hulunote-notes/id note))
       (:hulunote-notes/root-nav-id note)])))

(defmacro in-daily-note [bot-uuid platform region & body]
  `(if-let [~'*account-id (find-account-by-bot-uuid ~bot-uuid ~platform)]
     (if-let [db-note-res# (find-or-create-daynote-by-account! ~'*account-id)]
       (let [[~'*database-id ~'*note-id ~'*root-nav-id] db-note-res#
             result# (do ~@body)
             setting# (get-or-create-bot-setting :single ~bot-uuid ~platform)]
         (assoc result# :bot-setting setting#))
       (dict/get-dict-error :error-no-default-database ~region))
     (dict/get-dict-error :error-bot-unbind ~region)))

(defn get-bot-token-api
  "获取机器人的请求的token"
  [{:keys [region]}
   {:keys [bot-key]}]
  (let [key (get @config/functor-api-conf :bot-key)]
    (if (= key bot-key)
      {:success true
       :token (u/make-jwt-token-forever {:role :hulunote :id bot-host-id})}
      (dict/get-dict-error :error-permission-deny region))))

(defn get-bot-bind-url-api
  "用户获取绑定链接的接口
   暂定为 https://www.hulunote.io/#/login?platform={platform}&code={binding-code}"
  [{:keys [region]}
   {:keys [bot-uuid binding-platform]}]
  (let [item {:key bot-uuid :at (System/currentTimeMillis)}
        binding-code (u/rand-string 10) #_(u/uuid)
        url (str "https://www.hulunote.io/#/login?platform=" binding-platform "&code=" binding-code )]
    ;; 存kv到map中
    (swap! binding-code-map assoc binding-code item)
    {:success true
     :url url}))

(defn unbind-bot
  "取消机器人绑定"
  [{:keys [region]}
   {:keys [bot-uuid platform]}]
  (in-binded bot-uuid platform region
             (-> (sql/update :hulunote-bot-binding)
                 (sql/sset {:is-delete true
                            :updated-at (sqlh/call :now)})
                 (sql/where [:and
                             [:= :bot-uuid bot-uuid]
                             [:= :platform platform]])
                 (u/execute-one!))
             {:success true}))

;; 查找或创建引用的节点
(defn- get-or-create-quote-nav [account-id database-id note-id root-nav-id quote-id quote-text]
  (let [nav (-> (sql/select :*)
                (sql/from :hulunote-navs)
                (sql/where [:and
                            [:= :account-id account-id]
                            [:= :note-id note-id] 
                            [:= :extra-id quote-id]
                            [:= :is-delete false]])
                (u/execute-one!))]
    (if (empty? nav)
      ;; 没有quote节点，需要创建
      (nav/create-new-nav-auto-order account-id database-id note-id (u/uuid) root-nav-id quote-text)
      ;; 有直接返回
      nav)))

;; 缓存短时间写笔记的level信息
(def short-time-level-cache
  (auto-lru/create-lru-cache "short-time-level-cache" 64 60000))

(defn- update-short-time-level-item [bot-uuid current-nav-id platform]
  (let [now-ts (java.sql.Timestamp. (System/currentTimeMillis))]
    (-> (sql/insert-into :hulunote-bot-chat-level)
        (sql/values [ {:id bot-uuid
                       :nav-id current-nav-id
                       :platform platform
                       :noted-at now-ts} ])
        (pg-sqlh/upsert (-> (pg-sqlh/on-conflict :id)
                            (pg-sqlh/do-update-set :nav-id :platform :noted-at)))
        (u/execute-one! ))
    (auto-lru/lru-set short-time-level-cache bot-uuid 
                      #:hulunote-bot-chat-level{:id bot-uuid
                                                :nav-id current-nav-id
                                                :platform platform
                                                :noted-at now-ts})))

(defn- get-or-set-short-time-level-id
  [bot-uuid current-nav-id platform]
  (let [from-cache (auto-lru/lru-get short-time-level-cache bot-uuid)
        from-db (when-not from-cache
                  (-> (sql/select :*)
                      (sql/from :hulunote-bot-chat-level)
                      (sql/where [:and
                                  [:= :id bot-uuid]
                                  [:= :platform platform]])
                      (u/execute-one! )))
        {:hulunote-bot-chat-level/keys [nav-id noted-at] :as saved-item} 
        (cond 
          from-cache from-cache
          from-db from-db
          :eles nil)]
    (if-not saved-item
      ;; 保存这个item，然后返回nil
      (do
        (update-short-time-level-item bot-uuid current-nav-id platform)
        nil)
      ;; 比较上一个item，若现在时间在10s内，则返回nav-id；若不在，则返回nil，并更新db和cache
      (let [now (System/currentTimeMillis)
            diff (- now (.getTime noted-at))]
        (if (<= diff 10000)
          nav-id
          (do
            (update-short-time-level-item bot-uuid current-nav-id platform)
            nil))))))

(defn single-can-use-vip-feature
  "检查是否能使用vip功能"
  [{:keys [region]}
   {:keys [bot-uuid platform feature]}]
  (in-binded bot-uuid platform region
             (if (payment/get-or-take-vip-trail-chance *account-id feature)
               {:success true}
               (dict/get-dict-error :error-feature-need-vip region))))

(defn single-daynote-record-api
  "单独聊天的记录笔记接口"
  [{:keys [region]}
   {:keys [bot-uuid platform parid nav-id extra-id
           content quote-id quote-text]}]
  (in-daily-note bot-uuid platform region
                 (try
                   (let [quote-nav-id (when (and quote-text quote-id)
                                        (:hulunote-navs/id
                                         (get-or-create-quote-nav *account-id
                                                                  *database-id
                                                                  *note-id
                                                                  *root-nav-id quote-id quote-text)))
                         nav-id (or nav-id (u/uuid))
                         level-id (when-not quote-nav-id 
                                    (get-or-set-short-time-level-id bot-uuid nav-id platform) )]
                     (nav/create-new-nav-auto-order-extra *account-id *database-id *note-id nav-id
                                                          (or parid quote-nav-id level-id *root-nav-id) content extra-id)
                     {:success true})
                   (catch Exception ex
                     (u/log-error ex)
                     (dict/get-dict-error :error-server region)))))

(defn single-get-daynote-markdown-api
  "单独聊天的获取笔记markdown"
  [{:keys [region]}
   {:keys [bot-uuid platform]}]
  (in-daily-note bot-uuid platform region
                 (try
                   (let [note-markdown (transform-helper/note->markdown *note-id)]
                     {:success true
                      :note note-markdown})
                   (catch Exception ex
                     (u/log-error ex)
                     (dict/get-dict-error :error-server region)))))

(defn single-chatgpt-api
  "单独聊天的chatgpt"
  [{:keys [region]}
   {:keys [bot-uuid platform message]}]
  (in-binded bot-uuid platform region
             ;; TODO: 前期暂不需要屏蔽，后面需要判断添加使用次数
             (async/go
               (let [db-note-res (find-or-create-daynote-by-account! *account-id)
                     result (if db-note-res
                              (openai/chatgpt-request-write-note *account-id (second db-note-res) nil message region)
                              (openai/chatgpt-request-core message region))
                     reply (str "QA reply for: '" message "' :\n" (or (:result result) (:error result)))]
                 (send-message-to-bot platform bot-uuid reply)))
             {:success true
              :message (dict/get-dict-string :pending-for-requested region)}))

(defn single-get-or-update-setting
  "单独聊天设置或更新配置"
  [{:keys [region]}
   {:keys [bot-uuid platform setting-key setting-value]}]
  (in-binded bot-uuid platform region
             (let [setting (get-or-create-bot-setting :single bot-uuid platform)
                   new-setting (assoc setting setting-key setting-value)]
               (update-bot-setting :single bot-uuid platform new-setting)
               {:success true
                :setting new-setting})))

(defn single-remind-job
  "单独聊天添加提醒任务
  (由于面向外国服务，提醒任务是以服务器时间为标准，无法做到精确的时间
     这里均以 in x minutes/hours/days 为提醒时间)"
  [{:keys [region]}
   {:keys [bot-uuid platform in-millis remind-text]}]
  (in-binded bot-uuid platform region
             (let [now (System/currentTimeMillis)
                   remind-at (-> now
                                 (+ in-millis)
                                 (java.sql.Timestamp.)
                                 (u/flip-str-from-timestamp "yyyy-MM-dd HH:mm"))]
               (-> (sql/insert-into :hulunote-bot-notification)
                   (sql/values [{:account-id *account-id
                                 :bot-uuid bot-uuid
                                 :platform platform
                                 :remind-text remind-text
                                 :remind-at remind-at}])
                   (u/execute-one!))
               {:success true})))

(defn bind-group-and-create-database
  "群聊绑定并创建笔记库"
  [{:keys [region]}
   {:keys [bot-uuid group-uuid group-name platform]}]
  (in-binded bot-uuid platform region
             (try
               (u/with-transaction
                 (let [group-binding (-> (sql/select :*)
                                         (sql/from :hulunote-bot-group-binding)
                                         (sql/where [:= :group-uuid group-uuid])
                                         (u/execute-one!))
                       name (str "<" platform "-Group>" group-name)]
                   (cond
                    ;; 没有历史绑定信息，创建绑定
                     (empty? group-binding)
                     (let [{:keys [database-id]}
                           (database/create-bot-database *account-id name (str "Database note for " group-name " in " platform " group") platform region)]
                      ;; 添加绑定记录
                       (-> (sql/insert-into :hulunote-bot-group-binding)
                           (sql/values [{:account-id *account-id
                                         :platform platform
                                         :bot-uuid bot-uuid
                                         :group_uuid group-uuid
                                         :database-uuid database-id}])
                           (u/execute-one!))
                       {:success true})

                    ;; 有历史记录，已删除的，检查database，按需创建
                     (true? (:hulunote-bot-group-binding/is-delete group-binding))
                     (let [binded-database (-> (sql/select :*)
                                               (sql/from :hulunote-databases)
                                               (sql/where [:and
                                                           [:= :id (:hulunote-bot-group-binding/database-uuid group-binding)]
                                                           [:= :is-delete false]])
                                               (u/execute-one!))
                           binded-user-id (:hulunote-databases/account-id binded-database)]
                       (cond
                        ;; 没有笔记库用户信息，视为已删除了的笔记库
                         (nil? binded-user-id)
                         (let [{:keys [database-id]}
                               (database/create-bot-database *account-id name (str "Database note for " group-name " in " platform " group") platform region)]
                           (-> (sql/update :hulunote-bot-group-binding)
                               (sql/sset {:database-uuid database-id
                                          :is-delete false})
                               (sql/where [:= (:hulunote-bot-group-binding/id group-binding)])
                               (u/execute-one!))
                           {:success true})

                        ;; 同一个用户绑定的同一个，恢复删除就行
                         (and binded-user-id
                              (= binded-user-id *account-id))
                         (do (-> (sql/update :hulunote-bot-group-binding)
                                 (sql/sset {:is-delete false})
                                 (sql/where [:= :id (:hulunote-bot-group-binding/id group-binding)])
                                 (u/execute-one!))
                             {:success true})

                        ;; 绑定的笔记库和现在的用户不是同一个
                         :else
                         (let [{:keys [database-id]}
                               (database/create-bot-database *account-id name (str "Database note for " group-name " in " platform " group") platform region)]
                           (-> (sql/update :hulunote-bot-group-binding)
                               (sql/sset {:database-uuid database-id
                                          :bot-uuid bot-uuid
                                          :is-delete false})
                               (sql/where [:= :id (:hulunote-bot-group-binding/id group-binding)])
                               (u/execute-one!))
                           {:success true})))

                    ;; 有历史记录，且未删除状态，返回已绑定信息
                     :else
                     (dict/get-dict-error :error-bot-group-already-binded region))))
               (catch Exception ex
                 (u/log-error ex)
                 (dict/get-dict-error :error-server region)))))

(defn- find-group-bind-by-id [group-uuid platform]
  (let [binding (-> (sql/select :*)
                    (sql/from :hulunote-bot-group-binding)
                    (sql/where [:and
                                [:= :group-uuid group-uuid]
                                [:= :platform platform]
                                [:= :is-delete false]])
                    (u/execute-one!))]
    (if (empty? binding)
      nil
      (let [database-id (:hulunote-bot-group-binding/database-uuid binding)
            bot-uuid (:hulunote-bot-group-binding/bot-uuid binding)
            binder-id (find-account-by-bot-uuid bot-uuid platform)]
        [database-id binder-id]))))

(defmacro in-group-binded [group-uuid platform region & body]
  `(if-let [group-info# (find-group-bind-by-id ~group-uuid ~platform)]
     (let [[~'*database-id ~'*binder-id] group-info#
           result# (do ~@body)
           setting# (get-or-create-bot-setting :group ~group-uuid ~platform)]
       (assoc result# :bot-setting setting#))
     (dict/get-dict-error :error-bot-group-unbind ~region)))

(defmacro in-group-daily-note [group-uuid platform region & body]
  `(if-let [group-info# (find-group-bind-by-id ~group-uuid ~platform)]
     (let [[~'*database-id ~'*binder-id] group-info#
           note# (note/get-or-create-daynote ~'*binder-id ~'*database-id)
           ~'*note-id (str (:hulunote-notes/id note#))
           ~'*root-nav-id (:hulunote-notes/root-nav-id note#)
           result# (do ~@body)
           setting# (get-or-create-bot-setting :group ~group-uuid ~platform)]
       (assoc result# :bot-setting setting#))
     (dict/get-dict-error :error-bot-group-unbind ~region)))

(defn unbind-group
  "取消群绑定"
  [{:keys [region]}
   {:keys [bot-uuid group-uuid platform]}]
  (in-group-binded group-uuid platform region
                   (let [account-id (find-account-by-bot-uuid bot-uuid platform)]
                     (if (= account-id *binder-id)
                       (do (-> (sql/update :hulunote-bot-group-binding)
                               (sql/sset {:is-delete true
                                          :updated-at (sqlh/call :now)})
                               (sql/where [:and
                                           [:= :group-uuid group-uuid]
                                           [:= :platform platform]])
                               (u/execute-one!))
                           {:success true})
                       (dict/get-dict-error :error-bot-group-not-the-binder region)))))

(defn group-can-use-vip-feature
  "检查群是否能使用vip功能"
  [{:keys [region]}
   {:keys [group-uuid platform feature]}]
  (in-group-binded group-uuid platform region
                   (if (payment/get-or-take-vip-trail-chance *binder-id feature)
                     {:success true}
                     (dict/get-dict-error :error-feature-need-vip region))))

(defn group-daynote-record-api
  "群聊的记录笔记接口"
  [{:keys [region]}
   {:keys [group-uuid platform parid nav-id extra-id
           content quote-id quote-text]}]
  (in-group-daily-note group-uuid platform region
                       (try
                         (let [quote-nav-id (when (and quote-text quote-id)
                                              (:hulunote-navs/id
                                               (get-or-create-quote-nav *binder-id
                                                                        *database-id
                                                                        *note-id
                                                                        *root-nav-id quote-id quote-text)))
                               nav-id (or nav-id (u/uuid))
                               level-id (when-not quote-nav-id
                                          (get-or-set-short-time-level-id group-uuid nav-id platform ))]
                           (nav/create-new-nav-auto-order-extra *binder-id *database-id *note-id nav-id
                                                                (or parid quote-nav-id level-id *root-nav-id) content extra-id)
                           {:success true})
                         (catch Exception ex
                           (u/log-error ex)
                           (dict/get-dict-error :error-server region)))))

(defn group-get-daynote
  [{:keys [region]}
   {:keys [group-uuid platform]}]
  (in-group-daily-note group-uuid platform region
                       (try 
                         (let [note-markdown (transform-helper/note->markdown *note-id)]
                           {:success true
                            :note note-markdown})
                         (catch Exception ex
                           (u/log-error ex)
                           (dict/get-dict-error :error-server region)))))

(defn group-chatgpt-api
  "群聊天的chatgpt"
  [{:keys [region]}
   {:keys [group-uuid platform mention message]}]
  (in-group-binded group-uuid platform region
             ;; TODO: 前期暂不需要屏蔽，后面需要判断添加使用次数
             (async/go
               (let [group-info (find-group-bind-by-id group-uuid platform)
                     database-id (and group-info (first group-info))
                     note (when database-id
                            (note/get-or-create-daynote *binder-id database-id))
                     note-id (when note (str (:hulunote-notes/id note)))

                     result (if note-id
                              (openai/chatgpt-request-write-note *binder-id note-id nil message region)
                              (openai/chatgpt-request-core message region))
                     reply (str "QA reply for: '" message "' :\n" (or (:result result) (:error result)))]
                 (send-message-to-bot platform group-uuid reply mention)))
             {:success true
              :message (dict/get-dict-string :pending-for-requested region)}))

(defn group-update-setting
  "群聊的更新机器人配置"
  [{:keys [region]}
   {:keys [bot-uuid group-uuid platform setting-key setting-value need-vip]}]
  (in-group-binded group-uuid platform region
                   (let [user-id (find-account-by-bot-uuid bot-uuid platform)]
                     (if-not (= user-id *binder-id)
                       (dict/get-dict-error :error-bot-group-not-the-binder region)
                       (let [setting (get-or-create-bot-setting :group group-uuid platform)
                             new-setting (assoc setting setting-key setting-value)] 
                         (if (and (false? (payment/is-vip? *binder-id))
                                  need-vip)
                           (dict/get-dict-error :error-feature-need-vip region)
                           (do (update-bot-setting :group group-uuid platform new-setting)
                               {:success true :setting new-setting})))))))

(defn group-get-setting
  "获取群聊机器人配置"
  [{:keys [region]}
   {:keys [group-uuid platform]}]
  (in-group-binded group-uuid platform region
                   {:success true}))

(defn group-remind-job
  "单独聊天添加提醒任务
  (由于面向外国服务，提醒任务是以服务器时间为标准，无法做到精确的时间
     这里均以 in x minutes/hours/days 为提醒时间)"
  [{:keys [region]}
   {:keys [group-uuid platform in-millis remind-text]}]
  (in-group-binded group-uuid platform region
             (let [now (System/currentTimeMillis)
                   remind-at (-> now
                                 (+ in-millis)
                                 (java.sql.Timestamp.)
                                 (u/flip-str-from-timestamp "yyyy-MM-dd HH:mm"))]
               (-> (sql/insert-into :hulunote-bot-notification)
                   (sql/values [{:account-id *binder-id
                                 :bot-uuid group-uuid
                                 :platform platform
                                 :remind-text remind-text
                                 :remind-at remind-at
                                 :is-group true}])
                   (u/execute-one!))
               {:success true})))

(defn group-chat-fully-record
  "全量记录群聊天记录"
  [{:keys [region]} 
   {:keys [platform group-uuid group-name talker-name talker-uuid mentions quote content]}]
  (let [mention-strs (json/generate-string mentions)
        talker-uuid (if talker-uuid talker-uuid "<not binded user>")]
    (-> (sql/insert-into :hulunote-bot-group-record)
        (sql/values [{:id (str (java.util.UUID/randomUUID))
                      :platform platform
                      :group-uuid group-uuid
                      :group-name group-name
                      :talker-name talker-name
                      :talker-uuid talker-uuid
                      :mentions mention-strs
                      :quote quote
                      :content content}])
        (u/execute!))
    {:success true}))

(defn get-chat-record-from-pg [platform group-uuid day-str] 
  (let [st (u/timestamp-from-format "yyyy-MM-dd" day-str)
        et (u/timestamp-from-format "yyyy-MM-dd" day-str)
        et (-> (.getTime et)
               (+ (+ (* 3600000 23)
                     3500000))
               (java.sql.Timestamp.))
        db-res (-> (sql/select :*)
                   (sql/from :hulunote-bot-group-record)
                   (sql/where [:and
                               [:= :platform platform]
                               [:= :group-uuid group-uuid]
                               [:>= :created-at st]
                               [:<= :created-at et]])
                   (sql/order-by [:created-at :asc])
                   (u/execute! ))
        #_formatter #_(java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss")]
    (reduce #(let [acc %1
                   record %2
                   talker (:hulunote-bot-group-record/talker-name record)
                   content (:hulunote-bot-group-record/content record)]
               (str acc talker ":: " content " \n"))
            "" db-res)))

(defn group-summary-history-core [platform group-uuid database-id bot-uuid day-str cache-id region]
  (if-let [user-id (find-account-by-bot-uuid bot-uuid platform)]
    (let [bill-result (huluseed/remove-hulunote-seed user-id 3 (str "Summary group history [" platform "]"))]
      (if (:error bill-result)
        bill-result
        (do
          (async/go
            (let [command "summary chat record, and make a list without number prefix, don't reply anything else.\n"
                  content (get-chat-record-from-pg platform group-uuid day-str)
                  result (openai/chatgpt-request-summary-core content command region)
                  error (:error result)]
              (if error
                (send-message-to-bot platform group-uuid (str "Sorry, summary group chat record failed with errors, please try again."))
                (let [reply (str "Summary for this group at " day-str ": \n\n"
                                 (:result result))]
                  ;; save to cache
                  (if cache-id
                    (-> (sql/update :hulunote-bot-group-summary-cache)
                        (sql/sset {:summary (:result result)
                                   :updated-at (sqlh/call :now)})
                        (sql/where [:= :id cache-id])
                        (u/execute-one!))
                    (-> (sql/insert-into :hulunote-bot-group-summary-cache)
                        (sql/values [{:platform platform
                                      :group-uuid group-uuid
                                      :day-str day-str
                                      :summary (:result result)}])
                        (u/execute-one! )))
                  ;; send result to bot group
                  (send-message-to-bot platform group-uuid reply)))))
          {:success true
           :result "requested, please wait..."})))
    (dict/get-dict-error :error-bot-unbind region)))

(defn group-summary-history
  "calculate group chat history"
  [{:keys [region]}
   {:keys [platform group-uuid bot-uuid day-str]}]
  (in-group-binded group-uuid platform region
                   (let [day-str (or day-str (u/local-today-str ))
                         cache (-> (sql/select :*)
                                   (sql/from :hulunote-bot-group-summary-cache)
                                   (sql/where [:and
                                               [:= :platform platform]
                                               [:= :day-str day-str]])
                                   (u/execute-one! ))
                         cache-id (when cache (:hulunote-bot-group-summary-cache/id cache))
                         last-update-time (when cache
                                            (.getTime (:hulunote-bot-group-summary-cache/updated-at cache)))
                         now (System/currentTimeMillis)]
                     (if (or (nil? cache)
                             (> (- now last-update-time) (* 30 60 1000)))
                       (group-summary-history-core platform group-uuid *database-id bot-uuid day-str cache-id region)
                       (let [reply (str "request group summary less than half an hour, reply with last cache:\n\n"
                                        "Summary for this group at " day-str ": \n\n"
                                        (:hulunote-bot-group-summary-cache/summary cache))]
                         {:success true
                          :result reply}))
                     )))

(defn- workout-group-history-records [parid database-id note-id account-id records]
  (->> records
       ;; 过滤不含@的，不含#的
       (filter #(let [content (:hulunote-bot-group-record/content %)]
                  (not (or (strings/includes? content "@")
                           (strings/includes? content "#")
                           (strings/includes? content "<?xml")
                           (strings/includes? content "<msg>")))))
       (reduce #(let [content (:hulunote-bot-group-record/content %2)
                      talker-name (:hulunote-bot-group-record/talker-name %2)
                      nav-content (str talker-name ":: " content)
                      order (second %1)
                      nav {:id (u/uuid)
                           :parid parid
                           :same-deep-order (double order)
                           :account-id account-id
                           :note-id note-id
                           :database-id database-id
                           :content nav-content}]
                  [(conj (first %1) nav)
                   (+ order 100)])
               [[] 0])
       (first)))

(defn group-chat-history-tidy
  "通过时间获取群聊天记录的整理，再存到群笔记库"
  [{:keys [region]}
   {:keys [group-uuid platform]}]
  (in-group-daily-note group-uuid platform region
                       (let [setting (get-or-create-bot-setting :group group-uuid platform)
                             has-tidy-setting? (get setting "mode-vip-tidy")
                             is-binder-vip? (payment/is-vip? *binder-id)]
                         (cond
                           (not has-tidy-setting?)
                           (dict/get-dict-error :error-not-active-tidy-mode region)
                           
                           (not is-binder-vip?)
                           (dict/get-dict-error :error-feature-need-vip region)
                           
                           :else
                           (let [now (System/currentTimeMillis)
                                 last-10s (- now 11000)
                                 time (java.sql.Timestamp. last-10s)
                                 records (-> (sql/select :*)
                                             (sql/from :hulunote-bot-group-record)
                                             (sql/where [:and
                                                         [:= :group-uuid group-uuid]
                                                         [:= :platform platform]
                                                         [:>= :created-at time]])
                                             (sql/order-by [:created-at :asc])
                                             (u/execute!))
                                 
                                 head-content (str "**" (u/str-from-date "HH:mm:ss" (java.util.Date.)) "Chats: **")
                                 head-nav-id (u/uuid) 
                                 result (workout-group-history-records head-nav-id *database-id *note-id *binder-id records)]
                             (if (< (count result) 2)
                               (dict/get-dict-error :error-incompatible-result region)
                               (try
                                 ;; 先创建父节点
                                 (nav/create-new-nav-auto-order *binder-id *database-id *note-id
                                                                head-nav-id *root-nav-id head-content)
                                 ;; 再存子节点
                                 (-> (sql/insert-into :hulunote-navs)
                                     (sql/values result)
                                     (u/execute!))
                                 (doseq [i result] (println i))
                                 {:success true}
                                 (catch Exception ex
                                   (u/log-error ex)
                                   (dict/get-dict-error :error-server region)))))))))

(defn get-remind-jobs-at
  "按时间获取提醒任务的信息"
  [{:keys [region]}
   {:keys [platform remind-at]}]
  (let [data-records (-> (sql/select :*)
                         (sql/from :hulunote-bot-notification)
                         (sql/where [:and
                                     [:= :platform platform]
                                     [:= :remind-at remind-at]])
                         (u/execute! ))
        records (map #(do {:account-id (:hulunote-bot-notification/account-id %)
                           :bot-uuid (:hulunote-bot-notification/bot-uuid %)
                           :remind-at (:hulunote-bot-notification/remind-at %)
                           :remind-text (:hulunote-bot-notification/remind-text %)
                           :is-group (:hulunote-bot-notification/is-group %)})
                     data-records)]
    {:success true
     :records records}))

(def apis
  ["/bot"
   ["/init" {:post #'get-bot-token-api :middleware [[wrap-restful-format]
                                                    [wrap-params]
                                                    [wrap-keyword-params]
                                                    [middleware/wrap-handler "API"]]}]
   ["/v1" {:middleware [[wrap-restful-format]
                        [wrap-params]
                        [wrap-keyword-params]
                        [middleware/auth-token]
                        [middleware/wrap-handler "API"]
                        [middleware/wrap-handler-auth-wxbot bot-host-id]]}
    ["/user-binding-request" {:post #'get-bot-bind-url-api}]
    ["/single-unbind" {:post #'unbind-bot}]
    ["/single-can-use-vip-feature" {:post #'single-can-use-vip-feature}]
    ["/single-daynote-record" {:post #'single-daynote-record-api}]
    ["/single-get-daynote" {:post #'single-get-daynote-markdown-api}]
    ["/single-chatgpt" {:post #'single-chatgpt-api}]
    ["/single-update-setting" {:post #'single-get-or-update-setting}]
    ["/single-remind-job" {:post #'single-remind-job}]
    
    ["/group-bind-confirm" {:post #'bind-group-and-create-database}]
    ["/group-unbind" {:post #'unbind-group}]
    ["/group-can-use-vip-feature" {:post #'group-can-use-vip-feature}]
    ["/group-daynote-record" {:post #'group-daynote-record-api}]
    ["/group-get-daynote" {:post #'group-get-daynote}]
    ["/group-chatgpt" {:post #'group-chatgpt-api}]
    ["/group-update-setting" {:post #'group-update-setting}]
    ["/group-get-setting" {:post #'group-get-setting}]
    ["/group-remind-job" {:post #'group-remind-job}]
    ["/group-chat-fully-record" {:post #'group-chat-fully-record}]
    ["/group-summary-history" {:post #'group-summary-history}]
    ["/group-chat-history-tidy" {:post #'group-chat-history-tidy}]
    
    ["/get-remind-jobs-at" {:post #'get-remind-jobs-at}]]])
