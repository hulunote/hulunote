(ns functor-api.state.schedule
  (:require [mount.core :as mount]
            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.triggers :as t]
            [clojurewerkz.quartzite.jobs :as j :refer [defjob]]
            [clojurewerkz.quartzite.schedule.cron :refer [schedule cron-schedule]]
            [clojure.java.shell :refer [sh]]
            [functor-api.util :as u]
            [functor-api.config :as config]
            [functor-api.service.hulubot :as hulubot])
  (:import [java.lang.management ManagementFactory]
           [com.sun.management OperatingSystemMXBean]))

;; 定时调度检查cpu
(def last-cpu-usage (atom 0))

(defn- get-percentage [value]
  (double (/ (int (* value 1000)) 10)))

(defn- format-memory-bytes [bytes]
  (cond
    (>= bytes 1073741824) (str (format "%.2f" (double (/ bytes 1073741824))) "GB")
    (>= bytes 1048576) (str (format "%.2f" (double (/ bytes 1048576))) "MB")
    (>= bytes 1024) (str (format "%.2f" (double (/ bytes 1024))) "KB")
    :else (str bytes "B")))

(defjob CheckCpuUsage
  [_ctx]
  (let [cpu-bean (java.lang.management.ManagementFactory/getOperatingSystemMXBean)
        cpu (.getSystemCpuLoad cpu-bean)
        cpu% (get-percentage cpu)
        memory-bean (java.lang.management.ManagementFactory/getMemoryMXBean)
        heap-used (-> memory-bean (.getHeapMemoryUsage) (.getUsed) (format-memory-bytes))
        non-heap-used (-> memory-bean (.getNonHeapMemoryUsage) (.getUsed) (format-memory-bytes))]
    (if (and (>= cpu% 90)
             (>= @last-cpu-usage 90))
      (let []
        (u/log-error "CPU在1分钟内持续90，发通知信息！")
        (comment "TODO：发送通知"))
      (do
        (u/log-info "CPU监控：" cpu% "%")
        (u/log-info "内存监控：heap:" heap-used " non-heap:" non-heap-used)))
    (reset! last-cpu-usage cpu%)))

(defn check-cpu-usage-schedule
  "检查CPU使用情况（每30秒）"
  []
  (let [crontab "*/30 * * * * ?"
        scheduler (-> (qs/initialize) qs/start)
        job (j/build
             (j/of-type CheckCpuUsage)
             (j/with-identity (j/key "schdeule-check-cpu.job")))
        trigger (t/build
                 (t/with-identity (t/key "schdeule-check-cpu.trigger"))
                 (t/start-now)
                 (t/with-schedule
                   (schedule
                    (cron-schedule crontab))))]
    (u/log-info "start check-cpu schedule on " crontab " ...")
    (qs/schedule scheduler job trigger)
    scheduler))

(defjob CheckBotBinding
  [_ctx]
  (hulubot/check-and-clear-binding-code-map))

(defn check-and-clear-bot-binding
  "检查并清理机器人绑定"
  []
  (let [crontab "0 */30 * * * ?"
        scheduler (-> (qs/initialize) qs/start)
        job (j/build
             (j/of-type CheckBotBinding)
             (j/with-identity (j/key "schdeule-check-and-clear-bot-binding.job")))
        trigger (t/build
                 (t/with-identity (t/key "schdeule-check-and-clear-bot-binding.trigger"))
                 (t/start-now)
                 (t/with-schedule
                   (schedule
                    (cron-schedule crontab))))]
    (u/log-info "start check-check-and-clear-bot-binding schedule on " crontab " ...")
    (qs/schedule scheduler job trigger)
    scheduler))

(mount/defstate schedule-jobs
  :start
  [(check-cpu-usage-schedule)
   (check-and-clear-bot-binding)])
