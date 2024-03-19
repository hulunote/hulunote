(ns hulunote.diaries
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.render :as render]
            [hulunote.db :as db]
            [hulunote.components :as comps]))

(comment
  ;; 打印出来树了，很多个，全的。
  (cljs.pprint/pprint
    (u/get-nav-sub-navs-sorted @db/dsdb "5fea85b1-ba81-426f-b9a1-13a7ef2f189f"))

  ;; 修改一个nav之后，全部nav都会刷新。。。不能局部刷新，那就会卡顿了。。。
  (d/transact db/dsdb
    [[:db/add [:id "5fea85b2-5fe4-4747-a2cb-f3a8e05556b0"]
      :content (str "=====" (rand 100000) "=====")]])
  ;;  更新无关的note也会更新所有的nav，那就不对了。。。
  (d/transact db/dsdb
    [[:db/add [:hulunote-notes/id "5fea85b1-a818-4549-9625-4b25b435274f"]
      :hulunote-notes/title (str "=====" (rand 100000) "=====")]])

  )
(rum/defc diaries-page
  [db]
  (let [daily-list (db/sort-daily-list (db/get-daily-list db))]
    [:div.night-center-boxBg.night-textColor-2
     ;; "diaries page"
     (comps/header-editor)
     [:div.flex.overflow-scroll-new.hulunote-note-wrapper.show-page-right-siderbar-is-close
      [:div.flex.flex-column.mt0.overflow-scroll-new.main-editer-class.w-100.w-100-m.w-100-ns
       [:div.mt4]
       (for [item daily-list]
         [:div
          [:div.f3.b.ma4 (u/daily-title->en (first item))]
          [:div {:style {:padding-left "12px"}}
           (render/render-navs db (last item))]
          [:div {:style {:padding "35px"}}
           [:div {:style {:background "rgba(151, 151, 151, 0.25)"
                          :height "1px" :width "100%"}}]]])
       [:div.mb5]]]]))
