(ns hulunote.database
  (:require [datascript.core :as d]
            [hulunote.db :as db]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.storage :as storage]
            [hulunote.components :as comps]
            [re-frame.core :as re-frame]))

(rum/defcs database-page
  < {:will-mount
     (fn [state]
       (re-frame/dispatch-sync
         [:get-database-list
          {:op-fn
           (fn [{:keys [database-list settings]}]
             ;; (d/transact! db/dsdb {:db/id (d/tempid :db.part/user)
             ;;                       :settings settings})
             (doseq [item database-list]
               (do
                 (d/transact! db/dsdb [item])))
             ;;
             )}])
       )
     }
  [state db]
  (let [database-list (db/get-database db)]
    [:div.flex.flex-column
     (comps/header)
     [:div.flex.items-center.justify-center.flex-column
      {:style {:height "600px"}}
      [:div.main-title.text-center.main-container
       "WhatsApp & Telegram chat sorting and summary with AI"]
      [:p.submain-title "Define your own AI Chat for community management"]]
     ;; =====
     [:div.mt4.td-overlay
      [:div.advantages
       [:div.advantage-card.flex
        [:div.card-body.flex.justify-center.w-100.mt4
         [:div.flex-column
          [:div.flex.justify-center
           [:img.img-class {:style {:width "146px"}
                            :src "/img/mutil_chat.svg"}]]
          [:div.card-title "Mutil Chat Support"]
          [:div.card-desc "Support WhatsApp, Telegram and other chat tools"]]]]
       [:div.advantage-card.flex
        [:div.card-body.flex.justify-center.w-100.mt4
         [:div.flex-column
          [:div [:img.img-class {:src "/img/mutil_database.svg"}]]
          [:div.card-title "Chat Database"]
          [:div.card-desc "Support exporting chat history and chat editing and creation"]]]]
       [:div.advantage-card.flex
        [:div.card-body.flex.justify-center.w-100.mt4
         [:div.flex-column
          [:div.flex.justify-center
           [:img.img-class {:style {:width "146px"}
                            :src "/img/ai_ass.svg"}]]
          [:div.card-title "AI Assisted Chat"]
          [:div.card-desc "Chat sorting and summary with AI, help you read messages and communicate more efficiently"]]]]
       [:div.advantage-card.flex
        [:div.card-body.flex.justify-center.w-100.mt4
         [:div.flex-column
          [:div.flex.justify-center
           [:img.img-class {:style {:width "146px"}
                            :src "/img/ai_define.svg"}]]
          [:div.card-title "Define your AI Chat"]
          [:div.card-desc "Realize custom chatbot and instruction recognition based on OpenAI"]]]]
       ]]
     [:div
      {:style {:min-height 600
               :background "#f0f0f0"}}
      [:div.sub-title2.pt7.mb3 "Chat Database List"]
      [:div.advantages.pt7
       (if (empty? database-list)
         [:div
          [:div.flex.pointer.db-card-big
           [:div.flex.flex-column.items-center.justify-center.w-100
            [:div "Scan the QR code to add a WhatsApp or Telegram robot, bind your mail"]
            [:div.flex.flex-row
             [:img.mt3 {:width "180" :src "/img/WhatsApp.png"}]
             [:img.mt3.ml4 {:width "180" :src "/img/telegram-hulunote.jpg"}]]]]]
         (for [item database-list]
           [:div.flex.pointer.db-card
            {:on-click #(js/open ;; router/switch-router!
                          (str "#/app/"
                            (:hulunote-databases/name (first item))
                            "/diaries"))}
            [:div.flex.items-center.justify-center.w-100
             (:hulunote-databases/name (first item))]]))]]
     ;; ====
     (comps/footer)]))
