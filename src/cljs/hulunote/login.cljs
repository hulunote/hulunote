(ns hulunote.login
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            ;; [herb.core :refer [<class]]
            [hulunote.styles :as styles]
            [hulunote.components :as comps]
            ;;
            [re-frame.core :as re-frame]
            [hulunote.router :as router]
            [hulunote.storage :as storage]
            [hulunote.db :as db]))

(defn valid-cell-number [mail]
  (re-frame/dispatch-sync
    [:send-ack-msg  {:email mail
                     :op-fn #(u/alert (str "Sent mail ok"))}]))

(defn valid-pass [a]
  "")

(defn alert-fn [a]
  )

;; 从WhatsApp点击注册链接: https://www.hulunote.io/#/login?platform={platform}&code={binding-code}
(defn signup-api [{:keys [username password platform-code ack-number]}]
  (re-frame/dispatch-sync
    [:web-signup {:email username
                  :password password
                  :ack-number ack-number
                  :binding-code platform-code
                  :op-fn #(u/alert (str "Sign up ok"))}]))

(defn login-api [{:keys [username password]}]
  (re-frame/dispatch-sync
    [:web-login {:email username
                 :password password
                 :op-fn (fn [data]
                          (swap! storage/jwt-auth merge {:hulunote (:hulunote data)
                                                         :token (:token data)})
                          ;; database 就在首页上摆出来就行了！！！=> 不单独弄一个页面！就像roam一样，几个方框摆在那里就行！！！
                          (router/switch-router! "/")
                          )}]))

;; https://github.com/tonsky/rum#components-local-state
;; { :init                 ;; state, props     ⇒ state
;;   :will-mount           ;; state            ⇒ state
;;   :before-render        ;; state            ⇒ state
;;   :wrap-render          ;; render-fn        ⇒ render-fn
;;   :render               ;; state            ⇒ [pseudo-dom state]
;;   :did-catch            ;; state, err, info ⇒ state
;;   :did-mount            ;; state            ⇒ state
;;   :after-render         ;; state            ⇒ state
;;   :will-remount         ;; old-state, state ⇒ state
;;   :should-update        ;; old-state, state ⇒ boolean
;;   :will-update          ;; state            ⇒ state
;;   :did-update           ;; state            ⇒ state
;;   :will-unmount }       ;; state            ⇒ state
(rum/defcs login-page < {:will-mount
                         (fn [state]
                           (let [{:keys [code]} (u/parse-query-string (u/get-params))]
                             (assoc state ::code code)))}
  (rum/local false ::is-signup)
  (rum/local "" ::username)
  (rum/local "" ::password)
  (rum/local "" ::password-confirm)
  (rum/local "" ::platform-code)
  (rum/local "" ::cell-ack)
  [state db]
  (let [is-signup (if (::code state)
                    (atom true)
                    (::is-signup state))
        username (::username state)
        password (::password state)
        password-confirm (::password-confirm state)
        platform-code (::platform-code state)
        cell-ack (::cell-ack state)]
    [:div.flex.flex-column
     (comps/header)
     [:div.flex.flex-column.justify-center.items-center ;; .bg-white
      {:style {:height        (if @is-signup
                                "38rem"
                                "30rem")
               :border-radius "0.5em"}}
      [:div.b.f2.mt3 (if @is-signup
                       "Sign up"
                       "Sign in")]
      [:div.ma4.mt2 {:style {:width "20rem"}}
       [:div
        [:div.pt2.b "Email"]
        [:div.mt2
         [:input.f5 {:style     {:border        "1px solid rgba(187, 187, 187, 1)"
                                 :line-height   "2.3rem"
                                 :border-radius "0.3em"
                                 :width         "20rem"}
                     :placeholder "Your email"
                     :on-change #(reset! username (.. % -target -value))
                     :type      "text"}]]]
       [:div
        [:div.pt2.b "Password"]
        [:div.mt2
         [:input.f5 {:style     {:width         "20rem"
                                 :line-height   "2.3rem"
                                 :border-radius "0.3em"
                                 :border        "1px solid rgba(187, 187, 187, 1)"}
                     :placeholder "Your password"
                     :on-change #(reset! password (.. % -target -value))
                     :on-key-down
                     #(case (.-which %)
                        13 (.click (u/get-ele "login-button"))
                        nil)
                     :type      "password"}]]]
       (if @is-signup
         [:div
          [:div.flex.flex-row.pt2
           [:div.b "Chat App Bind code"]]
          [:div.mt2
           [:input.f5 {:style       {:width         "20rem"
                                     :line-height   "2.3rem"
                                     :border-radius "0.3em"
                                     :border        "1px solid rgba(187, 187, 187, 1)"}
                       :value       (if (::code state)
                                      (::code state)
                                      @platform-code)
                       :placeholder "Your platform code"
                       :on-change   #(reset! platform-code (.. % -target -value))
                       :type        "text"}]]]
         [:nobr])
       (if @is-signup
         [:div
          [:div.pt2.b "Verification code"]
          [:div.mt2.flex.flex-row
           [:div.w-80.flex.flex-column
            [:input.f5 {:style     {:width         "100%"
                                    :line-height "2.3rem"
                                    :border-radius "0.2em"
                                    :border        "1px solid rgba(187, 187, 187, 1)"}
                        :id        "cell-ack"
                        :placeholder "Your email verification code"
                        :on-change #(reset! cell-ack (.. % -target -value))
                        :type      "text"}]]
           [:div.w-10-l.flex.flex-column
            [:button.f5.ba.bg-white
             {:class "" #_(<class  styles/button-hover-orange)
              :on-click
              #(valid-cell-number @username)
              :style {:border-radius "0.3em"
                      :height        "100%"
                      :margin-left   "1rem"
                      :border        "1px solid rgba(187, 187, 187, 1)"
                      :width         "3rem"}}
             "Sent"]]]]
         [:nobr])
       ]
      [:div.mt2
       (if @is-signup
         [:button.f4.bg-white
          {:on-click
           (fn []
             (signup-api {:username        @username
                          :cell-number     @username
                          :password        @password
                          :platform-code @platform-code
                          :ack-number      @cell-ack}))

           :class "" #_(<class  styles/button-hover-orange)
           :style {:border-radius "0.3em"
                   :height        "2.3rem"
                   :border        "1px solid rgba(187, 187, 187, 1)"
                   :width         "20rem"}}
          "Sign up"]
         [:button.f4.bg-white
          {:id "login-button"
           :on-click #(if (or (empty? @username)
                            (empty? @password))
                        (alert-fn [:toast "用户名或者密码不能为空"])
                        (login-api {:username @username :password @password}))
           :class    "" #_(<class  styles/button-hover-orange)
           :style    {:border-radius "0.3em"
                      :height        "2.3rem"
                      :border        "1px solid rgba(187, 187, 187, 1)"
                      :width         "20rem"}}
          "Login"])]
      [:div.mt3 {:style {:width "20rem"}}
       [:hr {:style {:border "0.5px solid #CCCCCC"}}]]
      [:div.mt3.relative
       [:a.blue
        {:on-click #(if @is-signup
                      (reset! is-signup false)
                      (reset! is-signup true))
         :class    "" #_(<class  styles/hover-underline)
         :style    {:height "2.3rem"
                    :line-height "2.3rem"
                    :width  "20rem"}}
        (if @is-signup
          "Login"
          "Sign up")]]]
     ;;
     (comps/footer)]))
