(ns hulunote.settings
  (:require [rum.core :as rum]
            [hulunote.storage :as storage]
            [hulunote.http :as http]
            [hulunote.util :as u]
            [hulunote.db :as db]
            [hulunote.sidebar :as sidebar]
            [hulunote.router :as router]
            [hulunote.mcp :as mcp]
            [hulunote.mcp-state :as mcp-state]
            [hulunote.mcp-ui :as mcp-ui]
            [hulunote.chat :as chat]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [cljs.core.async :as a :refer [<! go]]))

;; ==================== State ====================
(defonce settings-tab (atom :profile)) ;; :profile :token :mcp-servers :chat
(defonce profile-state (atom {:nickname ""
                               :introduction ""
                               :avatar nil
                               :loading false}))
(defonce token-state (atom {:generating false
                             :new-token nil
                             :expires-at nil
                             :copied false}))
(defonce chat-settings-state (atom {:api-key ""
                                     :model "anthropic/claude-sonnet-4.6"
                                     :available-models []
                                     :models-loading? false
                                     :saved? false}))

;; ==================== Navigation ====================
(defn open-settings! []
  (let [db @db/dsdb
        {:keys [params]} (db/get-route db)
        database-name (:database params)]
    (when database-name
      (router/go-to-settings! database-name))))

(defn close-settings! []
  ;; Navigate back
  (js/history.back))

(defn get-current-database-name [db]
  (let [{:keys [params]} (db/get-route db)]
    (:database params)))

;; ==================== Helper ====================
(defn js->clj-safe [obj]
  (if (object? obj)
    (js->clj obj :keywordize-keys true)
    obj))

;; ==================== Profile API calls ====================
(defn save-profile! []
  (swap! profile-state assoc :loading true)
  (let [{:keys [nickname introduction]} @profile-state]
    (re-frame/dispatch
      [:update-profile
       {:nickname nickname
        :introduction introduction
        :op-fn (fn [data]
                 (swap! profile-state assoc :loading false)
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
                       (swap! storage/jwt-auth
                         assoc-in [:hulunote :accounts/avatar] (:avatar_url result))
                       (u/alert "Avatar uploaded successfully"))
                     (u/alert (str "Upload failed: " (:error result)))))))
        (.catch (fn [err]
                  (swap! profile-state assoc :loading false)
                  (u/alert (str "Upload failed: " err)))))))

;; ==================== Token API calls ====================
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
                 (when token
                   (swap! storage/jwt-auth assoc :token token)
                   (when-let [hulunote-info (:hulunote data)]
                     (swap! storage/jwt-auth assoc :hulunote hulunote-info))
                   (when (and (exists? js/window.electronAPI)
                              (.-setAuthToken js/window.electronAPI))
                     (.setAuthToken js/window.electronAPI token)))))}]))

(defn copy-token! [token]
  (-> (.writeText js/navigator.clipboard token)
      (.then (fn []
               (swap! token-state assoc :copied true)
               (js/setTimeout #(swap! token-state assoc :copied false) 2000)))))

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

;; ==================== Chat Settings API calls ====================
(defn init-chat-settings! []
  (when (chat/chat-available?)
    (go
      (when-let [ch (chat/get-api-key!)]
        (let [result (js->clj-safe (<! ch))]
          (when (:success result)
            (swap! chat-settings-state assoc :api-key (or (:apiKey result) ""))))))
    (go
      (when-let [ch (chat/get-model!)]
        (let [result (js->clj-safe (<! ch))]
          (when (:success result)
            (swap! chat-settings-state assoc :model (or (:model result) "anthropic/claude-sonnet-4.6"))))))))

(defn load-chat-models! []
  (when (and (chat/chat-available?)
             (not (:models-loading? @chat-settings-state)))
    (swap! chat-settings-state assoc :models-loading? true)
    (go
      (when-let [ch (chat/get-models!)]
        (let [result (js->clj-safe (<! ch))]
          (swap! chat-settings-state assoc :models-loading? false)
          (when (:success result)
            (let [models (:models result)
                  preferred-providers ["anthropic" "openai" "google" "meta-llama" "deepseek" "mistralai"]
                  provider-rank (into {} (map-indexed (fn [i p] [p i]) preferred-providers))
                  sorted-models (->> models
                                     (filter #(:id %))
                                     (sort-by (fn [m]
                                                (let [id (:id m)
                                                      provider (first (str/split id #"/"))]
                                                  [(get provider-rank provider 99) id]))))]
              (swap! chat-settings-state assoc :available-models sorted-models))))))))

(defn save-chat-settings! []
  (let [{:keys [api-key model]} @chat-settings-state]
    (go
      (when-let [ch (chat/set-api-key! api-key)]
        (let [result (js->clj-safe (<! ch))]
          (when (:success result)
            (swap! chat-settings-state assoc :saved? true)
            (js/setTimeout #(swap! chat-settings-state assoc :saved? false) 2000)))))
    (go
      (when-let [ch (chat/set-model! model)]
        (js->clj-safe (<! ch))))))

;; ==================== Init ====================
(defn init-profile! []
  (let [hulunote-info (:hulunote @storage/jwt-auth)]
    (reset! profile-state
      {:nickname (or (:accounts/nickname hulunote-info)
                     (:accounts/username hulunote-info) "")
       :introduction (or (:accounts/introduction hulunote-info) "")
       :avatar (or (:accounts/avatar hulunote-info) nil)
       :loading false})
    (reset! token-state {:generating false :new-token nil :expires-at nil :copied false})))

;; ==================== UI Components ====================

;; Shared styles
(def input-style
  {:width "100%"
   :padding "12px 16px"
   :border "1px solid rgba(255,255,255,0.15)"
   :border-radius "8px"
   :font-size "14px"
   :outline "none"
   :box-sizing "border-box"
   :background "#363b48"
   :color "#fdfeffc4"})

(def label-style
  {:display "block"
   :margin-bottom "8px"
   :font-size "14px"
   :font-weight "500"
   :color "rgba(255,255,255,0.6)"})

(def section-style
  {:background "rgba(255,255,255,0.04)"
   :border-radius "12px"
   :padding "20px"
   :margin-bottom "24px"
   :border "1px solid rgba(255,255,255,0.06)"})

(def btn-primary-style
  {:padding "12px 32px"
   :border "none"
   :border-radius "8px"
   :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
   :color "#fff"
   :font-size "15px"
   :font-weight "600"
   :cursor "pointer"
   :transition "all 0.2s ease"})

(def btn-disabled-style
  (merge btn-primary-style
    {:background "#555"
     :cursor "not-allowed"}))

;; Tab button
(rum/defc settings-tab-button [label tab-key current-tab on-click]
  [:button
   {:on-click on-click
    :style {:padding "10px 24px"
            :border "none"
            :background (if (= tab-key current-tab)
                          "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                          "rgba(255,255,255,0.06)")
            :color (if (= tab-key current-tab) "#fff" "rgba(255,255,255,0.5)")
            :font-size "14px"
            :font-weight "600"
            :border-radius "8px"
            :cursor "pointer"
            :transition "all 0.2s ease"
            :white-space "nowrap"}}
   label])

;; ==================== Profile Tab ====================
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
                      :border "3px solid rgba(255,255,255,0.2)"}
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
                      :cursor "pointer" :border "2px solid #2a2f3a"}
              :on-click #(.click (.getElementById js/document "avatar-upload-input"))}
        [:svg {:width "12" :height "12" :viewBox "0 0 24 24" :fill "#fff"}
         [:path {:d "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 000-1.41l-2.34-2.34a1 1 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"}]]]]
      [:div
       [:div {:style {:font-size "14px" :color "rgba(255,255,255,0.5)" :margin-bottom "4px"}} "Profile Photo"]
       [:div {:style {:font-size "12px" :color "rgba(255,255,255,0.3)"}} "Click to upload (max 5MB)"]]]

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
      [:label {:style label-style} "Display Name"]
      [:input {:type "text"
               :value nickname
               :placeholder "Enter your display name..."
               :on-change #(swap! profile-state assoc :nickname (.. % -target -value))
               :style input-style}]]

     ;; Introduction field
     [:div {:style {:margin-bottom "24px"}}
      [:label {:style label-style} "Introduction"]
      [:textarea {:value introduction
                  :placeholder "Tell us about yourself..."
                  :on-change #(swap! profile-state assoc :introduction (.. % -target -value))
                  :rows 4
                  :style (merge input-style
                           {:resize "vertical" :font-family "inherit"})}]]

     ;; Save button
     [:button
      {:on-click save-profile!
       :disabled loading
       :style (if loading btn-disabled-style btn-primary-style)}
      (if loading "Saving..." "Save Profile")]]))

;; ==================== Token Tab ====================
(rum/defc token-tab < rum/reactive []
  (let [{:keys [generating new-token expires-at copied]} (rum/react token-state)
        current-info (get-current-token-info)]
    [:div {:style {:padding "24px 0"}}
     ;; Current token status
     [:div {:style section-style}
      [:h3 {:style {:margin "0 0 12px 0" :font-size "16px"
                    :font-weight "600" :color "#fdfeffc4"}}
       "Current Token Status"]
      (if current-info
        [:div
         [:div {:style {:display "flex" :align-items "center" :gap "8px" :margin-bottom "8px"}}
          [:div {:style {:width "10px" :height "10px" :border-radius "50%"
                         :background (if (:is-expired current-info) "#ff4d4f" "#52c41a")}}]
          [:span {:style {:font-size "14px" :color "rgba(255,255,255,0.6)"}}
           (if (:is-expired current-info) "Token Expired" "Token Active")]]
         [:div {:style {:font-size "14px" :color "rgba(255,255,255,0.5)" :margin-bottom "4px"}}
          (str "Remaining: " (:remaining-days current-info) " days")]
         [:div {:style {:font-size "13px" :color "rgba(255,255,255,0.3)"}}
          (str "Expires: "
               (.toLocaleDateString
                 (js/Date. (* (:expires-at current-info) 1000))))]]
        [:div {:style {:font-size "14px" :color "rgba(255,255,255,0.4)"}}
         "No active token"])]

     ;; Generate new token section
     [:div {:style section-style}
      [:h3 {:style {:margin "0 0 8px 0" :font-size "16px"
                    :font-weight "600" :color "#fdfeffc4"}}
       "Generate New Token"]
      [:p {:style {:margin "0 0 16px 0" :font-size "14px" :color "rgba(255,255,255,0.5)"}}
       "Generate a new JWT token with 3-month (90 days) validity. The new token will replace your current one."]

      [:button
       {:on-click generate-token!
        :disabled generating
        :style (if generating btn-disabled-style btn-primary-style)}
       (if generating "Generating..." "Generate 3-Month Token")]]

     ;; New token display
     (when new-token
       [:div {:style {:background "rgba(82,196,26,0.1)" :border "1px solid rgba(82,196,26,0.3)"
                      :border-radius "12px" :padding "20px" :margin-bottom "24px"}}
        [:h3 {:style {:margin "0 0 8px 0" :font-size "16px"
                      :font-weight "600" :color "#52c41a"}}
         "New Token Generated"]
        (when expires-at
          [:div {:style {:font-size "13px" :color "#52c41a" :margin-bottom "12px"}}
           (str "Valid until: " (.toLocaleDateString (js/Date. expires-at)))])
        [:div {:style {:display "flex" :gap "8px" :align-items "stretch"}}
         [:input {:type "text"
                  :value new-token
                  :read-only true
                  :style (merge input-style
                           {:flex "1" :font-size "12px" :font-family "monospace"})}]
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

;; ==================== MCP Servers Tab ====================
(rum/defc mcp-servers-tab < rum/reactive []
  (let [_ (rum/react mcp-ui/ui-state)
        _ (rum/react mcp-state/mcp-state)]
    [:div {:style {:padding "24px 0"}}
     ;; Header with Add Server button
     [:div {:style {:display "flex"
                    :justify-content "space-between"
                    :align-items "center"
                    :margin-bottom "24px"}}
      [:div
       [:p {:style {:color "rgba(255,255,255,0.5)"
                    :margin 0
                    :font-size "14px"}}
        "Configure Model Context Protocol servers for AI integration"]]

      [:button.pointer
       {:on-click #(swap! mcp-ui/ui-state assoc :show-add-form true)
        :style {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                :color "#fff"
                :border "none"
                :padding "10px 20px"
                :border-radius "8px"
                :font-size "14px"
                :font-weight "600"
                :cursor "pointer"
                :display "flex"
                :align-items "center"
                :gap "8px"}}
       [:span {:style {:font-size "18px"}} "+"]
       [:span "Add Server"]]]

     ;; Not in Electron warning
     (when-not (mcp/electron?)
       [:div {:style {:background "rgba(250,140,22,0.1)"
                      :border "1px solid rgba(250,140,22,0.3)"
                      :border-radius "8px"
                      :padding "16px 20px"
                      :margin-bottom "24px"
                      :display "flex"
                      :align-items "center"
                      :gap "12px"}}
        [:span {:style {:font-size "24px"}} "!"]
        [:div
         [:div {:style {:font-weight "600"
                        :color "#fa8c16"
                        :margin-bottom "4px"}}
          "MCP is only available in Electron"]
         [:div {:style {:color "rgba(250,140,22,0.8)"
                        :font-size "13px"}}
          "Please use the Hulunote desktop application to configure MCP servers."]]])

     ;; Server list
     (mcp-ui/server-list)

     ;; Tools panel
     (mcp-ui/tools-panel)

     ;; Modals
     (mcp-ui/add-server-form)
     (mcp-ui/tool-modal)]))

;; ==================== Chat Tab ====================
(rum/defc chat-tab < rum/reactive []
  (let [{:keys [api-key model available-models models-loading? saved?]}
        (rum/react chat-settings-state)]
    [:div {:style {:padding "24px 0"}}
     ;; Not in Electron warning
     (when-not (chat/chat-available?)
       [:div {:style {:background "rgba(250,140,22,0.1)"
                      :border "1px solid rgba(250,140,22,0.3)"
                      :border-radius "8px"
                      :padding "16px 20px"
                      :margin-bottom "24px"
                      :display "flex"
                      :align-items "center"
                      :gap "12px"}}
        [:span {:style {:font-size "24px"}} "!"]
        [:div
         [:div {:style {:font-weight "600"
                        :color "#fa8c16"
                        :margin-bottom "4px"}}
          "Chat is only available in Electron"]
         [:div {:style {:color "rgba(250,140,22,0.8)"
                        :font-size "13px"}}
          "Please use the Hulunote desktop application to configure chat settings."]]])

     ;; API Key
     [:div {:style {:margin-bottom "20px"}}
      [:label {:style label-style} "OpenRouter API Key"]
      [:input
       {:type "password"
        :placeholder "sk-or-..."
        :value api-key
        :on-change #(swap! chat-settings-state assoc :api-key (.. % -target -value))
        :style input-style}]
      [:div {:style {:font-size "12px"
                     :color "rgba(255,255,255,0.3)"
                     :margin-top "6px"}}
       "Get your API key from "
       [:a {:href "https://openrouter.ai/keys"
            :target "_blank"
            :style {:color "#667eea"}}
        "openrouter.ai/keys"]]]

     ;; Model
     [:div {:style {:margin-bottom "24px"}}
      [:label {:style label-style}
       "Model"
       (when models-loading?
         [:span {:style {:margin-left "8px"
                         :font-size "12px"
                         :color "rgba(255,255,255,0.3)"}}
          "Loading models..."])]
      [:select
       {:value model
        :on-change #(swap! chat-settings-state assoc :model (.. % -target -value))
        :style input-style}
       (if (seq available-models)
         ;; Dynamic model list
         (for [m available-models]
           (let [id (:id m)
                 mname (or (:name m) id)]
             [:option {:key id :value id} mname]))
         ;; Default options before loading
         (list
           [:option {:key "anthropic/claude-sonnet-4.6" :value "anthropic/claude-sonnet-4.6"} "Claude Sonnet 4"]
           [:option {:key "anthropic/claude-haiku-4.5" :value "anthropic/claude-haiku-4.5"} "Claude Haiku 4"]
           [:option {:key "openai/gpt-4o" :value "openai/gpt-4o"} "GPT-4o"]
           [:option {:key "google/gemini-3.1-pro-preview" :value "google/gemini-3.1-pro-preview"} "Gemini 3 Pro"]
           [:option {:key "deepseek/deepseek-chat-v3-0324" :value "deepseek/deepseek-chat-v3-0324"} "DeepSeek V3"]))]
      (when (seq available-models)
        [:div {:style {:font-size "12px"
                       :color "rgba(255,255,255,0.3)"
                       :margin-top "6px"}}
         (str (count available-models) " models available from OpenRouter")])]

     ;; Save button
     [:button
      {:on-click save-chat-settings!
       :style (if saved?
                (merge btn-primary-style {:background "#52c41a"})
                btn-primary-style)}
      (if saved? "Saved!" "Save Chat Settings")]]))

;; ==================== Settings Page ====================
(rum/defcs settings-page
  < {:will-mount
     (fn [state]
       ;; Init profile data
       (init-profile!)
       ;; Init MCP state
       (when (mcp/mcp-available?)
         (mcp-state/init!))
       ;; Init Chat settings
       (init-chat-settings!)
       (load-chat-models!)
       ;; Set initial tab if provided
       (let [args (rest (:rum/args state))
             opts (first args)]
         (when-let [tab (:initial-tab opts)]
           (reset! settings-tab tab)))
       state)
     :will-unmount
     (fn [state]
       (mcp-state/cleanup!)
       state)}
  rum/reactive
  [state db & [opts]]
  (let [current-tab (rum/react settings-tab)
        database-name (get-current-database-name db)
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)]
    [:div.night-center-boxBg.night-textColor-2
     (sidebar/app-top-bar {:title "Settings"})
     [:div.page-wrapper
      ;; Left sidebar
      (sidebar/left-sidebar db database-name)
      ;; Main content area
      [:div.main-content-area
       {:class (when sidebar-collapsed? "sidebar-collapsed")}
       [:div.flex.flex-column
        {:style {:padding "20px"
                 :max-width "900px"
                 :margin "0 auto"}}

        ;; Page header
        [:h1 {:style {:font-size "24px"
                      :font-weight "600"
                      :margin "0 0 24px 0"}}
         "Settings"]

        ;; Tab bar
        [:div {:style {:display "flex"
                       :gap "8px"
                       :margin-bottom "32px"
                       :flex-wrap "wrap"}}
         (settings-tab-button "Profile" :profile current-tab #(reset! settings-tab :profile))
         (settings-tab-button "Token" :token current-tab #(reset! settings-tab :token))
         (settings-tab-button "MCP Servers" :mcp-servers current-tab #(reset! settings-tab :mcp-servers))
         (settings-tab-button "Chat" :chat current-tab
           #(do (reset! settings-tab :chat)
                (load-chat-models!)))]

        ;; Tab content
        (case current-tab
          :profile (profile-tab)
          :token (token-tab)
          :mcp-servers (mcp-servers-tab)
          :chat (chat-tab)
          nil)]]]]))

;; Keep backward compat - settings-modal is now a no-op
(rum/defc settings-modal []
  nil)
