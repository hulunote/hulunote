(ns hulunote.home
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.components :as comps]
            [hulunote.storage :as storage]))

;; Feature card component - dark futuristic style
(rum/defc feature-card [icon title description & [{:keys [glow-color] :or {glow-color "102, 126, 234"}}]]
  [:div.feature-card
   {:style {:background "linear-gradient(145deg, #1e1e30 0%, #16162a 100%)"
            :border-radius "16px"
            :padding "32px 24px"
            :border (str "1px solid rgba(" glow-color ", 0.2)")
            :box-shadow (str "0 4px 30px rgba(0,0,0,0.3), inset 0 1px 0 rgba(" glow-color ", 0.1)")
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
                  :background (str "linear-gradient(90deg, transparent, rgba(" glow-color ", 0.6), transparent)")}}]
   [:div.flex.flex-column.items-center
    [:div {:style {:font-size "44px"
                   :margin-bottom "16px"
                   :filter (str "drop-shadow(0 0 8px rgba(" glow-color ", 0.5))")}}
     icon]
    [:div {:style {:font-size "18px"
                   :font-weight "700"
                   :color "#fff"
                   :margin-bottom "12px"
                   :text-align "center"
                   :letter-spacing "0.5px"}}
     title]
    [:div {:style {:font-size "13px"
                   :color "rgba(255,255,255,0.6)"
                   :text-align "center"
                   :line-height "1.7"}}
     description]]])

;; AI pipeline step component
(rum/defc pipeline-step [icon label is-last]
  [:div.flex.items-center
   [:div {:style {:background "linear-gradient(135deg, rgba(102,126,234,0.15), rgba(118,75,162,0.15))"
                  :border "1px solid rgba(102,126,234,0.25)"
                  :color "#fff"
                  :padding "12px 22px"
                  :border-radius "28px"
                  :font-size "13px"
                  :font-weight "500"
                  :white-space "nowrap"
                  :display "flex"
                  :align-items "center"
                  :gap "8px"
                  :box-shadow "0 2px 12px rgba(0,0,0,0.2)"}}
    [:span {:style {:font-size "16px"}} icon]
    label]
   (when-not is-last
     [:div {:style {:color "rgba(102,126,234,0.5)"
                    :padding "0 6px"
                    :font-size "20px"
                    :font-weight "300"}}
      "→"])])

;; Tech stack item
(rum/defc tech-item [label tech]
  [:div.flex.flex-row.items-center
   {:style {:padding "8px 0"
            :border-bottom "1px solid rgba(255,255,255,0.06)"}}
   [:div {:style {:width "120px"
                  :font-weight "500"
                  :color "rgba(255,255,255,0.5)"}}
    label]
   [:div {:style {:color "#fff"}}
    tech]])

;; Home page component
(rum/defc home-page [db]
  [:div.flex.flex-column
   {:style {:min-height "100vh"
            :background "#0a0a1a"}}

   ;; Header
   [:div.td-navbar
    {:style {:display "flex"
             :align-items "center"
             :justify-content "space-between"
             :padding "0 32px"
             :height "60px"
             :background "rgba(10,10,26,0.9)"
             :border-bottom "1px solid rgba(102,126,234,0.15)"
             :backdrop-filter "blur(20px)"}}
    [:div.flex.items-center
     [:img.pointer
      {:width "36px"
       :style {:border-radius "50%"
               :box-shadow "0 0 12px rgba(102,126,234,0.3)"}
       :src (u/asset-path "/img/hulunote.webp")}]
     [:div.pl3
      {:style {:font-size "22px"
               :font-weight "700"
               :color "#fff"
               :letter-spacing "1px"}}
      "HULUNOTE"]]
    [:div.flex.items-center
     [:a.pointer
      {:href "https://github.com/hulunote/hulunote"
       :target "_blank"
       :style {:color "rgba(255,255,255,0.7)"
               :margin-right "24px"
               :text-decoration "none"
               :font-size "14px"}}
      "⭐ GitHub"]
     (if (u/is-expired?)
       [:button.pointer
        {:on-click #(router/switch-router! "/login")
         :style {:background "linear-gradient(135deg, #667eea, #764ba2)"
                 :color "#fff"
                 :border "none"
                 :padding "8px 20px"
                 :border-radius "20px"
                 :font-weight "600"
                 :cursor "pointer"
                 :box-shadow "0 2px 12px rgba(102,126,234,0.4)"}}
        "Login"]
       [:div.flex.items-center
        [:div
         {:style {:color "rgba(255,255,255,0.7)"
                  :margin-right "16px"}}
         (first (clojure.string/split (:accounts/mail (:hulunote @storage/jwt-auth)) "@"))]
        [:button.pointer
         {:on-click #(router/switch-router! "/")
          :style {:background "linear-gradient(135deg, #667eea, #764ba2)"
                  :color "#fff"
                  :border "none"
                  :padding "8px 20px"
                  :border-radius "20px"
                  :font-weight "600"
                  :cursor "pointer"}}
         "My Databases"]])]]

   ;; Hero Section
   [:div.flex.flex-column.items-center.justify-center
    {:style {:background "linear-gradient(180deg, #0d0d1a 0%, #141428 100%)"
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
                   :background "radial-gradient(ellipse, rgba(102,126,234,0.12) 0%, rgba(118,75,162,0.06) 50%, transparent 70%)"
                   :pointer-events "none"}}]
    [:div {:style {:position "relative" :z-index "1"}}
     [:h1 {:style {:font-size "60px"
                   :font-weight "800"
                   :color "#fff"
                   :margin "0 0 20px 0"
                   :line-height "1.1"
                   :letter-spacing "-1px"}}
      "Hulunote"]
     [:p {:style {:font-size "28px"
                  :font-weight "700"
                  :margin "0 0 16px 0"
                  :max-width "600px"
                  :background "linear-gradient(135deg, #a5b4fc, #c084fc)"
                  :background-clip "text"
                  :-webkit-background-clip "text"
                  :-webkit-text-fill-color "transparent"}}
      "AI + Note Hippocampus"]
     [:p {:style {:font-size "16px"
                  :color "rgba(255,255,255,0.5)"
                  :margin "0 0 48px 0"
                  :max-width "500px"}}
      "Open-source outliner with bidirectional linking — your second brain, powered by AI"]

     [:div.flex.flex-row.justify-center
      {:style {:gap "16px"}}
      [:button.pointer
       {:on-click #(if (u/is-expired?)
                     (router/switch-router! "/login")
                     (router/switch-router! "/"))
        :style {:background "linear-gradient(135deg, #667eea, #764ba2)"
                :color "#fff"
                :border "none"
                :padding "14px 32px"
                :border-radius "30px"
                :font-size "16px"
                :font-weight "600"
                :cursor "pointer"
                :box-shadow "0 4px 20px rgba(102,126,234,0.4)"}}
       (if (u/is-expired?)
         "Login to Start →"
         "Go to Databases →")]
      [:a.pointer
       {:href "https://github.com/hulunote/hulunote"
        :target "_blank"
        :style {:background "transparent"
                :color "rgba(255,255,255,0.7)"
                :border "1px solid rgba(255,255,255,0.2)"
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
    {:style {:background "linear-gradient(180deg, #141428 0%, #0d0d1a 100%)"
             :padding "100px 20px"
             :position "relative"
             :overflow "hidden"}}
    ;; Background grid
    [:div {:style {:position "absolute"
                   :top "0" :left "0" :right "0" :bottom "0"
                   :background-image "linear-gradient(rgba(102,126,234,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(102,126,234,0.03) 1px, transparent 1px)"
                   :background-size "60px 60px"
                   :pointer-events "none"}}]
    [:div.flex.flex-column.items-center
     {:style {:max-width "1200px"
              :margin "0 auto"
              :position "relative"
              :z-index "1"}}
     [:div {:style {:font-size "13px"
                    :font-weight "600"
                    :color "#667eea"
                    :letter-spacing "3px"
                    :text-transform "uppercase"
                    :margin-bottom "16px"}}
      "Core Features"]
     [:h2 {:style {:font-size "40px"
                   :font-weight "800"
                   :color "#fff"
                   :margin "0 0 16px 0"
                   :text-align "center"}}
      "Everything You Need to Think Better"]
     [:p {:style {:font-size "16px"
                  :color "rgba(255,255,255,0.4)"
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
                    {:glow-color "102, 126, 234"})
      (feature-card "🔗" "Bidirectional Links"
                    "[[Wiki-style links]] with automatic backlinks — every idea is a node in your knowledge graph"
                    {:glow-color "118, 75, 162"})
      (feature-card "📅" "Daily Notes"
                    "Automatic date-based journals — capture fleeting thoughts, build lasting habits"
                    {:glow-color "102, 126, 234"})
      (feature-card "📚" "Multi-Database"
                    "Isolated workspaces for work, research, life — each with its own knowledge universe"
                    {:glow-color "118, 75, 162"})]]]

   ;; AI + Hippocampus Section - Flagship
   [:div
    {:style {:background "linear-gradient(180deg, #0d0d1a 0%, #0a0a16 50%, #0d0d1a 100%)"
             :padding "120px 20px"
             :position "relative"
             :overflow "hidden"}}
    ;; Radial glow background
    [:div {:style {:position "absolute"
                   :top "50%" :left "50%"
                   :transform "translate(-50%, -50%)"
                   :width "900px" :height "900px"
                   :background "radial-gradient(circle, rgba(102,126,234,0.08) 0%, rgba(118,75,162,0.04) 35%, transparent 65%)"
                   :pointer-events "none"}}]
    ;; Horizontal divider glow
    [:div {:style {:position "absolute"
                   :top "0" :left "10%" :right "10%"
                   :height "1px"
                   :background "linear-gradient(90deg, transparent, rgba(102,126,234,0.3), transparent)"}}]
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
                     :background "linear-gradient(135deg, rgba(102,126,234,0.12), rgba(118,75,162,0.12))"
                     :border "1px solid rgba(102,126,234,0.25)"
                     :display "flex"
                     :align-items "center" :justify-content "center"
                     :margin "0 auto"
                     :box-shadow "0 0 60px rgba(102,126,234,0.15), 0 0 120px rgba(118,75,162,0.08), inset 0 0 30px rgba(102,126,234,0.05)"}}
       [:div {:style {:font-size "60px"
                      :filter "drop-shadow(0 0 24px rgba(102,126,234,0.5))"}}
        "🧠"]]
      ;; Outer ring
      [:div {:style {:position "absolute"
                     :top "-15px" :left "50%"
                     :transform "translateX(-50%)"
                     :width "160px" :height "160px"
                     :border-radius "50%"
                     :border "1px solid rgba(102,126,234,0.1)"
                     :pointer-events "none"}}]
      ;; Second outer ring
      [:div {:style {:position "absolute"
                     :top "-30px" :left "50%"
                     :transform "translateX(-50%)"
                     :width "190px" :height "190px"
                     :border-radius "50%"
                     :border "1px solid rgba(102,126,234,0.05)"
                     :pointer-events "none"}}]]

     [:div {:style {:font-size "13px"
                    :font-weight "600"
                    :color "#a5b4fc"
                    :letter-spacing "4px"
                    :text-transform "uppercase"
                    :margin-bottom "20px"}}
      "AI + Note Hippocampus"]
     [:h2 {:style {:font-size "48px"
                   :font-weight "800"
                   :color "#fff"
                   :margin "0 0 24px 0"
                   :line-height "1.15"
                   :letter-spacing "-0.5px"}}
      "Your Second Brain," [:br] "Supercharged by AI"]
     [:p {:style {:font-size "18px"
                  :color "rgba(255,255,255,0.5)"
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
      (pipeline-step "🧠" "Knowledge connects" true)]

     ;; AI Feature cards - 2x2 grid
     [:div {:style {:display "grid"
                    :grid-template-columns "repeat(auto-fit, minmax(260px, 1fr))"
                    :gap "20px"
                    :width "100%"
                    :margin-bottom "56px"}}
      (feature-card "🔌" "MCP Server"
                    "Connect Claude Desktop or any MCP client — read, write, and search your notes via natural language"
                    {:glow-color "102, 126, 234"})
      (feature-card "🦞" "OpenClaw Agent"
                    "Autonomous AI agents that browse, create, and organize your knowledge base around the clock"
                    {:glow-color "118, 75, 162"})
      (feature-card "✨" "AI Note Generation"
                    "Describe a topic — AI creates structured notes with hierarchical outlines and bidirectional links"
                    {:glow-color "139, 92, 246"})
      (feature-card "🔍" "Cross-DB AI Search"
                    "AI finds and connects related ideas across all your databases — surface insights you never knew existed"
                    {:glow-color "99, 102, 241"})]

     ;; CTA buttons
     [:div.flex.flex-row.justify-center
      {:style {:gap "16px"
               :flex-wrap "wrap"}}
      [:a
       {:href "https://github.com/hulunote/hulunote-mcp-server"
        :target "_blank"
        :style {:background "linear-gradient(135deg, #667eea, #764ba2)"
                :color "#fff"
                :border "none"
                :padding "14px 32px"
                :border-radius "30px"
                :font-size "15px"
                :font-weight "700"
                :text-decoration "none"
                :box-shadow "0 4px 24px rgba(102,126,234,0.4)"
                :letter-spacing "0.5px"}}
       "Get MCP Server →"]
      [:a
       {:href "https://github.com/hulunote/openclaw-hulunote-assistant"
        :target "_blank"
        :style {:background "transparent"
                :color "#a5b4fc"
                :border "1px solid rgba(102,126,234,0.3)"
                :padding "14px 32px"
                :border-radius "30px"
                :font-size "15px"
                :font-weight "700"
                :text-decoration "none"
                :letter-spacing "0.5px"}}
       "Get OpenClaw Plugin →"]]]]

   ;; Tech Stack Section
   [:div
    {:style {:background "linear-gradient(180deg, #0d0d1a 0%, #141428 100%)"
             :padding "80px 20px"}}
    [:div.flex.flex-column.items-center
     {:style {:max-width "800px"
              :margin "0 auto"}}
     [:div {:style {:font-size "13px"
                    :font-weight "600"
                    :color "#667eea"
                    :letter-spacing "3px"
                    :text-transform "uppercase"
                    :margin-bottom "16px"}}
      "Tech Stack"]
     [:h2 {:style {:font-size "36px"
                   :font-weight "800"
                   :color "#fff"
                   :margin "0 0 48px 0"}}
      "Built for Performance"]
     [:div
      {:style {:display "grid"
               :grid-template-columns "repeat(auto-fit, minmax(300px, 1fr))"
               :gap "24px"
               :width "100%"}}

      ;; Backend
      [:div
       {:style {:background "linear-gradient(145deg, #1e1e30, #16162a)"
                :border-radius "16px"
                :padding "32px"
                :border "1px solid rgba(102,126,234,0.15)"}}
       [:h3 {:style {:font-size "20px"
                     :font-weight "700"
                     :color "#667eea"
                     :margin "0 0 24px 0"}}
        "🦀 Backend"]
       (tech-item "Language" "Rust")
       (tech-item "Framework" "Axum")
       (tech-item "ORM" "SQLx")
       (tech-item "Database" "PostgreSQL")]

      ;; Frontend
      [:div
       {:style {:background "linear-gradient(145deg, #1e1e30, #16162a)"
                :border-radius "16px"
                :padding "32px"
                :border "1px solid rgba(118,75,162,0.15)"}}
       [:h3 {:style {:font-size "20px"
                     :font-weight "700"
                     :color "#c084fc"
                     :margin "0 0 24px 0"}}
        "⚛️ Frontend"]
       (tech-item "Language" "ClojureScript")
       (tech-item "UI Library" "Rum")
       (tech-item "State" "DataScript")
       (tech-item "Build Tool" "Shadow-cljs")]]]]

   ;; Quick Start Section
   [:div
    {:style {:background "linear-gradient(180deg, #141428 0%, #0d0d1a 100%)"
             :padding "80px 20px"}}
    [:div.flex.flex-column.items-center
     {:style {:max-width "800px"
              :margin "0 auto"}}
     [:div {:style {:font-size "13px"
                    :font-weight "600"
                    :color "#667eea"
                    :letter-spacing "3px"
                    :text-transform "uppercase"
                    :margin-bottom "16px"}}
      "Quick Start"]
     [:h2 {:style {:font-size "36px"
                   :font-weight "800"
                   :color "#fff"
                   :margin "0 0 48px 0"}}
      "Up and Running in Minutes"]
     [:div
      {:style {:background "linear-gradient(145deg, #1a1a2e, #12122a)"
               :border-radius "16px"
               :padding "32px"
               :width "100%"
               :font-family "Monaco, Consolas, 'Courier New', monospace"
               :border "1px solid rgba(102,126,234,0.15)"
               :box-shadow "0 4px 30px rgba(0,0,0,0.3)"}}
      [:pre
       {:style {:color "rgba(255,255,255,0.5)"
                :margin "0"
                :font-size "14px"
                :line-height "2"
                :white-space "pre-wrap"
                :word-break "break-all"}}
       "# 1. Initialize database\n"
       [:span {:style {:color "#a5b4fc"}} "createdb hulunote_open\npsql -d hulunote_open -f hulunote-rust/init.sql\n\n"]
       "# 2. Start backend\n"
       [:span {:style {:color "#a5b4fc"}} "cd hulunote-rust && cargo run\n\n"]
       "# 3. Start frontend\n"
       [:span {:style {:color "#a5b4fc"}} "cd hulunote && yarn && shadow-cljs watch hulunote\n\n"]
       "# 4. Open browser at http://localhost:6689"]]
     [:div.mt4
      {:style {:color "rgba(255,255,255,0.4)"
               :font-size "14px"}}
      "Test Account: " [:code {:style {:background "rgba(102,126,234,0.15)" :padding "2px 8px" :border-radius "4px" :color "#a5b4fc"}} "chanshunli@gmail.com"]
      " / "
      [:code {:style {:background "rgba(102,126,234,0.15)" :padding "2px 8px" :border-radius "4px" :color "#a5b4fc"}} "123456"]]]]

   ;; CTA Section
   [:div
    {:style {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
             :padding "80px 20px"
             :text-align "center"
             :position "relative"
             :overflow "hidden"}}
    ;; Overlay pattern
    [:div {:style {:position "absolute"
                   :top "0" :left "0" :right "0" :bottom "0"
                   :background-image "linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px)"
                   :background-size "40px 40px"
                   :pointer-events "none"}}]
    [:div {:style {:position "relative" :z-index "1"}}
     [:h2 {:style {:font-size "40px"
                   :font-weight "800"
                   :color "#fff"
                   :margin "0 0 16px 0"}}
      "Open Source. Free. Self-Hosted."]
     [:p {:style {:font-size "18px"
                  :color "rgba(255,255,255,0.85)"
                  :margin "0 0 40px 0"}}
      "Your data, your control — your second brain belongs to you"]
     [:div.flex.flex-row.justify-center
      {:style {:gap "16px"
               :flex-wrap "wrap"}}
      [:a
       {:href "https://github.com/hulunote/hulunote"
        :target "_blank"
        :style {:background "#fff"
                :color "#667eea"
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
                :color "#fff"
                :border "2px solid rgba(255,255,255,0.4)"
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
                :color "#fff"
                :border "2px solid rgba(255,255,255,0.4)"
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
                :color "#fff"
                :border "2px solid rgba(255,255,255,0.4)"
                :padding "12px 28px"
                :border-radius "30px"
                :font-size "16px"
                :font-weight "600"
                :text-decoration "none"}}
       "OpenClaw Plugin"]]]]

   ;; Footer
   [:div
    {:style {:background "#0a0a16"
             :padding "40px 20px"
             :text-align "center"
             :border-top "1px solid rgba(102,126,234,0.1)"}}
    [:div.flex.flex-row.justify-center
     {:style {:gap "24px"
              :margin-bottom "20px"}}
     [:a {:href "https://twitter.com/hulunote"
          :target "_blank"
          :style {:color "rgba(165,180,252,0.6)"
                  :text-decoration "none"
                  :font-size "14px"}}
      "Twitter"]
     [:a {:href "https://youtube.com/@hulunote"
          :target "_blank"
          :style {:color "rgba(165,180,252,0.6)"
                  :text-decoration "none"
                  :font-size "14px"}}
      "YouTube"]
     [:a {:href "https://github.com/hulunote"
          :target "_blank"
          :style {:color "rgba(165,180,252,0.6)"
                  :text-decoration "none"
                  :font-size "14px"}}
      "GitHub"]]
    [:div {:style {:color "rgba(255,255,255,0.25)"
                   :font-size "13px"}}
     "© 2026  Hulunote — AI + Note Hippocampus — MIT License"]]])
