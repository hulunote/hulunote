(ns hulunote.render
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.db :as db]
            [hulunote.http :as http]
            [hulunote.components :as comps]
            [re-frame.core :as re-frame]))

(declare render-navs)

(rum/defc label [text]
  [:div {:class "label"} text])

;; State atom for tracking which nav is being edited
(defonce editing-nav-id (atom nil))
(defonce editing-content (atom ""))

(defn toggle-nav-display!
  "Toggle the is-display property of a nav (expand/collapse children)"
  [db nav-id current-is-display note-id database-name]
  (let [new-is-display (not current-is-display)]
    ;; Update local datascript
    (d/transact! db/dsdb
      [[:db/add [:id nav-id] :is-display new-is-display]])
    ;; Sync to backend
    (re-frame/dispatch-sync
      [:update-nav
       {:database-name database-name
        :note-id note-id
        :id nav-id
        :is-display new-is-display}])))

(defn start-editing!
  "Start editing a nav"
  [nav-id content]
  (reset! editing-nav-id nav-id)
  (reset! editing-content content))

(defn cancel-editing!
  "Cancel editing"
  []
  (reset! editing-nav-id nil)
  (reset! editing-content ""))

(defn save-nav-content!
  "Save the edited content of a nav"
  [nav-id note-id database-name]
  (let [new-content @editing-content]
    ;; Update local datascript
    (d/transact! db/dsdb
      [[:db/add [:id nav-id] :content new-content]])
    ;; Sync to backend
    (re-frame/dispatch-sync
      [:update-nav
       {:database-name database-name
        :note-id note-id
        :id nav-id
        :content new-content}])
    ;; Clear editing state
    (cancel-editing!)))

(defn handle-key-down
  "Handle keyboard events in edit mode"
  [e nav-id note-id database-name]
  (let [key-code (.-keyCode e)]
    (cond
      ;; Enter key - save
      (and (= key-code 13) (not (.-shiftKey e)))
      (do
        (.preventDefault e)
        (save-nav-content! nav-id note-id database-name))
      ;; Escape key - cancel
      (= key-code 27)
      (cancel-editing!))))

(defn has-children?
  "Check if a nav has children"
  [db nav-id]
  (let [nav (u/get-nav-sub-navs-sorted db nav-id)]
    (seq (:parid nav))))

(rum/defc nav-bullet < rum/reactive
  "Bullet point component with expand/collapse functionality"
  [db nav-id is-display note-id database-name]
  (let [has-child (has-children? db nav-id)]
    [:span {:class (str "controls hulu-text-font " 
                        (when has-child "has-children"))
            :style {:align-items "center"
                    :vertical-align "middle"
                    :width "16px"
                    :cursor "pointer"
                    :padding-left "5px"
                    :justify-content "center"
                    :display "flex"
                    :margin-right "3px"
                    :border-radius "50%"
                    :height "16px"}
            :on-click (fn [e]
                        (u/stop-click-bubble e)
                        (when has-child
                          (toggle-nav-display! db nav-id is-display note-id database-name)))}
     (if has-child
       ;; Show triangle for nodes with children
       [:span {:class (str "expand-icon " (if is-display "expanded" "collapsed"))
               :style {:font-size "10px"
                       :color "#666"
                       :transition "transform 0.2s ease"
                       :transform (if is-display "rotate(90deg)" "rotate(0deg)")
                       :display "inline-block"}}
        "▶"]
       ;; Show dot for leaf nodes
       [:span {:class "controls bg-black-50 customize-dot night-circular"
               :style {:height 5
                       :width 5
                       :border-radius "50%"
                       :background-color "#D8D8D8"
                       :cursor "pointer"
                       :display "inline-nav"
                       :vertical-align "middle"}}])]))

(rum/defc nav-content-editor < rum/reactive
  "Editable content component"
  [nav-id content note-id database-name]
  (let [is-editing (= nav-id (rum/react editing-nav-id))]
    (if is-editing
      [:input.nav-editor-input
       {:type "text"
        :auto-focus true
        :value (rum/react editing-content)
        :style {:border "1px solid #4a90d9"
                :border-radius "3px"
                :padding "2px 6px"
                :outline "none"
                :width "100%"
                :min-width "200px"
                :font-size "inherit"
                :font-family "inherit"
                :background "#fff"}
        :on-change #(reset! editing-content (.. % -target -value))
        :on-key-down #(handle-key-down % nav-id note-id database-name)
        :on-blur #(save-nav-content! nav-id note-id database-name)}]
      [:span.nav-content
       {:on-double-click #(start-editing! nav-id content)
        :style {:cursor "text"}}
       (comps/parse-and-render content {})])))

(rum/defc nav-input < rum/reactive
  [db id note-id database-name]
  (let [{:keys [last-account-id parid is-display
                hulunote-note content parser-content
                properties same-deep-order updated-at
                created-at last-user-cursor]}
        (u/get-nav-by-id db id)]
    [:div.nav-item
     [:div.head-dot.flex
      {:style {:padding-left "13px"
               :padding-top "5px"
               :padding-bottom "5px"}}
      (nav-bullet db id is-display note-id database-name)
      (nav-content-editor id content note-id database-name)]
     (when is-display
       [:div.content-box {:style {:margin-left "24px"
                                  :padding-left "5px"
                                  :position "relative"}}
        [:div.content-box.outline-line.night-outline-line]
        (render-navs db id note-id database-name)])]))

(rum/defc render-navs [db id & [note-id database-name]]
  (let [nav (u/get-nav-sub-navs-sorted db id)]
    (for [ch (:parid nav)]
      (let [{:keys [id] dbid :db/id} ch
            ;; Get note-id and database-name from nav if not provided
            nav-info (u/get-nav-by-id db id)
            actual-note-id (or note-id (:hulunote-note nav-info))
            actual-db-name (or database-name 
                               (when-let [db-id (:database-id nav-info)]
                                 ;; Try to get database name from database-id
                                 db-id))]
        (nav-input db id actual-note-id actual-db-name)))))
