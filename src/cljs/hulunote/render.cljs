(ns hulunote.render
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.components :as comps]))

(declare render-navs)

(rum/defc label [text]
  [:div {:class "label"} text])

(comment
  (comps/parse-and-render "aaa 32321" {})
  ;; => #js {"$$typeof" #object[Symbol(react.element)], :type #object[class$], :key nil, :ref nil, :props #js {":rum/args" ("aaa 32321" {})}, :_owner nil, :_store #js {}}

  (label "111321x")
  ;; => #js {"$$typeof" #object[Symbol(react.element)], :type #object[class$], :key nil, :ref nil, :props #js {":rum/args" ("111321x")}, :_owner nil, :_store #js {}}
  )
(rum/defc nav-input [db id]
  (let [{:keys [last-account-id parid is-display
                hulunote-note content parser-content
                properties same-deep-order updated-at
                created-at last-user-cursor]}
        (u/get-nav-by-id db id)]
    [:div
     [:div.head-dot.flex
      {:style {:padding-left "13px"
               :padding-top "5px"
               :padding-bottom "5px"}}
      [:span {:class "controls hulu-text-font",
              :style
              {:align-items "center"
               :vertical-align "middle"
               :width "16px"
               :cursor "pointer"
               :padding-left "5px"
               :justify-content "center"
               :display "flex"
               :margin-right "3px"
               :border-radius "50%"
               :height "16px"}}
       [:span {:class "controls bg-black-50 customize-dot night-circular",
               :style {:height 5
                       :width   5
                       :border-radius "50%"
                       :background-color "#D8D8D8"
                       :cursor         "pointer"
                       :display "inline-nav"
                       :vertical-align "middle"}}]]
      ;; [:span content]
      (comps/parse-and-render content {})]
     (when is-display
       [:div.content-box {:style {:margin-left  "24px"
                                  :padding-left "5px"
                                  :position "relative"}}
        [:div.content-box.outline-line.night-outline-line ]
        (render-navs db id)])]))

(rum/defc render-navs [db id]
  (let [nav (u/get-nav-sub-navs-sorted db id)]
    (for [ch (:parid nav)]
      (let [{:keys [id] dbid :db/id} ch]
        (nav-input db id)))))
