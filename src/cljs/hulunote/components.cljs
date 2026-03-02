(ns hulunote.components
  (:require [datascript.core :as d]
            [hulunote.db :as db]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.parser :as parser]
            [instaparse.core :as insta]
            [hulunote.storage :as storage]
            [re-frame.core :as re-frame]
            ["@material-ui/core" :refer [Tooltip]])
  (:require-macros
   [daiquiri.core :refer [html]])
  (:import (goog.date DateTime Interval)))

;; ==================== Bi-directional Link Functions ====================

(defn get-current-database-name
  "Get current database name from URL hash, with URL decoding"
  []
  (let [hash (.-hash js/window.location)
        ;; Parse hash like "#/app/database-name/..."
        match (re-find #"#/app/([^/]+)/" hash)]
    (when match
      ;; URL decode the database name (e.g., "Test%20Knowledge%20Base" -> "Test Knowledge Base")
      (js/decodeURIComponent (second match)))))

(defn find-note-by-title
  "Find a note by its title in the current database"
  [db title]
  (d/q '[:find ?note-id .
         :in $ ?title
         :where
         [?e :hulunote-notes/title ?title]
         [?e :hulunote-notes/id ?note-id]]
    db title))

(defn get-value
  "Get value from map, trying both keyword and string keys"
  [m k]
  (or (get m k)
      (get m (name k))
      (get m (keyword (clojure.string/replace (name k) "/" "-")))))

(defn navigate-to-note-by-title!
  "Navigate to a note by title. If not found, create it first."
  [title]
  (let [database-name (get-current-database-name)
        existing-note-id (find-note-by-title @db/dsdb title)]
    (prn "navigate-to-note-by-title! database-name:" database-name "title:" title "existing-note-id:" existing-note-id)
    (if existing-note-id
      ;; Note exists, navigate directly
      (router/go-to-note! database-name existing-note-id)
      ;; Note doesn't exist, create it first
      (when database-name
        (re-frame/dispatch-sync
          [:create-note
           {:database-name database-name
            :title title
            :op-fn (fn [note-info]
                     (prn "Note created from bi-directional link:" note-info)
                     ;; Try different possible key formats from backend
                     (let [id (or (get-value note-info :hulunote-notes/id)
                                  (get-value note-info :id)
                                  (:id note-info))
                           root-nav-id (or (get-value note-info :hulunote-notes/root-nav-id)
                                           (get-value note-info :root-nav-id)
                                           (:root_nav_id note-info)
                                           (:root-nav-id note-info))]
                       (prn "Parsed bi-directional link note - id:" id "root-nav-id:" root-nav-id)
                       (if (and id root-nav-id)
                         (do
                           ;; Add note to local datascript
                           (d/transact! db/dsdb
                             [{:hulunote-notes/id id
                               :hulunote-notes/title title
                               :hulunote-notes/root-nav-id root-nav-id
                               :hulunote-notes/database-id database-name
                               :hulunote-notes/is-delete false
                               :hulunote-notes/is-public false
                               :hulunote-notes/is-shortcut false
                               :hulunote-notes/updated-at (.toISOString (js/Date.))}])
                           ;; Add root nav to datascript
                           (d/transact! db/dsdb
                             [{:id root-nav-id
                               :content "ROOT"
                               :hulunote-note id
                               :same-deep-order 0
                               :is-display true
                               :origin-parid db/root-id}])
                           ;; Create the first editable nav node
                           (let [first-nav-id (str (d/squuid))]
                             (re-frame/dispatch-sync
                               [:create-nav
                                {:database-name database-name
                                 :note-id id
                                 :id first-nav-id
                                 :parid root-nav-id
                                 :content ""
                                 :order 0
                                 :op-fn (fn [nav-data]
                                          (prn "First nav created for bi-directional link note:" nav-data)
                                          ;; Add nav to local datascript
                                          (d/transact! db/dsdb
                                            [{:id first-nav-id
                                              :content ""
                                              :hulunote-note id
                                              :same-deep-order 0
                                              :is-display true
                                              :origin-parid root-nav-id}
                                             [:db/add [:id root-nav-id] :parid [:id first-nav-id]]])
                                          ;; Navigate to the new note page
                                          (router/go-to-note! database-name id))}])))
                         ;; If no root-nav-id, just navigate (backend may auto-create)
                         (when id
                           (prn "Warning: No root-nav-id returned for bi-directional link note, navigating anyway")
                           (router/go-to-note! database-name id)))))}])))))

;; Header for editor pages - fixed at top with proper height
(rum/defc header-editor []
  [:div.td-navbar
   {:style {:display "flex"
            :align-items "center"
            :padding "0 16px"
            :height "50px"}}
   [:img.pointer
    {:on-click #(router/switch-router! "/")
     :width "30px"
     :style {:border-radius "50%"}
     :src (u/asset-path "/img/hulunote.webp")}]
   [:div.pl3.pointer
    {:on-click #(router/switch-router! "/")
     :style {:font-size "18px"
             :font-weight "600"
             :color "#fff"}}
    "HULUNOTE"]])

(declare show-whatsapp-qrcode)
(declare show-telegram-qrcode)

(rum/defc header []
  [:div.flex.flex-row.td-navbar
   [:div.flex.inner-wrapper
    [:div.flex.items-center.flex.flex-auto
     [:div.main-hulu-name.pl3.pointer
      {:on-click #(u/open-url "https://hulunote.io")
       :style {:font-size "24px"
               :color "while"
               :font-weight "700"}}
      "HULUNOTE"]]
    [:div.flex.items-center
     [:div.head-bar-pl.pointer.nav-link {:on-click #(u/open-url "https://www.whatsapp.com/")}
      (show-whatsapp-qrcode)]
     [:div.head-bar-pl.pointer.nav-link {:on-click #(u/open-url "https://telegram.org/")}
      (show-telegram-qrcode)]

     [:div.pr3.pl3
      (if (u/is-expired?)
        [:button.btn-new.btn-login {:on-click #(router/switch-router! "/login")} "Login"]
        [:div.flex.flex-row.mb-no-show
         [:div
          (first (clojure.string/split  (:accounts/mail (:hulunote @storage/jwt-auth)) "@"))]])]]]])

(rum/defc footer []
  [:div
   {:style {:height "550px"
            :background "rgb(46 49 54)"}}
   [:div.flex.flex-row.justify-center.mt5
    [:div.flex.flex-column.pa3
     [:div.b {:style {:font-size "25px"}} "Help"]
     [:div.b.pt3.pointer {:on-click #(u/open-url "https://github.com/hulunote/hulunote")} "GitHub"]
     [:div.b.pt3 "WhatsApp group"]
     [:div.b.pt3 "Telegram group"]]
    [:div.flex.flex-column.pa3.ml4
     [:div.b {:style {:font-size "25px"}} "Resources"]
     [:div.b.pt3 "Documents"]
     [:div.b.pt3 "Blog"]]
    [:div.flex.flex-column.pa3.ml4
     [:div.b {:style {:font-size "25px"}} "About"]
     [:div.b.pt3.pointer {:on-click #(u/open-url "https://twitter.com/hulunote")} "Twitter"]
     [:div.b.pt3.pointer {:on-click #(u/open-url "https://youtube.com/@hulunote")} "YouTube"]
     [:div.b.pt3 "About us"]]]
   [:div.flex.justify-center.pa3 "© 2023 Hulunote"]])


(defn render-recursion-page-link
  "Render a clickable page link [[title]] that navigates to the note"
  [title]
  (let [title-str (if (sequential? title)
                    (apply str (flatten title))
                    (str title))]
    [:span.hulunote-note-link
     {:style {:cursor "pointer"}
      :on-click (fn [e]
                  (u/stop-click-bubble e)
                  (navigate-to-note-by-title! title-str))}
     [:span.link-style.blue "[["]
     [:span.link-title-style
      {:style {:color "var(--theme-accent)"
               :text-decoration "underline"
               :text-decoration-style "dotted"}}
      title-str]
     [:span.link-style.blue "]]"]]))

(defn render-recursion-page-tag
  "Render a clickable page tag #title that navigates to the note"
  [title]
  (let [title-str (if (sequential? title)
                    (apply str (flatten title))
                    (str title))]
    [:span.hulunote-note-link
     {:style {:cursor "pointer"}
      :on-click (fn [e]
                  (u/stop-click-bubble e)
                  (navigate-to-note-by-title! title-str))}
     [:span.link-style.blue "#"]
     [:span.link-title-style
      {:style {:color "var(--theme-accent)"
               :text-decoration "underline"
               :text-decoration-style "dotted"}}
      title-str]]))

(declare parse-and-render)

(defn transform
  [tree {:keys []}]
  (insta/transform
    {:word      (fn [& content]
                  content)
     :block     (fn [& contents]
                  (concat [:span.normal-block-class.night-textColor-2]
                    (parser/combine-adjacent-strings contents)))
     :page-link (fn [& title]
                  (if (nil? title)
                    [:span "[[]]"]
                    (render-recursion-page-link title)))
     :hashtag
     (fn [& title]
       (if (nil? title)
         [:span "#[[]]"]
         (render-recursion-page-tag title)))

     :url-link-text-contents (fn [& raw-contents]
                               (parser/combine-adjacent-strings raw-contents))
     :url-link-url-parts     (fn [& chars]
                               (clojure.string/join chars))
     :any-chars              (fn [& chars]
                               (clojure.string/join chars))
     :link-any-chars         (fn [& chars]
                               (clojure.string/join chars))

     :url-image (fn [[alt] url]
                  [:div [:img.url-image-class.pt2
                         {:alt    alt
                          :height "auto"
                          :width  "100%"
                          :src    url}]])
     :url-link       (fn [text-contents url]
                       [:a {:href     url
                            :target   "_blank"
                            :on-click (fn [e]
                                        (u/open-url url)
                                        (u/stop-click-bubble e))}
                        (clojure.string/join text-contents)])
     :http-url       (fn [args]
                       (let [url (clojure.string/join "" (rest args))]
                         [:a {:href     (str "http://" url)
                              :on-click #(do (u/stop-click-bubble %)
                                             (u/open-url (str "http://" url)))
                              :target   "_blank"}
                          (str url " ")]))
     :https-url      (fn [args]
                       (let [url (clojure.string/join "" (rest args))]
                         [:a {:href     (str "https://" url)
                              :on-click #(do (u/stop-click-bubble %)
                                             (u/open-url (str "https://" url)))
                              :target   "_blank"}
                          (str url " ")]))
     :bold           (fn [text]
                       [:strong.markdown-syntax-bold
                        (parse-and-render
                          (u/rest-join text) {})])
     :italics        (fn [text]
                       [:span.markdown-syntax-italics
                        (parse-and-render
                          (clojure.string/join text) {})])
     :strikethrough  (fn [text]
                       [:span.markdown-syntax-strikethrough
                        (parse-and-render
                          (clojure.string/join text) {})])
     :highlight      (fn [text]
                       [:span.hight-light-text-class.pl1.pr1.night-heightLight.markdown-syntax-highlight
                        (parse-and-render
                          (u/rest-join text) {}) ])
     :code           (fn [text]
                       [:code.pl1.pr1.night-codeBg.markdown-syntax-code
                        [:span (u/rest-join text)]])
     :enter          (fn [text]
                       [:br])
     :line          (fn [text]
                      [:div.mt2 {:style {:background "rgb(216, 216, 216)" :height "1px"}}])}
    tree))

(rum/defc parse-and-render
  [string render-params]
  (try
    (let [result (parser/parse-to-ast string)]
      (cond
        (insta/failure? result)
        , [:span
           {:title (pr-str (insta/get-failure result))
            :class "parse-failure"}
           string]
        :else
        ,
        (html (vec (transform result render-params)))))
    (catch :default e
      [:span
       {:title (str "parse error" e)
        :class "parse-error"}
       string])))

(rum/defc toast
  [db]
  (let [{:keys [date text]} (db/get-message db)]
    (when (and date (< (.getTime (DateTime.)) date))
      [:div.absolute.flex.flex-row.items-center.justify-center
       {:style {:width "100%"
                :z-index 10001
                :bottom "10em"}}
       [:div.ph3.pv2.br3.animated.fadeIn
        {:style {:background "#303030"
                 :opacity "0.8"
                 :color "white"}}
        text]])))

(defn wrapped-tooltip [props children]
  (apply rum/react Tooltip props children))

(rum/defc tooltip1 [title tip]
  [:div
   (rum/adapt-class Tooltip
     {:title tip :arrow true}
     [:div title])])

(rum/defc show-whatsapp-qrcode [title tip]
  [:div
   (rum/adapt-class Tooltip
     {:title
      (html [:div.pa2
             [:img.mt2 {:src (u/asset-path "/img/WhatsApp.png")}]
             [:div.flex.justify-center.w-100.flex-column.pa2.mt2
              [:div "Scan the QR code to add a WhatsApp robot, bind your mail, join a group or chat, and you can organize and summarize your chats through AI"]]])
      :arrow true}
     [:div "WhatsApp"])])

(rum/defc show-telegram-qrcode [title tip]
  [:div
   (rum/adapt-class Tooltip
     {:title
      (html [:div.pa2
             [:img.mt2 {:src (u/asset-path "/img/telegram-hulunote.jpg")}]
             [:div.flex.justify-center.w-100.flex-column.pa2.mt2
              [:div "Scan the QR code to add a Telegram robot, bind your mail, join a group or chat, and you can organize and summarize your chats through AI"]]])
      :arrow true}
     [:div "Telegram"])])
