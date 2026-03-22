(ns hulunote.login
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.styles :as styles]
            [hulunote.components :as comps]
            [re-frame.core :as re-frame]
            [hulunote.router :as router]
            [hulunote.storage :as storage]
            [hulunote.http :as http]
            [hulunote.db :as db]))

(defn remove-nil-values [m]
  (into {} (remove (fn [[_ v]] (nil? v)) m)))

(defn- navigate-after-login! [database-list]
  (let [default-db (some (fn [item]
                           (when (:hulunote-databases/is-default item)
                             item))
                     database-list)]
    (if-let [default-db-name (:hulunote-databases/name default-db)]
      (do
        (http/database-data-load default-db-name)
        (router/go-to-diaries! default-db-name))
      (router/switch-router! "/"))))


(defn signup-api [{:keys [username password platform-code registration-code]}]
  (re-frame/dispatch-sync
    [:web-signup {:email username
                  :password password
                  :registration_code registration-code
                  :binding-code platform-code
                  :op-fn #(u/alert (str "Sign up successful! Please reload for Signin"))}]))

(defn login-api [{:keys [username password]}]
  (re-frame/dispatch-sync
    [:web-login {:email username
                 :password password
                 :op-fn (fn [data]
                          (swap! storage/jwt-auth merge {:hulunote (:hulunote data)
                                                         :token (:token data)})
                          ;; Send token to Electron main process to start built-in MCP server
                          (when (and (exists? js/window.electronAPI)
                                     (.-setAuthToken js/window.electronAPI))
                            (.setAuthToken js/window.electronAPI (:token data)))
                          (re-frame/dispatch-sync
                            [:get-database-list
                             {:op-fn (fn [{:keys [database-list]}]
                                       (doseq [item (or database-list [])]
                                         (d/transact! db/dsdb [(remove-nil-values item)]))
                                       (navigate-after-login! database-list))}]))}]))

;; Input field component
(rum/defc input-field [label placeholder type value on-change & [{:keys [on-key-down id]}]]
  [:div {:style {:margin-bottom "20px"}}
   [:label {:style {:display "block"
                    :font-size "14px"
                    :font-weight "600"
                    :color "var(--light-text-primary)"
                    :margin-bottom "8px"}}
    label]
   [:input {:style {:width "100%"
                    :padding "12px 16px"
                    :font-size "15px"
                    :border "2px solid var(--light-border)"
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
            :on-focus #(set! (.. % -target -style -borderColor) "var(--ui-accent)")
            :on-blur #(set! (.. % -target -style -borderColor) "var(--light-border)")}]])

;; Primary button component
(rum/defc primary-button [text on-click & [{:keys [id]}]]
  [:button.pointer
   {:id id
    :on-click on-click
   :style {:width "100%"
            :padding "14px"
            :font-size "16px"
            :font-weight "600"
            :color "var(--ui-accent-text)"
            :background "var(--ui-accent-gradient)"
            :border "none"
            :border-radius "10px"
            :cursor "pointer"
            :transition "transform 0.2s, box-shadow 0.2s"
            :box-shadow "var(--light-shadow-primary)"}}
   text])

;; Secondary button component
(rum/defc secondary-button [text on-click]
  [:button.pointer
   {:on-click on-click
    :style {:width "100%"
            :padding "14px"
            :font-size "16px"
            :font-weight "600"
            :color "var(--ui-accent)"
            :background "var(--light-surface)"
            :border "2px solid var(--ui-accent)"
            :border-radius "10px"
            :cursor "pointer"
            :transition "background 0.2s"}}
   text])

;; Link button component
(rum/defc link-button [text on-click]
  [:span.pointer
   {:on-click on-click
    :style {:color "var(--ui-accent)"
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
  (rum/local "" ::registration-code)
  [state db]
  (let [is-signup (if (::code state)
                    (atom true)
                    (::is-signup state))
        username (::username state)
        password (::password state)
        password-confirm (::password-confirm state)
        platform-code (::platform-code state)
        registration-code (::registration-code state)]
    [:div.flex.flex-column
     {:style {:min-height "100vh"
              :background "var(--light-page-bg)"}}

     ;; Header
     [:div
      {:style {:display "flex"
               :align-items "center"
               :justify-content "space-between"
               :padding "0 32px"
               :height "60px"
               :background "var(--ui-accent-gradient)"}}
      [:div.flex.items-center.pointer
       {:on-click #(router/switch-router! "/main")}
       [:img
        {:width "36px"
         :style {:border-radius "50%"}
         :src (u/asset-path "/img/hulunote.webp")}]
        [:div.pl3
        {:style {:font-size "22px"
                 :font-weight "700"
                 :color "var(--ui-accent-text)"}}
        "HULUNOTE"]]]

     ;; Main Content
     [:div.flex.flex-column.items-center.justify-center
      {:style {:flex "1"
               :padding "40px 20px"}}

      ;; Login Card
      [:div
       {:style {:background "var(--light-surface)"
                :border-radius "16px"
                :padding "40px"
                :width "100%"
                :max-width "400px"
                :box-shadow "var(--light-shadow-card)"}}

       ;; Logo & Title
       [:div.flex.flex-column.items-center
        {:style {:margin-bottom "32px"}}
        [:div {:style {:font-size "48px"
                       :margin-bottom "16px"}}
         (if @is-signup "✨" "👋")]
        [:h1 {:style {:font-size "28px"
                      :font-weight "700"
                      :color "var(--light-text-primary)"
                      :margin "0 0 8px 0"}}
         (if @is-signup "Create Account" "Welcome Back")]
        [:p {:style {:font-size "14px"
                     :color "var(--light-text-secondary)"
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
           #_(input-field "Bind Code (Optional)" "Platform binding code" "text"
                        (if (::code state) (::code state) @platform-code)
                        #(reset! platform-code (.. % -target -value)))

           ;; Registration Code
           (input-field "Registration Code" "Enter your registration code" "text"
                        @registration-code
                        #(reset! registration-code (.. % -target -value)))])]

       ;; Submit Button
       [:div {:style {:margin-bottom "24px"}}
        (if @is-signup
          (primary-button "Create Account"
                          #(signup-api {:username @username
                                        :password @password
                                        :platform-code @platform-code
                                        :registration-code @registration-code}))
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
                       :background "var(--light-border)"}}]
        [:span {:style {:padding "0 16px"
                        :color "var(--light-text-muted)"
                        :font-size "14px"}}
         "or"]
        [:div {:style {:flex "1"
                       :height "1px"
                       :background "var(--light-border)"}}]]

       ;; Toggle Login/Signup
       [:div.flex.justify-center
        {:style {:font-size "14px"
                 :color "var(--light-text-secondary)"}}
        (if @is-signup
          [:span "Already have an account? "
           (link-button "Sign In" #(reset! is-signup false))]
          [:span "Don't have an account? "
           (link-button "Sign Up" #(reset! is-signup true))])]]]

     ;; Footer
     [:div
      {:style {:background "var(--light-text-primary)"
               :padding "24px 20px"
               :text-align "center"}}
      [:div {:style {:color "var(--ui-text-soft)"
                     :font-size "14px"}}
       "© 2026  Hulunote - MIT License"]]]))
