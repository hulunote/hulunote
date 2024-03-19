(ns hulunote.components
  (:require [datascript.core :as d]
            [hulunote.db :as db]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.parser :as parser]
            [instaparse.core :as insta]
            [hulunote.storage :as storage]
            ["@material-ui/core" :refer [Tooltip]])
  (:require-macros
   [daiquiri.core :refer [html]])
  (:import (goog.date DateTime Interval)))

;; doc/react-interop.md ;;=> https://github.com/tonsky/rum/blob/gh-pages/doc/react-interop.md => 代码语义搜索element => daiquiri.core
;;125:  (daiquiri.core/html [:div [:p "Hello, world"]]))
;; [daiquiri.core :as daiquiri]

;; TODO: 加一个弹框，漂浮的定时弹框。不要alert了

(rum/defc header-editor []
  [:div [:div.pl3.pt2
         [:img.pointer
          {:on-click #(router/switch-router! "/")
           :width "30px"
           :style {:border-radius "50%"}
           :src "/img/hulunote.webp"}]]])

(declare show-whatsapp-qrcode)
(declare show-telegram-qrcode)

(rum/defc header []
  [:div.flex.flex-row.td-navbar
   [:div.flex.inner-wrapper
    [:div.flex.items-center.flex.flex-auto
     ;; 不协调放在这里的logo，不如不放
     #_[:div.pl3.pr2 [:img.br3 {:width "24px"
                                :src "/img/hulunote.webp"}]]
     [:div.main-hulu-name.pl3.pointer
      {:on-click #(js/open "https://hulunote.io")
       :style {:font-size "24px"
               :color "while"
               :font-weight "700"}}
      "HULUNOTE"]
     ;; [:div.b.pa3.head-bar-pl.pointer {:on-click #(js/open "https://apps.apple.com/cn/app/hulunote/id1595763964")} "App"]
     ]
    [:div.flex.items-center
     [:div.head-bar-pl.pointer.nav-link {:on-click #(js/open "https://www.whatsapp.com/")}
      (show-whatsapp-qrcode)]
     [:div.head-bar-pl.pointer.nav-link {:on-click #(js/open "https://telegram.org/")}
      (show-telegram-qrcode)]

     [:div.pr3.pl3
      (if (u/is-expired?)
        [:button.btn-new.btn-login {:on-click #(router/switch-router! "/login")} "Login"]
        [:div.flex.flex-row.mb-no-show
         ;; TODO: 点击下拉可退出
         [:div
          (first (clojure.string/split  (:accounts/mail (:hulunote @storage/jwt-auth)) "@"))]])]]]])

(rum/defc footer []
  [:div
   {:style {:height "550px"
            :background "rgb(46 49 54)"}}
   [:div.flex.flex-row.justify-center.mt5
    [:div.flex.flex-column.pa3 ;; .ml4
     [:div.b {:style {:font-size "25px"}} "Help"]
     [:div.b.pt3.pointer #(js/open "https://forum.hulunote.io") "Forum"]
     [:div.b.pt3 "WhatsApp group"]
     [:div.b.pt3 "Telegram group"]]
    [:div.flex.flex-column.pa3.ml4
     [:div.b {:style {:font-size "25px"}} "Resources"]
     [:div.b.pt3 "Documents"]
     [:div.b.pt3 "Blog"]]
    [:div.flex.flex-column.pa3.ml4
     [:div.b {:style {:font-size "25px"}} "About"]
     [:div.b.pt3.pointer {:on-click #(js/open "https://twitter.com/hulunote")} "Twitter"]
     [:div.b.pt3.pointer {:on-click #(js/open "https://youtube.com/@hulunote")} "YouTube"]
     [:div.b.pt3 "About us"]]
    ]
   [:div.flex.justify-center.pa3 "© 2023 Hulunote"]])


(defn render-recursion-page-link
  [title]
  [:span.hulunote-note-link
   [:span.link-style.blue "[["]
   [:span.link-titlt-style title]
   [:span.link-style.blue "]]"]])

(defn render-recursion-page-tag
  [title]
  [:span.hulunote-note-link
   [:span.link-style.blue "#"]
   [:span.link-titlt-style title]])

(declare parse-and-render)

(comment
  (parser/parse-to-ast "aaa~")
  ;; => [:block [:word "aaa"] "~"]

  (transform [:block [:word "aaa"] "~"]   {})
  ;; => (:span.normal-block-class.night-textColor-2
  ;;     {:class "classId-435169028450"}
  ;;     ("aaa")
  ;;     "~")

  )
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

     ;; 基本[[]]和#就行了，没有块引用,表格等其它用了很少的功能。还有基本的Markdown的语法罢了。=> 防止其它无效的精力扩展！专门做好所有机器人的聊天收集整理！！！这个就是这个产品的核心！AI聊天整理！

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
     ;; 字体
     :bold           (fn [text] ;;加粗
                       [:strong.markdown-syntax-bold
                        (parse-and-render
                          (u/rest-join text) {})])
     :italics        (fn [text] ;;斜体
                       [:span.markdown-syntax-italics
                        (parse-and-render
                          (clojure.string/join text) {})])
     :strikethrough  (fn [text] ;;被划掉
                       [:span.markdown-syntax-strikethrough
                        (parse-and-render
                          (clojure.string/join text) {})])
     :highlight      (fn [text] ;;高亮
                       [:span.hight-light-text-class.pl1.pr1.night-heightLight.markdown-syntax-highlight
                        (parse-and-render
                          (u/rest-join text) {}) ])
     :code           (fn [text]
                       [:code.pl1.pr1.night-codeBg.markdown-syntax-code
                        [:span (u/rest-join text)]])
     :enter          (fn [text]
                       [:br])
     :line          (fn [text]
                      [:div.mt2 {:style {:background "rgb(216, 216, 216)" :height "1px"}}])
     ;; 克制减少不对的方向，不加语音功能！=> 聊天整理才是核心，其它全部收敛
     ;; :voice        (fn [& url]
     ;;                 (let [mp3-url (str (last (last url)))]
     ;;                   (if (re-find #"^http(.*)mp3$" mp3-url)
     ;;                     [:audio
     ;;                      {:controls "controls"}
     ;;                      [:source {:src mp3-url
     ;;                                :type "audio/mp3"}]]
     ;;                     [:div.red "Unsupport"])))
     }
    tree))

(rum/defc parse-and-render
  [string render-params]
  (try
    (let [;; string "![dasdas](/img/hulunote.webp)"
          result (parser/parse-to-ast string)]
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

;; 加了state之后就db无法响应式了。。。
(rum/defc toast ;; < {:did-mount
  ;;    (fn [state]
  ;;      (let [ ;; comp      (:rum/react-component state)
  ;;            ;; callback #(rum/request-render comp)
  ;;            ;; interval  (js/setInterval callback 1000)
  ;;            ]
  ;;        ;; (assoc state ::interval interval)
  ;;        (js/setTimeout #(prn 111) 1000))
  ;;      state)}
  [db]
  (let [{:keys [date text]} (db/get-message db)]
    (when (and date (< (.getTime (DateTime.)) date))
      [:div.absolute.flex.flex-row.items-center.justify-center
       {:style {:width "100%"
                :z-index 10001 ;;最高层的图层
                :bottom "10em"}}
       [:div.ph3.pv2.br3.animated.fadeIn
        {:style {:background "#303030"
                 :opacity "0.8"
                 :color "white"}}
        text]])))

;; GPT4问的问题: rum 如何调用 material-ui的Tooltip组件 ：`["@material-ui/core" :refer [Tooltip]]`
(comment
  [comps/wrapped-tooltip {:title "Tooltip text"}
   [:button "Hover me"]])
(defn wrapped-tooltip [props children]
  (apply rum/react Tooltip props children))

;; 谷歌搜索: rum call react component => https://cljdoc.org/d/rum/rum/0.12.10/doc/react-interop
;; (comps/tooltip1 "标题" "提示")
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
             [:img.mt2 {:src "/img/WhatsApp.png"}]
             [:div.flex.justify-center.w-100.flex-column.pa2.mt2
              [:div "Scan the QR code to add a WhatsApp robot, bind your mail, join a group or chat, and you can organize and summarize your chats through AI"]]])
      :arrow true}
     [:div "WhatsApp"])])

(rum/defc show-telegram-qrcode [title tip]
  [:div
   (rum/adapt-class Tooltip
     {:title
      (html [:div.pa2
             [:img.mt2 {:src "/img/telegram-hulunote.jpg"}]
             [:div.flex.justify-center.w-100.flex-column.pa2.mt2
              [:div "Scan the QR code to add a Telegram robot, bind your mail, join a group or chat, and you can organize and summarize your chats through AI"]]])
      :arrow true}
     [:div "Telegram"])])
