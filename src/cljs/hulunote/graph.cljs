(ns hulunote.graph
  "Knowledge Graph visualization page.
   Shows notes as nodes and [[links]] as edges in a force-directed graph.
   Supports interactive zoom, pan, drag, and click-to-navigate."
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [clojure.string :as str]
            [hulunote.util :as u]
            [hulunote.db :as db]
            [hulunote.router :as router]
            [hulunote.sidebar :as sidebar]
            [hulunote.components :as comps]
            ["d3" :as d3]))

;; ==================== Data Extraction ====================

(defn extract-links-from-content
  "Extract all [[link]] and #[[link]] targets from a nav content string."
  [content]
  (when (string? content)
    (let [bracket-links (re-seq #"\[\[([^\]]+)\]\]" content)
          hash-links (re-seq #"#\[\[([^\]]+)\]\]" content)
          simple-hash (re-seq #"#(\w+)" content)]
      (distinct
        (concat
          (map second bracket-links)
          (map second hash-links)
          (map second simple-hash))))))

(defn build-graph-data
  "Build nodes and links from all notes in DataScript.
   Returns {:nodes [{:id :title :linkCount}] :links [{:source :target}]}"
  [conn]
  (let [;; Get all non-deleted notes
        notes (d/q '[:find ?note-id ?title
                     :where
                     [?e :hulunote-notes/id ?note-id]
                     [?e :hulunote-notes/title ?title]]
                conn)
        ;; Build title->id map
        title->id (into {} (map (fn [[id title]] [title id]) notes))
        ;; Get all nav contents
        all-navs (d/q '[:find ?nav-content ?note-id
                        :where
                        [?nav :content ?nav-content]
                        [?nav :hulunote-note ?note-id]]
                   conn)
        ;; Build link pairs: [source-note-id target-note-id]
        raw-links (for [[content source-note-id] all-navs
                        :when (and (string? content) (not= content "ROOT"))
                        :let [targets (extract-links-from-content content)]
                        target-title targets
                        :let [target-id (get title->id target-title)]
                        :when (and target-id (not= target-id source-note-id))]
                    [source-note-id target-id])
        ;; Deduplicate links
        unique-links (distinct raw-links)
        ;; Count links per node
        link-counts (frequencies (mapcat identity unique-links))
        ;; Build nodes
        nodes (mapv (fn [[note-id title]]
                      {:id note-id
                       :title title
                       :linkCount (get link-counts note-id 0)})
                notes)
        ;; Only include nodes that have at least one connection or all if few notes
        connected-ids (set (mapcat identity unique-links))
        has-few-notes (< (count notes) 30)
        filtered-nodes (if has-few-notes
                         nodes
                         (filterv #(connected-ids (:id %)) nodes))
        node-id-set (set (map :id filtered-nodes))
        ;; Filter links to only include nodes in our set
        filtered-links (filterv (fn [[s t]]
                                  (and (node-id-set s) (node-id-set t)))
                         unique-links)]
    {:nodes filtered-nodes
     :links (mapv (fn [[s t]] {:source s :target t}) filtered-links)}))

;; ==================== D3 Force Graph ====================

(defonce graph-state (atom nil))

(defn- node-radius [link-count]
  (+ 4 (min 16 (* 2 (Math/sqrt (or link-count 0))))))

(defn- truncate-title [title max-len]
  (if (> (count title) max-len)
    (str (subs title 0 max-len) "...")
    title))

(defn- node-link-count [node]
  (or (unchecked-get node "linkCount") 0))

(defn- node-title [node]
  (or (unchecked-get node "title") ""))

(defn destroy-graph! []
  (when-let [state @graph-state]
    (when-let [sim (:simulation state)]
      (.stop sim))
    (reset! graph-state nil)))

(defn create-graph!
  "Create a D3 force-directed graph in the given container."
  [container graph-data database-name]
  (destroy-graph!)
  (let [width (.-clientWidth container)
        height (max 600 (.-clientHeight container))
        nodes (clj->js (:nodes graph-data))
        links (clj->js (:links graph-data))

        ;; Create SVG
        svg (-> (d3/select container)
                (.html "")
                (.append "svg")
                (.attr "width" "100%")
                (.attr "height" "100%")
                (.attr "viewBox" (str "0 0 " width " " height))
                (.style "background" "transparent"))

        ;; Defs for gradient and glow
        defs (.append svg "defs")

        ;; Glow filter
        filter (.append defs "filter")
        _ (-> filter (.attr "id" "glow"))
        _ (-> (.append filter "feGaussianBlur")
              (.attr "stdDeviation" "3")
              (.attr "result" "coloredBlur"))
        _ (let [merge (.append filter "feMerge")]
            (-> (.append merge "feMergeNode") (.attr "in" "coloredBlur"))
            (-> (.append merge "feMergeNode") (.attr "in" "SourceGraphic")))

        ;; Main group for zoom/pan
        g (.append svg "g")

        ;; Zoom behavior
        zoom-behavior (-> (d3/zoom)
                          (.scaleExtent #js [0.1 4])
                          (.on "zoom" (fn [event]
                                        (.attr g "transform" (.-transform event)))))
        _ (.call svg zoom-behavior)

        ;; Force simulation
        simulation (-> (d3/forceSimulation nodes)
                       (.force "link"
                         (-> (d3/forceLink links)
                             (.id (fn [d] (.-id d)))
                             (.distance 100)))
                       (.force "charge"
                         (-> (d3/forceManyBody)
                             (.strength -200)))
                       (.force "center"
                         (d3/forceCenter (/ width 2) (/ height 2)))
                       (.force "collision"
                         (-> (d3/forceCollide)
                             (.radius (fn [d] (+ (node-radius (node-link-count d)) 8))))))

        ;; Draw links
        link (-> (.append g "g")
                 (.attr "class" "links")
                 (.selectAll "line")
                 (.data links)
                 (.join "line")
                 (.attr "stroke" "rgba(102, 126, 234, 0.3)")
                 (.attr "stroke-width" 1))

        ;; Draw node groups
        node (-> (.append g "g")
                 (.attr "class" "nodes")
                 (.selectAll "g")
                 (.data nodes)
                 (.join "g")
                 (.style "cursor" "pointer"))

        ;; Add circles to nodes
        _ (-> (.append node "circle")
              (.attr "r" (fn [d] (node-radius (node-link-count d))))
              (.attr "fill" (fn [d]
                              (let [lc (node-link-count d)]
                                (cond
                                  (> lc 5) "#667eea"
                                  (> lc 2) "#764ba2"
                                  (> lc 0) "#5a6b8a"
                                  :else "#3d4455"))))
              (.attr "stroke" "rgba(102, 126, 234, 0.5)")
              (.attr "stroke-width" 1.5)
              (.style "filter" "url(#glow)"))

        ;; Add labels
        _ (-> (.append node "text")
              (.text (fn [d] (truncate-title (node-title d) 20)))
              (.attr "dy" (fn [d] (+ (node-radius (node-link-count d)) 14)))
              (.attr "text-anchor" "middle")
              (.style "font-size" "11px")
              (.style "fill" "rgba(255,255,255,0.7)")
              (.style "pointer-events" "none")
              (.style "font-family" "-apple-system, BlinkMacSystemFont, sans-serif"))

        ;; Drag behavior
        drag-behavior (-> (d3/drag)
                          (.on "start" (fn [event d]
                                         (when-not (.-active event)
                                           (.alphaTarget simulation 0.3)
                                           (.restart simulation))
                                         (set! (.-fx d) (.-x d))
                                         (set! (.-fy d) (.-y d))))
                          (.on "drag" (fn [event d]
                                        (set! (.-fx d) (.-x event))
                                        (set! (.-fy d) (.-y event))))
                          (.on "end" (fn [event d]
                                       (when-not (.-active event)
                                         (.alphaTarget simulation 0))
                                       (set! (.-fx d) nil)
                                       (set! (.-fy d) nil))))
        _ (.call node drag-behavior)

        ;; Click to navigate
        _ (.on node "click" (fn [event d]
                              (.stopPropagation event)
                              (router/go-to-note! database-name (.-id d))))

        ;; Hover effects
        _ (.on node "mouseover" (fn [event d]
                                  (-> (d3/select (.-currentTarget event))
                                      (.select "circle")
                                      (.transition)
                                      (.duration 200)
                                      (.attr "stroke-width" 3)
                                      (.attr "stroke" "#667eea"))
                                  (-> (d3/select (.-currentTarget event))
                                      (.select "text")
                                      (.transition)
                                      (.duration 200)
                                      (.style "fill" "#fff")
                                      (.style "font-size" "13px"))))
        _ (.on node "mouseout" (fn [event d]
                                 (-> (d3/select (.-currentTarget event))
                                     (.select "circle")
                                     (.transition)
                                     (.duration 200)
                                     (.attr "stroke-width" 1.5)
                                     (.attr "stroke" "rgba(102, 126, 234, 0.5)"))
                                 (-> (d3/select (.-currentTarget event))
                                     (.select "text")
                                     (.transition)
                                     (.duration 200)
                                     (.style "fill" "rgba(255,255,255,0.7)")
                                     (.style "font-size" "11px"))))

        ;; Tick handler
        _ (.on simulation "tick"
            (fn []
              (-> link
                  (.attr "x1" (fn [d] (.-x (.-source d))))
                  (.attr "y1" (fn [d] (.-y (.-source d))))
                  (.attr "x2" (fn [d] (.-x (.-target d))))
                  (.attr "y2" (fn [d] (.-y (.-target d)))))
              (-> node
                  (.attr "transform" (fn [d] (str "translate(" (.-x d) "," (.-y d) ")"))))))

        ;; Initial zoom to fit
        _ (js/setTimeout
            (fn []
              (-> svg
                  (.transition)
                  (.duration 500)
                  (.call (.transform zoom-behavior
                           (d3/zoomIdentity)))))
            100)]

    (reset! graph-state {:simulation simulation
                         :svg svg})))

;; ==================== Graph Page CSS ====================

(def ^:private graph-css
  "
.graph-page-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--app-topbar-height));
  background: transparent;
}
.graph-canvas {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: transparent;
}
.graph-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: rgba(255, 255, 255, 0.4);
  gap: 12px;
}
.graph-empty-icon {
  font-size: 48px;
  opacity: 0.3;
}
.graph-empty-text {
  font-size: 16px;
}
.graph-legend {
  position: absolute;
  bottom: 16px;
  right: 16px;
  width: 280px;
  max-width: min(280px, calc(100vw - 72px));
  background: rgba(46, 51, 64, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 12px 16px;
  box-sizing: border-box;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}
.graph-legend-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: rgba(255, 255, 255, 0.8);
}
.graph-legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.graph-legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.graph-help {
  position: absolute;
  bottom: 16px;
  right: 8px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.graph-help-btn {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(46, 51, 64, 0.94);
  color: rgba(255, 255, 255, 0.82);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}
.graph-help-btn:hover {
  background: rgba(102, 126, 234, 0.22);
  border-color: rgba(102, 126, 234, 0.42);
  color: #fff;
}
.graph-help-description {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.45;
  color: rgba(255, 255, 255, 0.62);
}
.graph-stats-card {
  position: absolute;
  top: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(46, 51, 64, 0.92);
  color: rgba(255, 255, 255, 0.62);
  box-sizing: border-box;
}
.graph-stat {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.graph-stat-num {
  font-size: 15px;
  font-weight: 700;
  color: var(--theme-accent);
  line-height: 1;
}
.graph-stat-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.62);
  line-height: 1;
}
")

(defonce ^:private graph-css-injected? (atom false))

(defn- ensure-graph-css! []
  (let [existing-style (.getElementById js/document "hulunote-graph-css")]
    (if existing-style
      (set! (.-textContent existing-style) graph-css)
      (let [style (.createElement js/document "style")]
        (set! (.-id style) "hulunote-graph-css")
        (set! (.-textContent style) graph-css)
        (.appendChild (.-head js/document) style)))
    (when-not @graph-css-injected?
      (reset! graph-css-injected? true))))

;; ==================== Graph Page Component ====================

(rum/defcs graph-page < rum/reactive
  (rum/local false ::help-open?)
  {:will-unmount
   (fn [state]
     (destroy-graph!)
     state)}
  [state db]
  (ensure-graph-css!)
  (let [{:keys [route-name params]} (db/get-route db)
        database-name (:database params)
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)
        graph-data (build-graph-data db)
        node-count (count (:nodes graph-data))
        link-count (count (:links graph-data))
        help-open? (::help-open? state)]
    [:div.night-center-boxBg.night-textColor-2
     [:div.page-wrapper
     (sidebar/app-top-bar {:title "Knowledge Graph"})
     (sidebar/left-sidebar db database-name)
     [:div.main-content-area
      {:class (when @sidebar/sidebar-collapsed? "sidebar-collapsed")}
      [:div.graph-page-container
       {:on-click #(reset! help-open? false)}
       ;; Canvas
       [:div.graph-canvas
        (if (zero? node-count)
          [:div.graph-empty
           [:div.graph-empty-icon "\uD83D\uDD78\uFE0F"]
           [:div.graph-empty-text "No notes yet. Create some notes with [[links]] to see the graph!"]]
          [:<>
           [:div.graph-stats-card
            [:div.graph-stat
             [:span.graph-stat-num (str node-count)]
             [:span.graph-stat-label "notes"]]
            [:div.graph-stat
             [:span.graph-stat-num (str link-count)]
             [:span.graph-stat-label "connections"]]]
           [:div#graph-canvas
            {:style {:width "100%" :height "100%"}
             :ref (fn [el]
                    (when (and el database-name (seq (:nodes graph-data)))
                      ;; Small delay to ensure DOM layout is ready
                      (js/setTimeout
                        #(when (zero? (.-childElementCount el))
                           (create-graph! el graph-data database-name))
                        100)))}]
           [:div.graph-help
            {:on-click #(.stopPropagation %)}
            (when @help-open?
              [:div.graph-legend
               [:div.graph-legend-title "Node Connectivity"]
               [:p.graph-help-description
                "Node color indicates how many connections a note has in the graph."]
               [:div.graph-legend-item
                [:div.graph-legend-dot {:style {:background "#667eea"}}]
                "Hub note (5+ connections)"]
               [:div.graph-legend-item
                [:div.graph-legend-dot {:style {:background "#764ba2"}}]
                "Active note (3-4 connections)"]
               [:div.graph-legend-item
                [:div.graph-legend-dot {:style {:background "#5a6b8a"}}]
                "Lightly connected (1-2 connections)"]
               [:div.graph-legend-item
                [:div.graph-legend-dot {:style {:background "#3d4455"}}]
                "Isolated (0 connections)"]])
            [:button.graph-help-btn
             {:type "button"
              :aria-label "Graph help"
              :title "Graph help"
              :on-click #(swap! help-open? not)}
             "?"]]])]]]]]))
