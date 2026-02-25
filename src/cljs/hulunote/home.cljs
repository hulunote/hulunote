(ns hulunote.home
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.components :as comps]
            [hulunote.storage :as storage]))

;; Feature card component
(rum/defc feature-card [icon title description]
  [:div.feature-card
   {:style {:background "#fff"
            :border-radius "12px"
            :padding "32px 24px"
            :box-shadow "0 4px 20px rgba(0,0,0,0.08)"
            :transition "transform 0.3s, box-shadow 0.3s"
            :cursor "default"
            :min-height "200px"}}
   [:div.flex.flex-column.items-center
    [:div {:style {:font-size "48px"
                   :margin-bottom "16px"}}
     icon]
    [:div {:style {:font-size "20px"
                   :font-weight "600"
                   :color "#1a1a2e"
                   :margin-bottom "12px"
                   :text-align "center"}}
     title]
    [:div {:style {:font-size "14px"
                   :color "#666"
                   :text-align "center"
                   :line-height "1.6"}}
     description]]])

;; Tech stack item
(rum/defc tech-item [label tech]
  [:div.flex.flex-row.items-center
   {:style {:padding "8px 0"
            :border-bottom "1px solid #eee"}}
   [:div {:style {:width "120px"
                  :font-weight "500"
                  :color "#666"}}
    label]
   [:div {:style {:color "#1a1a2e"}}
    tech]])

;; Home page component
(rum/defc home-page [db]
  [:div.flex.flex-column
   {:style {:min-height "100vh"}}

   ;; Header
   [:div.td-navbar
    {:style {:display "flex"
             :align-items "center"
             :justify-content "space-between"
             :padding "0 32px"
             :height "60px"
             :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"}}
    [:div.flex.items-center
     [:img.pointer
      {:width "36px"
       :style {:border-radius "50%"}
       :src (u/asset-path "/img/hulunote.webp")}]
     [:div.pl3
      {:style {:font-size "22px"
               :font-weight "700"
               :color "#fff"}}
      "HULUNOTE"]]
    [:div.flex.items-center
     [:a.pointer
      {:href "https://github.com/hulunote/hulunote"
       :target "_blank"
       :style {:color "#fff"
               :margin-right "24px"
               :text-decoration "none"
               :font-size "14px"}}
      "⭐ GitHub"]
     (if (u/is-expired?)
       [:button.pointer
        {:on-click #(router/switch-router! "/login")
         :style {:background "#fff"
                 :color "#667eea"
                 :border "none"
                 :padding "8px 20px"
                 :border-radius "20px"
                 :font-weight "600"
                 :cursor "pointer"}}
        "Login"]
       [:div.flex.items-center
        [:div
         {:style {:color "#fff"
                  :margin-right "16px"}}
         (first (clojure.string/split (:accounts/mail (:hulunote @storage/jwt-auth)) "@"))]
        [:button.pointer
         {:on-click #(router/switch-router! "/")
          :style {:background "#fff"
                  :color "#667eea"
                  :border "none"
                  :padding "8px 20px"
                  :border-radius "20px"
                  :font-weight "600"
                  :cursor "pointer"}}
         "My Databases"]])]]

   ;; Hero Section
   [:div.flex.flex-column.items-center.justify-center
    {:style {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
             :padding "80px 20px 100px"
             :text-align "center"}}
    [:h1 {:style {:font-size "52px"
                  :font-weight "800"
                  :color "#fff"
                  :margin "0 0 20px 0"
                  :line-height "1.2"}}
     "Hulunote"]
    [:p {:style {:font-size "24px"
                 :color "rgba(255,255,255,0.9)"
                 :margin "0 0 16px 0"
                 :max-width "600px"}}
     "Open-Source Outliner with Bidirectional Linking"]
    [:p {:style {:font-size "16px"
                 :color "rgba(255,255,255,0.8)"
                 :margin "0 0 40px 0"
                 :max-width "500px"}}
     "Inspired by Roam Research, designed for networked thought"]

    [:div.flex.flex-row
     {:style {:gap "16px"}}
     [:button.pointer
      {:on-click #(if (u/is-expired?)
                    (router/switch-router! "/login")
                    (router/switch-router! "/"))
       :style {:background "#fff"
               :color "#667eea"
               :border "none"
               :padding "14px 32px"
               :border-radius "30px"
               :font-size "16px"
               :font-weight "600"
               :cursor "pointer"
               :box-shadow "0 4px 15px rgba(0,0,0,0.2)"}}
      (if (u/is-expired?)
        "Login to Start →"
        "Go to Databases →")]
     [:a.pointer
      {:href "https://github.com/hulunote/hulunote"
       :target "_blank"
       :style {:background "transparent"
               :color "#fff"
               :border "2px solid #fff"
               :padding "12px 28px"
               :border-radius "30px"
               :font-size "16px"
               :font-weight "600"
               :text-decoration "none"
               :display "flex"
               :align-items "center"}}
      "View Source"]]]

   ;; Features Section
   [:div
    {:style {:background "#f8f9fa"
             :padding "80px 20px"}}
    [:div.flex.flex-column.items-center
     {:style {:max-width "1200px"
              :margin "0 auto"}}
     [:h2 {:style {:font-size "36px"
                   :font-weight "700"
                   :color "#1a1a2e"
                   :margin "0 0 48px 0"}}
      "Core Features"]
     [:div
      {:style {:display "grid"
               :grid-template-columns "repeat(auto-fit, minmax(280px, 1fr))"
               :gap "24px"
               :width "100%"}}
      (feature-card "📝" "Outliner Structure"
                    "Organize thoughts in hierarchical bullet points with infinite nesting for complex ideas")
      (feature-card "🔗" "Bidirectional Links"
                    "Connect ideas with [[wiki-style links]] and automatic backlinks to build your knowledge graph")
      (feature-card "📅" "Daily Notes"
                    "Journaling with automatic date-based pages to build consistent writing habits")
      (feature-card "📚" "Multiple Databases"
                    "Separate workspaces for different projects with isolated data for focused work")]]]

   ;; AI Section - Future Vision
   [:div
    {:style {:background "linear-gradient(135deg, #1a1a2e 0%, #2d2d44 100%)"
             :padding "80px 20px"}}
    [:div.flex.flex-column.items-center
     {:style {:max-width "900px"
              :margin "0 auto"
              :text-align "center"}}
     [:div {:style {:font-size "48px"
                    :margin-bottom "20px"}}
      "🤖"]
     [:h2 {:style {:font-size "36px"
                   :font-weight "700"
                   :color "#fff"
                   :margin "0 0 20px 0"}}
      "Embracing AI, Building the Future"]
     [:p {:style {:font-size "18px"
                  :color "rgba(255,255,255,0.8)"
                  :line-height "1.8"
                  :margin "0 0 32px 0"}}
      "Hulunote is committed to deeply integrating AI capabilities into your note-taking workflow. We plan to implement MCP (Model Context Protocol) client integration, enabling seamless collaboration between your notes and various AI models for intelligent writing assistance, knowledge summarization, and interactive Q&A."]
     [:div
      {:style {:display "flex"
               :flex-wrap "wrap"
               :gap "12px"
               :justify-content "center"}}
      [:span {:style {:background "rgba(102, 126, 234, 0.3)"
                      :color "#a5b4fc"
                      :padding "8px 16px"
                      :border-radius "20px"
                      :font-size "14px"}}
       "🔌 MCP Client Integration"]
      [:span {:style {:background "rgba(102, 126, 234, 0.3)"
                      :color "#a5b4fc"
                      :padding "8px 16px"
                      :border-radius "20px"
                      :font-size "14px"}}
       "✨ Smart Writing Assistant"]
      [:span {:style {:background "rgba(102, 126, 234, 0.3)"
                      :color "#a5b4fc"
                      :padding "8px 16px"
                      :border-radius "20px"
                      :font-size "14px"}}
       "🧠 Knowledge Graph Enhancement"]
      [:span {:style {:background "rgba(102, 126, 234, 0.3)"
                      :color "#a5b4fc"
                      :padding "8px 16px"
                      :border-radius "20px"
                      :font-size "14px"}}
       "💬 AI-Powered Q&A"]]]]

   ;; Tech Stack Section
   [:div
    {:style {:background "#fff"
             :padding "80px 20px"}}
    [:div.flex.flex-column.items-center
     {:style {:max-width "800px"
              :margin "0 auto"}}
     [:h2 {:style {:font-size "36px"
                   :font-weight "700"
                   :color "#1a1a2e"
                   :margin "0 0 48px 0"}}
      "Tech Stack"]
     [:div
      {:style {:display "grid"
               :grid-template-columns "repeat(auto-fit, minmax(300px, 1fr))"
               :gap "40px"
               :width "100%"}}

      ;; Backend
      [:div
       {:style {:background "#f8f9fa"
                :border-radius "12px"
                :padding "32px"}}
       [:h3 {:style {:font-size "20px"
                     :font-weight "600"
                     :color "#667eea"
                     :margin "0 0 24px 0"}}
        "🦀 Backend"]
       (tech-item "Language" "Rust")
       (tech-item "Framework" "Axum")
       (tech-item "ORM" "SQLx")
       (tech-item "Database" "PostgreSQL")]

      ;; Frontend
      [:div
       {:style {:background "#f8f9fa"
                :border-radius "12px"
                :padding "32px"}}
       [:h3 {:style {:font-size "20px"
                     :font-weight "600"
                     :color "#764ba2"
                     :margin "0 0 24px 0"}}
        "⚛️ Frontend"]
       (tech-item "Language" "ClojureScript")
       (tech-item "UI Library" "Rum")
       (tech-item "State" "DataScript")
       (tech-item "Build Tool" "Shadow-cljs")]]]]

   ;; Quick Start Section
   [:div
    {:style {:background "#f8f9fa"
             :padding "80px 20px"}}
    [:div.flex.flex-column.items-center
     {:style {:max-width "800px"
              :margin "0 auto"}}
     [:h2 {:style {:font-size "36px"
                   :font-weight "700"
                   :color "#1a1a2e"
                   :margin "0 0 48px 0"}}
      "Quick Start"]
     [:div
      {:style {:background "#1a1a2e"
               :border-radius "12px"
               :padding "32px"
               :width "100%"
               :font-family "Monaco, Consolas, 'Courier New', monospace"}}
      [:pre
       {:style {:color "#a5b4fc"
                :margin "0"
                :font-size "14px"
                :line-height "2"
                :white-space "pre-wrap"
                :word-break "break-all"}}
       "# 1. Initialize database\n"
       [:span {:style {:color "#98c379"}} "createdb hulunote_open\npsql -d hulunote_open -f hulunote-rust/init.sql\n\n"]
       "# 2. Start backend\n"
       [:span {:style {:color "#98c379"}} "cd hulunote-rust && cargo run\n\n"]
       "# 3. Start frontend\n"
       [:span {:style {:color "#98c379"}} "cd hulunote && yarn && shadow-cljs watch hulunote\n\n"]
       "# 4. Open browser at http://localhost:6689"]]
     [:div.mt4
      {:style {:color "#666"
               :font-size "14px"}}
      "Test Account: " [:code {:style {:background "#eee" :padding "2px 8px" :border-radius "4px"}} "chanshunli@gmail.com"]
      " / "
      [:code {:style {:background "#eee" :padding "2px 8px" :border-radius "4px"}} "123456"]]]]

   ;; CTA Section
   [:div
    {:style {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
             :padding "80px 20px"
             :text-align "center"}}
    [:h2 {:style {:font-size "36px"
                  :font-weight "700"
                  :color "#fff"
                  :margin "0 0 20px 0"}}
     "Open Source, Free, Self-Hosted"]
    [:p {:style {:font-size "18px"
                 :color "rgba(255,255,255,0.9)"
                 :margin "0 0 32px 0"}}
     "Your data, your control"]
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
               :font-weight "600"
               :text-decoration "none"}}
      "Frontend Repo"]
     [:a
      {:href "https://github.com/hulunote/hulunote-rust"
       :target "_blank"
       :style {:background "transparent"
               :color "#fff"
               :border "2px solid #fff"
               :padding "12px 28px"
               :border-radius "30px"
               :font-size "16px"
               :font-weight "600"
               :text-decoration "none"}}
      "Backend Repo"]]]

   ;; Footer
   [:div
    {:style {:background "#1a1a2e"
             :padding "40px 20px"
             :text-align "center"}}
    [:div.flex.flex-row.justify-center
     {:style {:gap "24px"
              :margin-bottom "20px"}}
     [:a {:href "https://twitter.com/hulunote"
          :target "_blank"
          :style {:color "#a5b4fc"
                  :text-decoration "none"}}
      "Twitter"]
     [:a {:href "https://youtube.com/@hulunote"
          :target "_blank"
          :style {:color "#a5b4fc"
                  :text-decoration "none"}}
      "YouTube"]
     [:a {:href "https://github.com/hulunote"
          :target "_blank"
          :style {:color "#a5b4fc"
                  :text-decoration "none"}}
      "GitHub"]]
    [:div {:style {:color "rgba(255,255,255,0.5)"
                   :font-size "14px"}}
     "© 2026  Hulunote - MIT License"]]])
