(ns hulunote.settings
  (:require [rum.core :as rum]
            [hulunote.storage :as storage]
            [hulunote.http :as http]
            [hulunote.util :as u]
            [re-frame.core :as re-frame]
            [clojure.string :as str]))

;; ==================== State ====================
(defonce settings-open? (atom false))
(defonce settings-tab (atom :profile)) ;; :profile or :token
(defonce profile-state (atom {:nickname ""
                               :introduction ""
                               :avatar nil
                               :loading false}))
(defonce token-state (atom {:generating false
                             :new-token nil
                             :expires-at nil
                             :copied false}))

(defn open-settings! []
  ;; Load current profile data
  (let [hulunote-info (:hulunote @storage/jwt-auth)]
    (reset! profile-state
      {:nickname (or (:accounts/nickname hulunote-info)
                     (:accounts/username hulunote-info) "")
       :introduction (or (:accounts/introduction hulunote-info) "")
       :avatar (or (:accounts/avatar hulunote-info) nil)
       :loading false})
    (reset! token-state {:generating false :new-token nil :expires-at nil :copied false})
    (reset! settings-tab :profile)
    (reset! settings-open? true)))

(defn close-settings! []
  (reset! settings-open? false))

;; ==================== API calls ====================
(defn save-profile! []
  (swap! profile-state assoc :loading true)
  (let [{:keys [nickname introduction]} @profile-state]
    (re-frame/dispatch
      [:update-profile
       {:nickname nickname
        :introduction introduction
        :op-fn (fn [data]
                 (swap! profile-state assoc :loading false)
                 ;; Update local storage with new profile data
                 (when-let [profile (:profile data)]
                   (swap! storage/jwt-auth
                     update :hulunote merge
                     {:accounts/nickname (:accounts/nickname profile)
                      :accounts/introduction (:accounts/introduction profile)}))
                 (u/alert "Profile saved successfully"))}])))

(defn upload-avatar! [file]
  (swap! profile-state assoc :loading true)
  (let [form-data (js/FormData.)]
    (.append form-data "avatar" file)
    (-> (js/fetch (http/http-uri "/user/upload-avatar")
          (clj->js {:method "POST"
                    :headers {"X-FUNCTOR-API-TOKEN" (:token @storage/jwt-auth)}
                    :body form-data}))
        (.then (fn [resp] (.json resp)))
        (.then (fn [data]
                 (let [result (js->clj data :keywordize-keys true)]
                   (swap! profile-state assoc :loading false)
                   (if (:avatar_url result)
                     (do
                       (swap! profile-state assoc :avatar (:avatar_url result))
                       ;; Update local storage
                       (swap! storage/jwt-auth
                         assoc-in [:hulunote :accounts/avatar] (:avatar_url result))
                       (u/alert "Avatar uploaded successfully"))
                     (u/alert (str "Upload failed: " (:error result)))))))
        (.catch (fn [err]
                  (swap! profile-state assoc :loading false)
                  (u/alert (str "Upload failed: " err)))))))

(defn generate-token! []
  (swap! token-state assoc :generating true)
  (re-frame/dispatch
    [:generate-user-token
     {:op-fn (fn [data]
               (let [token (:token data)
                     expires-at (:expires_at data)]
                 (reset! token-state
                   {:generating false
                    :new-token token
                    :expires-at expires-at
                    :copied false})
                 ;; Update stored token
                 (when token
                   (swap! storage/jwt-auth assoc :token token)
                   ;; Update hulunote info if provided
                   (when-let [hulunote-info (:hulunote data)]
                     (swap! storage/jwt-auth assoc :hulunote hulunote-info))
                   ;; Send to Electron if available
                   (when (and (exists? js/window.electronAPI)
                              (.-setAuthToken js/window.electronAPI))
                     (.setAuthToken js/window.electronAPI token)))))}]))

(defn copy-token! [token]
  (-> (.writeText js/navigator.clipboard token)
      (.then (fn []
               (swap! token-state assoc :copied true)
               (js/setTimeout #(swap! token-state assoc :copied false) 2000)))))

;; ==================== Helper: Token Expiry Info ====================
(defn get-current-token-info []
  (let [token (:token @storage/jwt-auth)]
    (when (and token (not (str/blank? token)))
      (let [parsed (u/parse-jwt-token token)
            exp (get parsed "exp")
            now (/ (js/Date.now) 1000)]
        (when exp
          {:expires-at exp
           :remaining-days (max 0 (js/Math.floor (/ (- exp now) 86400)))
           :is-expired (> now exp)})))))

;; ==================== SVG Icons ====================
(defn settings-icon []
  [:svg {:width "16" :height "16" :viewBox "0 0 24 24" :fill "none"
         :stroke "currentColor" :stroke-width "2"
         :stroke-linecap "round" :stroke-linejoin "round"}
   [:circle {:cx "12" :cy "12" :r "3"}]
   [:path {:d "M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"}]])

;; ==================== Components ====================
(rum/defc tab-button [label tab-key current-tab on-click]
  [:button
   {:on-click on-click
    :style {:padding "10px 24px"
            :border "none"
            :background (if (= tab-key current-tab)
                          "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                          "transparent")
            :color (if (= tab-key current-tab) "#fff" "#666")
            :font-size "14px"
            :font-weight "600"
            :border-radius "8px"
            :cursor "pointer"
            :transition "all 0.2s ease"}}
   label])

(rum/defc profile-tab < rum/reactive []
  (let [{:keys [nickname introduction avatar loading]} (rum/react profile-state)]
    [:div {:style {:padding "24px 0"}}
     ;; Avatar section
     [:div {:style {:display "flex" :align-items "center" :gap "20px" :margin-bottom "32px"}}
      [:div {:style {:position "relative"}}
       [:div {:style {:width "80px" :height "80px" :border-radius "50%"
                      :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                      :display "flex" :align-items "center" :justify-content "center"
                      :overflow "hidden" :cursor "pointer"
                      :border "3px solid #e0e0e0"}
              :on-click #(.click (.getElementById js/document "avatar-upload-input"))}
        (if avatar
          [:img {:src (if (str/starts-with? (or avatar "") "http")
                        avatar
                        (str (http/http-uri "") avatar))
                 :style {:width "100%" :height "100%" :object-fit "cover"}}]
          [:span {:style {:color "#fff" :font-size "32px" :font-weight "600"}}
           (-> (or nickname "U") first str/upper-case)])]
       [:div {:style {:position "absolute" :bottom "0" :right "0"
                      :background "#667eea" :border-radius "50%"
                      :width "24px" :height "24px"
                      :display "flex" :align-items "center" :justify-content "center"
                      :cursor "pointer" :border "2px solid #fff"}
              :on-click #(.click (.getElementById js/document "avatar-upload-input"))}
        [:svg {:width "12" :height "12" :viewBox "0 0 24 24" :fill "#fff"}
         [:path {:d "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 000-1.41l-2.34-2.34a1 1 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"}]]]]
      [:div
       [:div {:style {:font-size "14px" :color "#666" :margin-bottom "4px"}} "Profile Photo"]
       [:div {:style {:font-size "12px" :color "#999"}} "Click to upload (max 5MB)"]]]

     ;; Hidden file input
     [:input {:id "avatar-upload-input"
              :type "file"
              :accept "image/png,image/jpeg,image/gif,image/webp"
              :style {:display "none"}
              :on-change (fn [e]
                           (when-let [file (aget (.. e -target -files) 0)]
                             (upload-avatar! file)
                             (set! (.. e -target -value) "")))}]

     ;; Nickname field
     [:div {:style {:margin-bottom "20px"}}
      [:label {:style {:display "block" :margin-bottom "8px"
                       :font-size "14px" :font-weight "500" :color "#333"}}
       "Display Name"]
      [:input {:type "text"
               :value nickname
               :placeholder "Enter your display name..."
               :on-change #(swap! profile-state assoc :nickname (.. % -target -value))
               :style {:width "100%" :padding "12px 16px"
                       :border "2px solid #e0e0e0" :border-radius "8px"
                       :font-size "15px" :outline "none"
                       :transition "border-color 0.2s"
                       :box-sizing "border-box"}}]]

     ;; Introduction field
     [:div {:style {:margin-bottom "24px"}}
      [:label {:style {:display "block" :margin-bottom "8px"
                       :font-size "14px" :font-weight "500" :color "#333"}}
       "Introduction"]
      [:textarea {:value introduction
                  :placeholder "Tell us about yourself..."
                  :on-change #(swap! profile-state assoc :introduction (.. % -target -value))
                  :rows 4
                  :style {:width "100%" :padding "12px 16px"
                          :border "2px solid #e0e0e0" :border-radius "8px"
                          :font-size "15px" :outline "none"
                          :resize "vertical" :font-family "inherit"
                          :transition "border-color 0.2s"
                          :box-sizing "border-box"}}]]

     ;; Save button
     [:button
      {:on-click save-profile!
       :disabled loading
       :style {:padding "12px 32px"
               :border "none"
               :border-radius "8px"
               :background (if loading "#ccc" "linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
               :color "#fff"
               :font-size "15px"
               :font-weight "600"
               :cursor (if loading "not-allowed" "pointer")
               :transition "all 0.2s ease"}}
      (if loading "Saving..." "Save Profile")]]))

(rum/defc token-tab < rum/reactive []
  (let [{:keys [generating new-token expires-at copied]} (rum/react token-state)
        current-info (get-current-token-info)]
    [:div {:style {:padding "24px 0"}}
     ;; Current token status
     [:div {:style {:background "#f8f9fa" :border-radius "12px"
                    :padding "20px" :margin-bottom "24px"}}
      [:h3 {:style {:margin "0 0 12px 0" :font-size "16px"
                    :font-weight "600" :color "#333"}}
       "Current Token Status"]
      (if current-info
        [:div
         [:div {:style {:display "flex" :align-items "center" :gap "8px" :margin-bottom "8px"}}
          [:div {:style {:width "10px" :height "10px" :border-radius "50%"
                         :background (if (:is-expired current-info) "#ff4d4f" "#52c41a")}}]
          [:span {:style {:font-size "14px" :color "#666"}}
           (if (:is-expired current-info) "Token Expired" "Token Active")]]
         [:div {:style {:font-size "14px" :color "#666" :margin-bottom "4px"}}
          (str "Remaining: " (:remaining-days current-info) " days")]
         [:div {:style {:font-size "13px" :color "#999"}}
          (str "Expires: "
               (.toLocaleDateString
                 (js/Date. (* (:expires-at current-info) 1000))))]]
        [:div {:style {:font-size "14px" :color "#999"}}
         "No active token"])]

     ;; Generate new token section
     [:div {:style {:background "#f8f9fa" :border-radius "12px"
                    :padding "20px" :margin-bottom "24px"}}
      [:h3 {:style {:margin "0 0 8px 0" :font-size "16px"
                    :font-weight "600" :color "#333"}}
       "Generate New Token"]
      [:p {:style {:margin "0 0 16px 0" :font-size "14px" :color "#666"}}
       "Generate a new JWT token with 3-month (90 days) validity. The new token will replace your current one."]

      [:button
       {:on-click generate-token!
        :disabled generating
        :style {:padding "12px 24px"
                :border "none"
                :border-radius "8px"
                :background (if generating "#ccc" "linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
                :color "#fff"
                :font-size "15px"
                :font-weight "600"
                :cursor (if generating "not-allowed" "pointer")}}
       (if generating "Generating..." "Generate 3-Month Token")]]

     ;; New token display
     (when new-token
       [:div {:style {:background "#f0f9f0" :border "1px solid #b7eb8f"
                      :border-radius "12px" :padding "20px" :margin-bottom "24px"}}
        [:h3 {:style {:margin "0 0 8px 0" :font-size "16px"
                      :font-weight "600" :color "#389e0d"}}
         "New Token Generated"]
        (when expires-at
          [:div {:style {:font-size "13px" :color "#52c41a" :margin-bottom "12px"}}
           (str "Valid until: " (.toLocaleDateString (js/Date. expires-at)))])
        [:div {:style {:display "flex" :gap "8px" :align-items "stretch"}}
         [:input {:type "text"
                  :value new-token
                  :read-only true
                  :style {:flex "1" :padding "10px 12px"
                          :border "1px solid #d9d9d9" :border-radius "6px"
                          :font-size "12px" :font-family "monospace"
                          :background "#fff" :box-sizing "border-box"}}]
         [:button
          {:on-click #(copy-token! new-token)
           :style {:padding "10px 16px"
                   :border "none"
                   :border-radius "6px"
                   :background (if copied "#52c41a" "#667eea")
                   :color "#fff"
                   :font-size "13px"
                   :font-weight "500"
                   :cursor "pointer"
                   :white-space "nowrap"}}
          (if copied "Copied!" "Copy")]]])]))

(rum/defc settings-modal < rum/reactive []
  (let [open? (rum/react settings-open?)
        current-tab (rum/react settings-tab)]
    (when open?
      [:div {:style {:position "fixed" :top 0 :left 0 :right 0 :bottom 0
                     :background "rgba(0,0,0,0.5)" :display "flex"
                     :align-items "center" :justify-content "center"
                     :z-index 10001}
             :on-click close-settings!}
       [:div {:style {:background "#fff" :border-radius "16px"
                      :width "560px" :max-height "80vh"
                      :overflow-y "auto"
                      :box-shadow "0 8px 32px rgba(0,0,0,0.2)"}
              :on-click #(.stopPropagation %)}
        ;; Header
        [:div {:style {:display "flex" :align-items "center" :justify-content "space-between"
                       :padding "24px 32px 16px 32px"
                       :border-bottom "1px solid #f0f0f0"}}
         [:h2 {:style {:margin 0 :font-size "22px" :font-weight "700" :color "#1a1a2e"}}
          "Settings"]
         [:button {:on-click close-settings!
                   :style {:background "none" :border "none" :cursor "pointer"
                           :font-size "20px" :color "#999" :padding "4px"}}
          "\u00D7"]]

        ;; Tab bar
        [:div {:style {:display "flex" :gap "8px" :padding "16px 32px 0 32px"}}
         (tab-button "Profile" :profile current-tab #(reset! settings-tab :profile))
         (tab-button "Token" :token current-tab #(reset! settings-tab :token))]

        ;; Tab content
        [:div {:style {:padding "0 32px 32px 32px"}}
         (case current-tab
           :profile (profile-tab)
           :token (token-tab)
           nil)]]])))
