(ns hulunote.shortcuts
  (:require [clojure.string :as str]
            [hulunote.commands :as commands]
            [hulunote.settings-state :as settings-state]
            [hulunote.shortcut-state :as shortcut-state]))

(defonce installed-handler (atom nil))

(def modifier-order
  ["Mod" "Alt" "Shift"])

(def category-order
  [:general :notes :navigation :database :plugins])

(defn editable-target?
  [target]
  (boolean
    (when target
      (or (when-let [tag-name (some-> target .-tagName str/lower-case)]
            (#{"input" "textarea" "select"} tag-name))
          (.-isContentEditable target)
          (when-let [closest-fn (.-closest target)]
            (or (.closest target "[contenteditable='true']")
                (.closest target ".CodeMirror")
                (.closest target ".cm-editor")))))))

(defn normalize-key
  [key]
  (when (seq key)
    (case key
      "Esc" "Escape"
      "Spacebar" "Space"
      " " "Space"
      "ArrowUp" "ArrowUp"
      "ArrowDown" "ArrowDown"
      "ArrowLeft" "ArrowLeft"
      "ArrowRight" "ArrowRight"
      "Enter" "Enter"
      "Escape" "Escape"
      "Tab" "Tab"
      "Backspace" "Backspace"
      "Delete" "Delete"
      "," ","
      "." "."
      "/" "/"
      "\\" "\\"
      (if (= 1 (count key))
        (str/upper-case key)
        key))))

(defn normalize-shortcut
  [shortcut]
  (when (seq shortcut)
    (let [parts (->> (str/split shortcut #"\+")
                     (map str/trim)
                     (remove str/blank?))
          normalized (map (fn [part]
                            (case (str/lower-case part)
                              ("cmd" "command" "meta" "ctrl" "control" "mod") "Mod"
                              ("alt" "option") "Alt"
                              "shift" "Shift"
                              (normalize-key part)))
                          parts)
          modifiers (->> normalized
                         (filter #(#{"Mod" "Alt" "Shift"} %))
                         distinct
                         (sort-by #(.indexOf modifier-order %)))
          main-key (some #(when-not (#{"Mod" "Alt" "Shift"} %) %) normalized)]
      (when main-key
        (str/join "+" (concat modifiers [main-key]))))))

(defn mac-platform?
  []
  (boolean (re-find #"Mac|iPhone|iPad" (or (.-platform js/navigator) ""))))

(defn display-shortcut
  [shortcut]
  (when-let [normalized (normalize-shortcut shortcut)]
    (let [parts (str/split normalized #"\+")
          mac? (mac-platform?)]
      (str/join
        (if mac? "" " + ")
        (map (fn [part]
               (case part
                 "Mod" (if mac? "⌘" "Ctrl")
                 "Alt" (if mac? "⌥" "Alt")
                 "Shift" (if mac? "⇧" "Shift")
                 "ArrowUp" "↑"
                 "ArrowDown" "↓"
                 "ArrowLeft" "←"
                 "ArrowRight" "→"
                 "Escape" "Esc"
                 "Backspace" "⌫"
                 "Delete" "Del"
                 "Space" "Space"
                 part))
             parts)))))

(defn event->shortcut
  [e]
  (let [key (normalize-key (.-key e))]
    (when (and key
               (not (#{"Meta" "Control" "Shift" "Alt"} key)))
      (normalize-shortcut
        (str/join "+"
                  (concat
                    (cond-> []
                      (or (.-metaKey e) (.-ctrlKey e)) (conj "Mod")
                      (.-altKey e) (conj "Alt")
                      (.-shiftKey e) (conj "Shift"))
                    [key]))))))

(defn current-scope
  [e]
  (cond
    (:open? @settings-state/settings-modal-state) :modal
    (editable-target? (.-target e)) :input
    :else :global))

(defn effective-shortcut
  [command]
  (let [command-id (:id command)]
    (cond
      (shortcut-state/has-user-shortcut-override? command-id)
      (shortcut-state/get-user-shortcut command-id)

      (contains? @shortcut-state/plugin-shortcut-overrides command-id)
      (get @shortcut-state/plugin-shortcut-overrides command-id)

      :else
      (:default-shortcut command))))

(defn conflicting-command
  [command-id shortcut]
  (when-let [normalized (normalize-shortcut shortcut)]
    (some (fn [command]
            (when (and (not= (:id command) command-id)
                       (= (normalize-shortcut (effective-shortcut command))
                          normalized))
              command))
          (commands/all-commands))))

(defn sortable-category-index
  [category]
  (or (first
        (keep-indexed (fn [idx item]
                        (when (= item category)
                          idx))
                      category-order))
      999))

(defn command-match?
  [command shortcut scope]
  (and shortcut
       (= (normalize-shortcut (effective-shortcut command)) shortcut)
       (if-let [enabled? (:enabled? command)]
         (enabled? {:scope scope})
         true)
       (contains? (:scopes command) scope)
       (or (not= scope :input)
           (:allow-in-input? command))))

(defn matching-command
  [e]
  (let [shortcut (event->shortcut e)
        scope (current-scope e)]
    (some (fn [command]
            (when (command-match? command shortcut scope)
              command))
          (commands/all-commands))))

(defn handle-keydown!
  [e]
  (when-not (:command-id @shortcut-state/shortcut-recording-state)
    (when-let [command (matching-command e)]
      (.preventDefault e)
      (.stopPropagation e)
      (commands/execute-command! (:id command)
                                 {:event e
                                  :scope (current-scope e)})
      true)))

(defn install-global-shortcuts!
  []
  (when-let [handler @installed-handler]
    (.removeEventListener js/document "keydown" handler))
  (let [handler (fn [e]
                  (handle-keydown! e))]
    (.addEventListener js/document "keydown" handler)
    (reset! installed-handler handler)))

(defn uninstall-global-shortcuts!
  []
  (when-let [handler @installed-handler]
    (.removeEventListener js/document "keydown" handler)
    (reset! installed-handler nil)))
