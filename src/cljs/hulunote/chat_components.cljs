(ns hulunote.chat-components
  (:require [clojure.string :as str]
            [rum.core :as rum]
            [hulunote.chat-core :as chat-core]
            [hulunote.chat-state :as chat-state]
            [hulunote.icon :as icon]
            [hulunote.settings-state :as settings-state]
            [hulunote.util :as u]
            ["react-markdown" :default ReactMarkdown]
            ["remark-gfm" :default remarkGfm]))

(defonce composer-ui-state
  (atom {:settings-open? false}))

(defonce composer-textarea-node
  (atom nil))

(defn close-composer-settings! []
  (swap! composer-ui-state assoc :settings-open? false))

(defn toggle-composer-settings! [evt]
  (u/stop-click-bubble evt)
  (swap! composer-ui-state update :settings-open? not))

(defn placeholder-add-context! [evt]
  (u/stop-click-bubble evt)
  (js/console.log "[chat-ui] add context is not implemented yet"))

(defn open-chat-settings! [evt]
  (u/stop-click-bubble evt)
  (close-composer-settings!)
  (settings-state/open-settings! :chat))

(defn start-new-chat! [evt]
  (u/stop-click-bubble evt)
  (close-composer-settings!)
  (chat-core/start-new-conversation!))

(defn send-on-enter! [evt]
  (when (and (= (.-key evt) "Enter")
             (not (.-shiftKey evt)))
    (.preventDefault evt)
    (chat-core/send-message!)))

(defn short-model-name [model]
  (let [raw (or model "")]
    (or (last (str/split raw #"/"))
        raw)))

(def composer-max-width "760px")

(defn thinking-summary-text
  [content]
  (let [text (or content "")]
    (cond
      (str/includes? text "Thinking...") "Thought for a moment"
      (str/includes? text "Tool Calls") "Reasoning"
      :else "View reasoning")))

(defn thinking-content?
  [content]
  (let [text (or content "")]
    (or (str/includes? text "Thinking...")
        (str/includes? text "--- Tool Calls ---")
        (str/includes? text "--- End ---"))))

(defn clean-thinking-content
  [content]
  (let [normalized (-> (or content "")
                       (str/replace #"--- Tool Calls ---" "")
                       (str/replace #"--- End ---" "")
                       (str/replace #"(?=🧠 Thinking\.\.\. \(step \d+/\d+\))" "\n")
                       (str/replace #"(?=Thinking\.\.\. \(step \d+/\d+\))" "\n")
                       (str/replace #"\s+ERROR:" "\nERROR:")
                       (str/replace #"\n+" "\n"))]
    (->> (str/split-lines normalized)
         (map str/trim)
         (remove str/blank?)
         (str/join "\n")
         (str/trim))))

(defn split-thinking-content
  [content]
  (let [lines (str/split-lines (or content ""))
        first-nonblank (some->> lines (remove str/blank?) first)
        thinking-start? (and first-nonblank
                             (or (str/includes? first-nonblank "Thinking...")
                                 (str/includes? first-nonblank "--- Tool Calls ---")))
        line-count (count lines)]
    (if-not thinking-start?
      {:thinking nil
       :body content}
      (loop [idx 0
             thinking-lines []]
        (if (>= idx line-count)
          {:thinking (str/trim (str/join "\n" thinking-lines))
           :body ""}
          (let [line (nth lines idx)
                next-thinking-lines (conj thinking-lines line)]
            (if (str/includes? line "--- End ---")
              {:thinking (str/trim (str/join "\n" next-thinking-lines))
               :body (str/trim (str/join "\n" (drop (inc idx) lines)))}
              (recur (inc idx) next-thinking-lines))))))))

(rum/defc markdown-block [content class-name]
  (rum/adapt-class ReactMarkdown
    {:className class-name
     :remarkPlugins #js [remarkGfm]
     :linkTarget "_blank"}
    content))

(rum/defc thinking-block [content]
  (let [summary-text (thinking-summary-text content)]
    [:details.chat-thinking-details
     {:style {:margin-bottom "14px"}}
     [:summary.chat-thinking-summary
      {:style {:display "flex"
               :align-items "center"
               :gap "8px"
               :cursor "pointer"
               :list-style "none"
               :font-size "14px"
               :color "var(--ui-text-muted)"
               :user-select "none"
               :outline "none"}}
      [:span.chat-thinking-chevron
       {:style {:font-size "12px"
                :line-height 1}}
       "›"]
      [:span summary-text]]
     [:div
      {:style {:margin-top "10px"
               :padding-left "18px"
               :border-left "1px solid var(--ui-border-subtle)"}}
      [:div.chat-thinking-log
       {:style {:white-space "pre-wrap"
                :word-break "break-word"
                :font-size "14px"
                :line-height "1.75"
                :color "var(--ui-text-muted)"}}
       (clean-thinking-content content)]]]))

(defn resize-textarea!
  [textarea]
  (when textarea
    (set! (.. textarea -style -height) "auto")
    (let [next-height (min 240 (max 60 (.-scrollHeight textarea)))]
      (set! (.. textarea -style -height) (str next-height "px"))
      (set! (.. textarea -style -overflowY)
            (if (>= (.-scrollHeight textarea) 240) "auto" "hidden")))))

(rum/defc message-bubble [msg]
  (let [{:keys [role content]} msg
        {:keys [thinking body]} (when (= role "assistant")
                                  (split-thinking-content content))
        is-user (= role "user")
        is-error (= role "error")
        is-system (= role "system")
        is-thinking (or (= role "thinking")
                        (and is-system (thinking-content? content)))
        is-assistant (and (not is-user) (not is-error) (not is-system) (not is-thinking))]
    [:div.message
     {:style {:display "flex"
              :justify-content (if is-user "flex-end" "flex-start")
              :margin-bottom "14px"}}
     (cond
       is-thinking
       [:div {:style {:width "100%"
                      :max-width "100%"}}
        (thinking-block content)]

       is-assistant
       [:div.message-bubble
        {:style {:max-width "100%"
                 :padding 0
                 :border "none"
                 :background "transparent"
                 :color "var(--ui-text-primary)"
                 :word-break "break-word"}}
        (when (thinking-content? thinking)
          (thinking-block thinking))
        (markdown-block (or body content) "chat-markdown chat-assistant-markdown")]

       :else
       [:div.message-bubble
        {:style {:max-width "78%"
                 :padding (cond
                            is-system "10px 12px"
                            is-user "6px 14px"
                            :else "14px 16px")
                 :border-radius (if is-user "18px" "18px 18px 18px 6px")
                 :background (cond
                               is-user "var(--ui-surface-hover)"
                               is-error "var(--ui-danger-soft)"
                               is-system "var(--ui-overlay-soft)"
                               :else "transparent")
                 :color (cond
                          is-user "var(--ui-text-primary)"
                          is-error "var(--ui-danger)"
                          is-system "var(--ui-text-muted)"
                          :else "var(--ui-text-primary)")
                 :white-space "pre-wrap"
                 :word-break "break-word"
                 :font-size (if is-system "12px" "15px")
                 :font-family (when is-system "monospace")
                 :line-height "1.6"
                 :border (cond
                           is-user "none"
                           is-system "1px solid var(--ui-border-subtle)"
                           is-error "1px solid var(--ui-border-danger)"
                           :else "1px solid var(--ui-border-subtle)")}}
        content])]))

(rum/defc settings-modal < rum/reactive []
  (let [{:keys [show-settings? api-key model available-models models-loading?]} (rum/react chat-state/chat-state)]
    (when show-settings?
      [:div.modal-overlay
       {:style {:position "fixed"
                :top 0 :left 0 :right 0 :bottom 0
                :background "var(--ui-overlay-modal-strong)"
                :display "flex"
                :align-items "center"
                :justify-content "center"
                :z-index 10000}
        :on-click #(swap! chat-state/chat-state assoc :show-settings? false)}
       [:div.modal-content
        {:style {:background "var(--ui-surface-modal)"
                 :border-radius "18px"
                 :padding "30px"
                 :min-width "460px"
                 :border "1px solid var(--ui-border-subtle)"
                 :box-shadow "var(--ui-shadow-modal)"}
         :on-click #(.stopPropagation %)}
        [:h2 {:style {:margin "0 0 24px 0"
                      :font-size "24px"
                      :font-weight "600"
                      :color "var(--ui-text-primary)"}}
         "Chat Settings"]
        [:div {:style {:margin-bottom "20px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "var(--ui-text-muted)"}}
          "OpenRouter API Key"]
         [:input
          {:type "password"
           :placeholder "sk-or-..."
           :value api-key
           :on-change #(swap! chat-state/chat-state assoc :api-key (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "1px solid var(--ui-border-strong)"
                   :border-radius "10px"
                   :font-size "14px"
                   :outline "none"
                   :box-sizing "border-box"
                   :background "var(--ui-surface-modal-input)"
                   :color "var(--ui-text-primary)"}}]
         [:div {:style {:font-size "12px"
                        :color "var(--ui-text-subtle)"
                        :margin-top "6px"}}
          "Get your API key from "
          [:a {:href "https://openrouter.ai/keys"
               :target "_blank"
               :style {:color "var(--ui-accent)"}}
           "openrouter.ai/keys"]]]
        [:div {:style {:margin-bottom "24px"}}
         [:label {:style {:display "block"
                          :margin-bottom "8px"
                          :font-size "14px"
                          :font-weight "500"
                          :color "var(--ui-text-muted)"}}
          "Model"
          (when models-loading?
            [:span {:style {:margin-left "8px"
                            :font-size "12px"
                            :color "var(--ui-text-subtle)"}}
             "Loading models..."])]
         [:select
          {:value model
           :on-change #(swap! chat-state/chat-state assoc :model (.. % -target -value))
           :style {:width "100%"
                   :padding "12px 16px"
                   :border "1px solid var(--ui-border-strong)"
                   :border-radius "10px"
                   :font-size "14px"
                   :outline "none"
                   :box-sizing "border-box"
                   :background "var(--ui-surface-modal-input)"
                   :color "var(--ui-text-primary)"}}
          (if (seq available-models)
            (for [m available-models]
              (let [id (:id m)
                    name (or (:name m) id)]
                [:option {:key id :value id} name]))
            (list
              [:option {:key "anthropic/claude-sonnet-4.6" :value "anthropic/claude-sonnet-4.6"} "Claude Sonnet 4"]
              [:option {:key "anthropic/claude-haiku-4.5" :value "anthropic/claude-haiku-4.5"} "Claude Haiku 4"]
              [:option {:key "openai/gpt-4o" :value "openai/gpt-4o"} "GPT-4o"]
              [:option {:key "google/gemini-3.1-pro-preview" :value "google/gemini-3.1-pro-preview"} "Gemini 3 Pro"]
              [:option {:key "deepseek/deepseek-chat-v3-0324" :value "deepseek/deepseek-chat-v3-0324"} "DeepSeek V3"]))]
         (when (seq available-models)
           [:div {:style {:font-size "12px"
                          :color "var(--ui-text-subtle)"
                          :margin-top "6px"}}
            (str (count available-models) " models available from OpenRouter")])]
        [:div {:style {:display "flex"
                       :justify-content "flex-end"
                       :gap "12px"}}
         [:button.pointer
          {:on-click #(swap! chat-state/chat-state assoc :show-settings? false)
           :style {:padding "12px 24px"
                   :border "1px solid var(--ui-border-control)"
                   :border-radius "10px"
                   :background "transparent"
                   :font-size "16px"
                   :font-weight "500"
                   :color "var(--ui-control-text)"
                   :cursor "pointer"}}
          "Cancel"]
         [:button.pointer
          {:on-click #(do
                        (chat-core/save-api-key! api-key)
                        (chat-core/save-model! model))
           :style {:padding "12px 24px"
                   :border "none"
                   :border-radius "10px"
                   :background "var(--ui-accent-gradient)"
                   :font-size "16px"
                   :font-weight "600"
                   :color "var(--ui-accent-text)"
                   :cursor "pointer"}}
          "Save"]]]])))

(defn icon-button-style [active?]
  {:width "36px"
   :height "36px"
   :border-radius "999px"
   :display "flex"
   :align-items "center"
   :justify-content "center"
   :border "none"
   :background (if active? "var(--ui-surface-hover)" "transparent")
   :cursor "pointer"
   :padding 0})

(defn top-toolbar-button-style []
  {:width "36px"
   :height "36px"
   :border-radius "10px"
   :display "flex"
   :align-items "center"
   :justify-content "center"
   :border "none"
   :background "transparent"
   :cursor "pointer"
   :padding 0})

(rum/defc chat-top-toolbar []
  [:div
   {:style {:width "100%"
            :display "flex"
            :align-items "center"
            :justify-content "flex-end"
            :padding "10px 12px 0 12px"
            :box-sizing "border-box"
            :flex-shrink 0}}
   [:button.pointer
    {:on-click start-new-chat!
     :title "New chat"
     :style (top-toolbar-button-style)}
    (icon/svg-icon {:name "new_chat"
                    :style {:width "20px"
                            :height "20px"
                            :color "var(--ui-text-primary)"}})]])

(defn toggle-pill-style [enabled?]
  {:width "34px"
   :height "20px"
   :display "inline-block"
   :flex-shrink 0
   :border-radius "999px"
   :background (if enabled? "var(--ui-accent)" "var(--ui-border-strong)")
   :position "relative"
   :transition "all 140ms ease"})

(defn toggle-knob-style [enabled?]
  {:position "absolute"
   :top "2px"
   :left (if enabled? "16px" "2px")
   :width "16px"
   :height "16px"
   :display "block"
   :border-radius "50%"
   :background (if enabled? "white" "var(--ui-surface-panel)")
   :box-shadow (if enabled?
                "0 1px 2px rgba(0,0,0,0.12)"
                "0 1px 2px rgba(0,0,0,0.10), inset 0 0 0 1px var(--ui-border-subtle)")
   :transition "all 140ms ease"})

(rum/defc quick-settings-popover < rum/reactive [connected-count]
  (let [{:keys [use-tools? api-key-set? model messages]} (rum/react chat-state/chat-state)]
    [:div
     {:style {:position "absolute"
              :left 0
              :bottom "48px"
              :width "260px"
              :box-sizing "border-box"
              :padding "8px"
              :border-radius "16px"
              :background "var(--ui-surface-popover)"
              :border "1px solid var(--ui-border-subtle)"
              :box-shadow "var(--ui-shadow-popover)"
              :z-index 20}}
     [:div
     {:style {:display "grid"
               :grid-template-columns "minmax(0, 1fr) auto"
               :align-items "center"
               :column-gap "12px"
               :padding "10px 6px 10px 6px"
               :background "transparent"}}
      [:div {:style {:min-width 0
                     :flex 1}}
       [:div {:style {:font-size "13px"
                      :font-weight "600"
                      :color "var(--ui-text-primary)"}}
        "Use MCP tools"]
       [:div {:style {:font-size "12px"
                      :margin-top "2px"
                      :color "var(--ui-text-subtle)"}}
        (str connected-count " server(s) connected")]]
      [:button.pointer
       {:on-click #(do (u/stop-click-bubble %)
                       (chat-core/toggle-use-tools!))
        :style {:border "none"
                :background "transparent"
                :padding 0
                :justify-self "end"
                :cursor "pointer"}}
       [:span {:style (toggle-pill-style use-tools?)}
        [:span {:style (toggle-knob-style use-tools?)}]]]]
     [:div
      {:style {:display "grid"
               :gap "8px"
               :padding "10px 6px 8px 6px"}}
      [:div
       {:style {:display "flex"
                :justify-content "space-between"
                :font-size "12px"
                :color "var(--ui-text-subtle)"}}
       [:span "API key"]
       [:span {:style {:color (if api-key-set? "var(--ui-success)" "var(--ui-danger)")}}
        (if api-key-set? "Configured" "Missing")]]
      [:div
       {:style {:display "flex"
                :justify-content "space-between"
                :font-size "12px"
                :color "var(--ui-text-subtle)"}}
       [:span "Model"]
       [:span {:style {:max-width "150px"
                       :overflow "hidden"
                       :text-overflow "ellipsis"
                       :white-space "nowrap"
                       :color "var(--ui-text-muted)"}}
        (short-model-name model)]]]
     [:div {:style {:height "1px"
                    :margin "4px 0"
                    :background "var(--ui-border-subtle)"}}]
     [:button.pointer
      {:on-click open-chat-settings!
       :style {:width "100%"
               :display "flex"
               :align-items "center"
               :justify-content "space-between"
               :padding "10px 12px"
               :border "none"
               :border-radius "12px"
               :background "transparent"
               :color "var(--ui-text-primary)"
               :font-size "13px"
               :cursor "pointer"}}
      [:span "Chat Settings"]
      (icon/svg-icon {:name "keyboard_arrow_down"
                      :style {:width "16px"
                              :height "16px"
                              :transform "rotate(-90deg)"
                              :color "var(--ui-text-subtle)"}})]
     (when (seq messages)
       [:button.pointer
        {:on-click #(do
                      (u/stop-click-bubble %)
                      (chat-core/clear-messages!)
                      (close-composer-settings!))
         :style {:width "100%"
                 :display "flex"
                 :align-items "center"
                 :justify-content "space-between"
                 :padding "10px 12px"
                 :border "none"
                 :border-radius "12px"
                 :background "transparent"
                 :color "var(--ui-text-primary)"
                 :font-size "13px"
                 :cursor "pointer"}}
        [:span "Clear conversation"]
        (icon/svg-icon {:name "reset"
                        :style {:width "16px"
                                :height "16px"
                                :color "var(--ui-text-subtle)"}})])]))

(rum/defcs composer
  < rum/reactive
  {:did-mount (fn [state]
                (resize-textarea! @composer-textarea-node)
                state)
   :did-update (fn [state]
                 (resize-textarea! @composer-textarea-node)
                 state)}
  [state connected-count]
  (let [{:keys [input loading? api-key-set?]} (rum/react chat-state/chat-state)
        {:keys [settings-open?]} (rum/react composer-ui-state)
        send-disabled? (or (str/blank? input) (not api-key-set?))]
    [:div
     {:style {:width "100%"
              :max-width composer-max-width
              :margin "0 auto"
              :padding "14px 16px 12px 16px"
              :border-radius "26px"
              :background "var(--ui-surface-panel)"
              :border "1px solid var(--ui-border-subtle)"
              :box-shadow "0 8px 20px rgba(0,0,0,0.04), 0 1px 3px rgba(0,0,0,0.03)"}}
     [:textarea
      {:value input
       :disabled loading?
       :class "chat-composer-textarea"
       :placeholder (if api-key-set?
                      "Ask Hulunote AI to write, organize, connect, or expand your notes…"
                      "Set up your API key in Settings to start chatting…")
       :rows 2
       :ref (fn [node]
              (reset! composer-textarea-node node)
              (resize-textarea! node))
       :on-click #(.stopPropagation %)
       :on-change #(do
                     (chat-core/set-input! (.. % -target -value))
                     (resize-textarea! (.-target %)))
       :on-key-down send-on-enter!
       :style {:width "100%"
               :min-height "60px"
               :max-height "240px"
               :resize "none"
               :padding 0
               :border "none"
               :background "transparent"
               :outline "none"
               :box-sizing "border-box"
               :font-size "16px"
               :line-height "1.6"
               :color "var(--ui-text-primary)"
               :overflow-y "hidden"
               :font-family "inherit"}}]
     [:div
      {:style {:position "relative"
               :display "flex"
               :align-items "center"
               :justify-content "space-between"}}
     [:div {:style {:display "flex"
                     :align-items "center"
                     :gap "8px"
                     :position "relative"}}
       [:button.pointer
        {:on-click placeholder-add-context!
         :title "Add context"
         :style (icon-button-style false)}
        (icon/svg-icon {:name "add"
                        :style {:width "20px"
                                :height "20px"
                                :color "var(--ui-text-subtle)"}})]
       [:div {:style {:position "relative"}}
        [:button.pointer
         {:on-click toggle-composer-settings!
          :title "Quick settings"
          :style (icon-button-style settings-open?)}
         (icon/svg-icon {:name "tune"
                         :style {:width "20px"
                                 :height "20px"
                                 :color "var(--ui-text-subtle)"}})]
        (when settings-open?
          (quick-settings-popover connected-count))]]
      [:div {:style {:display "flex"
                     :align-items "center"}}
       [:button.pointer
        {:on-click (if loading? chat-core/stop-message! chat-core/send-message!)
         :disabled (when-not loading? send-disabled?)
         :title (if loading? "Stop" "Send")
         :style {:width "36px"
                 :height "36px"
                 :border "none"
                 :border-radius "50%"
                 :display "flex"
                 :align-items "center"
                 :justify-content "center"
                 :padding 0
                 :background "transparent"
                 :color (if (and (not loading?) send-disabled?)
                          "var(--ui-text-subtle)"
                          "var(--ui-text-primary)")
                 :cursor (if (or loading? (not send-disabled?)) "pointer" "not-allowed")
                 :opacity (when (and (not loading?) send-disabled?) "0.4")}}
        (icon/svg-icon {:name (if loading? "stop_circle" "send_up")
                        :style {:width "26px"
                                :height "26px"}})]]]]))

(rum/defc empty-state < rum/reactive [connected-count]
  [:div
   {:style {:width "100%"
            :max-width composer-max-width
            :display "flex"
            :flex-direction "column"
            :align-items "center"
            :gap "26px"
            :padding "0 24px"
            :margin-bottom "10vh"}}
    [:img {:src (u/asset-path "/img/hulunote.webp")
           :style {:width "72px"
                   :height "72px"
                   :object-fit "cover"
                   :border-radius "20px"
                   :box-shadow "0 10px 24px rgba(0,0,0,0.10), 0 1px 3px rgba(0,0,0,0.06)"}}]
    [:div {:style {:text-align "center"}} 
     [:div {:style {:font-size "42px"
                    :font-weight "700"
                    :line-height "1.18"
                    :letter-spacing "-0.03em"
                    :color "var(--ui-text-primary)"}}
      "Think with your notes, not beside them"]
     [:div {:style {:margin-top "10px"
                    :font-size "16px"
                    :line-height "1.7"
                    :color "var(--ui-text-subtle)"}}
      "Turn ideas into notes, links, and structure with AI."]]
    (composer connected-count)])

(rum/defc loading-bubble []
  [:div {:style {:display "flex"
                 :justify-content "flex-start"
                 :margin-bottom "14px"}}
   [:div {:style {:padding "12px 14px"
                  :background "var(--ui-overlay-soft)"
                  :border-radius "18px"
                  :border "1px solid var(--ui-border-subtle)"
                  :color "var(--ui-text-muted)"
                  :font-size "14px"
                  :display "flex"
                  :align-items "center"
                  :gap "8px"}}
    [:span {:style {:display "inline-block"
                    :width "8px"
                    :height "8px"
                    :border-radius "50%"
                    :background "var(--ui-accent)"
                    :animation "pulse 1.5s ease-in-out infinite"}}]
    "Thinking..."]])

(rum/defc conversation-view < rum/reactive [connected-count]
  (let [{:keys [messages loading?]} (rum/react chat-state/chat-state)]
    [:div
     {:style {:flex 1
              :display "flex"
              :flex-direction "column"
              :min-height 0
              :padding "0 32px 28px 32px"}}
     [:div
      {:style {:flex 1
               :min-height 0
               :overflow-y "auto"
               :padding "0 6px 18px 6px"}}
      [:div {:style {:max-width composer-max-width
                     :margin "0 auto"}}
       (for [[idx msg] (map-indexed vector messages)]
         (rum/with-key (message-bubble msg) idx))
       (when loading?
         (loading-bubble))]]
     (composer connected-count)]))

(rum/defc chat-panel < rum/reactive [connected-count]
  (let [{:keys [messages]} (rum/react chat-state/chat-state)
        has-messages? (seq messages)]
    [:div
     {:style {:height "calc(100vh - var(--app-topbar-height, 0px))"
              :display "flex"
              :flex-direction "column"
              :background "var(--ui-content-bg)"
              :overflow "hidden"}
      :on-click (fn [_]
                  (close-composer-settings!))}
     (if has-messages?
       [:<>
        (chat-top-toolbar)
        (conversation-view connected-count)]
       [:div
        {:style {:flex 1
                 :min-height 0
                 :display "flex"
                 :flex-direction "column"
                 :align-items "center"
                 :justify-content "center"}}
        (empty-state connected-count)])]))
