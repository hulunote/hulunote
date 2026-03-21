(ns hulunote.settings.shared
  (:require [rum.core :as rum]
            [hulunote.storage :as storage]
            [hulunote.http :as http]
            [hulunote.util :as u]
            [hulunote.mcp :as mcp]
            [hulunote.mcp-state :as mcp-state]
            [hulunote.chat :as chat]
            [re-frame.core :as re-frame]
            [clojure.string :as str]
            [cljs.core.async :as a :refer [<! go]]))

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

(def button-base-style
  {:min-height "38px"
   :padding "0 18px"
   :border-radius "8px"
   :font-size "13px"
   :font-weight "600"
   :cursor "pointer"
   :display "inline-flex"
   :align-items "center"
   :justify-content "center"
   :gap "8px"
   :transition "all 0.15s ease"})

(def btn-primary-style
  (merge button-base-style
         {:border "1px solid var(--theme-accent-30)"
          :background "var(--theme-accent-20)"
          :color "#fff"}))

(def btn-success-style
  (merge button-base-style
         {:border "1px solid rgba(82,196,26,0.35)"
          :background "rgba(82,196,26,0.18)"
          :color "#d7ffd1"}))

(def btn-secondary-style
  (merge button-base-style
         {:border "1px solid var(--surface-border)"
          :background "rgba(255,255,255,0.02)"
          :color "rgba(255,255,255,0.86)"}))

(def btn-disabled-style
  (merge button-base-style
         {:border "1px solid rgba(255,255,255,0.08)"
          :background "rgba(255,255,255,0.05)"
          :color "rgba(255,255,255,0.35)"
          :cursor "not-allowed"}))

(rum/defc action-button
  [{:keys [on-click disabled? tone style]} & children]
  [:button
   {:on-click on-click
    :disabled disabled?
    :style (merge
             (cond
               disabled? btn-disabled-style
               (= tone :secondary) btn-secondary-style
               (= tone :success) btn-success-style
               :else btn-primary-style)
             style)}
   children])

(defn js->clj-safe [obj]
  (if (object? obj)
    (js->clj obj :keywordize-keys true)
    obj))

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

(defn init-profile! []
  (let [hulunote-info (:hulunote @storage/jwt-auth)]
    (reset! profile-state
            {:nickname (or (:accounts/nickname hulunote-info)
                           (:accounts/username hulunote-info) "")
             :introduction (or (:accounts/introduction hulunote-info) "")
             :avatar (or (:accounts/avatar hulunote-info) nil)
             :loading false})
    (reset! token-state {:generating false :new-token nil :expires-at nil :copied false})))

(defn init-settings! [initial-tab set-tab!]
  (init-profile!)
  (when (mcp/mcp-available?)
    (mcp-state/init!))
  (init-chat-settings!)
  (load-chat-models!)
  (set-tab! (or initial-tab :profile)))
