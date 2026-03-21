(ns hulunote.settings.hotkeys
  (:require [rum.core :as rum]
            [hulunote.commands :as commands]
            [hulunote.icon :as icon]
            [hulunote.shortcuts :as shortcuts]
            [hulunote.shortcut-state :as shortcut-state]
            [hulunote.settings.shared :as shared]))

(def inside-block-shortcuts
  [{:id "block.indent"
    :title "Indent"
    :shortcut "Tab"}
   {:id "block.outdent"
    :title "Outdent"
    :shortcut "Shift+Tab"}
   {:id "block.new-sibling"
    :title "New Sibling"
    :shortcut "Enter"}
   {:id "block.cancel-editing"
    :title "Cancel Editing"
    :shortcut "Escape"}
   {:id "block.delete-empty"
    :title "Delete Empty Block"
    :shortcut "Backspace"}])

(defn everywhere-commands
  []
  (->> (commands/all-commands)
       (filter :customizable?)
       (sort-by (fn [{:keys [category title]}]
                  [(shortcuts/sortable-category-index category)
                   title]))))

(defn toggle-recording!
  [command-id]
  (if (= command-id (:command-id @shortcut-state/shortcut-recording-state))
    (shortcut-state/stop-recording!)
    (shortcut-state/start-recording! command-id)))

(defn commit-shortcut!
  [command shortcut]
  (let [normalized (shortcuts/normalize-shortcut shortcut)]
    (if-let [conflict (shortcuts/conflicting-command (:id command) normalized)]
      (shortcut-state/set-recording-error!
        (str "Already used by " (:title conflict)))
      (do
        (if (= normalized
               (shortcuts/normalize-shortcut (:default-shortcut command)))
          (shortcut-state/clear-user-shortcut! (:id command))
          (shortcut-state/set-user-shortcut! (:id command) normalized))
        (shortcut-state/stop-recording!)))))

(defn handle-record-keydown!
  [command e]
  (.preventDefault e)
  (.stopPropagation e)
  (case (.-key e)
    "Escape" (shortcut-state/stop-recording!)
    (when-let [shortcut (shortcuts/event->shortcut e)]
      (commit-shortcut! command shortcut))))

(rum/defc icon-button
  [{:keys [title on-click icon-name disabled?]}]
  [:button
   {:title title
    :on-click (fn [e]
                (.preventDefault e)
                (.stopPropagation e)
                (on-click))
    :disabled disabled?
    :style {:width "28px"
            :height "28px"
            :padding 0
            :border "1px solid var(--surface-border)"
            :border-radius "7px"
            :background "rgba(255,255,255,0.02)"
            :color (if disabled?
                     "rgba(255,255,255,0.24)"
                     "rgba(255,255,255,0.72)")
            :cursor (if disabled? "not-allowed" "pointer")
            :display "inline-flex"
            :align-items "center"
            :justify-content "center"
            :flex-shrink 0}}
   (icon/svg-icon
     {:name icon-name
      :style {:width "16px"
              :height "16px"
              :opacity (if disabled? 0.24 0.72)
              :filter "brightness(0) invert(1)"}})])

(rum/defc shortcut-button < rum/reactive [command]
  (let [{:keys [id]} command
        recording-state (rum/react shortcut-state/shortcut-recording-state)
        recording? (= id (:command-id recording-state))
        effective (shortcuts/effective-shortcut command)]
    [:button
     {:on-click #(toggle-recording! id)
      :on-key-down #(handle-record-keydown! command %)
     :on-blur #(when recording?
                  (js/setTimeout
                    (fn []
                      (when (= id (:command-id @shortcut-state/shortcut-recording-state))
                        (shortcut-state/stop-recording!)))
                    0))
      :style {:min-width "146px"
              :height "28px"
              :padding "0 10px"
              :border-radius "7px"
              :border (if recording?
                        "1px solid var(--theme-accent-30)"
                        "1px solid var(--surface-border)")
              :background (if recording?
                            "var(--theme-accent-20)"
                            "rgba(255,255,255,0.02)")
              :color "#fff"
              :font-size "12px"
              :font-weight "600"
              :cursor "pointer"
              :display "inline-flex"
              :align-items "center"
              :justify-content "center"
              :line-height "1"
              :outline "none"}}
     (if recording?
       "Press shortcut"
       (or (shortcuts/display-shortcut effective) "Blank"))]))

(rum/defc shortcut-row < rum/reactive [command]
  (let [{:keys [id title default-shortcut]} command
        recording-state (rum/react shortcut-state/shortcut-recording-state)
        _user-shortcut (rum/react shortcut-state/user-shortcut-overrides)
        has-user-override? (shortcut-state/has-user-shortcut-override? id)
        current-shortcut (shortcuts/effective-shortcut command)
        recording? (= id (:command-id recording-state))
        error-message (when recording?
                        (:error recording-state))]
    [:div
     {:style {:padding "10px 0"
              :display "flex"
              :align-items "center"
              :justify-content "space-between"
              :gap "20px"
              :border-bottom "1px solid rgba(255,255,255,0.06)"}}
     [:div {:style {:min-width 0}}
      [:div {:style {:font-size "14px"
                     :font-weight "600"
                     :color "#fff"}}
       title]]
     [:div {:style {:display "flex"
                    :flex-direction "column"
                    :align-items "flex-end"
                    :gap "6px"
                    :flex-shrink 0}}
      [:div {:style {:display "flex"
                     :align-items "center"
                     :gap "8px"}}
       (shortcut-button command)
       (when (seq (shortcuts/normalize-shortcut current-shortcut))
         (icon-button
           {:title "Clear shortcut"
            :icon-name "delete"
            :on-click #(do
                         (shortcut-state/blank-user-shortcut! id)
                         (shortcut-state/stop-recording!))}))
       (when (and default-shortcut has-user-override?)
         (icon-button
           {:title "Reset to default"
            :icon-name "reset"
            :on-click #(do
                         (shortcut-state/clear-user-shortcut! id)
                         (shortcut-state/stop-recording!))})) ]
      (cond
        error-message
        [:div {:style {:font-size "12px"
                       :color "#ff9c9c"}}
         error-message]

        recording?
        [:div {:style {:font-size "12px"
                       :color "rgba(255,255,255,0.36)"}}
         "Press a new shortcut or Esc to cancel"]

        :else nil)]]))

(rum/defc readonly-shortcut-row [item]
  [:div
   {:style {:padding "10px 0"
            :display "flex"
            :align-items "center"
            :justify-content "space-between"
            :gap "20px"
            :border-bottom "1px solid rgba(255,255,255,0.06)"}}
   [:div {:style {:min-width 0}}
    [:div {:style {:font-size "14px"
                   :font-weight "600"
                   :color "#fff"}}
     (:title item)]]
   [:div {:style {:min-width "144px"
                  :min-height "34px"
                  :padding "0 12px"
                  :border-radius "8px"
                  :border "1px solid var(--surface-border)"
                  :background "rgba(255,255,255,0.02)"
                  :color "rgba(255,255,255,0.8)"
                  :font-size "12px"
                  :font-weight "600"
                  :display "inline-flex"
                  :align-items "center"
                  :justify-content "center"}}
    (or (shortcuts/display-shortcut (:shortcut item))
        (:shortcut item))]])

(rum/defc section [title items renderer]
  [:div {:style {:margin-bottom "22px"}}
   [:div {:style {:font-size "15px"
                  :font-weight "800"
                  :letter-spacing "0.02em"
                  :color "rgba(255,255,255,0.7)"
                  :margin-bottom "8px"}}
    title]
   [:div {:style {:border-top "2px solid rgba(255,255,255,0.12)"}}
    (for [item items]
      (rum/with-key
        (renderer item)
        (:id item)))]])

(rum/defc page < rum/reactive []
  [:div {:style {:padding "24px 0"}}
   (section "Everywhere" (everywhere-commands) shortcut-row)
   (section "Inside a Block" inside-block-shortcuts readonly-shortcut-row)])
