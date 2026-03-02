(ns hulunote.render
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.util :as u]
            [hulunote.db :as db]
            [hulunote.http :as http]
            [hulunote.components :as comps]
            [re-frame.core :as re-frame]))

(declare render-navs)
(declare get-visible-nav-list)

(rum/defc label [text]
  [:div {:class "label"} text])

;; State atom for tracking which nav is being edited
(defonce editing-nav-id (atom nil))
(defonce editing-content (atom ""))
;; Pending cursor position for the next editor mount.
(defonce pending-selection (atom nil))

;; State for cursor position (used for up/down navigation to maintain column position)
(defonce target-cursor-column (atom nil))

;; State for drag-and-drop reorder
(defonce dragging-nav-id (atom nil))
(defonce drag-over-nav-id (atom nil))
(defonce drag-over-mode (atom nil))

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
  "Start editing a nav with optional cursor position"
  ([nav-id content]
   (start-editing! nav-id content nil))
  ([nav-id content cursor-pos]
   (reset! editing-nav-id nav-id)
   (let [text (or content "")
         len (count text)
         default-pos len
         pos (if (some? cursor-pos)
               (max 0 (min cursor-pos len))
               default-pos)]
     (reset! editing-content text)
     (reset! pending-selection {:nav-id nav-id
                                :start pos
                                :end pos
                                :focus? true}))))

(defn start-editing-at-end!
  "Start editing a nav with cursor at the end"
  [nav-id content]
  (let [text (or content "")]
    (start-editing! nav-id text (count text))))

(defn start-editing-at-start!
  "Start editing a nav with cursor at the beginning"
  [nav-id content]
  (start-editing! nav-id content 0))

(defn dom-caret-offset-in-root
  "Get text offset within root element from viewport click coordinates."
  [root x y]
  (let [doc js/document
        mk-offset (fn [container offset]
                    (when (and container (some? offset))
                      (let [r (.createRange doc)]
                        (.selectNodeContents r root)
                        (.setEnd r container offset)
                        (count (.toString r)))))]
    (cond
      (.-caretRangeFromPoint doc)
      (when-let [clicked-range (.caretRangeFromPoint doc x y)]
        (mk-offset (.-startContainer clicked-range)
                   (.-startOffset clicked-range)))

      (.-caretPositionFromPoint doc)
      (when-let [pos (.caretPositionFromPoint doc x y)]
        (mk-offset (.-offsetNode pos)
                   (.-offset pos)))

      :else nil)))

(defn estimate-cursor-pos-from-click
  "Estimate input cursor position from click X coordinate within rendered nav content."
  [e content]
  (let [text (or content "")
        text-len (count text)
        target (.-target e)
        content-el (or (when-let [closest-fn (.-closest target)]
                         (.closest target ".nav-content"))
                       (when (and (.-classList target)
                                  (.contains (.-classList target) "nav-content"))
                         target))]
    (when (and content-el (> text-len 0))
      (let [rect (.getBoundingClientRect content-el)
            width (.-width rect)
            exact-offset (dom-caret-offset-in-root content-el (.-clientX e) (.-clientY e))]
        (if (some? exact-offset)
          (max 0 (min exact-offset text-len))
          (when (> width 0)
            (let [x (- (.-clientX e) (.-left rect))
                  clamped-x (max 0 (min x width))
                  ratio (/ clamped-x width)]
              (int (js/Math.round (* ratio text-len))))))))))

(defn cancel-editing!
  "Cancel editing"
  []
  (reset! editing-nav-id nil)
  (reset! editing-content "")
  (reset! pending-selection nil)
  (reset! target-cursor-column nil))

(defn save-nav-content!
  "Save the edited content of a nav.
   By default clears editing state; pass {:clear-editing? false} to keep editing."
  ([nav-id note-id database-name]
   (save-nav-content! nav-id note-id database-name {:clear-editing? true}))
  ([nav-id note-id database-name {:keys [clear-editing? content]
                                  :or {clear-editing? true}}]
   (let [new-content (if (some? content) content @editing-content)]
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
    ;; Clear editing state only when requested and still editing this nav.
    (when (and clear-editing?
               (= nav-id @editing-nav-id))
      (cancel-editing!)))))

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

(defn normalize-sibling-orders!
  "Normalize sibling order values based on current visual sibling sequence.
   This avoids unstable insert positions when legacy data has duplicate/nil orders."
  [siblings note-id database-name]
  (doseq [[idx sibling] (map-indexed vector siblings)]
    (let [sid (:id sibling)
          normalized-order (* idx 100)
          current-order (:same-deep-order sibling)]
      (when (or (nil? current-order)
                (not= current-order normalized-order))
        ;; Update local datascript
        (d/transact! db/dsdb
          [[:db/add [:id sid] :same-deep-order normalized-order]])
        ;; Sync to backend
        (re-frame/dispatch-sync
          [:update-nav
           {:database-name database-name
            :note-id note-id
            :id sid
            :order normalized-order}])))))

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

(defn collect-descendant-ids
  "Collect all descendant ids of nav-id."
  [nav-id]
  (let [nav (u/get-nav-sub-navs-sorted @db/dsdb nav-id)]
    (reduce
      (fn [acc child]
        (let [cid (:id child)]
          (into (conj acc cid) (collect-descendant-ids cid))))
      #{}
      (:parid nav))))

(defn valid-drop-target?
  "Validate drop target for moving drag-nav-id onto target-nav-id."
  [drag-nav-id target-nav-id]
  (and drag-nav-id
       target-nav-id
       (not= drag-nav-id target-nav-id)
       (not (contains? (collect-descendant-ids drag-nav-id) target-nav-id))))

(defn move-nav-after!
  "Move drag-nav-id to be the next sibling of target-nav-id."
  [drag-nav-id target-nav-id note-id database-name]
  (when (valid-drop-target? drag-nav-id target-nav-id)
    (let [drag-nav (u/get-nav-by-id @db/dsdb drag-nav-id)
          target-nav (u/get-nav-by-id @db/dsdb target-nav-id)
          old-parid (:origin-parid drag-nav)
          new-parid (:origin-parid target-nav)
          target-order (or (:same-deep-order target-nav) 0)
          next-sibling (find-next-sibling target-nav-id)
          next-order (when next-sibling (:same-deep-order next-sibling))
          new-order (calculate-order-between target-order next-order)]
      ;; Update local datascript
      (when old-parid
        (d/transact! db/dsdb
          [[:db/retract [:id old-parid] :parid [:id drag-nav-id]]]))
      (d/transact! db/dsdb
        [[:db/add [:id drag-nav-id] :origin-parid new-parid]
         [:db/add [:id drag-nav-id] :same-deep-order new-order]
         [:db/add [:id new-parid] :parid [:id drag-nav-id]]])
      ;; Sync to backend
      (re-frame/dispatch-sync
        [:update-nav
         {:database-name database-name
          :note-id note-id
          :id drag-nav-id
          :parid new-parid
          :order new-order}]))))

(defn move-nav-to-child!
  "Move drag-nav-id to be the last child of target-nav-id."
  [drag-nav-id target-nav-id note-id database-name]
  (when (valid-drop-target? drag-nav-id target-nav-id)
    (let [drag-nav (u/get-nav-by-id @db/dsdb drag-nav-id)
          old-parid (:origin-parid drag-nav)
          new-parid target-nav-id
          last-child-order (get-last-child-order new-parid)
          new-order (calculate-order-between last-child-order nil)]
      ;; Update local datascript
      (when old-parid
        (d/transact! db/dsdb
          [[:db/retract [:id old-parid] :parid [:id drag-nav-id]]]))
      (d/transact! db/dsdb
        [[:db/add [:id drag-nav-id] :origin-parid new-parid]
         [:db/add [:id drag-nav-id] :same-deep-order new-order]
         [:db/add [:id new-parid] :parid [:id drag-nav-id]]
         [:db/add [:id new-parid] :is-display true]])
      ;; Sync to backend
      (re-frame/dispatch-sync
        [:update-nav
         {:database-name database-name
          :note-id note-id
          :id drag-nav-id
          :parid new-parid
          :order new-order}])
      (re-frame/dispatch-sync
        [:update-nav
         {:database-name database-name
          :note-id note-id
          :id new-parid
          :is-display true}]))))

(defn detect-drop-mode
  "Determine drop mode by cursor X relative to target row text left edge.
   Right of text left edge => child; left zone between bullet and text => sibling."
  [e]
  (let [row-el (.-currentTarget e)
        content-el (when row-el (.querySelector row-el ".nav-content"))
        text-left (when content-el (.-left (.getBoundingClientRect content-el)))
        mouse-x (.-clientX e)]
    (if (and text-left (>= mouse-x text-left))
      :child
      :sibling)))

;; ==================== Visual Navigation Helpers ====================
;; These functions help navigate between visible nodes (respecting collapsed state)

(defn collect-visible-navs
  "Recursively collect all visible navs in display order.
   Only includes children if parent's is-display is true.
   Returns a vector of {:id nav-id :content content}"
  [nav-id]
  (let [nav (u/get-nav-by-id @db/dsdb nav-id)
        nav-with-children (u/get-nav-sub-navs-sorted @db/dsdb nav-id)
        children (:parid nav-with-children)
        is-display (:is-display nav)]
    (if (and (seq children) is-display)
      ;; Has visible children - include this nav and recurse into children
      (into [{:id nav-id :content (:content nav)}]
            (mapcat #(collect-visible-navs (:id %)) children))
      ;; No children or collapsed - just this nav
      [{:id nav-id :content (:content nav)}])))

(defn get-visible-nav-list
  "Get a flat list of all visible navs starting from root-nav-id.
   This represents the visual order of navs as seen by the user."
  [root-nav-id]
  (let [root-nav (u/get-nav-sub-navs-sorted @db/dsdb root-nav-id)
        children (:parid root-nav)]
    ;; Start from root's children (don't include root itself)
    (vec (mapcat #(collect-visible-navs (:id %)) children))))

(defn find-visible-nav-index
  "Find the index of a nav in the visible nav list"
  [visible-list nav-id]
  (first (keep-indexed (fn [idx item]
                         (when (= (:id item) nav-id) idx))
                       visible-list)))

(defn get-prev-visible-nav
  "Get the previous visible nav (the one above in visual hierarchy)"
  [root-nav-id current-nav-id]
  (let [visible-list (get-visible-nav-list root-nav-id)
        current-idx (find-visible-nav-index visible-list current-nav-id)]
    (when (and current-idx (> current-idx 0))
      (nth visible-list (dec current-idx)))))

(defn get-next-visible-nav
  "Get the next visible nav (the one below in visual hierarchy)"
  [root-nav-id current-nav-id]
  (let [visible-list (get-visible-nav-list root-nav-id)
        current-idx (find-visible-nav-index visible-list current-nav-id)]
    (when (and current-idx (< current-idx (dec (count visible-list))))
      (nth visible-list (inc current-idx)))))

;; ==================== Nav Operations ====================

(defn create-sibling-nav!
  "Create a new sibling nav after the current one.
   Uses midpoint order calculation."
  [current-nav-id note-id database-name]
  (let [siblings (vec (get-siblings-sorted current-nav-id))
        _ (normalize-sibling-orders! siblings note-id database-name)
        current-idx (->> siblings
                         (map-indexed (fn [i s] [i (:id s)]))
                         (filter #(= (second %) current-nav-id))
                         first
                         first)
        current-nav (u/get-nav-by-id @db/dsdb current-nav-id)
        parid (or (:origin-parid current-nav) db/root-id)
        current-order (if (some? current-idx) (* current-idx 100) 0)
        next-order (when (and current-idx (< current-idx (dec (count siblings))))
                     (* (inc current-idx) 100))
        ;; Calculate new order as midpoint between normalized neighbors
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
    ;; Enter edit mode immediately to avoid idle-state flash on the new node.
    (start-editing! new-nav-id "")
    ;; Sync to backend
    (re-frame/dispatch-sync
      [:create-nav
       {:database-name database-name
        :note-id note-id
        :id new-nav-id
        :parid parid
        :content ""
        :order new-order}])))

(defn create-sibling-above!
  "Create a new sibling nav before the current one."
  [current-nav-id note-id database-name]
  (let [siblings (vec (get-siblings-sorted current-nav-id))
        _ (normalize-sibling-orders! siblings note-id database-name)
        current-idx (->> siblings
                         (map-indexed (fn [i s] [i (:id s)]))
                         (filter #(= (second %) current-nav-id))
                         first
                         first)
        current-nav (u/get-nav-by-id @db/dsdb current-nav-id)
        parid (or (:origin-parid current-nav) db/root-id)
        current-order (if (some? current-idx) (* current-idx 100) 0)
        prev-order (when (and current-idx (> current-idx 0))
                     (* (dec current-idx) 100))
        ;; For first sibling, use a stable order before current.
        new-order (if (some? prev-order)
                    (calculate-order-between prev-order current-order)
                    (- current-order 100))
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
    ;; Enter edit mode immediately to avoid idle-state flash on the new node.
    (start-editing! new-nav-id "")
    ;; Sync to backend
    (re-frame/dispatch-sync
      [:create-nav
       {:database-name database-name
        :note-id note-id
        :id new-nav-id
        :parid parid
        :content ""
        :order new-order}])))

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

(defn get-root-nav-id
  "Get the root nav id for the current note"
  [note-id]
  (when note-id
    (let [note (d/q '[:find (pull ?e [:hulunote-notes/root-nav-id]) .
                      :in $ ?note-id
                      :where [?e :hulunote-notes/id ?note-id]]
                 @db/dsdb note-id)]
      (:hulunote-notes/root-nav-id note))))

(defn apply-inline-wrap!
  "Wrap current selection with marker, or insert paired markers for collapsed caret."
  [input marker]
  (let [content @editing-content
        marker-len (count marker)
        start (or (.-selectionStart input) 0)
        end (or (.-selectionEnd input) start)
        [from to] (if (<= start end) [start end] [end start])
        selected (subs content from to)
        new-content (str (subs content 0 from)
                         marker
                         selected
                         marker
                         (subs content to))
        collapsed? (= from to)
        new-start (+ from marker-len)
        new-end (if collapsed?
                  new-start
                  (+ new-start (count selected)))]
    (reset! editing-content new-content)
    (js/setTimeout
      (fn []
        (.focus input)
        (.setSelectionRange input new-start new-end))
      0)))

(defn handle-key-down
  "Handle keyboard events in edit mode.
   - Arrow Up/Down: navigate between visible nodes (like editing a document)
   - Arrow Left at position 0: move to end of previous visible node
   - Arrow Right at end: move to start of next visible node
   - Tab: indent (increase depth, become child of prev sibling)
   - Shift+Tab: outdent (decrease depth, become sibling of parent)
   - Enter: save and create new sibling
   - Escape: cancel editing
   - Backspace/Delete: delete node when content is empty"
  [e nav-id note-id database-name]
  (let [key-code (.-keyCode e)
        key (.-key e)
        mod? (or (.-metaKey e) (.-ctrlKey e))
        shift? (.-shiftKey e)
        saved-content @editing-content
        input (.-target e)
        current-content (or (.-value input) @editing-content)
        cursor-pos (.-selectionStart input)
        content-length (count current-content)
        root-nav-id (get-root-nav-id note-id)]
    ;; Prevent global key handlers from stealing focus while editing.
    (.stopPropagation e)
    (cond
      ;; Cmd/Ctrl + B/I/Y/H - inline markdown styling
      (and mod? (#{"b" "B"} key))
      (do
        (.preventDefault e)
        (apply-inline-wrap! input "**"))

      (and mod? (#{"i" "I"} key))
      (do
        (.preventDefault e)
        (apply-inline-wrap! input "__"))

      (and mod? (#{"y" "Y"} key))
      (do
        (.preventDefault e)
        (apply-inline-wrap! input "~~"))

      (and mod? (#{"h" "H"} key))
      (do
        (.preventDefault e)
        (apply-inline-wrap! input "^^"))

      ;; Arrow Up (38) - move to previous visible node
      (= key-code 38)
      (when-let [prev-nav (get-prev-visible-nav root-nav-id nav-id)]
        (.preventDefault e)
        ;; Save current content first
        (d/transact! db/dsdb
          [[:db/add [:id nav-id] :content current-content]])
        ;; Remember target column for maintaining position across lines
        (when-not @target-cursor-column
          (reset! target-cursor-column cursor-pos))
        (let [prev-content (or (:content prev-nav) "")
              prev-len (count prev-content)
              ;; Try to maintain the same column position, but clamp to content length
              new-cursor-pos (min @target-cursor-column prev-len)]
          (start-editing! (:id prev-nav) prev-content new-cursor-pos)))
      
      ;; Arrow Down (40) - move to next visible node
      (= key-code 40)
      (when-let [next-nav (get-next-visible-nav root-nav-id nav-id)]
        (.preventDefault e)
        ;; Save current content first
        (d/transact! db/dsdb
          [[:db/add [:id nav-id] :content current-content]])
        ;; Remember target column for maintaining position across lines
        (when-not @target-cursor-column
          (reset! target-cursor-column cursor-pos))
        (let [next-content (or (:content next-nav) "")
              next-len (count next-content)
              ;; Try to maintain the same column position, but clamp to content length
              new-cursor-pos (min @target-cursor-column next-len)]
          (start-editing! (:id next-nav) next-content new-cursor-pos)))
      
      ;; Arrow Left (37) at position 0 - move to end of previous visible node
      (and (= key-code 37) (= cursor-pos 0) (not shift?))
      (when-let [prev-nav (get-prev-visible-nav root-nav-id nav-id)]
        (.preventDefault e)
        ;; Save current content first
        (d/transact! db/dsdb
          [[:db/add [:id nav-id] :content current-content]])
        ;; Clear target column since we're doing horizontal movement
        (reset! target-cursor-column nil)
        (let [prev-content (or (:content prev-nav) "")]
          (start-editing-at-end! (:id prev-nav) prev-content)))
      
      ;; Arrow Right (39) at end - move to start of next visible node
      (and (= key-code 39) (= cursor-pos content-length) (not shift?))
      (when-let [next-nav (get-next-visible-nav root-nav-id nav-id)]
        (.preventDefault e)
        ;; Save current content first
        (d/transact! db/dsdb
          [[:db/add [:id nav-id] :content current-content]])
        ;; Clear target column since we're doing horizontal movement
        (reset! target-cursor-column nil)
        (let [next-content (or (:content next-nav) "")]
          (start-editing-at-start! (:id next-nav) next-content)))
      
      ;; Other arrow keys - clear target column when not up/down
      (or (= key-code 37) (= key-code 39))
      (reset! target-cursor-column nil)
      
      ;; Backspace key (8) - delete node when content is empty
      ;; Keep Delete key for text editing to avoid accidental exit from edit mode.
      (and (= key-code 8)
           (empty? current-content))
      (do
        (.preventDefault e)
        ;; Find previous sibling or parent to focus after deletion
        (let [prev-sibling (find-prev-sibling nav-id)
              nav (u/get-nav-by-id @db/dsdb nav-id)
              parent-nav (when-let [parid (:origin-parid nav)]
                          (u/get-nav-by-id @db/dsdb parid))
              ;; Try to use visible navigation for better UX
              prev-visible (get-prev-visible-nav root-nav-id nav-id)
              next-focus-id (or (:id prev-visible)
                               (:id prev-sibling)
                               (when (and parent-nav 
                                         (not= (:id parent-nav) db/root-id)
                                         (not= (:content parent-nav) "ROOT"))
                                 (:id parent-nav)))]
          ;; Delete the current nav
          (delete-nav! nav-id note-id database-name)
          ;; Focus on the previous visible node
          (when next-focus-id
            (let [next-nav (u/get-nav-by-id @db/dsdb next-focus-id)]
              (js/setTimeout
                #(start-editing-at-end! next-focus-id (or (:content next-nav) ""))
                50)))))
      
      ;; Tab key - indent (make child of previous sibling)
      (and (= key-code 9) (not shift?))
      (do
        (.preventDefault e)
        (save-nav-content! nav-id note-id database-name {:clear-editing? false})
        (js/setTimeout
          #(do (indent-nav! nav-id note-id database-name)
               (start-editing! nav-id saved-content cursor-pos))
          50))
      
      ;; Shift+Tab - outdent (make sibling of parent)
      (and (= key-code 9) shift?)
      (do
        (.preventDefault e)
        (save-nav-content! nav-id note-id database-name {:clear-editing? false})
        (js/setTimeout
          #(do (outdent-nav! nav-id note-id database-name)
               (start-editing! nav-id saved-content cursor-pos))
          50))
      
      ;; Enter key - save and create new sibling
      (and (= key-code 13) (not shift?))
      (do
        (.preventDefault e)
        (reset! target-cursor-column nil)
        ;; Keep editing state during create flow to avoid visual flicker.
        (save-nav-content! nav-id note-id database-name {:clear-editing? false})
        ;; Defer create+focus to next tick to avoid blur/keydown timing race.
        (js/setTimeout
          #(if (and (= cursor-pos 0)
                    (not (empty? current-content)))
             (create-sibling-above! nav-id note-id database-name)
             (create-sibling-nav! nav-id note-id database-name))
          0)))
      
      ;; Escape key - cancel
      (= key-code 27)
      (cancel-editing!)
      
      ;; Any other key - clear target column (typing resets column tracking)
      :else
      (when (and (not (#{37 38 39 40} key-code)) ; not arrow keys
                 (not (#{16 17 18 91} key-code))) ; not modifier keys
        (reset! target-cursor-column nil))))

(defn should-handle-editor-key?
  "Run custom keyboard handler only for control/navigation/markdown shortcut keys."
  [e]
  (let [key-code (.-keyCode e)
        key (.-key e)
        mod? (or (.-metaKey e) (.-ctrlKey e))
        markdown-shortcut? (and mod? (#{"b" "B" "i" "I" "y" "Y" "h" "H"} key))
        control-key? (contains? #{8 9 13 27 37 38 39 40} key-code)]
    (or markdown-shortcut? control-key?)))

;; ==================== UI Components ====================

(defn has-children?
  "Check if a nav has children"
  [db nav-id]
  (let [nav (u/get-nav-sub-navs-sorted db nav-id)]
    (seq (:parid nav))))

(rum/defc nav-bullet < rum/reactive
  "Bullet point component with expand/collapse functionality and context menu"
  [db nav-id is-display note-id database-name content is-editing]
  (let [has-child (has-children? db nav-id)]
    [:span
     {:class (str "controls hulu-text-font " (when has-child "has-children"))
      :style {:align-items "center"
              :vertical-align "middle"
              :width "26px"
              :cursor "pointer"
              :padding-left "0"
              :justify-content "flex-start"
              :display "flex"
              :margin-right "10px"
              :gap "7px"
              :border-radius "8px"
              :height "16px"}
      :draggable (not is-editing)
      :on-drag-start (fn [e]
                       (.stopPropagation e)
                       (set! (.. e -dataTransfer -effectAllowed) "move")
                       (.setData (.-dataTransfer e) "text/plain" nav-id)
                       (reset! dragging-nav-id nav-id)
                       (reset! drag-over-nav-id nil)
                       (reset! drag-over-mode nil))
      :on-drag-end (fn [e]
                     (.stopPropagation e)
                     (reset! drag-over-nav-id nil)
                     (reset! drag-over-mode nil)
                     (reset! dragging-nav-id nil))
      :on-click (fn [e]
                  ;; Bullet area should not trigger row editing click.
                  (u/stop-click-bubble e))
      :on-context-menu (fn [e]
                         (show-context-menu! e nav-id content note-id database-name))}
     ;; Keep a stable slot before the dot; show triangle only for nodes with children.
     [:span
      {:style {:width "9px"
               :display "inline-flex"
               :justify-content "center"}}
      (when has-child
        [:span
         {:class (str "controls expand-icon " (if is-display "expanded" "collapsed"))
         :style {:font-size "9px"
                  :color "var(--text-muted)"
                  :transition "transform 0.15s ease, color 0.15s ease"
                  :transform (if is-display "rotate(90deg)" "rotate(0deg)")
                  :display "inline-block"
                  :line-height "1"
                  :width "9px"
                  :background "transparent"
                  :text-align "center"}
          :on-click (fn [e]
                      (u/stop-click-bubble e)
                      (toggle-nav-display! db nav-id is-display note-id database-name))}
         "▶"])]
     [:span
      {:class "controls customize-dot night-circular"
       :style {:height (if is-editing "var(--bullet-size-editing)" "var(--bullet-size-idle)")
               :width (if is-editing "var(--bullet-size-editing)" "var(--bullet-size-idle)")
               :margin-left (if is-editing "calc((var(--bullet-size-idle) - var(--bullet-size-editing)) / 2)" "0")
               :border-radius "50%"
               :background-color (if is-editing "var(--theme-accent)" "var(--bullet-idle-color)")
               :box-shadow (cond
                             is-editing "0 0 0 2px var(--theme-accent-glow)"
                             (and has-child (not is-display)) "0 0 0 2px var(--bullet-collapsed-glow)"
                             :else "none")
               :cursor "pointer"
               :display "block"
               :vertical-align "middle"}}]]))

(rum/defc nav-content-editor < rum/reactive
  "Editable content component"
  [nav-id content note-id database-name]
  (let [is-editing (= nav-id (rum/react editing-nav-id))]
    (if is-editing
      [:input.nav-editor-input
       {:type "text"
        :value (rum/react editing-content)
        :ref (fn [el]
               (when-let [{:keys [nav-id start end focus?]} @pending-selection]
                 (when (and el (= nav-id @editing-nav-id))
                   ;; Keep focus in editor across controlled rerenders.
                   (when (not= (.-activeElement js/document) el)
                     (.focus el))
                   (.setSelectionRange el start end)
                   (reset! pending-selection nil))))
        :style {:border "none"
                :border-radius "0"
                :padding "0"
                :outline "none"
                :width "100%"
                :min-width "100px"
                :font-size "inherit"
                :font-family "inherit"
                :font-weight "inherit"
                :letter-spacing "inherit"
                :line-height "inherit"
                :background "transparent"
                :color "inherit"}
        :on-change (fn [e]
                     (let [input (.-target e)
                           value (.-value input)
                       start (or (.-selectionStart input) 0)
                       end (or (.-selectionEnd input) start)]
                       ;; Keep caret stable across controlled-input rerenders.
                       (reset! pending-selection {:nav-id nav-id
                                                  :start start
                                                  :end end
                                                  :focus? true})
                       (reset! editing-content value)))
        :on-key-down (fn [e]
                       ;; Always isolate editor key events from global handlers.
                       (.stopPropagation e)
                       (let [key-code (.-keyCode e)
                             input-value (or (.. e -target -value) "")]
                         (when (or (= key-code 9) ; Tab
                                   (= key-code 13) ; Enter
                                   (and (= key-code 8) ; Backspace
                                        (empty? input-value)))
                           (handle-key-down e nav-id note-id database-name))))
        :on-blur (fn [_] nil)}]
      [:span.nav-content
       {:style {:cursor "text"
                :min-height "20px"
                :display "inline-block"
                :min-width "100px"}
        :on-click (fn [e]
                    (u/stop-click-bubble e)
                    (reset! target-cursor-column nil)
                    (let [cursor-pos (estimate-cursor-pos-from-click e content)]
                      (start-editing! nav-id content cursor-pos)))}
       (when-not (empty? content)
         (comps/parse-and-render content {}))])))

(rum/defc nav-input < rum/reactive
  [db id note-id database-name]
  (let [{:keys [last-account-id parid is-display
                hulunote-note content parser-content
                properties same-deep-order updated-at
                created-at last-user-cursor]}
        (u/get-nav-by-id db id)
        is-editing (= id (rum/react editing-nav-id))
        is-drop-target (= id (rum/react drag-over-nav-id))
        current-drop-mode (rum/react drag-over-mode)]
    [:div.nav-item
     ;; Entire row is clickable to enter edit mode
     [:div {:class (str "head-dot flex "
                        (when is-editing "is-editing")
                        (when is-drop-target
                          (str " "
                               (if (= current-drop-mode :child)
                                 "drop-target-child"
                                 "drop-target-sibling"))))
            :style {:padding-left "13px"
                    :padding-top "5px"
                    :padding-bottom "5px"
                    :cursor "text"}
            :on-drag-over (fn [e]
                            (when (valid-drop-target? @dragging-nav-id id)
                              (.preventDefault e)
                              (set! (.. e -dataTransfer -dropEffect) "move")
                              (reset! drag-over-nav-id id)
                              (reset! drag-over-mode (detect-drop-mode e))))
            :on-drag-leave (fn [_]
                             (when (= id @drag-over-nav-id)
                               (reset! drag-over-nav-id nil)
                               (reset! drag-over-mode nil)))
            :on-drop (fn [e]
                       (.preventDefault e)
                       (.stopPropagation e)
                       (let [drag-id (or @dragging-nav-id
                                         (.getData (.-dataTransfer e) "text/plain"))
                             mode (or @drag-over-mode (detect-drop-mode e))]
                         (if (= mode :child)
                           (move-nav-to-child! drag-id id note-id database-name)
                           (move-nav-after! drag-id id note-id database-name)))
                       (reset! drag-over-nav-id nil)
                       (reset! drag-over-mode nil)
                       (reset! dragging-nav-id nil))
            :on-click (fn [e]
                        (reset! target-cursor-column nil)
                        (let [cursor-pos (estimate-cursor-pos-from-click e content)]
                          (start-editing! id content cursor-pos)))}
      (nav-bullet db id is-display note-id database-name content is-editing)
      (nav-content-editor id content note-id database-name)]
     (when is-display
       [:div.content-box {:style {:margin-left "22px"
                                  :padding-left "0"
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
        (rum/with-key
          (nav-input db id actual-note-id actual-db-name)
          (or id dbid))))))

;; Global context menu - render at app level
(rum/defc global-context-menu < rum/reactive
  []
  (context-menu))
