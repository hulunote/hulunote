(ns hulunote.settings.profile
  (:require [clojure.string :as str]
            [rum.core :as rum]
            [hulunote.http :as http]
            [hulunote.icon :as icon]
            [hulunote.settings.shared :as shared]))

(rum/defc page < rum/reactive []
  (let [{:keys [nickname introduction avatar loading]} (rum/react shared/profile-state)]
    [:div {:style {:padding "24px 0"}}
     [:div {:style {:display "flex" :align-items "center" :gap "20px" :margin-bottom "32px"}}
      [:div {:style {:position "relative"}}
       [:div {:style {:width "80px" :height "80px" :border-radius "50%"
                      :background "var(--theme-accent-gradient)"
                      :display "flex" :align-items "center" :justify-content "center"
                      :overflow "hidden" :cursor "pointer"
                      :border "3px solid var(--app-control-border)"}
              :on-click #(.click (.getElementById js/document "avatar-upload-input"))}
        (if avatar
          [:img {:src (if (str/starts-with? (or avatar "") "http")
                        ;; keep remote avatars untouched
                        avatar
                        (str (http/http-uri "") avatar))
                 :style {:width "100%" :height "100%" :object-fit "cover"}}]
          [:span {:style {:color "var(--theme-accent-text)" :font-size "32px" :font-weight "600"}}
           (-> (or nickname "U") first str str/upper-case)])]
       [:div {:style {:position "absolute" :bottom "0" :right "0"
                      :background "var(--theme-accent)" :border-radius "50%"
                      :width "24px" :height "24px"
                      :display "flex" :align-items "center" :justify-content "center"
                      :cursor "pointer" :border "2px solid var(--app-modal-surface)"}
              :on-click #(.click (.getElementById js/document "avatar-upload-input"))}
        (icon/svg-icon
          {:name "edit"
           :style {:width "12px"
                   :height "12px"
                   :color "var(--ui-accent-text)"}})]]
      [:div
       [:div {:style {:font-size "14px" :color "var(--app-text-soft)" :margin-bottom "4px"}} "Profile Photo"]
       [:div {:style {:font-size "12px" :color "var(--app-text-faint)"}} "Click to upload (max 5MB)"]]]

     [:input {:id "avatar-upload-input"
              :type "file"
              :accept "image/png,image/jpeg,image/gif,image/webp"
              :style {:display "none"}
              :on-change (fn [e]
                           (when-let [file (aget (.. e -target -files) 0)]
                             (shared/upload-avatar! file)
                             (set! (.. e -target -value) "")))}]

     [:div {:style {:margin-bottom "20px"}}
      [:label {:style shared/label-style} "Display Name"]
      [:input {:type "text"
               :value nickname
               :placeholder "Enter your display name..."
               :on-change #(swap! shared/profile-state assoc :nickname (.. % -target -value))
               :style shared/input-style}]]

     [:div {:style {:margin-bottom "24px"}}
      [:label {:style shared/label-style} "Introduction"]
      [:textarea {:value introduction
                  :placeholder "Tell us about yourself..."
                  :on-change #(swap! shared/profile-state assoc :introduction (.. % -target -value))
                  :rows 4
                  :style (merge shared/input-style
                                {:resize "vertical" :font-family "inherit"})}]]

     [:div {:style {:display "flex"
                    :justify-content "flex-end"}}
      (shared/action-button
        {:on-click shared/save-profile!
         :disabled? loading}
        (if loading "Saving..." "Save Profile"))]]))
