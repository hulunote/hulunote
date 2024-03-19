(ns functor-api.service.files
  (:require [functor-api.util :as u]
            [clojure.string :as strings]
            [clojure.java.io :as io]
            [clojure.core.async :as a]
            [functor-api.config :as config]
            [functor-api.service.payment :as payment] 
            [functor-api.middleware-new :as middleware]
            [amazonica.aws.s3 :as s3]
            [amazonica.core :as aws]
            [functor-api.dict :as dict]
            [honeysql.helpers :as sql]
            [honeysql.core :as sqlh]))

(def import-record* true)
(def export-record* false)

(defn- get-upload-file-folder-by-type [type]
  (cond
    (u/in? type ["image" "jpg" "jpeg" "bmp" "pdf" "gif" "png"]) "images/"
    (u/in? type ["mp3" "m3u" "m4a" "wav" "ogg"]) "audios/"
    (u/in? type ["mp4" "m4v" "mpeg" "mov"]) "videos/"
    :else "others/"))

(defn- get-upload-file-content-type [type]
  (let [content-type (get {"image" "image/png"
                           "png" "image/png"
                           "jpg" "image/jpeg"
                           "jpeg" "image/jpeg"
                           "bmp" "image/bmp"
                           "pdf" "application/pdf"
                           "gif" "image/gif"

                           "mp3" "audio/mpeg"
                           "m3u" "audio/x-mpegurl"
                           "m4a" "audio/x-m4a"
                           "wav" "audio/x-wav"
                           "ogg" "audio/ogg"

                           "mp4" "video/mp4"
                           "m4v" "video/x-m4v"
                           "mpeg" "video/mpeg"
                           "mov" "video/quicktime"}
                          type "application/octet-stream")]
    {:content-type content-type}))

(defn- check-s3-folder-size [path]
  (let [res (s3/list-objects-v2 :bucket-name "hulunote"
                                :prefix path)
        files (:object-summaries res)]
    (reduce #(let [size (:size %2)
                   mb (/ size 1048576)]
               (+ %1 mb))
            0 files)))

(defn- check-account-s3-over-size [account-id]
  (let [image-size (check-s3-folder-size (str "images/" account-id "/"))
        audio-size (check-s3-folder-size (str "audios/" account-id "/"))
        video-size (check-s3-folder-size (str "videos/" account-id "/"))
        others-size (check-s3-folder-size (str "others/" account-id "/"))
        all-size (+ image-size audio-size video-size others-size)] 
    (if (payment/is-vip? account-id)
      (if (> all-size 5120)
        (dict/get-dict-error :error-s3-over-5g)
        {:success true})
      (if (> all-size 100)
        (dict/get-dict-error :error-s3-over-100m)
        {:success true}))))

(defn save-import-export-using-record
  "记录导入导出的使用记录"
  [account-id import-or-export kind data-id]
  (let [kind-str (name kind)]
    (-> (sql/insert-into :import-export-using-record)
        (sql/values [{:account-id account-id
                      :is-import import-or-export
                      :kind kind-str
                      :data-id data-id}])
        (u/execute-one!))))


(defn upload-file->s3
  "上传静态文件到s3"
  [{:keys [hulunote region]}
   {:keys [file file-name type]}] 
  (let [account-id (:hulunote/id hulunote)
        aws-s3-info (:aws-s3 @config/functor-api-conf)
        {:keys [access-key access-secret endpoint path-prefix]} aws-s3-info
        aws-file-folder (get-upload-file-folder-by-type type)
        aws-file-name (str aws-file-folder account-id "/"
                           (u/gen-token)
                           (.getTime (java.util.Date.))
                           (or file-name (:filename file) (str (u/rand-string 16) "." type)))
        content-type (get-upload-file-content-type type)
        link-name (str path-prefix aws-file-name)]
    (if-not aws-s3-info
      (throw (Exception. "aws-s3未配置"))
      (aws/with-credential [access-key access-secret endpoint]
        (let [checkment (check-account-s3-over-size account-id)]
          (if (:error checkment)
            checkment
            (do (with-open [in (io/input-stream (:tempfile file))]
                  (s3/put-object :bucket-name "hulunote"
                                 :key aws-file-name
                                 :input-stream in
                                 :metadata content-type))
                ;; 保存上传记录
                (save-import-export-using-record account-id import-record* :upload-file aws-file-name)
                {:data {:filename link-name}})))))))

(defn upload-file-base64->s3
  "上传静态文件的base64形式到s3"
  [{:keys [hulunote region]}
   {:keys [base64 file-name type]}]
  (let [account-id (:hulunote/id hulunote)
        aws-s3-info (:aws-s3 @config/functor-api-conf)
        {:keys [access-key access-secret endpoint path-prefix]} aws-s3-info
        aws-file-folder (get-upload-file-folder-by-type type)
        aws-file-name (str aws-file-folder account-id "/"
                           (u/gen-token)
                           (.getTime (java.util.Date.))
                           (or file-name (str (u/rand-string 16) "." type)))
        content-type (get-upload-file-content-type type)
        link-name (str path-prefix aws-file-name)]
    (if-not aws-s3-info
      (throw (Exception. "aws-s3未配置"))
      (aws/with-credential [access-key access-secret endpoint]
        (let [checkment (check-account-s3-over-size account-id)]
          (if (:error checkment)
            checkment
            (do (with-open [in (io/input-stream (u/decode-base64->bytes base64))]
                  (s3/put-object :bucket-name "hulunote"
                                 :key aws-file-name
                                 :input-stream in
                                 :metadata content-type))
                ;; 保存上传记录
                (save-import-export-using-record account-id import-record* :upload-file aws-file-name)
                {:data {:filename link-name}})))))))

(defn upload-file-via-bot
  "机器人上传静态文件"
  [{:keys [hulunote region]}
   {:keys [group-uuid bot-uuid platform file file-name type] :as param}]
  (let [bot-account-id (-> (sql/select :account-id)
                       (sql/from :hulunote-bot-binding)
                       (sql/where [:and
                                   [:= :bot-uuid bot-uuid]
                                   [:= :platform platform]
                                   [:= :is-delete false]])
                       (u/execute-one!)
                       (:hulunote-bot-binding/account-id))
        group-account-id (-> (sql/select :account-id)
                             (sql/from :hulunote-bot-group-binding)
                             (sql/where [:and
                                         [:= :group-uuid group-uuid]
                                         [:= :platform platform]
                                         [:= :is-delete false]])
                             (u/execute-one!)
                             (:hulunote-bot-group-binding/account-id))
        account-id (or bot-account-id group-account-id)]
    (if account-id
      (upload-file->s3 {:hulunote {:hulunote/id account-id}} param)
      (dict/get-dict-error :error-bot-unbind region))))

(defn upload-file-base64-via-bot
  "机器人上传静态文件base64"
  [{:keys [hulunote region]}
   {:keys [group-uuid bot-uuid platform base64 file-name type] :as param}]
  (let [bot-account-id (-> (sql/select :account-id)
                       (sql/from :hulunote-bot-binding)
                       (sql/where [:and
                                   [:= :bot-uuid bot-uuid]
                                   [:= :platform platform]
                                   [:= :is-delete false]])
                       (u/execute-one!)
                       (:hulunote-bot-binding/account-id))
        group-account-id (-> (sql/select :account-id)
                             (sql/from :hulunote-bot-group-binding)
                             (sql/where [:and
                                         [:= :group-uuid group-uuid]
                                         [:= :platform platform]
                                         [:= :is-delete false]])
                             (u/execute-one!)
                             (:hulunote-bot-group-binding/account-id))
        account-id (or bot-account-id group-account-id)]
    (if account-id
      (upload-file-base64->s3 {:hulunote {:hulunote/id account-id}} param)
      (dict/get-dict-error :error-bot-unbind region))))

(def apis
  ["/files" {:middleware middleware/common-middlewares}
   ["/upload" {:post #'upload-file->s3}]
   ["/upload-base64" {:post #'upload-file-base64->s3}]
   ["/upload-via-bot" {:post #'upload-file-via-bot}]
   ["/upload-base64-via-bot" {:post #'upload-file-base64-via-bot}]])
