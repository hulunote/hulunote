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

(defn- flush-pending-renders! []
  (let [renders @pending-renders]
    (reset! pending-renders [])
    (doseq [[body-el diagram-text] renders]
      (let [render-id (str "mermaid-" (swap! render-counter inc))]
        (-> (.render js/mermaid render-id diagram-text)
            (.then (fn [result]
                     (set! (.-innerHTML body-el) (.-svg result))))
            (.catch (fn [err]
                      (let [error-div (.createElement js/document "div")]
                        (.setAttribute error-div "class" "hulunote-mermaid-error")
                        (set! (.-textContent error-div) (str "Mermaid error: " (.-message err)))
                        (set! (.-innerHTML body-el) "")
                        (.appendChild body-el error-div)))))))))

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
  "Render mermaid diagram into the given container element."
  [container diagram-text]
  (ensure-css!)
  (load-mermaid-script!)
  (let [wrapper (.createElement js/document "div")
        badge (.createElement js/document "div")
        body (.createElement js/document "div")]
    (.setAttribute wrapper "class" "hulunote-mermaid-wrapper")
    (.setAttribute badge "class" "hulunote-mermaid-badge")
    (set! (.-innerHTML badge) "<span>MERMAID</span>")
    (.setAttribute body "class" "hulunote-mermaid-body")
    (.appendChild wrapper badge)
    (.appendChild wrapper body)
    (set! (.-innerHTML container) "")
    (.appendChild container wrapper)
    (if @initialized?
      ;; Mermaid is ready, render immediately
      (let [render-id (str "mermaid-" (swap! render-counter inc))]
        (-> (.render js/mermaid render-id diagram-text)
            (.then (fn [result]
                     (set! (.-innerHTML body) (.-svg result))))
            (.catch (fn [err]
                      (let [error-div (.createElement js/document "div")]
                        (.setAttribute error-div "class" "hulunote-mermaid-error")
                        (set! (.-textContent error-div) (str "Mermaid error: " (.-message err)))
                        (set! (.-innerHTML body) "")
                        (.appendChild body error-div))))))
      ;; Still loading, queue for later
      (do
        (set! (.-innerHTML body) "<div class='hulunote-mermaid-loading'>Loading mermaid...</div>")
        (swap! pending-renders conj [body diagram-text])))))
