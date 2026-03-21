(ns hulunote.settings
  (:require [rum.core :as rum]
            [hulunote.settings-state :as settings-state]
            [hulunote.settings.shared :as shared]
            [hulunote.settings.profile :as profile]
            [hulunote.settings.token :as token]
            [hulunote.settings.hotkeys :as hotkeys]
            [hulunote.settings.mcp-servers :as mcp-servers]
            [hulunote.settings.chat :as chat]
            [hulunote.util :as u]
            [hulunote.mcp-state :as mcp-state]))

(defonce settings-tab (atom :profile))
(defonce initialized-settings-version (atom nil))

(def settings-nav-items
  [{:key :profile :label "Profile"}
   {:key :token :label "Token"}
   {:key :shortcuts :label "Hotkeys"}
   {:key :mcp-servers :label "MCP Servers"}
   {:key :chat :label "Chat"}])

(defn open-settings!
  ([] (open-settings! :profile))
  ([initial-tab]
   (settings-state/open-settings! initial-tab)))

(defn close-settings! []
  (settings-state/close-settings!)
  (mcp-state/cleanup!))

(defn select-settings-tab! [tab-key]
  (reset! settings-tab tab-key)
  (when (= tab-key :chat)
    (shared/load-chat-models!)))

(defn ensure-settings-initialized! [{:keys [open? tab version]}]
  (when (and open?
             (not= version @initialized-settings-version))
    (reset! initialized-settings-version version)
    (shared/init-settings! tab #(reset! settings-tab %))))

(rum/defc settings-nav-item [item current-tab]
  (let [{:keys [key label]} item
        active? (= key current-tab)]
    [:button
     {:on-click #(select-settings-tab! key)
      :style {:width "100%"
              :padding "7px 12px"
              :border (if active?
                        "1px solid var(--theme-accent-30)"
                        "1px solid rgba(255,255,255,0)")
              :background (if active?
                            "var(--theme-accent-20)"
                            "transparent")
              :color "#fff"
              :opacity (if active? 1 0.8)
              :border-radius "8px"
              :cursor "pointer"
              :transition "all 0.15s ease"
              :text-align "left"}}
     [:div {:style {:font-size "13px"
                    :font-weight "600"}}
      label]]))

(defn render-settings-content [current-tab]
  (case current-tab
    :profile (profile/page)
    :token (token/page)
    :shortcuts (hotkeys/page)
    :mcp-servers (mcp-servers/page)
    :chat (chat/page)
    nil))

(rum/defc settings-shell < rum/reactive
  [{:keys [on-close overlay?]}]
  (let [current-tab (rum/react settings-tab)]
    [:div
     {:style (merge
               {:display "flex"
                :justify-content "center"
                :align-items "center"
                :padding "24px"}
               (when overlay?
                 {:position "fixed"
                  :inset "0"
                  :z-index 240
                  :background "rgba(10, 13, 20, 0.52)"
                  :backdrop-filter "blur(5px)"}))
      :on-click (when on-close (fn [_] (on-close)))}
     [:div
      {:style {:width "min(88vw, 1120px)"
               :height "min(84vh, 820px)"
               :position "relative"
               :background "var(--surface-panel)"
               :border "1px solid var(--surface-border-strong)"
               :border-radius "18px"
               :box-shadow "0 24px 72px rgba(0,0,0,0.38)"
               :overflow "hidden"
               :display "grid"
               :grid-template-columns "220px 1fr"}
       :on-click u/stop-click-bubble}
      (when on-close
        [:button
         {:on-click (fn [e]
                      (u/stop-click-bubble e)
                      (on-close))
          :style {:position "absolute"
                  :top "16px"
                  :right "18px"
                  :width "32px"
                  :height "32px"
                  :border "1px solid var(--surface-border)"
                  :border-radius "8px"
                  :background "transparent"
                  :color "rgba(255,255,255,0.72)"
                  :cursor "pointer"
                  :font-size "18px"
                  :line-height "1"
                  :z-index 1}}
         "×"])
      [:div
       {:style {:padding "18px 14px"
                :border-right "1px solid var(--surface-border)"
                :background "rgba(255,255,255,0.02)"
                :display "flex"
                :flex-direction "column"
                :gap "6px"}}
       [:div {:style {:padding "4px 8px 12px"}}
        [:div {:style {:font-size "18px"
                       :font-weight "700"
                       :color "#fff"}}
         "Settings"]]
       (for [item settings-nav-items]
         (rum/with-key
           (settings-nav-item item current-tab)
           (:key item)))]
      [:div
       {:style {:display "flex"
                :flex-direction "column"
                :min-width 0
                :min-height 0}}
       [:div
        {:style {:flex 1
                 :overflow-y "auto"
                 :padding "24px 28px"}}
        (render-settings-content current-tab)]]]]))

;; Route fallback for backward compatibility.
(rum/defcs settings-page
  < {:will-mount
     (fn [state]
       (let [args (rest (:rum/args state))
             opts (first args)]
         (shared/init-settings! (:initial-tab opts) #(reset! settings-tab %)))
       state)
     :will-unmount
     (fn [state]
       (mcp-state/cleanup!)
       state)}
  rum/reactive
  [state _db & [_opts]]
  [:div.night-center-boxBg.night-textColor-2
   {:style {:min-height "100vh"}}
   (settings-shell {:overlay? false})])

(rum/defc settings-modal < rum/reactive []
  (let [modal-state (rum/react settings-state/settings-modal-state)
        open? (:open? modal-state)]
    (ensure-settings-initialized! modal-state)
    (when open?
      (settings-shell {:overlay? true
                       :on-close close-settings!}))))
