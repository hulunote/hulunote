(ns hulunote.home
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.components :as comps]
            [hulunote.storage :as storage]))

(def feature-card-glows
  {:accent {:border "var(--theme-accent-border-soft)"
            :top-line "var(--home-card-top-line)"
            :shadow "var(--home-card-icon-glow)"
            :inset "var(--home-card-inset-shadow)"}
   :purple {:border "var(--home-purple-border-strong)"
            :top-line "linear-gradient(90deg, transparent, var(--home-purple-line), transparent)"
            :shadow "var(--home-purple-shadow)"
            :inset "var(--home-purple-inset-shadow)"}
   :cyan {:border "var(--home-cyan-border)"
          :top-line "linear-gradient(90deg, transparent, var(--home-cyan-line), transparent)"
          :shadow "drop-shadow(0 0 8px var(--home-cyan-soft-text))"
          :inset "var(--home-cyan-inset-shadow)"}
   :orange {:border "var(--home-orange-border)"
            :top-line "linear-gradient(90deg, transparent, var(--home-orange), transparent)"
            :shadow "drop-shadow(0 0 8px var(--home-orange))"
            :inset "var(--home-orange-inset-shadow)"}
   :indigo {:border "var(--home-indigo-border)"
            :top-line "linear-gradient(90deg, transparent, var(--home-indigo-line), transparent)"
            :shadow "var(--home-indigo-shadow)"
            :inset "var(--home-indigo-inset-shadow)"}})

;; Feature card component - dark futuristic style
(rum/defc feature-card [icon title description & [{:keys [tone] :or {tone :accent}}]]
  (let [{:keys [border top-line shadow inset]}
        (get feature-card-glows tone (:accent feature-card-glows))]
    [:div.feature-card
     {:style {:background "var(--home-panel-gradient)"
              :border-radius "16px"
              :padding "32px 24px"
              :border (str "1px solid " border)
              :box-shadow (str "var(--home-panel-shadow), " inset)
              :transition "transform 0.3s, box-shadow 0.3s, border-color 0.3s"
              :cursor "default"
              :min-height "220px"
              :position "relative"
              :overflow "hidden"}}
     ;; Subtle top glow line
     [:div {:style {:position "absolute"
                    :top "0"
                    :left "20%"
                    :right "20%"
                    :height "1px"
                    :background top-line}}]
     [:div.flex.flex-column.items-center
      [:div {:style {:font-size "44px"
                     :margin-bottom "16px"
                     :filter shadow}}
       icon]
      [:div {:style {:font-size "18px"
                     :font-weight "700"
                     :color "var(--theme-accent-text)"
                     :margin-bottom "12px"
                     :text-align "center"
                     :letter-spacing "0.5px"}}
       title]
      [:div {:style {:font-size "13px"
                     :color "var(--app-text-muted)"
                     :text-align "center"
                     :line-height "1.7"}}
       description]]]))

;; AI pipeline step component
(rum/defc pipeline-step [icon label is-last]
  [:div.flex.items-center
   [:div {:style {:background "linear-gradient(135deg, var(--theme-accent-15), var(--home-purple-border))"
                  :border "1px solid var(--home-accent-border)"
                  :color "var(--theme-accent-text)"
                  :padding "12px 22px"
                  :border-radius "28px"
                  :font-size "13px"
                  :font-weight "500"
                  :white-space "nowrap"
                  :display "flex"
                  :align-items "center"
                  :gap "8px"
                  :box-shadow "var(--home-panel-shadow-subtle)"}}
    [:span {:style {:font-size "16px"}} icon]
    label]
   (when-not is-last
     [:div {:style {:color "var(--theme-accent-border-strong)"
                    :padding "0 6px"
                    :font-size "20px"
                    :font-weight "300"}}
      "→"])])

;; Tech stack item
(rum/defc tech-item [label tech]
  [:div.flex.flex-row.items-center
   {:style {:padding "8px 0"
            :border-bottom "1px solid var(--home-divider)"}}
   [:div {:style {:width "120px"
                  :font-weight "500"
                  :color "var(--app-text-soft)"}}
    label]
   [:div {:style {:color "var(--theme-accent-text)"}}
    tech]])

;; Home page component
(rum/defc home-page [db]
  [:div.flex.flex-column
   {:style {:min-height "100vh"
            :background "var(--home-bg-base)"}}

   ;; Header
   [:div.td-navbar
    {:style {:display "flex"
             :align-items "center"
             :justify-content "space-between"
             :padding "0 32px"
             :height "60px"
             :background "var(--home-header-bg)"
             :border-bottom "1px solid var(--home-header-border)"
             :backdrop-filter "blur(20px)"}}
    [:div.flex.items-center
     [:img.pointer
      {:width "36px"
       :style {:border-radius "50%"
               :box-shadow "0 0 12px var(--home-accent-line)"}
       :src (u/asset-path "/img/hulunote.webp")}]
     [:div.pl3
      {:style {:font-size "22px"
               :font-weight "700"
               :color "var(--theme-accent-text)"
               :letter-spacing "1px"}}
      "HULUNOTE"]]
    [:div.flex.items-center
     [:a.pointer
      {:href "https://github.com/hulunote/hulunote"
       :target "_blank"
       :style {:color "var(--app-control-text)"
               :margin-right "24px"
               :text-decoration "none"
               :font-size "14px"}}
      "⭐ GitHub"]
     (if (u/is-expired?)
       [:button.pointer
        {:on-click #(router/switch-router! "/login")
         :style {:background "var(--theme-accent-gradient)"
                 :color "var(--theme-accent-text)"
                 :border "none"
                 :padding "8px 20px"
                 :border-radius "20px"
                 :font-weight "600"
                 :cursor "pointer"
                 :box-shadow "var(--home-accent-shadow-soft)"}}
        "Login"]
       [:div.flex.items-center
        [:div
         {:style {:color "var(--app-control-text)"
                  :margin-right "16px"}}
         (first (clojure.string/split (:accounts/mail (:hulunote @storage/jwt-auth)) "@"))]
        [:button.pointer
         {:on-click #(router/switch-router! "/")
          :style {:background "var(--theme-accent-gradient)"
                  :color "var(--theme-accent-text)"
                  :border "none"
                  :padding "8px 20px"
                  :border-radius "20px"
                  :font-weight "600"
                  :cursor "pointer"}}
         "My Databases"]])]]

   ;; Hero Section
   [:div.flex.flex-column.items-center.justify-center
    {:style {:background "var(--home-bg-section-reverse)"
             :padding "100px 20px 120px"
             :text-align "center"
             :position "relative"
             :overflow "hidden"}}
    ;; Radial hero glow
    [:div {:style {:position "absolute"
                   :top "30%"
                   :left "50%"
                   :transform "translate(-50%, -50%)"
                   :width "600px"
                   :height "400px"
                   :background "var(--home-hero-glow)"
                   :pointer-events "none"}}]
    [:div {:style {:position "relative" :z-index "1"}}
     [:h1 {:style {:font-size "60px"
                   :font-weight "800"
                   :color "var(--theme-accent-text)"
                   :margin "0 0 20px 0"
                   :line-height "1.1"
                   :letter-spacing "-1px"}}
      "Hulunote"]
     [:p {:style {:font-size "28px"
                  :font-weight "700"
                  :margin "0 0 16px 0"
                  :max-width "600px"
                  :background "linear-gradient(135deg, var(--home-text-accent), var(--home-text-purple))"
                  :background-clip "text"
                  :-webkit-background-clip "text"
                  :-webkit-text-fill-color "transparent"}}
      "AI + Note Hippocampus"]
     [:p {:style {:font-size "16px"
                  :color "var(--app-text-soft)"
                  :margin "0 0 48px 0"
                  :max-width "560px"}}
      "Open-source outliner with bidirectional linking, knowledge graph visualization, and Mermaid diagrams — your second brain, powered by AI"]

     [:div.flex.flex-row.justify-center
      {:style {:gap "16px"}}
      [:button.pointer
       {:on-click #(if (u/is-expired?)
                     (router/switch-router! "/login")
                     (router/switch-router! "/"))
        :style {:background "var(--theme-accent-gradient)"
                :color "var(--theme-accent-text)"
                :border "none"
                :padding "14px 32px"
                :border-radius "30px"
                :font-size "16px"
                :font-weight "600"
                :cursor "pointer"
                :box-shadow "var(--home-accent-shadow)"}}
       (if (u/is-expired?)
         "Login to Start →"
         "Go to Databases →")]
      [:a.pointer
       {:href "https://github.com/hulunote/hulunote"
        :target "_blank"
        :style {:background "transparent"
                :color "var(--app-control-text)"
                :border "1px solid var(--app-control-border)"
                :padding "12px 28px"
                :border-radius "30px"
                :font-size "16px"
                :font-weight "600"
                :text-decoration "none"
                :display "flex"
                :align-items "center"}}
       "View Source"]]]]

   ;; Core Features Section
   [:div
    {:style {:background "var(--home-bg-section)"
             :padding "100px 20px"
             :position "relative"
             :overflow "hidden"}}
    ;; Background grid
    [:div {:style {:position "absolute"
                   :top "0" :left "0" :right "0" :bottom "0"
                   :background-image "linear-gradient(var(--home-grid-line) 1px, transparent 1px), linear-gradient(90deg, var(--home-grid-line) 1px, transparent 1px)"
                   :background-size "60px 60px"
                   :pointer-events "none"}}]
    [:div.flex.flex-column.items-center
     {:style {:max-width "1200px"
              :margin "0 auto"
              :position "relative"
              :z-index "1"}}
     [:div {:style {:font-size "13px"
                    :font-weight "600"
                    :color "var(--theme-accent)"
                    :letter-spacing "3px"
                    :text-transform "uppercase"
                    :margin-bottom "16px"}}
      "Core Features"]
     [:h2 {:style {:font-size "40px"
                   :font-weight "800"
                   :color "var(--theme-accent-text)"
                   :margin "0 0 16px 0"
                   :text-align "center"}}
      "Everything You Need to Think Better"]
     [:p {:style {:font-size "16px"
                  :color "var(--app-text-subtle)"
                  :margin "0 0 60px 0"
                  :text-align "center"}}
      "Outliner structure meets AI-powered intelligence"]
     [:div
      {:style {:display "grid"
               :grid-template-columns "repeat(auto-fit, minmax(260px, 1fr))"
               :gap "20px"
               :width "100%"}}
      (feature-card "📝" "Outliner Structure"
                    "Infinite nesting, drag & drop, zoom in on any node — your thoughts, infinitely deep"
                    {:tone :accent})
      (feature-card "🔗" "Bidirectional Links"
                    "[[Wiki-style links]] with automatic backlinks — every idea is a node in your knowledge graph"
                    {:tone :purple})
      (feature-card "🌐" "Knowledge Graph"
                    "Interactive force-directed graph visualization — see your entire knowledge network, zoom, pan, click to navigate"
                    {:tone :cyan})
      (feature-card "📊" "Mermaid Diagrams"
                    "Draw flowcharts, sequence diagrams, mind maps with Mermaid syntax — AI-powered visual thinking"
                    {:tone :orange})
      (feature-card "📅" "Daily Notes"
                    "Automatic date-based journals — capture fleeting thoughts, build lasting habits"
                    {:tone :accent})
      (feature-card "📚" "Multi-Database"
                    "Isolated workspaces for work, research, life — each with its own knowledge universe"
                    {:tone :purple})]]]

   ;; AI + Hippocampus Section - Flagship
   [:div
    {:style {:background "var(--home-bg-focus)"
             :padding "120px 20px"
             :position "relative"
             :overflow "hidden"}}
    ;; Radial glow background
    [:div {:style {:position "absolute"
                   :top "50%" :left "50%"
                   :transform "translate(-50%, -50%)"
                   :width "900px" :height "900px"
                   :background "var(--home-focus-glow)"
                   :pointer-events "none"}}]
    ;; Horizontal divider glow
    [:div {:style {:position "absolute"
                   :top "0" :left "10%" :right "10%"
                   :height "1px"
                   :background "linear-gradient(90deg, transparent, var(--home-accent-line), transparent)"}}]
    [:div.flex.flex-column.items-center
     {:style {:max-width "1100px"
              :margin "0 auto"
              :position "relative"
              :z-index "1"
              :text-align "center"}}

     ;; Brain icon with glow ring
     [:div {:style {:position "relative"
                    :margin-bottom "36px"}}
      [:div {:style {:width "130px" :height "130px"
                     :border-radius "50%"
                     :background "var(--home-brain-gradient)"
                     :border "1px solid var(--home-accent-border)"
                     :display "flex"
                     :align-items "center" :justify-content "center"
                     :margin "0 auto"
                     :box-shadow "var(--home-brain-shadow)"}}
       [:div {:style {:font-size "60px"
                      :filter "drop-shadow(0 0 24px var(--theme-accent-border-strong))"}}
        "🧠"]]
      ;; Outer ring
      [:div {:style {:position "absolute"
                     :top "-15px" :left "50%"
                     :transform "translateX(-50%)"
                     :width "160px" :height "160px"
                     :border-radius "50%"
                     :border "1px solid var(--home-accent-line-soft)"
                     :pointer-events "none"}}]
      ;; Second outer ring
      [:div {:style {:position "absolute"
                     :top "-30px" :left "50%"
                     :transform "translateX(-50%)"
                     :width "190px" :height "190px"
                     :border-radius "50%"
                     :border "1px solid var(--home-accent-line-faint)"
                     :pointer-events "none"}}]]

     [:div {:style {:font-size "13px"
                    :font-weight "600"
                    :color "var(--home-text-accent)"
                    :letter-spacing "4px"
                    :text-transform "uppercase"
                    :margin-bottom "20px"}}
      "AI + Note Hippocampus"]
     [:h2 {:style {:font-size "48px"
                   :font-weight "800"
                   :color "var(--theme-accent-text)"
                   :margin "0 0 24px 0"
                   :line-height "1.15"
                   :letter-spacing "-0.5px"}}
      "Your Second Brain," [:br] "Supercharged by AI"]
     [:p {:style {:font-size "18px"
                  :color "var(--home-text-soft)"
                  :line-height "1.8"
                  :margin "0 0 56px 0"
                  :max-width "680px"}}
      "The hippocampus turns experiences into lasting memories. Hulunote does the same for your knowledge — and now AI acts as your cognitive co-pilot, helping you capture, organize, connect, and retrieve information through natural language."]

     ;; AI Pipeline Flow
     [:div {:style {:display "flex"
                    :align-items "center"
                    :justify-content "center"
                    :flex-wrap "wrap"
                    :gap "4px"
                    :margin-bottom "56px"}}
      (pipeline-step "💬" "You speak" false)
      (pipeline-step "🤖" "AI understands" false)
      (pipeline-step "📝" "Notes are created" false)
      (pipeline-step "🧠" "Knowledge connects" false)
      (pipeline-step "🌐" "Graph visualizes" true)]

     ;; AI Feature cards - 2x2 grid
     [:div {:style {:display "grid"
                    :grid-template-columns "repeat(auto-fit, minmax(260px, 1fr))"
                    :gap "20px"
                    :width "100%"
                    :margin-bottom "56px"}}
      (feature-card "🔌" "MCP Server"
                    "Connect Claude Desktop or any MCP client — read, write, and search your notes via natural language"
                    {:tone :accent})
      (feature-card "🦞" "OpenClaw Agent"
                    "Autonomous AI agents that browse, create, and organize your knowledge base around the clock"
                    {:tone :purple})
      (feature-card "✨" "AI Note Generation"
                    "Describe a topic — AI creates structured notes with hierarchical outlines and bidirectional links"
                    {:tone :purple})
      (feature-card "🔍" "Cross-DB AI Search"
                    "AI finds and connects related ideas across all your databases — surface insights you never knew existed"
                    {:tone :indigo})]

     ;; CTA buttons
     [:div.flex.flex-row.justify-center
      {:style {:gap "16px"
               :flex-wrap "wrap"}}
      [:a
       {:href "https://github.com/hulunote/hulunote-mcp-server"
        :target "_blank"
        :style {:background "var(--theme-accent-gradient)"
                :color "var(--theme-accent-text)"
                :border "none"
                :padding "14px 32px"
                :border-radius "30px"
                :font-size "15px"
                :font-weight "700"
                :text-decoration "none"
                :box-shadow "var(--home-accent-shadow)"
                :letter-spacing "0.5px"}}
       "Get MCP Server →"]
      [:a
       {:href "https://github.com/hulunote/openclaw-hulunote-assistant"
        :target "_blank"
        :style {:background "transparent"
                :color "var(--home-text-accent)"
                :border "1px solid var(--theme-accent-30)"
                :padding "14px 32px"
                :border-radius "30px"
                :font-size "15px"
                :font-weight "700"
                :text-decoration "none"
                :letter-spacing "0.5px"}}
       "Get OpenClaw Plugin →"]]]]

   ;; ==================== Visual AI Brain Section ====================
   [:div
    {:style {:background "var(--home-bg-focus)"
             :padding "120px 20px"
             :position "relative"
             :overflow "hidden"}}
    ;; Subtle radial glow
    [:div {:style {:position "absolute"
                   :top "50%" :left "50%"
                   :transform "translate(-50%, -50%)"
                   :width "1000px" :height "600px"
                   :background "var(--home-visual-glow)"
                   :pointer-events "none"}}]
    ;; Top divider
    [:div {:style {:position "absolute"
                   :top "0" :left "10%" :right "10%"
                   :height "1px"
                   :background "linear-gradient(90deg, transparent, var(--home-cyan-line), transparent)"}}]
    [:div.flex.flex-column.items-center
     {:style {:max-width "1100px"
              :margin "0 auto"
              :position "relative"
              :z-index "1"
              :text-align "center"}}

     [:div {:style {:font-size "13px"
                    :font-weight "600"
                    :color "var(--home-text-cyan)"
                    :letter-spacing "4px"
                    :text-transform "uppercase"
                    :margin-bottom "20px"}}
      "Visualize Your AI Brain"]
     [:h2 {:style {:font-size "48px"
                   :font-weight "800"
                   :color "var(--theme-accent-text)"
                   :margin "0 0 24px 0"
                   :line-height "1.15"
                   :letter-spacing "-0.5px"}}
      "Knowledge Graph" [:br]
      [:span {:style {:background "var(--home-cyan-gradient)"
                      :background-clip "text"
                      :-webkit-background-clip "text"
                      :-webkit-text-fill-color "transparent"}}
       " + Mermaid Diagrams"]]
     [:p {:style {:font-size "18px"
                  :color "var(--home-text-soft)"
                  :line-height "1.8"
                  :margin "0 0 60px 0"
                  :max-width "700px"}}
      "See your entire knowledge universe as an interactive graph. AI can operate on the graph — create, connect, and visualize ideas. Draw flowcharts, mind maps, and diagrams with simple text."]

     ;; Two-column showcase
     [:div {:style {:display "grid"
                    :grid-template-columns "repeat(auto-fit, minmax(440px, 1fr))"
                    :gap "24px"
                    :width "100%"
                    :margin-bottom "56px"}}

      ;; Knowledge Graph Preview
      [:div {:style {:background "var(--home-panel-gradient)"
                     :border-radius "16px"
                     :border "1px solid var(--home-cyan-border)"
                     :overflow "hidden"
                     :box-shadow "var(--home-panel-shadow), var(--home-cyan-inset-shadow)"}}
       ;; Title bar
       [:div {:style {:padding "12px 20px"
                      :border-bottom "1px solid var(--home-cyan-border-soft)"
                      :display "flex"
                      :align-items "center"
                      :gap "10px"}}
        [:div {:style {:width "20px" :height "20px"
                       :border-radius "6px"
                       :background "var(--home-cyan-gradient)"
                       :display "flex" :align-items "center" :justify-content "center"
                       :font-size "11px"}}
         "🌐"]
        [:span {:style {:font-size "14px" :font-weight "600" :color "var(--theme-accent-text)"}} "Knowledge Graph"]
        [:span {:style {:font-size "12px" :color "var(--home-text-faint)" :margin-left "auto"}} "Interactive"]]
       ;; Graph SVG mock
       [:div {:style {:padding "24px"
                      :min-height "280px"
                      :position "relative"
                      :background "var(--home-bg-footer)"}}
        [:svg {:width "100%" :height "280" :viewBox "0 0 460 280"
               :style {:overflow "visible"}}
         ;; Connection lines
        [:line {:x1 "230" :y1 "90" :x2 "120" :y2 "170" :stroke "var(--graph-link)" :stroke-width "1"}]
        [:line {:x1 "230" :y1 "90" :x2 "340" :y2 "140" :stroke "var(--graph-link)" :stroke-width "1"}]
        [:line {:x1 "230" :y1 "90" :x2 "180" :y2 "40" :stroke "var(--graph-link)" :stroke-width "1"}]
        [:line {:x1 "230" :y1 "90" :x2 "310" :y2 "50" :stroke "var(--graph-link)" :stroke-width "1"}]
         [:line {:x1 "120" :y1 "170" :x2 "60" :y2 "230" :stroke "var(--home-graph-link-secondary)" :stroke-width "1"}]
         [:line {:x1 "120" :y1 "170" :x2 "180" :y2 "240" :stroke "var(--home-graph-link-secondary)" :stroke-width "1"}]
         [:line {:x1 "340" :y1 "140" :x2 "400" :y2 "210" :stroke "var(--home-graph-link-secondary)" :stroke-width "1"}]
         [:line {:x1 "340" :y1 "140" :x2 "300" :y2 "220" :stroke "var(--home-graph-link-secondary)" :stroke-width "1"}]
         [:line {:x1 "180" :y1 "40" :x2 "310" :y2 "50" :stroke "var(--theme-accent-border-soft)" :stroke-width "1"}]
         [:line {:x1 "60" :y1 "230" :x2 "180" :y2 "240" :stroke "var(--home-graph-link-secondary-soft)" :stroke-width "1"}]
         [:line {:x1 "300" :y1 "220" :x2 "400" :y2 "210" :stroke "var(--home-graph-link-secondary-soft)" :stroke-width "1"}]
         ;; Hub node (center)
        [:circle {:cx "230" :cy "90" :r "14" :fill "var(--graph-node-hub)" :stroke "var(--theme-accent-border-strong)" :stroke-width "2"
                   :filter "url(#home-glow)"}]
        [:text {:x "230" :y "120" :text-anchor "middle" :fill "var(--app-control-text)" :font-size "10"} "AI Project"]
        ;; Connected nodes
        [:circle {:cx "120" :cy "170" :r "10" :fill "var(--graph-node-active)" :stroke "var(--home-graph-node-active-stroke)" :stroke-width "1.5"}]
        [:text {:x "120" :y "195" :text-anchor "middle" :fill "var(--home-text-dim)" :font-size "9"} "ML Notes"]
        [:circle {:cx "340" :cy "140" :r "10" :fill "var(--graph-node-active)" :stroke "var(--home-graph-node-active-stroke)" :stroke-width "1.5"}]
        [:text {:x "340" :y "165" :text-anchor "middle" :fill "var(--home-text-dim)" :font-size "9"} "Research"]
        [:circle {:cx "180" :cy "40" :r "8" :fill "var(--graph-node-light)" :stroke "var(--home-graph-node-light-stroke)" :stroke-width "1.5"}]
        [:text {:x "180" :y "28" :text-anchor "middle" :fill "var(--home-text-soft)" :font-size "9"} "Ideas"]
        [:circle {:cx "310" :cy "50" :r "8" :fill "var(--graph-node-light)" :stroke "var(--home-graph-node-light-stroke)" :stroke-width "1.5"}]
        [:text {:x "310" :y "38" :text-anchor "middle" :fill "var(--home-text-soft)" :font-size "9"} "Design"]
         ;; Leaf nodes
        [:circle {:cx "60" :cy "230" :r "5" :fill "var(--graph-node-isolated)" :stroke "var(--home-graph-node-isolated-stroke)" :stroke-width "1"}]
        [:text {:x "60" :y "250" :text-anchor "middle" :fill "var(--home-text-faint)" :font-size "8"} "Draft"]
        [:circle {:cx "180" :cy "240" :r "5" :fill "var(--graph-node-isolated)" :stroke "var(--home-graph-node-isolated-stroke)" :stroke-width "1"}]
        [:text {:x "180" :y "260" :text-anchor "middle" :fill "var(--home-text-faint)" :font-size "8"} "TODO"]
        [:circle {:cx "400" :cy "210" :r "6" :fill "var(--graph-node-isolated)" :stroke "var(--home-graph-node-isolated-stroke)" :stroke-width "1"}]
        [:text {:x "400" :y "230" :text-anchor "middle" :fill "var(--home-text-faint)" :font-size "8"} "Paper"]
        [:circle {:cx "300" :cy "220" :r "5" :fill "var(--graph-node-isolated)" :stroke "var(--home-graph-node-isolated-stroke)" :stroke-width "1"}]
        [:text {:x "300" :y "240" :text-anchor "middle" :fill "var(--home-text-faint)" :font-size "8"} "Code"]
         ;; Glow filter
         [:defs
          [:filter {:id "home-glow"}
           [:feGaussianBlur {:stdDeviation "4" :result "coloredBlur"}]
           [:feMerge
            [:feMergeNode {:in "coloredBlur"}]
            [:feMergeNode {:in "SourceGraphic"}]]]]]
       ;; Floating stats
       [:div {:style {:position "absolute"
                      :bottom "8px" :right "8px"
                      :font-size "11px"
                      :color "var(--home-cyan-soft-text)"}}
        "9 notes · 11 connections"]]]

      ;; Mermaid Diagram Preview
      [:div {:style {:background "var(--home-panel-gradient)"
                     :border-radius "16px"
                     :border "1px solid var(--home-orange-border)"
                     :overflow "hidden"
                     :box-shadow "var(--home-panel-shadow), var(--home-orange-inset-shadow)"}}
       ;; Title bar
       [:div {:style {:padding "12px 20px"
                      :border-bottom "1px solid var(--home-orange-border-soft)"
                      :display "flex"
                      :align-items "center"
                      :gap "10px"}}
        [:div {:style {:width "20px" :height "20px"
                       :border-radius "6px"
                       :background "var(--home-orange-gradient)"
                       :display "flex" :align-items "center" :justify-content "center"
                       :font-size "11px"}}
         "📊"]
        [:span {:style {:font-size "14px" :font-weight "600" :color "var(--theme-accent-text)"}} "Mermaid Diagrams"]
        [:span {:style {:font-size "12px" :color "var(--home-text-faint)" :margin-left "auto"}} "```mermaid"]]
       ;; Mermaid demo area
       [:div {:style {:display "flex" :min-height "280px"}}
        ;; Code side
        [:div {:style {:flex "1"
                       :padding "16px"
                       :background "var(--home-bg-footer)"
                       :border-right "1px solid var(--home-divider)"
                       :font-family "'SF Mono', 'Fira Code', monospace"
                       :font-size "12px"
                       :line-height "1.8"
                       :color "var(--home-text-dim)"
                       :overflow "hidden"}}
         [:div {:style {:color "var(--home-code-magenta)"}} "graph TD"]
         [:div {:style {:padding-left "12px"}}
          [:span {:style {:color "var(--home-code-key)"}} "  A"]
          [:span {:style {:color "var(--home-code-symbol)"}} "[🧠 AI Brain]"]
          [:span {:style {:color "var(--home-code-symbol)"}} " --> "]
          [:span {:style {:color "var(--home-code-key)"}} "B"]
          [:span {:style {:color "var(--home-code-symbol)"}} "[📝 Notes]"]]
         [:div {:style {:padding-left "12px"}}
          [:span {:style {:color "var(--home-code-key)"}} "  A"]
          [:span {:style {:color "var(--home-code-symbol)"}} " --> "]
          [:span {:style {:color "var(--home-code-key)"}} "C"]
          [:span {:style {:color "var(--home-code-symbol)"}} "[🌐 Graph]"]]
         [:div {:style {:padding-left "12px"}}
          [:span {:style {:color "var(--home-code-key)"}} "  B"]
          [:span {:style {:color "var(--home-code-symbol)"}} " --> "]
          [:span {:style {:color "var(--home-code-key)"}} "D"]
          [:span {:style {:color "var(--home-code-symbol)"}} "[✨ Insights]"]]
         [:div {:style {:padding-left "12px"}}
          [:span {:style {:color "var(--home-code-key)"}} "  C"]
          [:span {:style {:color "var(--home-code-symbol)"}} " --> "]
          [:span {:style {:color "var(--home-code-key)"}} "D"]]
         [:div {:style {:padding-left "12px"}}
          [:span {:style {:color "var(--home-code-key)"}} "  D"]
          [:span {:style {:color "var(--home-code-symbol)"}} " --> "]
          [:span {:style {:color "var(--home-code-key)"}} "E"]
          [:span {:style {:color "var(--home-code-symbol)"}} "[🚀 Action]"]]]
        ;; Rendered side
        [:div {:style {:flex "1"
                       :padding "20px"
                       :display "flex"
                       :align-items "center"
                       :justify-content "center"
                       :background "var(--home-mermaid-canvas-bg)"}}
         [:svg {:width "180" :height "250" :viewBox "0 0 180 250"}
          ;; Flowchart boxes
          ;; A - AI Brain
          [:rect {:x "50" :y "5" :width "80" :height "32" :rx "6" :fill "var(--home-mermaid-node-ai-bg)" :stroke "var(--home-mermaid-node-ai-border)" :stroke-width "1.5"}]
          [:text {:x "90" :y "25" :text-anchor "middle" :fill "var(--theme-accent-text)" :font-size "10" :font-weight "600"} "🧠 AI Brain"]
          ;; Arrows from A
          [:line {:x1 "70" :y1 "37" :x2 "50" :y2 "65" :stroke "var(--home-stroke)" :stroke-width "1" :marker-end "url(#arrowhead)"}]
          [:line {:x1 "110" :y1 "37" :x2 "130" :y2 "65" :stroke "var(--home-stroke)" :stroke-width "1" :marker-end "url(#arrowhead)"}]
          ;; B - Notes
          [:rect {:x "10" :y "65" :width "72" :height "32" :rx "6" :fill "var(--home-mermaid-node-notes-bg)" :stroke "var(--home-mermaid-node-notes-border)" :stroke-width "1.5"}]
          [:text {:x "46" :y "85" :text-anchor "middle" :fill "var(--theme-accent-text)" :font-size "10"} "📝 Notes"]
          ;; C - Graph
          [:rect {:x "98" :y "65" :width "72" :height "32" :rx "6" :fill "var(--home-mermaid-node-graph-bg)" :stroke "var(--home-mermaid-node-graph-border)" :stroke-width "1.5"}]
          [:text {:x "134" :y "85" :text-anchor "middle" :fill "var(--theme-accent-text)" :font-size "10"} "🌐 Graph"]
          ;; Arrows to D
          [:line {:x1 "50" :y1 "97" :x2 "70" :y2 "135" :stroke "var(--home-stroke)" :stroke-width "1" :marker-end "url(#arrowhead)"}]
          [:line {:x1 "130" :y1 "97" :x2 "110" :y2 "135" :stroke "var(--home-stroke)" :stroke-width "1" :marker-end "url(#arrowhead)"}]
          ;; D - Insights
          [:rect {:x "45" :y "135" :width "90" :height "32" :rx "6" :fill "var(--home-mermaid-node-insights-bg)" :stroke "var(--home-mermaid-node-insights-border)" :stroke-width "1.5"}]
          [:text {:x "90" :y "155" :text-anchor "middle" :fill "var(--theme-accent-text)" :font-size "10" :font-weight "600"} "✨ Insights"]
          ;; Arrow to E
          [:line {:x1 "90" :y1 "167" :x2 "90" :y2 "195" :stroke "var(--home-stroke)" :stroke-width "1" :marker-end "url(#arrowhead)"}]
          ;; E - Action
          [:rect {:x "45" :y "195" :width "90" :height "32" :rx "6" :fill "var(--home-mermaid-node-action-bg)" :stroke "var(--home-mermaid-node-action-border)" :stroke-width "1.5"}]
          [:text {:x "90" :y "215" :text-anchor "middle" :fill "var(--theme-accent-text)" :font-size "10"} "🚀 Action"]
          ;; Arrow marker
          [:defs
           [:marker {:id "arrowhead" :markerWidth "8" :markerHeight "6" :refX "8" :refY "3" :orient "auto"}
            [:polygon {:points "0 0, 8 3, 0 6" :fill "var(--home-stroke)"}]]]]]]]]

     ;; Feature highlight pills
     [:div {:style {:display "flex"
                    :flex-wrap "wrap"
                    :justify-content "center"
                    :gap "12px"
                    :margin-bottom "40px"}}
      (for [[icon text] [["🔍" "Zoom & Pan"]
                         ["🖱️" "Click to Navigate"]
                         ["🤏" "Drag Nodes"]
                         ["📐" "Flowcharts"]
                         ["🔄" "Sequence Diagrams"]
                         ["🧠" "Mind Maps"]
                         ["📊" "Gantt Charts"]
                         ["🤖" "AI Creates Diagrams"]]]
        [:div {:key text
               :style {:background "linear-gradient(135deg, var(--home-cyan-soft), var(--theme-accent-soft))"
                       :border "1px solid var(--home-cyan-border)"
                       :padding "8px 16px"
                       :border-radius "20px"
                       :font-size "13px"
                       :color "var(--app-control-text)"
                       :display "flex"
                       :align-items "center"
                       :gap "6px"}}
         [:span {:style {:font-size "14px"}} icon]
         text])]

     ;; Bottom tagline
     [:p {:style {:font-size "16px"
                  :color "var(--home-text-faint)"
                  :margin "0"
                  :max-width "600px"}}
      "More imagination space — visualize your AI note brain. Let AI operate on graphs and create diagrams for you."]]]

   ;; Tech Stack Section
   [:div
    {:style {:background "var(--home-bg-section-reverse)"
             :padding "80px 20px"}}
    [:div.flex.flex-column.items-center
     {:style {:max-width "800px"
              :margin "0 auto"}}
     [:div {:style {:font-size "13px"
                    :font-weight "600"
                    :color "var(--theme-accent)"
                    :letter-spacing "3px"
                    :text-transform "uppercase"
                    :margin-bottom "16px"}}
      "Tech Stack"]
     [:h2 {:style {:font-size "36px"
                   :font-weight "800"
                   :color "var(--theme-accent-text)"
                   :margin "0 0 48px 0"}}
      "Built for Performance"]
     [:div
      {:style {:display "grid"
               :grid-template-columns "repeat(auto-fit, minmax(300px, 1fr))"
               :gap "24px"
               :width "100%"}}

      ;; Backend
      [:div
       {:style {:background "var(--home-panel-gradient)"
                :border-radius "16px"
                :padding "32px"
                :border "1px solid var(--theme-accent-15)"}}
       [:h3 {:style {:font-size "20px"
                     :font-weight "700"
                     :color "var(--theme-accent)"
                     :margin "0 0 24px 0"}}
        "🦀 Backend"]
       (tech-item "Language" "Rust")
       (tech-item "Framework" "Axum")
       (tech-item "ORM" "SQLx")
       (tech-item "Database" "PostgreSQL")]

      ;; Frontend
      [:div
       {:style {:background "var(--home-panel-gradient)"
                :border-radius "16px"
                :padding "32px"
                :border "1px solid var(--home-purple-border)"}}
       [:h3 {:style {:font-size "20px"
                     :font-weight "700"
                     :color "var(--home-text-purple)"
                     :margin "0 0 24px 0"}}
        "⚛️ Frontend"]
       (tech-item "Language" "ClojureScript")
       (tech-item "UI Library" "Rum")
       (tech-item "State" "DataScript")
       (tech-item "Build Tool" "Shadow-cljs")]]]]

   ;; Quick Start Section
   [:div
    {:style {:background "var(--home-bg-section)"
             :padding "80px 20px"}}
    [:div.flex.flex-column.items-center
     {:style {:max-width "800px"
              :margin "0 auto"}}
     [:div {:style {:font-size "13px"
                    :font-weight "600"
                    :color "var(--theme-accent)"
                    :letter-spacing "3px"
                    :text-transform "uppercase"
                    :margin-bottom "16px"}}
      "Quick Start"]
     [:h2 {:style {:font-size "36px"
                   :font-weight "800"
                   :color "var(--theme-accent-text)"
                   :margin "0 0 48px 0"}}
      "Up and Running in Minutes"]
     [:div
      {:style {:background "var(--home-panel-gradient-alt)"
               :border-radius "16px"
               :padding "32px"
               :width "100%"
               :font-family "Monaco, Consolas, 'Courier New', monospace"
               :border "1px solid var(--theme-accent-15)"
               :box-shadow "var(--home-panel-shadow)"}}
      [:pre
       {:style {:color "var(--home-text-soft)"
                :margin "0"
                :font-size "14px"
                :line-height "2"
                :white-space "pre-wrap"
                :word-break "break-all"}}
       "# 1. Initialize database\n"
       [:span {:style {:color "var(--home-text-accent)"}} "createdb hulunote_open\npsql -d hulunote_open -f hulunote-rust/init.sql\n\n"]
       "# 2. Start backend\n"
       [:span {:style {:color "var(--home-text-accent)"}} "cd hulunote-rust && cargo run\n\n"]
       "# 3. Start frontend\n"
       [:span {:style {:color "var(--home-text-accent)"}} "cd hulunote && yarn && shadow-cljs watch hulunote\n\n"]
       "# 4. Open browser at http://localhost:6689"]]
     [:div.mt4
      {:style {:color "var(--home-text-faint)"
               :font-size "14px"}}
      "Test Account: " [:code {:style {:background "var(--theme-accent-15)" :padding "2px 8px" :border-radius "4px" :color "var(--home-text-accent)"}} "chanshunli@gmail.com"]
      " / "
      [:code {:style {:background "var(--theme-accent-15)" :padding "2px 8px" :border-radius "4px" :color "var(--home-text-accent)"}} "123456"]]]]

   ;; CTA Section
   [:div
    {:style {:background "var(--theme-accent-gradient)"
             :padding "80px 20px"
             :text-align "center"
             :position "relative"
             :overflow "hidden"}}
    ;; Overlay pattern
    [:div {:style {:position "absolute"
                   :top "0" :left "0" :right "0" :bottom "0"
                   :background-image "var(--home-cta-grid)"
                   :background-size "40px 40px"
                   :pointer-events "none"}}]
    [:div {:style {:position "relative" :z-index "1"}}
     [:h2 {:style {:font-size "40px"
                   :font-weight "800"
                   :color "var(--theme-accent-text)"
                   :margin "0 0 16px 0"}}
      "Open Source. Free. Self-Hosted."]
     [:p {:style {:font-size "18px"
                  :color "var(--home-text-strong)"
                  :margin "0 0 40px 0"}}
      "Your data, your control — your second brain belongs to you"]
     [:div.flex.flex-row.justify-center
      {:style {:gap "16px"
               :flex-wrap "wrap"}}
      [:a
       {:href "https://github.com/hulunote/hulunote"
        :target "_blank"
        :style {:background "var(--light-surface)"
                :color "var(--theme-accent)"
                :border "none"
                :padding "14px 32px"
                :border-radius "30px"
                :font-size "16px"
                :font-weight "700"
                :text-decoration "none"}}
       "Frontend Repo"]
      [:a
       {:href "https://github.com/hulunote/hulunote-rust"
        :target "_blank"
        :style {:background "transparent"
                :color "var(--theme-accent-text)"
                :border "2px solid var(--home-cta-outline-border)"
                :padding "12px 28px"
                :border-radius "30px"
                :font-size "16px"
                :font-weight "600"
                :text-decoration "none"}}
       "Backend Repo"]
      [:a
       {:href "https://github.com/hulunote/hulunote-mcp-server"
        :target "_blank"
        :style {:background "transparent"
                :color "var(--theme-accent-text)"
                :border "2px solid var(--home-cta-outline-border)"
                :padding "12px 28px"
                :border-radius "30px"
                :font-size "16px"
                :font-weight "600"
                :text-decoration "none"}}
       "MCP Server"]
      [:a
       {:href "https://github.com/hulunote/openclaw-hulunote-assistant"
        :target "_blank"
        :style {:background "transparent"
                :color "var(--theme-accent-text)"
                :border "2px solid var(--home-cta-outline-border)"
                :padding "12px 28px"
                :border-radius "30px"
                :font-size "16px"
                :font-weight "600"
                :text-decoration "none"}}
       "OpenClaw Plugin"]]]]

   ;; Footer
   [:div
    {:style {:background "var(--home-bg-footer)"
             :padding "40px 20px"
             :text-align "center"
             :border-top "1px solid var(--home-accent-line-soft)"}}
    [:div.flex.flex-row.justify-center
     {:style {:gap "24px"
              :margin-bottom "20px"}}
     [:a {:href "https://twitter.com/hulunote"
          :target "_blank"
          :style {:color "var(--home-footer-link)"
                  :text-decoration "none"
                  :font-size "14px"}}
      "Twitter"]
     [:a {:href "https://youtube.com/@hulunote"
          :target "_blank"
          :style {:color "var(--home-footer-link)"
                  :text-decoration "none"
                  :font-size "14px"}}
      "YouTube"]
     [:a {:href "https://github.com/hulunote"
          :target "_blank"
          :style {:color "var(--home-footer-link)"
                  :text-decoration "none"
                  :font-size "14px"}}
      "GitHub"]]
    [:div {:style {:color "var(--app-text-placeholder)"
                   :font-size "13px"}}
     "© 2026  Hulunote — AI + Note Hippocampus — MIT License"]]])
