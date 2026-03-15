(ns hulunote.plugin
  "Hulunote Plugin System

   Plugins are managed through two special notes in the outline:
     - hulunote/javascript — each child block is a JS URL or inline script
     - hulunote/css        — each child block is a CSS URL or inline style

   Example:  Create a note titled 'hulunote/javascript', add child blocks:
     /plugins/hulunote-kanban-table-plugin.js
     https://cdn.example.com/my-plugin.js

   Block content matching pattern: {{renderer-name}}
   When a block's content matches, the plugin renderer takes over
   the entire subtree (block + children) rendering."
  (:require [datascript.core :as d]
            [hulunote.db :as db]
            [hulunote.util :as u]
            [hulunote.codemirror :as cm]
            [clojure.string :as str]))

;; ==================== Plugin Registry ====================

(defonce ^:private plugins (atom {}))
(defonce ^:private renderers (atom {}))
(defonce ^:private injected-styles (atom {}))

;; Track what we've already loaded to avoid duplicates on hot-reload
(defonce ^:private loaded-scripts (atom #{}))
(defonce ^:private loaded-css (atom #{}))

;; ==================== Style Injection ====================

(defn- inject-style!
  "Inject a <style> tag into <head> for a plugin"
  [plugin-name css-text]
  (when (and css-text (seq css-text))
    (let [style-id (str "hulunote-plugin-style-" plugin-name)
          existing (.getElementById js/document style-id)]
      (when existing
        (.remove existing))
      (let [style-el (.createElement js/document "style")]
        (set! (.-id style-el) style-id)
        (set! (.-type style-el) "text/css")
        (set! (.-textContent style-el) css-text)
        (.appendChild (.-head js/document) style-el)
        (swap! injected-styles assoc plugin-name style-id)))))

(defn- remove-style!
  "Remove injected style tag for a plugin"
  [plugin-name]
  (when-let [style-id (get @injected-styles plugin-name)]
    (when-let [el (.getElementById js/document style-id)]
      (.remove el))
    (swap! injected-styles dissoc plugin-name)))

;; ==================== Block Data Helpers ====================

(defn get-block-tree
  "Get a block and its full subtree as a ClojureScript map."
  [block-id]
  (let [ds @db/dsdb
        nav (u/get-nav-sub-navs-sorted ds block-id)]
    (letfn [(nav->tree [n]
              (let [children (when-let [kids (seq (:parid n))]
                               (mapv nav->tree
                                 (sort-by :same-deep-order kids)))]
                (cond-> {:id (:id n)
                         :content (or (:content n) "")}
                  (seq children) (assoc :children children))))]
      (nav->tree nav))))

(defn block-tree->js
  "Convert a block tree to a plain JS object for plugin consumption"
  [tree]
  (clj->js tree))

;; ==================== Content Pattern Matching ====================

(def ^:private plugin-pattern
  "Regex to match {{renderer-name}} or {{renderer-name:params}}"
  #"^\{\{([a-zA-Z0-9_-]+)(?::(.+))?\}\}\s*$")

(defn match-plugin-renderer
  "Check if block content matches a plugin renderer pattern.
   Returns {:renderer-name string, :params string} or nil."
  [content]
  (when (string? content)
    (when-let [match (re-matches plugin-pattern (str/trim content))]
      (let [renderer-name (nth match 1)
            params (nth match 2 nil)]
        (when (contains? @renderers renderer-name)
          {:renderer-name renderer-name
           :params params})))))

;; ==================== Plugin Registration API ====================

(defn register-plugin!
  "Register a plugin. plugin-def is a JS object with:
   - name: string (required)
   - version: string
   - styles: string (CSS text)
   - renderers: object { rendererName: function(ctx) }
   - init: function(api)
   - destroy: function()"
  [plugin-def]
  (let [plugin-name (.-name plugin-def)
        version (.-version plugin-def)
        styles-css (.-styles plugin-def)
        renderers-obj (.-renderers plugin-def)]
    (when-not plugin-name
      (throw (js/Error. "Plugin must have a 'name' property")))

    (swap! plugins assoc plugin-name plugin-def)

    (when renderers-obj
      (doseq [renderer-name (js/Object.keys renderers-obj)]
        (let [render-fn (unchecked-get renderers-obj renderer-name)]
          (when (fn? render-fn)
            (swap! renderers assoc renderer-name
              {:plugin plugin-name
               :render render-fn})))))

    (when styles-css
      (inject-style! plugin-name styles-css))

    (when-let [init-fn (.-init plugin-def)]
      (init-fn (build-api)))

    (js/console.log (str "[Hulunote Plugin] Registered: " plugin-name " v" version))
    true))

(defn unregister-plugin!
  "Unregister a plugin by name"
  [plugin-name]
  (when-let [p (get @plugins plugin-name)]
    (when-let [destroy-fn (.-destroy p)]
      (destroy-fn))

    (let [renderer-names (->> @renderers
                           (filter #(= plugin-name (:plugin (val %))))
                           (map key))]
      (doseq [rn renderer-names]
        (swap! renderers dissoc rn)))

    (remove-style! plugin-name)
    (swap! plugins dissoc plugin-name)

    (js/console.log (str "[Hulunote Plugin] Unregistered: " plugin-name))
    true))

;; ==================== Plugin Rendering ====================

(defn render-plugin-block!
  "Render a plugin block into a container element."
  [container block-id content]
  (when-let [{:keys [renderer-name params]} (match-plugin-renderer content)]
    (when-let [{:keys [render]} (get @renderers renderer-name)]
      (let [tree (get-block-tree block-id)
            ctx #js {:blockId block-id
                     :content content
                     :params params
                     :children (block-tree->js (:children tree))
                     :container container
                     :api (build-api)}]
        (set! (.-innerHTML container) "")
        (render ctx)
        true))))

;; ==================== Public API (exposed to JS) ====================

(defn build-api
  "Build the API object exposed to plugins"
  []
  #js {:register     register-plugin!
       :unregister   unregister-plugin!
       :getPlugins   (fn [] (clj->js (keys @plugins)))
       :getBlockTree (fn [block-id] (block-tree->js (get-block-tree block-id)))
       :getBlockChildren (fn [block-id]
                           (let [tree (get-block-tree block-id)]
                             (clj->js (:children tree))))})

;; ==================== Dynamic Loading from Special Notes ====================

(defn- url?
  "Check if content looks like a URL (http://, https://, or path starting with /)"
  [content]
  (let [s (str/trim content)]
    (or (str/starts-with? s "http://")
        (str/starts-with? s "https://")
        (str/starts-with? s "/"))))

(defn- find-note-root-nav-id
  "Find the root-nav-id for a note with the given title"
  [title]
  (d/q '[:find ?root-nav-id .
          :in $ ?title
          :where
          [?e :hulunote-notes/title ?title]
          [?e :hulunote-notes/root-nav-id ?root-nav-id]]
    @db/dsdb title))

(defn- get-child-contents
  "Get the content of all direct children of a nav, sorted by order"
  [root-nav-id]
  (let [nav (u/get-nav-sub-navs-sorted @db/dsdb root-nav-id)]
    (->> (:parid nav)
      (sort-by :same-deep-order)
      (map :content)
      (remove str/blank?))))

(defn- load-js-url!
  "Load a JavaScript file by URL via <script> tag"
  [url]
  (when-not (contains? @loaded-scripts url)
    (swap! loaded-scripts conj url)
    (let [script (.createElement js/document "script")]
      (set! (.-src script) url)
      (set! (.-type script) "text/javascript")
      (set! (.-async script) true)
      (.appendChild (.-body js/document) script)
      (js/console.log (str "[Hulunote Plugin] Loading JS: " url)))))

(defn- load-js-inline!
  "Execute inline JavaScript code"
  [code]
  (let [hash (str (hash code))]
    (when-not (contains? @loaded-scripts hash)
      (swap! loaded-scripts conj hash)
      (let [script (.createElement js/document "script")]
        (set! (.-type script) "text/javascript")
        (set! (.-textContent script) code)
        (.appendChild (.-body js/document) script)
        (js/console.log "[Hulunote Plugin] Loading inline JS")))))

(defn- load-css-url!
  "Load a CSS file by URL via <link> tag"
  [url]
  (when-not (contains? @loaded-css url)
    (swap! loaded-css conj url)
    (let [link (.createElement js/document "link")]
      (set! (.-rel link) "stylesheet")
      (set! (.-type link) "text/css")
      (set! (.-href link) url)
      (.appendChild (.-head js/document) link)
      (js/console.log (str "[Hulunote Plugin] Loading CSS: " url)))))

(defn- load-css-inline!
  "Inject inline CSS into <head>"
  [code]
  (let [hash (str (hash code))]
    (when-not (contains? @loaded-css hash)
      (swap! loaded-css conj hash)
      (let [style (.createElement js/document "style")]
        (set! (.-type style) "text/css")
        (set! (.-textContent style) code)
        (.appendChild (.-head js/document) style)
        (js/console.log "[Hulunote Plugin] Loading inline CSS")))))

(defn- strip-code-fences
  "If content is wrapped in ``` fences, extract the code. Otherwise return as-is."
  [content]
  (cm/unwrap-code content))

(defn load-plugins-from-notes!
  "Scan for special notes 'hulunote/javascript' and 'hulunote/css'.
   Each child block is either a URL, inline code, or ``` fenced code.
   Called after database data is loaded into DataScript."
  []
  ;; Load hulunote/javascript
  (when-let [root-id (find-note-root-nav-id "hulunote/javascript")]
    (let [entries (get-child-contents root-id)]
      (js/console.log (str "[Hulunote Plugin] Found hulunote/javascript with " (count entries) " entries"))
      (doseq [entry entries]
        (let [s (str/trim (strip-code-fences entry))]
          (if (url? s)
            (load-js-url! s)
            (load-js-inline! s))))))

  ;; Load hulunote/css
  (when-let [root-id (find-note-root-nav-id "hulunote/css")]
    (let [entries (get-child-contents root-id)]
      (js/console.log (str "[Hulunote Plugin] Found hulunote/css with " (count entries) " entries"))
      (doseq [entry entries]
        (let [s (str/trim (strip-code-fences entry))]
          (if (url? s)
            (load-css-url! s)
            (load-css-inline! s)))))))

;; ==================== Initialization ====================

(defn init-plugin-system!
  "Initialize the plugin system. Expose global API on window."
  []
  (let [api (build-api)]
    (set! (.-HulunotePlugin js/window) api)
    (js/console.log "[Hulunote Plugin] System initialized. Use window.HulunotePlugin.register({...}) to register plugins.")))
