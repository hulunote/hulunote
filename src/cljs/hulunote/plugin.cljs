(ns hulunote.plugin
  "Hulunote Plugin System
   Provides a JS-interop API for registering external plugins that can:
   - Add custom block renderers (e.g., {{table}}, {{kanban}})
   - Inject custom CSS styles
   - Execute custom initialization logic

   Block content matching pattern: {{renderer-name}}
   When a block's content matches, the plugin renderer takes over
   the entire subtree (block + children) rendering."
  (:require [datascript.core :as d]
            [hulunote.db :as db]
            [hulunote.util :as u]))

;; ==================== Plugin Registry ====================

(defonce ^:private plugins (atom {}))
(defonce ^:private renderers (atom {}))
(defonce ^:private injected-styles (atom {}))

;; ==================== Style Injection ====================

(defn- inject-style!
  "Inject a <style> tag into <head> for a plugin"
  [plugin-name css-text]
  (when (and css-text (seq css-text))
    (let [style-id (str "hulunote-plugin-style-" plugin-name)
          existing (.getElementById js/document style-id)]
      ;; Remove existing style tag if present
      (when existing
        (.remove existing))
      ;; Create and inject new style tag
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
  "Get a block and its full subtree as a ClojureScript map.
   Returns {:id, :content, :children [{:id, :content, :children [...]}]}"
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
    (when-let [match (re-matches plugin-pattern (clojure.string/trim content))]
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
  (let [p (js->clj plugin-def :keywordize-keys true)
        plugin-name (:name p)]
    (when-not plugin-name
      (throw (js/Error. "Plugin must have a 'name' property")))

    ;; Store plugin definition
    (swap! plugins assoc plugin-name p)

    ;; Register renderers
    (doseq [[renderer-key renderer-fn] (:renderers p)]
      (let [renderer-name (name renderer-key)]
        (swap! renderers assoc renderer-name
          {:plugin plugin-name
           :render (get (js->clj (:renderers plugin-def) :keywordize-keys false)
                    renderer-name
                    (get (:renderers plugin-def) renderer-name))})))

    ;; Inject styles
    (when (:styles p)
      (inject-style! plugin-name (:styles p)))

    ;; Call init
    (when-let [init-fn (.-init plugin-def)]
      (init-fn (build-api)))

    (js/console.log (str "[Hulunote Plugin] Registered: " plugin-name " v" (:version p)))
    true))

(defn unregister-plugin!
  "Unregister a plugin by name"
  [plugin-name]
  (when-let [p (get @plugins plugin-name)]
    ;; Call destroy
    (when-let [destroy-fn (:destroy p)]
      (destroy-fn))

    ;; Remove renderers
    (let [renderer-names (->> @renderers
                           (filter #(= plugin-name (:plugin (val %))))
                           (map key))]
      (doseq [rn renderer-names]
        (swap! renderers dissoc rn)))

    ;; Remove styles
    (remove-style! plugin-name)

    ;; Remove plugin
    (swap! plugins dissoc plugin-name)

    (js/console.log (str "[Hulunote Plugin] Unregistered: " plugin-name))
    true))

;; ==================== Plugin Rendering ====================

(defn render-plugin-block!
  "Render a plugin block into a container element.
   Called from render.cljs when a block matches a plugin pattern.
   Returns true if rendered, false if no matching renderer."
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
        ;; Clear container and let plugin render
        (set! (.-innerHTML container) "")
        (render ctx)
        true))))

;; ==================== Public API (exposed to JS) ====================

(defn build-api
  "Build the API object exposed to plugins"
  []
  #js {:register   register-plugin!
       :unregister unregister-plugin!
       :getPlugins (fn [] (clj->js (keys @plugins)))
       :getBlockTree (fn [block-id] (block-tree->js (get-block-tree block-id)))
       :getBlockChildren (fn [block-id]
                           (let [tree (get-block-tree block-id)]
                             (clj->js (:children tree))))})

(defn init-plugin-system!
  "Initialize the plugin system. Expose global API on window."
  []
  (let [api (build-api)]
    (set! (.-HulunotePlugin js/window) api)
    (js/console.log "[Hulunote Plugin] System initialized. Use window.HulunotePlugin.register({...}) to register plugins.")))
