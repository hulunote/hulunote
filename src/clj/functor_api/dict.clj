(ns functor-api.dict)

(def dict {;; errors 
           :error-server {:code 1001
                          :en "System is busy, please refresh the page and try again"
                          :zh "系统繁忙,请刷新页面后重试哦~"}
           :error-api-bot-unauth {:code 1002
                                  :en "Bot Api authenticate failed"
                                  :zh "接口需要机器人身份请求"}
           :error-api-designate-unauth {:code 1003
                                        :en "Designate Api authenticate failed"
                                        :zh "接口不满足用户身份"}
           :error-auth-too-many {:code 1004
                                 :en "Too frequently, restricted today"
                                 :zh "用户登入太频频，今日限制登录"}
           :error-unauthorized {:code 1005
                                :en "Unauthorized"
                                :zh "登录已过期或未授权"}
           :error-ws-unauthorized {:code 1006
                                   :en "Websocket unauthorized"
                                   :zh "用户验证失败"}
           :error-export-failed {:code 1007
                                 :en "Export file failed"
                                 :zh "导出文件失败"}
           :error-ack-number {:code 1008
                              :en "Verify sign up code failed"
                              :zh "验证码错误"}
           :error-username-email-exists {:code 1009
                                         :en "Username or email has already signup."
                                         :zh "用户名或email已注册"}
           :error-missing-email {:code 1010
                                 :en "Missing email or wrong"
                                 :zh "缺少邮件字段或不正确"}
           :error-missing-password {:code 1011
                                    :en "Missing password"
                                    :zh "密码不能为空"}
           :error-missing-username-or-email {:code 1012
                                             :en "Missing username or email"
                                             :zh "用户或email不能为空"}
           :error-username-or-email-wrong {:code 1013
                                           :en "Username or email wrong"
                                           :zh "用户名或email错误"}
           :error-password {:code 1014
                            :en "Wrong password"
                            :zh "密码错误"}
           :error-too-database {:code 1015
                                :en "Free user can create only 5 databases"
                                :zh "免费用户最多只能创建5个笔记库"}
           :error-same-name-database {:code 1016
                                      :en "Database name already exists"
                                      :zh "笔记库名称已创建"}
           :error-cant-update-other-database {:code 1017
                                              :en "Can not update others' database"
                                              :zh "不能修改其他用户的笔记库信息"}
           :error-must-have-one-default-db {:code 1018
                                            :en "Must have at lease one default database"
                                            :zh "必须保留一个默认库"}
           :error-missing-parameter {:code 1019
                                     :en "Request missing parameter(s)"
                                     :zh "请求缺少参数"}
           :error-unsupport-param {:code 1020
                                   :en "Parameter contains unsupported character"
                                   :zh "参数包含了不支持的字符信息"}
           :error-missing-database-info {:code 1021
                                         :en "Missing note database info, aborted"
                                         :zh "缺少笔记库信息，拒绝"}
           :error-note-title-already-exists {:code 1022
                                             :en "Note title already exists in your database"
                                             :zh "笔记标题已在笔记库中"}
           :error-size-too-big {:code 1023
                                :en "Page size too many"
                                :zh "每页数量太大"}
           :error-permission-deny {:code 1024
                                   :en "Permission deny"
                                   :zh "无权操作"}
           :error-binding-code-expired {:code 1025
                                        :en "Binding code has expired"
                                        :zh "绑定请求已过期"}
           :error-bot-unbind {:code 1026
                              :en "Haven't binded your account"
                              :zh "用户未进行绑定"}
           :error-no-default-database {:code 1027
                                       :en "You don't have a default database"
                                       :zh "没有设置一个默认的笔记库"}
           :error-feature-need-vip {:code 1028
                                    :en "This feature is only for Pro edition users"
                                    :zh "该功能为专业版用能"}
           :error-s3-over-100m {:code 1029
                                :en "Over free user storage for 100M"
                                :zh "用户存储已超过100m，升级专业版可升级存储空间"}
           :error-s3-over-5g {:code 1030
                              :en "Over storage for 5G, please contact us"
                              :zh "用户存储已超过5G，请联系我们以扩充空间"}
           :error-email-ack-already-exists {:code 1031
                                            :en "The last email ack code avaliable in 5 minutes"
                                            :zh "上一个验证码5分钟内有效"}
           :error-chatgpt-request {:code 1032
                                   :en "Sorry, QA request error, please try again"
                                   :zh "QA请求失败，请稍后再试"}
           :error-bot-group-already-binded {:code 1033
                                            :en "This group has already binded to hulunote."
                                            :zh "本群已经绑定"}
           :error-bot-group-unbind {:code 1034
                                    :en "Haven't binded this group with hulunote"
                                    :zh "本群尚未绑定"}
           :error-bot-group-not-the-binder {:code 1035
                                            :en "You are not this group's binder"
                                            :zh "您不是这个群的绑定者"}
           :error-not-active-tidy-mode {:code 1036
                                        :en "Not active group tidy mode, ignore"
                                        :zh "没有开启高级自动整理模式，忽略"}
           :error-incompatible-result {:code 1037
                                       :en "Not incompatible result, ignore"
                                       :zh "没有符合的结果，忽略"}
           :error-database-not-found-by-name {:code 1038
                                      :en "Database id not found by name, please check the name"
                                      :zh "通过笔记库名称获取笔记库id失败，请检查名称"}
           :error-remind-time-spec {:code 1039
                                    :en (str "Sorry, remind job is following server time, "
                                             "which mean it can't catch client's time.\n"
                                             "Please input time spec follow: `in {x} minutes/hours/days`")
                                    :zh (str "抱歉，提醒任务是根据系统时间去进行提醒的，无法获取客户端时间."
                                             "因此无法输入具体时间，请输入一下如：`in {x} minutes/hours/days`")}
           :error-insufficient-balance {:code 1040
                                        :en "Sorry, your huluseed balance is insufficient"
                                        :zh "抱歉，葫芦籽余额不足"}
           
           ;; warn message
           :warn-bot-already-binded {:code 2001
                                     :en "Already binded"
                                     :zh "已有绑定记录"}
           :warn-developing-feature {:code 2002
                                     :en "This feature is working in progress, stay tuned"
                                     :zh "功能正在实现中，敬请期待"}
           
           ;; reward
           :reward-huluseed-by-invition {:code 3001
                                         :en "Reward 10 huluseed beacuase of you invite user"
                                         :zh "邀请用户注册，奖励10葫芦籽"}
           :reward-huluseed-by-ot-invition {:code 3002
                                            :en "Reward 10 huluseed beacuase of you invite user via ot"
                                            :zh "OT邀请用户注册，奖励10葫芦籽"}
           
           ;; vip
           :user-type-vip {:code 4001
                           :en "Pro edition user"
                           :zh "专业版用户"}
           :user-type-free {:code 4002
                            :en "Free edition user"
                            :zh "免费用户"}

           ;; pending or something else
           :pending-for-requested {:code 5001
                                   :en "Requested, please wait for reply"
                                   :zh "已提交，请等待回覆..."}
           
           :success-op {:code 0
                        :en "Operate success"
                        :zh "操作成功"}})

(defn get-dict-string 
  "获取关键字的字典信息（类似i18n）"
  ([key] (get-dict-string key :en)) 
  ([key region] 
   (if region 
     (get-in dict [key region])
     (get-in dict [key :en]))))

(defn get-dict-error
  "构造错误的返回信息"
  ([key] (get-dict-error key :en))
  ([key region]
   (let [item (get dict key)
         code (:code item) 
         msg (if region
               (get item region)
               (get item :en))]
     {:error msg
      :errcode code})))
