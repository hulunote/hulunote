(ns hulunote.diaries
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.render :as render]
            [hulunote.db :as db]
            [hulunote.components :as comps]
            [hulunote.sidebar :as sidebar]
            [hulunote.router :as router]))

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

(defn get-current-database-name
  "Get current database name from route params"
  [db]
  (let [{:keys [params]} (db/get-route db)]
    (:database params)))

(rum/defc diaries-page < rum/reactive
  [db]
  (let [daily-list (db/sort-daily-list (db/get-daily-list db))
        database-name (get-current-database-name db)
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)]
    [:div.night-center-boxBg.night-textColor-2
     (comps/header-editor)
     
     ;; Left sidebar
     (sidebar/left-sidebar db database-name)
     
     ;; Main content area with sidebar margin
     [:div.flex.overflow-scroll-new.hulunote-note-wrapper
      {:class (str "main-content-with-sidebar "
                   (when sidebar-collapsed? "sidebar-collapsed"))
       :style {:padding-right "calc((100% - 800px) / 2)"
               :height "100vh"}}
      [:div.flex.flex-column.mt0.overflow-scroll-new.main-editer-class.w-100.w-100-m.w-100-ns
       [:div.mt4]
       (if (empty? daily-list)
         ;; Empty state - show message and create first note button
         [:div.flex.flex-column.items-center.justify-center
          {:style {:height "50vh"}}
          [:div {:style {:font-size "24px" :margin-bottom "20px"}} 
           "No notes yet"]
          [:div {:style {:color "rgba(255,255,255,0.6)" :margin-bottom "30px"}}
           "Create your first note to get started"]
          [:button.new-note-btn
           {:on-click #(sidebar/create-new-note! database-name)}
           [:span.new-note-btn-icon "+"]
           "Create First Note"]]
         ;; Show existing notes
         (for [item daily-list]
           (let [[note-title note-id root-nav-id] item]
             [:div {:key note-id}
              [:div.f3.b.ma4 (u/daily-title->en note-title)]
              [:div {:style {:padding-left "12px"}}
               (render/render-navs db root-nav-id note-id database-name)]
              [:div {:style {:padding "35px"}}
               [:div {:style {:background "rgba(151, 151, 151, 0.25)"
                              :height "1px" :width "100%"}}]]])))
       [:div.mb5]]]]))
