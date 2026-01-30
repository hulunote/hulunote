(ns hulunote.login
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.styles :as styles]
            [hulunote.components :as comps]
            [re-frame.core :as re-frame]
            [hulunote.router :as router]
            [hulunote.storage :as storage]
            [hulunote.db :as db]))

(defn valid-cell-number [mail]
  (re-frame/dispatch-sync
    [:send-ack-msg  {:email mail
                     :op-fn #(u/alert (str "Verification code sent!"))}]))

(defn alert-fn [a]
  )

(defn signup-api [{:keys [username password platform-code ack-number]}]
  (re-frame/dispatch-sync
    [:web-signup {:email username
                  :password password
                  :ack-number ack-number
                  :binding-code platform-code
                  :op-fn #(u/alert (str "Sign up successful!"))}]))

(defn login-api [{:keys [username password]}]
  (re-frame/dispatch-sync
    [:web-login {:email username
                 :password password
                 :op-fn (fn [data]
                          (swap! storage/jwt-auth merge {:hulunote (:hulunote data)
                                                         :token (:token data)})
                          (router/switch-router! "/"))}]))

;; Input field component
(rum/defc input-field [label placeholder type value on-change & [{:keys [on-key-down id]}]]
  [:div {:style {:margin-bottom "20px"}}
   [:label {:style {:display "block"
                    :font-size "14px"
                    :font-weight "600"
                    :color "#1a1a2e"
                    :margin-bottom "8px"}}
    label]
   [:input {:style {:width "100%"
                    :padding "12px 16px"
                    :font-size "15px"
                    :border "2px solid #e0e0e0"
                    :border-radius "10px"
                    :outline "none"
                    :transition "border-color 0.3s, box-shadow 0.3s"
                    :box-sizing "border-box"}
            :placeholder placeholder
            :type type
            :value value
            :id id
            :on-change on-change
            :on-key-down on-key-down
            :on-focus #(set! (.. % -target -style -borderColor) "#667eea")
            :on-blur #(set! (.. % -target -style -borderColor) "#e0e0e0")}]])

;; Primary button component
(rum/defc primary-button [text on-click & [{:keys [id]}]]
  [:button.pointer
   {:id id
    :on-click on-click
    :style {:width "100%"
            :padding "14px"
            :font-size "16px"
            :font-weight "600"
            :color "#fff"
            :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
            :border "none"
            :border-radius "10px"
            :cursor "pointer"
            :transition "transform 0.2s, box-shadow 0.2s"
            :box-shadow "0 4px 15px rgba(102, 126, 234, 0.4)"}}
   text])

;; Secondary button component
(rum/defc secondary-button [text on-click]
  [:button.pointer
   {:on-click on-click
    :style {:width "100%"
            :padding "14px"
            :font-size "16px"
            :font-weight "600"
            :color "#667eea"
            :background "#fff"
            :border "2px solid #667eea"
            :border-radius "10px"
            :cursor "pointer"
            :transition "background 0.2s"}}
   text])

;; Link button component
(rum/defc link-button [text on-click]
  [:span.pointer
   {:on-click on-click
    :style {:color "#667eea"
            :font-weight "600"
            :text-decoration "none"
            :cursor "pointer"}}
   text])

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
     {:style {:min-height "100vh"
              :background "#f8f9fa"}}
     
     ;; Header
     [:div
      {:style {:display "flex"
               :align-items "center"
               :justify-content "space-between"
               :padding "0 32px"
               :height "60px"
               :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"}}
      [:div.flex.items-center.pointer
       {:on-click #(router/switch-router! "/main")}
       [:img
        {:width "36px"
         :style {:border-radius "50%"}
         :src (u/asset-path "/img/hulunote.webp")}]
       [:div.pl3
        {:style {:font-size "22px"
                 :font-weight "700"
                 :color "#fff"}}
        "HULUNOTE"]]]
     
     ;; Main Content
     [:div.flex.flex-column.items-center.justify-center
      {:style {:flex "1"
               :padding "40px 20px"}}
      
      ;; Login Card
      [:div
       {:style {:background "#fff"
                :border-radius "16px"
                :padding "40px"
                :width "100%"
                :max-width "400px"
                :box-shadow "0 10px 40px rgba(0,0,0,0.1)"}}
       
       ;; Logo & Title
       [:div.flex.flex-column.items-center
        {:style {:margin-bottom "32px"}}
        [:div {:style {:font-size "48px"
                       :margin-bottom "16px"}}
         (if @is-signup "✨" "👋")]
        [:h1 {:style {:font-size "28px"
                      :font-weight "700"
                      :color "#1a1a2e"
                      :margin "0 0 8px 0"}}
         (if @is-signup "Create Account" "Welcome Back")]
        [:p {:style {:font-size "14px"
                     :color "#666"
                     :margin "0"}}
         (if @is-signup
           "Start your note-taking journey"
           "Sign in to continue to Hulunote")]]
       
       ;; Form Fields
       [:div {:style {:margin-bottom "24px"}}
        
        ;; Email
        (input-field "Email" "Enter your email" "email" @username
                     #(reset! username (.. % -target -value)))
        
        ;; Password
        (input-field "Password" "Enter your password" "password" @password
                     #(reset! password (.. % -target -value))
                     {:on-key-down #(when (= (.-which %) 13)
                                      (.click (u/get-ele "login-button")))
                      :id "password-input"})
        
        ;; Signup only fields
        (when @is-signup
          [:div
           ;; Platform Code
           (input-field "Bind Code (Optional)" "Platform binding code" "text"
                        (if (::code state) (::code state) @platform-code)
                        #(reset! platform-code (.. % -target -value)))
           
           ;; Verification Code
           [:div {:style {:margin-bottom "20px"}}
            [:label {:style {:display "block"
                             :font-size "14px"
                             :font-weight "600"
                             :color "#1a1a2e"
                             :margin-bottom "8px"}}
             "Verification Code"]
            [:div.flex {:style {:gap "12px"}}
             [:input {:style {:flex "1"
                              :padding "12px 16px"
                              :font-size "15px"
                              :border "2px solid #e0e0e0"
                              :border-radius "10px"
                              :outline "none"
                              :box-sizing "border-box"}
                      :placeholder "Enter code"
                      :type "text"
                      :on-change #(reset! cell-ack (.. % -target -value))}]
             [:button.pointer
              {:on-click #(valid-cell-number @username)
               :style {:padding "12px 20px"
                       :font-size "14px"
                       :font-weight "600"
                       :color "#667eea"
                       :background "#f0f0ff"
                       :border "2px solid #667eea"
                       :border-radius "10px"
                       :cursor "pointer"
                       :white-space "nowrap"}}
              "Send"]]]])]
       
       ;; Submit Button
       [:div {:style {:margin-bottom "24px"}}
        (if @is-signup
          (primary-button "Create Account"
                          #(signup-api {:username @username
                                        :cell-number @username
                                        :password @password
                                        :platform-code @platform-code
                                        :ack-number @cell-ack}))
          (primary-button "Sign In"
                          #(if (or (empty? @username) (empty? @password))
                             (u/alert "Email and password are required")
                             (login-api {:username @username :password @password}))
                          {:id "login-button"}))]
       
       ;; Divider
       [:div.flex.items-center
        {:style {:margin-bottom "24px"}}
        [:div {:style {:flex "1"
                       :height "1px"
                       :background "#e0e0e0"}}]
        [:span {:style {:padding "0 16px"
                        :color "#999"
                        :font-size "14px"}}
         "or"]
        [:div {:style {:flex "1"
                       :height "1px"
                       :background "#e0e0e0"}}]]
       
       ;; Toggle Login/Signup
       [:div.flex.justify-center
        {:style {:font-size "14px"
                 :color "#666"}}
        (if @is-signup
          [:span "Already have an account? "
           (link-button "Sign In" #(reset! is-signup false))]
          [:span "Don't have an account? "
           (link-button "Sign Up" #(reset! is-signup true))])]]]
     
     ;; Footer
     [:div
      {:style {:background "#1a1a2e"
               :padding "24px 20px"
               :text-align "center"}}
      [:div {:style {:color "rgba(255,255,255,0.5)"
                     :font-size "14px"}}
       "© 2024 Hulunote - MIT License"]]]))
