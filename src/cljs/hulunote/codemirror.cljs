(ns hulunote.codemirror
  "CodeMirror integration for code blocks in the outliner.

   A block whose content is wrapped in ``` fences is rendered as a
   CodeMirror editor instead of the normal text input.

   Content format:
     ```js
     console.log('hello');
     ```

   The language tag after ``` is optional. Supported: js, css, clojure, html, markdown."
  (:require ["codemirror" :as CodeMirror]
            ["codemirror/mode/javascript/javascript"]
            ["codemirror/mode/css/css"]
            ["codemirror/mode/clojure/clojure"]
            ["codemirror/mode/htmlmixed/htmlmixed"]
            ["codemirror/mode/markdown/markdown"]
            ["codemirror/mode/xml/xml"]
            [clojure.string :as str]))

;; ==================== CSS Injection ====================

(defonce ^:private css-injected? (atom false))

(def ^:private codemirror-css
  "Core CodeMirror CSS + dark theme overrides, injected once."
  "
/* ---- Hulunote CodeMirror overrides ---- */
.hulunote-cm-wrapper {
  width: 100%;
  margin: 4px 0;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--code-block-border);
  font-size: 13px;
  line-height: 1.5;
}
.hulunote-cm-wrapper .CodeMirror {
  background: var(--code-block-bg) !important;
  color: var(--code-block-text) !important;
  height: auto;
  font-family: 'SF Mono', 'Fira Code', 'JetBrains Mono', Menlo, Monaco, monospace;
  font-size: 13px;
  line-height: 1.6;
  padding: 0;
}
.hulunote-cm-wrapper .CodeMirror-lines {
  padding: 8px 0;
}
.hulunote-cm-wrapper .CodeMirror-gutters {
  background: var(--code-block-gutter-bg) !important;
  border-right: 1px solid var(--code-block-border) !important;
}
.hulunote-cm-wrapper .CodeMirror-linenumber {
  color: var(--code-block-subtle) !important;
  padding: 0 8px 0 4px;
}
.hulunote-cm-wrapper .CodeMirror-cursor {
  border-left-color: var(--code-block-cursor) !important;
}
.hulunote-cm-wrapper .CodeMirror-selected {
  background: var(--code-block-selection) !important;
}
.hulunote-cm-wrapper .CodeMirror-focused .CodeMirror-selected {
  background: var(--code-block-selection-focused) !important;
}
/* Syntax highlighting — dark theme */
.hulunote-cm-wrapper .cm-keyword   { color: var(--code-block-keyword) !important; }
.hulunote-cm-wrapper .cm-def       { color: var(--code-block-def) !important; }
.hulunote-cm-wrapper .cm-variable  { color: var(--code-block-variable) !important; }
.hulunote-cm-wrapper .cm-variable-2 { color: var(--code-block-variable-2) !important; }
.hulunote-cm-wrapper .cm-variable-3 { color: var(--code-block-variable-3) !important; }
.hulunote-cm-wrapper .cm-type      { color: var(--code-block-variable-3) !important; }
.hulunote-cm-wrapper .cm-operator  { color: var(--code-block-operator) !important; }
.hulunote-cm-wrapper .cm-number    { color: var(--code-block-number) !important; }
.hulunote-cm-wrapper .cm-string    { color: var(--code-block-string) !important; }
.hulunote-cm-wrapper .cm-string-2  { color: var(--code-block-string) !important; }
.hulunote-cm-wrapper .cm-comment   { color: var(--code-block-comment) !important; font-style: italic; }
.hulunote-cm-wrapper .cm-atom      { color: var(--code-block-number) !important; }
.hulunote-cm-wrapper .cm-meta      { color: var(--code-block-variable-3) !important; }
.hulunote-cm-wrapper .cm-tag       { color: var(--code-block-variable-2) !important; }
.hulunote-cm-wrapper .cm-attribute { color: var(--code-block-keyword) !important; }
.hulunote-cm-wrapper .cm-property  { color: var(--code-block-def) !important; }
.hulunote-cm-wrapper .cm-qualifier { color: var(--code-block-keyword) !important; }
.hulunote-cm-wrapper .cm-builtin   { color: var(--code-block-variable-3) !important; }
.hulunote-cm-wrapper .cm-bracket   { color: var(--code-block-operator) !important; }
.hulunote-cm-wrapper .cm-header    { color: var(--code-block-def) !important; font-weight: bold; }
.hulunote-cm-wrapper .cm-link      { color: var(--code-block-string) !important; }
/* Language badge */
.hulunote-cm-lang-badge {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 10px;
  background: var(--code-block-gutter-bg);
  border-bottom: 1px solid var(--code-block-border);
  font-size: 11px;
  color: var(--code-block-muted);
  font-family: -apple-system, BlinkMacSystemFont, sans-serif;
  user-select: none;
}
.hulunote-cm-lang-badge span {
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.hulunote-cm-lang-select {
  background: transparent;
  color: var(--code-block-muted);
  border: 1px solid transparent;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  cursor: pointer;
  outline: none;
  font-family: -apple-system, BlinkMacSystemFont, sans-serif;
  padding: 2px 4px;
  border-radius: 3px;
}
.hulunote-cm-lang-select:hover {
  border-color: var(--code-block-subtle);
  background: var(--code-block-hover-bg);
}
.hulunote-cm-lang-select:focus {
  border-color: var(--mermaid-primary-color);
}
.hulunote-cm-lang-select option {
  background: var(--code-block-gutter-bg);
  color: var(--code-block-text);
}
")

(defn- ensure-css!
  "Inject CodeMirror CSS once"
  []
  (when-not @css-injected?
    ;; Load CodeMirror base CSS from the copied file
    (let [link (.createElement js/document "link")]
      (set! (.-rel link) "stylesheet")
      (set! (.-href link) "/css/codemirror.css")
      (.appendChild (.-head js/document) link))
    ;; Inject our custom overrides
    (let [style (.createElement js/document "style")]
      (set! (.-id style) "hulunote-codemirror-css")
      (set! (.-textContent style) codemirror-css)
      (.appendChild (.-head js/document) style))
    (reset! css-injected? true)))

;; ==================== Code Block Detection ====================

(def ^:private code-fence-pattern
  "Matches ```lang\\ncode\\n``` — multiline with dotall"
  #"^```(\w*)\n([\s\S]*?)```\s*$")

(defn parse-code-block
  "Parse a ``` fenced code block. Returns {:lang :code} or nil."
  [content]
  (when (and (string? content)
             (str/starts-with? (str/trim content) "```"))
    (let [trimmed (str/trim content)]
      (when-let [match (re-matches code-fence-pattern trimmed)]
        {:lang (let [l (nth match 1)]
                 (if (str/blank? l) nil l))
         :code (nth match 2)}))))

(defn code-block?
  "Returns true if content is a ``` fenced code block"
  [content]
  (some? (parse-code-block content)))

;; ==================== Language Mapping ====================

(def ^:private lang->mode
  {"js"         "javascript"
   "javascript" "javascript"
   "ts"         "javascript"
   "typescript" "javascript"
   "json"       "javascript"
   "css"        "css"
   "clj"        "clojure"
   "cljs"       "clojure"
   "clojure"    "clojure"
   "html"       "htmlmixed"
   "xml"        "xml"
   "md"         "markdown"
   "markdown"   "markdown"})

(def ^:private selectable-languages
  "Languages available in the code block dropdown [value label]"
  [["js" "JavaScript"]
   ["ts" "TypeScript"]
   ["json" "JSON"]
   ["css" "CSS"]
   ["clj" "Clojure"]
   ["html" "HTML"]
   ["xml" "XML"]
   ["md" "Markdown"]])

(def ^:private lang-canonical
  "Maps language aliases to canonical select values"
  {"javascript" "js" "typescript" "ts"
   "cljs" "clj" "clojure" "clj"
   "markdown" "md" "htmlmixed" "html"})

(defn- canonical-lang
  "Normalize a language tag to its canonical select value"
  [lang]
  (let [l (some-> lang str/lower-case)]
    (get lang-canonical l l)))

(defn- resolve-mode [lang]
  (get lang->mode (some-> lang str/lower-case) nil))

;; ==================== Wrap / Unwrap ====================

(defn wrap-code
  "Wrap code string with ``` fences"
  [code lang]
  (str "```" (or lang "") "\n" code "```"))

(defn unwrap-code
  "Extract just the code from a ``` fenced block, or return content as-is"
  [content]
  (if-let [{:keys [code]} (parse-code-block content)]
    code
    content))

;; ==================== CodeMirror Instance Management ====================

(defn create-editor!
  "Create a CodeMirror editor in the given container element.
   Options:
     :code           - initial code string
     :lang           - language string (js, css, clojure, etc.)
     :read-only?     - if true, editor is not editable
     :on-change      - fn called with new code string on every change
     :on-blur        - fn called with (code, lang) when editor loses focus
     :on-escape      - fn called when Escape is pressed
     :on-lang-change - fn called with (new-lang, current-code) when language is changed"
  [container {:keys [code lang read-only? on-change on-blur on-escape on-lang-change]}]
  (ensure-css!)
  (let [current-lang (atom (or lang "js"))
        mode (resolve-mode lang)
        wrapper (.createElement js/document "div")
        badge (.createElement js/document "div")
        select (.createElement js/document "select")]
    (.setAttribute wrapper "class" "hulunote-cm-wrapper")
    ;; Stop click from propagating to the nav-input's on-click (which would start text editing)
    (.addEventListener wrapper "click"
      (fn [e] (.stopPropagation e)))

    ;; Language badge with dropdown selector
    (.setAttribute badge "class" "hulunote-cm-lang-badge")
    (.setAttribute select "class" "hulunote-cm-lang-select")
    (let [canonical (canonical-lang (or lang "js"))]
      (doseq [[value label] selectable-languages]
        (let [opt (.createElement js/document "option")]
          (set! (.-value opt) value)
          (set! (.-textContent opt) label)
          (when (= value canonical)
            (set! (.-selected opt) true))
          (.appendChild select opt))))
    (.appendChild badge select)
    (.appendChild wrapper badge)

    ;; CodeMirror container
    (let [cm-container (.createElement js/document "div")]
      (.appendChild wrapper cm-container)
      (set! (.-innerHTML container) "")
      (.appendChild container wrapper)

      (let [cm (CodeMirror
                 cm-container
                 #js {:value (or code "")
                      :mode mode
                      :lineNumbers true
                      :readOnly (if read-only? "nocursor" false)
                      :tabSize 2
                      :indentWithTabs false
                      :lineWrapping true
                      :viewportMargin js/Infinity
                      :extraKeys #js {"Esc" (fn [_cm]
                                              (when on-escape (on-escape)))
                                      "Tab" (fn [cm]
                                              (if (.somethingSelected cm)
                                                (.indentSelection cm "add")
                                                (let [spaces (apply str (repeat (.-tabSize (.getOption cm "indentUnit")) " "))]
                                                  (.replaceSelection cm "  " "end"))))}})]
        ;; Language change handler
        (.addEventListener select "change"
          (fn [_]
            (let [new-lang (.-value select)
                  new-mode (resolve-mode new-lang)]
              (reset! current-lang new-lang)
              (.setOption cm "mode" new-mode)
              (when on-lang-change
                (on-lang-change new-lang (.getValue cm))))))

        ;; Events
        (when on-change
          (.on cm "change"
            (fn [_cm _change]
              (on-change (.getValue cm)))))
        (when on-blur
          (.on cm "blur"
            (fn [_cm _e]
              (on-blur (.getValue cm) @current-lang))))

        ;; Auto-focus when editable
        (when-not read-only?
          (js/setTimeout #(.focus cm) 50))

        cm))))
