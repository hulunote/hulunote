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

;; State for context menu
(defonce context-menu-state (atom {:visible false
                                    :x 0
                                    :y 0
                                    :nav-id nil
                                    :content ""
                                    :note-id nil
                                    :database-name nil}))

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
  (reset! editing-content (or content "")))

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

;; ==================== Context Menu Functions ====================

(defn show-context-menu!
  "Show context menu at specified position"
  [e nav-id content note-id database-name]
  (.preventDefault e)
  (.stopPropagation e)
  (reset! context-menu-state
    {:visible true
     :x (.-clientX e)
     :y (.-clientY e)
     :nav-id nav-id
     :content content
     :note-id note-id
     :database-name database-name}))

(defn hide-context-menu!
  "Hide context menu"
  []
  (swap! context-menu-state assoc :visible false))

(defn copy-nav-content!
  "Copy nav content to clipboard"
  [content]
  (let [textarea (.createElement js/document "textarea")]
    (set! (.-value textarea) (or content ""))
    (set! (.-style textarea) "position: fixed; left: -9999px;")
    (.appendChild (.-body js/document) textarea)
    (.select textarea)
    (.execCommand js/document "copy")
    (.removeChild (.-body js/document) textarea)
    (u/alert "Content copied to clipboard!")))

(defn delete-nav!
  "Delete a nav node"
  [nav-id note-id database-name]
  (let [nav (u/get-nav-by-id @db/dsdb nav-id)
        parid (:origin-parid nav)]
    ;; Remove from parent's children in local datascript
    (when parid
      (d/transact! db/dsdb
        [[:db/retract [:id parid] :parid [:id nav-id]]]))
    ;; Delete the nav entity
    (d/transact! db/dsdb
      [[:db/retractEntity [:id nav-id]]])
    ;; Sync deletion to backend
    (re-frame/dispatch-sync
      [:delete-nav
       {:database-name database-name
        :note-id note-id
        :id nav-id}])))

(rum/defc context-menu < rum/reactive
  "Context menu component for nav bullet"
  []
  (let [{:keys [visible x y nav-id content note-id database-name]} (rum/react context-menu-state)]
    (when visible
      [:div.nav-context-menu
       {:style {:position "fixed"
                :left (str x "px")
                :top (str y "px")
                :background "#2a2f3a"
                :border "1px solid #444"
                :border-radius "6px"
                :box-shadow "0 4px 12px rgba(0,0,0,0.3)"
                :z-index 10000
                :min-width "150px"
                :padding "4px 0"}
        :on-mouse-leave hide-context-menu!}
       ;; Show current position info
       [:div.context-menu-header
        {:style {:padding "8px 12px"
                 :color "#888"
                 :font-size "11px"
                 :border-bottom "1px solid #444"}}
        (str "Node ID: " (subs (or nav-id "") 0 8) "...")]
       ;; Copy content option
       [:div.context-menu-item
        {:style {:padding "8px 12px"
                 :cursor "pointer"
                 :color "#fff"
                 :font-size "13px"}
         :on-mouse-over #(set! (-> % .-target .-style .-background) "#3a4555")
         :on-mouse-out #(set! (-> % .-target .-style .-background) "transparent")
         :on-click (fn [e]
                     (.stopPropagation e)
                     (copy-nav-content! content)
                     (hide-context-menu!))}
        "📋 Copy Content"]
       ;; Delete node option
       [:div.context-menu-item
        {:style {:padding "8px 12px"
                 :cursor "pointer"
                 :color "#ff6b6b"
                 :font-size "13px"}
         :on-mouse-over #(set! (-> % .-target .-style .-background) "#3a4555")
         :on-mouse-out #(set! (-> % .-target .-style .-background) "transparent")
         :on-click (fn [e]
                     (.stopPropagation e)
                     (when (js/confirm "Are you sure you want to delete this node?")
                       (delete-nav! nav-id note-id database-name))
                     (hide-context-menu!))}
        "🗑️ Delete Node"]])))

;; ==================== Sibling Navigation Helpers ====================

(defn get-siblings-sorted
  "Get all siblings of a nav (nodes with same parent), sorted by order"
  [nav-id]
  (let [nav (u/get-nav-by-id @db/dsdb nav-id)
        parid (:origin-parid nav)
        parent-nav (u/get-nav-sub-navs-sorted @db/dsdb parid)]
    (vec (:parid parent-nav))))

(defn find-prev-sibling
  "Find the previous sibling of a nav"
  [nav-id]
  (let [siblings (get-siblings-sorted nav-id)
        current-idx (->> siblings
                         (map-indexed (fn [i s] [i (:id s)]))
                         (filter #(= (second %) nav-id))
                         first
                         first)]
    (when (and current-idx (> current-idx 0))
      (nth siblings (dec current-idx)))))

(defn find-next-sibling
  "Find the next sibling of a nav"
  [nav-id]
  (let [siblings (get-siblings-sorted nav-id)
        current-idx (->> siblings
                         (map-indexed (fn [i s] [i (:id s)]))
                         (filter #(= (second %) nav-id))
                         first
                         first)]
    (when (and current-idx (< current-idx (dec (count siblings))))
      (nth siblings (inc current-idx)))))

(defn calculate-order-between
  "Calculate order value between two orders using midpoint.
   If prev-order is nil, use (next-order / 2).
   If next-order is nil, use (prev-order + 100).
   If both are nil, use 0."
  [prev-order next-order]
  (cond
    (and prev-order next-order)
    (/ (+ prev-order next-order) 2.0)
    
    prev-order
    (+ prev-order 100)
    
    next-order
    (/ next-order 2.0)
    
    :else
    0))

(defn get-last-child-order
  "Get the order of the last child of a nav, or nil if no children"
  [nav-id]
  (let [nav (u/get-nav-sub-navs-sorted @db/dsdb nav-id)
        children (:parid nav)]
    (when (seq children)
      (:same-deep-order (last children)))))

;; ==================== Nav Operations ====================

(defn create-sibling-nav!
  "Create a new sibling nav after the current one.
   Uses midpoint order calculation."
  [current-nav-id note-id database-name]
  (let [current-nav (u/get-nav-by-id @db/dsdb current-nav-id)
        parid (or (:origin-parid current-nav) db/root-id)
        current-order (or (:same-deep-order current-nav) 0)
        next-sibling (find-next-sibling current-nav-id)
        next-order (when next-sibling (:same-deep-order next-sibling))
        ;; Calculate new order as midpoint between current and next
        new-order (calculate-order-between current-order next-order)
        new-nav-id (str (d/squuid))]
    ;; Create in local datascript first
    (d/transact! db/dsdb
      [{:id new-nav-id
        :content ""
        :hulunote-note note-id
        :same-deep-order new-order
        :is-display true
        :origin-parid parid}
       [:db/add [:id parid] :parid [:id new-nav-id]]])
    ;; Sync to backend
    (re-frame/dispatch-sync
      [:create-nav
       {:database-name database-name
        :note-id note-id
        :id new-nav-id
        :parid parid
        :content ""
        :order new-order}])
    ;; Start editing the new nav
    (start-editing! new-nav-id "")))

(defn indent-nav!
  "Indent a nav (make it a child of its previous sibling).
   Tab key operation - increases depth."
  [nav-id note-id database-name]
  (let [nav (u/get-nav-by-id @db/dsdb nav-id)
        current-parid (:origin-parid nav)
        prev-sibling (find-prev-sibling nav-id)]
    ;; Can only indent if there's a previous sibling
    (when prev-sibling
      (let [new-parid (:id prev-sibling)
            ;; Get the last child order of the new parent, put this nav after it
            last-child-order (get-last-child-order new-parid)
            new-order (calculate-order-between last-child-order nil)]
        ;; Update local datascript
        ;; 1. Remove from old parent's children
        (d/transact! db/dsdb
          [[:db/retract [:id current-parid] :parid [:id nav-id]]])
        ;; 2. Update nav's parent and order
        (d/transact! db/dsdb
          [[:db/add [:id nav-id] :origin-parid new-parid]
           [:db/add [:id nav-id] :same-deep-order new-order]
           [:db/add [:id new-parid] :parid [:id nav-id]]])
        ;; 3. Make sure new parent is expanded
        (d/transact! db/dsdb
          [[:db/add [:id new-parid] :is-display true]])
        ;; Sync to backend
        (re-frame/dispatch-sync
          [:update-nav
           {:database-name database-name
            :note-id note-id
            :id nav-id
            :parid new-parid
            :order new-order}])))))

(defn outdent-nav!
  "Outdent a nav (make it a sibling of its parent).
   Shift+Tab key operation - decreases depth.
   Can move nav up to be a sibling of its parent."
  [nav-id note-id database-name]
  (let [nav (u/get-nav-by-id @db/dsdb nav-id)
        current-parid (:origin-parid nav)
        parent-nav (u/get-nav-by-id @db/dsdb current-parid)]
    ;; Can only outdent if:
    ;; 1. Parent exists and is not the root-nav of the note
    ;; 2. Parent has a parent (grandparent exists)
    (when (and parent-nav 
               (:origin-parid parent-nav))
      (let [grandparid (:origin-parid parent-nav)
            parent-order (or (:same-deep-order parent-nav) 0)
            ;; Find the next sibling of parent to calculate order
            parent-next-sibling (find-next-sibling current-parid)
            next-order (when parent-next-sibling 
                         (:same-deep-order parent-next-sibling))
            ;; Put this nav right after its parent
            new-order (calculate-order-between parent-order next-order)]
        ;; Update local datascript
        ;; 1. Remove from old parent's children
        (d/transact! db/dsdb
          [[:db/retract [:id current-parid] :parid [:id nav-id]]])
        ;; 2. Update nav's parent and order
        (d/transact! db/dsdb
          [[:db/add [:id nav-id] :origin-parid grandparid]
           [:db/add [:id nav-id] :same-deep-order new-order]
           [:db/add [:id grandparid] :parid [:id nav-id]]])
        ;; Sync to backend
        (re-frame/dispatch-sync
          [:update-nav
           {:database-name database-name
            :note-id note-id
            :id nav-id
            :parid grandparid
            :order new-order}])))))

;; ==================== Keyboard Event Handler ====================

(defn handle-key-down
  "Handle keyboard events in edit mode.
   - Tab: indent (increase depth, become child of prev sibling)
   - Shift+Tab: outdent (decrease depth, become sibling of parent)
   - Enter: save and create new sibling
   - Escape: cancel editing
   - Backspace/Delete: delete node when content is empty"
  [e nav-id note-id database-name]
  (let [key-code (.-keyCode e)
        shift? (.-shiftKey e)
        saved-content @editing-content
        current-content @editing-content]
    (cond
      ;; Backspace key (8) or Delete key (46) - delete node when content is empty
      (and (or (= key-code 8) (= key-code 46))
           (empty? current-content))
      (do
        (.preventDefault e)
        ;; Find previous sibling or parent to focus after deletion
        (let [prev-sibling (find-prev-sibling nav-id)
              nav (u/get-nav-by-id @db/dsdb nav-id)
              parent-nav (when-let [parid (:origin-parid nav)]
                          (u/get-nav-by-id @db/dsdb parid))
              next-focus-id (or (:id prev-sibling)
                               (when (and parent-nav 
                                         (not= (:id parent-nav) db/root-id)
                                         (not= (:content parent-nav) "ROOT"))
                                 (:id parent-nav)))]
          ;; Delete the current nav
          (delete-nav! nav-id note-id database-name)
          ;; Focus on the previous sibling or parent
          (when next-focus-id
            (let [next-nav (u/get-nav-by-id @db/dsdb next-focus-id)]
              (js/setTimeout
                #(start-editing! next-focus-id (or (:content next-nav) ""))
                50)))))
      
      ;; Tab key - indent (make child of previous sibling)
      (and (= key-code 9) (not shift?))
      (do
        (.preventDefault e)
        (save-nav-content! nav-id note-id database-name)
        (js/setTimeout
          #(do (indent-nav! nav-id note-id database-name)
               (start-editing! nav-id saved-content))
          50))
      
      ;; Shift+Tab - outdent (make sibling of parent)
      (and (= key-code 9) shift?)
      (do
        (.preventDefault e)
        (save-nav-content! nav-id note-id database-name)
        (js/setTimeout
          #(do (outdent-nav! nav-id note-id database-name)
               (start-editing! nav-id saved-content))
          50))
      
      ;; Enter key - save and create new sibling
      (and (= key-code 13) (not shift?))
      (do
        (.preventDefault e)
        (save-nav-content! nav-id note-id database-name)
        (js/setTimeout
          #(create-sibling-nav! nav-id note-id database-name)
          50))
      
      ;; Escape key - cancel
      (= key-code 27)
      (cancel-editing!))))

;; ==================== UI Components ====================

(defn has-children?
  "Check if a nav has children"
  [db nav-id]
  (let [nav (u/get-nav-sub-navs-sorted db nav-id)]
    (seq (:parid nav))))

(rum/defc nav-bullet < rum/reactive
  "Bullet point component with expand/collapse functionality and context menu"
  [db nav-id is-display note-id database-name content]
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
                          (toggle-nav-display! db nav-id is-display note-id database-name)))
            :on-context-menu (fn [e]
                              (show-context-menu! e nav-id content note-id database-name))}
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
                ;; Fixed: Use dark background with light text for dark theme
                :background "#2a2f3a"
                :color "#fdfeffc4"}
        :on-change #(reset! editing-content (.. % -target -value))
        :on-key-down #(handle-key-down % nav-id note-id database-name)
        :on-blur #(save-nav-content! nav-id note-id database-name)}]
      [:span.nav-content
       {:style {:cursor "text"
                :min-height "20px"
                :display "inline-block"
                :min-width "100px"}}
       (if (empty? content)
         [:span {:style {:color "#666"}} "Click to edit..."]
         (comps/parse-and-render content {}))])))

(rum/defc nav-input < rum/reactive
  [db id note-id database-name]
  (let [{:keys [last-account-id parid is-display
                hulunote-note content parser-content
                properties same-deep-order updated-at
                created-at last-user-cursor]}
        (u/get-nav-by-id db id)
        is-editing (= id (rum/react editing-nav-id))]
    [:div.nav-item
     ;; Entire row is clickable to enter edit mode
     [:div.head-dot.flex
      {:style {:padding-left "13px"
               :padding-top "5px"
               :padding-bottom "5px"
               :cursor "text"}
       :on-click (fn [e]
                   ;; Only start editing if not clicking on bullet
                   (when-not (.. e -target -classList (contains "controls"))
                     (start-editing! id content)))}
      (nav-bullet db id is-display note-id database-name content)
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

;; Global context menu - render at app level
(rum/defc global-context-menu < rum/reactive
  []
  (context-menu))
