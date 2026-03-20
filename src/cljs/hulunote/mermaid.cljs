(ns hulunote.mermaid
  "Mermaid diagram rendering for code blocks.

   A block whose content is wrapped in ```mermaid fences is rendered as
   a Mermaid diagram instead of CodeMirror.

   Mermaid is loaded via CDN script tag to avoid ESM compatibility issues
   with shadow-cljs."
  (:require [clojure.string :as str]))

(defonce ^:private initialized? (atom false))
(defonce ^:private loaded? (atom false))
(defonce ^:private pending-renders (atom []))
(defonce ^:private render-counter (atom 0))

(def ^:private mermaid-css
  "
.hulunote-mermaid-wrapper {
  width: 100%;
  margin: 4px 0;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--theme-border, #333);
  background: #1e2028;
}
.hulunote-mermaid-badge {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 10px;
  background: #1a1c24;
  border-bottom: 1px solid #333;
  font-size: 11px;
  color: #888;
  font-family: -apple-system, BlinkMacSystemFont, sans-serif;
  user-select: none;
}
.hulunote-mermaid-badge span {
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #c792ea;
}
.hulunote-mermaid-body {
  padding: 16px;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60px;
  overflow-x: auto;
}
.hulunote-mermaid-body svg {
  max-width: 100%;
  height: auto;
}
.hulunote-mermaid-error {
  color: #f07178;
  font-size: 13px;
  padding: 8px;
  font-family: monospace;
  white-space: pre-wrap;
}
.hulunote-mermaid-loading {
  color: rgba(255,255,255,0.4);
  font-size: 13px;
  padding: 8px;
}
.hulunote-mermaid-edit-btn {
  cursor: pointer;
  color: #667eea;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
  border: 1px solid transparent;
  background: transparent;
  font-family: -apple-system, BlinkMacSystemFont, sans-serif;
  font-weight: 500;
}
.hulunote-mermaid-edit-btn:hover {
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.1);
}
.hulunote-mermaid-editor {
  width: 100%;
  min-height: 120px;
  background: #1e2028;
  color: #e0e0e0;
  border: none;
  padding: 12px;
  font-family: 'SF Mono', 'Fira Code', 'JetBrains Mono', Menlo, Monaco, monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
  outline: none;
  box-sizing: border-box;
}
")

(defonce ^:private css-injected? (atom false))

(defn- ensure-css! []
  (when-not @css-injected?
    (let [style (.createElement js/document "style")]
      (set! (.-id style) "hulunote-mermaid-css")
      (set! (.-textContent style) mermaid-css)
      (.appendChild (.-head js/document) style))
    (reset! css-injected? true)))

(defn- init-mermaid! []
  (when (and (exists? js/mermaid) (not @initialized?))
    (.initialize js/mermaid
      #js {:startOnLoad false
           :theme "dark"
           :themeVariables #js {:primaryColor "#667eea"
                                :primaryTextColor "#e0e0e0"
                                :primaryBorderColor "#7c8bab"
                                :lineColor "#7c8bab"
                                :secondaryColor "#764ba2"
                                :tertiaryColor "#2e3340"
                                :background "#1e2028"
                                :mainBkg "#2e3340"
                                :nodeBorder "#667eea"
                                :clusterBkg "#2e3340"
                                :titleColor "#e0e0e0"
                                :edgeLabelBackground "#2e3340"}
           :flowchart #js {:htmlLabels true
                           :curve "basis"}
           :sequence #js {:useMaxWidth true}
           :gantt #js {:useMaxWidth true}})
    (reset! initialized? true)))

(defn- render-diagram-body!
  "Render mermaid diagram SVG into a body element."
  [body-el diagram-text]
  (if @initialized?
    (let [render-id (str "mermaid-" (swap! render-counter inc))]
      (-> (.render js/mermaid render-id diagram-text)
          (.then (fn [result]
                   (set! (.-innerHTML body-el) (.-svg result))))
          (.catch (fn [err]
                    (let [error-div (.createElement js/document "div")]
                      (.setAttribute error-div "class" "hulunote-mermaid-error")
                      (set! (.-textContent error-div) (str "Mermaid error: " (.-message err)))
                      (set! (.-innerHTML body-el) "")
                      (.appendChild body-el error-div))))))
    (do
      (set! (.-innerHTML body-el) "<div class='hulunote-mermaid-loading'>Loading mermaid...</div>")
      (swap! pending-renders conj [body-el diagram-text]))))

(defn- flush-pending-renders! []
  (let [renders @pending-renders]
    (reset! pending-renders [])
    (doseq [[body-el diagram-text] renders]
      (render-diagram-body! body-el diagram-text))))

(defn- load-mermaid-script! []
  (when-not @loaded?
    (let [script (.createElement js/document "script")]
      (set! (.-src script) "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js")
      (set! (.-async script) true)
      (set! (.-onload script)
        (fn []
          (reset! loaded? true)
          (init-mermaid!)
          (flush-pending-renders!)))
      (.appendChild (.-head js/document) script))))

(defn mermaid-block?
  "Returns true if content is a ```mermaid fenced block"
  [content]
  (when (string? content)
    (let [trimmed (str/trim content)]
      (and (str/starts-with? trimmed "```mermaid")
           (str/ends-with? trimmed "```")))))

(defn parse-mermaid-block
  "Parse a ```mermaid code block. Returns the diagram text or nil."
  [content]
  (when (mermaid-block? content)
    (let [trimmed (str/trim content)
          body (subs trimmed (count "```mermaid") (- (count trimmed) 3))]
      (str/trim body))))

(defn render-mermaid!
  "Render mermaid diagram into the given container element.
   Options:
     :on-save - fn called with new diagram text when edit is saved"
  [container diagram-text & [{:keys [on-save]}]]
  (ensure-css!)
  (load-mermaid-script!)
  (let [wrapper (.createElement js/document "div")
        badge (.createElement js/document "div")
        body (.createElement js/document "div")
        current-text (atom diagram-text)
        editing? (atom false)]
    (.setAttribute wrapper "class" "hulunote-mermaid-wrapper")
    (.setAttribute badge "class" "hulunote-mermaid-badge")
    (set! (.-innerHTML badge) "<span>MERMAID</span>")
    (.setAttribute body "class" "hulunote-mermaid-body")

    ;; Add edit/done toggle button when on-save is provided
    (when on-save
      (let [edit-btn (.createElement js/document "span")]
        (.setAttribute edit-btn "class" "hulunote-mermaid-edit-btn")
        (set! (.-textContent edit-btn) "Edit")
        (.addEventListener edit-btn "click"
          (fn [e]
            (.stopPropagation e)
            (if @editing?
              ;; Exit edit mode → save and re-render diagram
              (let [textarea (.querySelector body "textarea")
                    new-text (when textarea (str/trim (.-value textarea)))]
                (when (and new-text (seq new-text))
                  (reset! current-text new-text)
                  (on-save new-text))
                (reset! editing? false)
                (set! (.-textContent edit-btn) "Edit")
                (render-diagram-body! body @current-text))
              ;; Enter edit mode → show textarea with diagram source
              (let [textarea (.createElement js/document "textarea")]
                (.setAttribute textarea "class" "hulunote-mermaid-editor")
                (set! (.-value textarea) @current-text)
                (set! (.-innerHTML body) "")
                (.appendChild body textarea)
                (reset! editing? true)
                (set! (.-textContent edit-btn) "Done")
                (.focus textarea)))))
        (.appendChild badge edit-btn)))

    (.appendChild wrapper badge)
    (.appendChild wrapper body)
    (set! (.-innerHTML container) "")
    (.appendChild container wrapper)

    ;; Initial diagram render
    (render-diagram-body! body diagram-text)))
