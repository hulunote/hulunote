(ns hulunote.single-note
  (:require [clojure.string :as str]
            [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.components :as comps]
            [hulunote.menu :as menu]
            [hulunote.util :as u]
            [hulunote.render :as render]
            [hulunote.db :as db]
            [hulunote.sidebar :as sidebar]
            [hulunote.router :as router]
            [re-frame.core :as re-frame]))

;; State for editing note title
(defonce editing-note-id (atom nil))
(defonce editing-note-title (atom ""))

;; State for note title context menu
(defonce title-menu-state (atom {:visible false
                                  :x 0
                                  :y 0
                                  :note-id nil
                                  :note-title nil
                                  :root-nav-id nil
                                  :database-name nil}))

(declare note-title-editor)
(declare linked-references)

(defn get-route-params
  "Get route params from db"
  [db]
  (let [{:keys [params]} (db/get-route db)]
    params))

(defn block-label
  [content]
  (let [text (some-> content str/trim)]
    (if (str/blank? text)
      "Untitled block"
      text)))

(defn get-note-by-id
  "Get note by id from datascript"
  [db note-id]
  (let [result (d/q
                 '[:find ?title ?root-nav-id ?is-shortcut
                   :in $ ?note-id
                   :where
                   [?e :hulunote-notes/id ?note-id]
                   [?e :hulunote-notes/title ?title]
                   [?e :hulunote-notes/root-nav-id ?root-nav-id]
                   [(get-else $ ?e :hulunote-notes/is-shortcut false) ?is-shortcut]]
                 db note-id)]
    (first result)))

(defn get-nav-breadcrumbs
  [db root-nav-id nav-id]
  (loop [current-id nav-id
         acc []]
    (if-let [nav (u/get-nav-by-id db current-id)]
      (let [entry {:id current-id
                   :content (:content nav)}
            parent-id (:origin-parid nav)]
        (if (or (nil? parent-id)
                (= parent-id root-nav-id)
                (= parent-id db/root-id))
          (vec (reverse (conj acc entry)))
          (recur parent-id (conj acc entry))))
      (vec (reverse acc)))))

(rum/defc focused-block-breadcrumbs
  [database-name note-id note-title breadcrumbs]
  (into
    [:div
     {:style {:display "flex"
              :align-items "center"
              :flex-wrap "wrap"
              :gap "8px"
              :margin-bottom "18px"
              :font-size "15px"
              :line-height "1.5"}}
     [:span
      {:style {:color "rgba(255,255,255,0.56)"
               :cursor "pointer"
               :font-weight "500"}
       :on-click #(router/go-to-note! database-name note-id)}
      note-title]]
    (mapcat
      (fn [[_ {:keys [id content]}]]
        [[:span {:key (str "sep-" id)
                 :style {:color "rgba(255,255,255,0.24)"}} "›"]
         [:span
          {:key (str "crumb-" id)
           :style {:color "rgba(255,255,255,0.50)"
                   :cursor "pointer"}
           :on-click #(router/go-to-block-focus! database-name note-id id)}
          (block-label content)]])
      (map-indexed vector breadcrumbs))))

(rum/defc focused-block-view
  [db database-name note-id note-title nav-id]
  (let [[_ root-nav-id] (get-note-by-id db note-id)
        breadcrumbs (get-nav-breadcrumbs db root-nav-id nav-id)
        parent-breadcrumbs (vec (butlast breadcrumbs))
        focused-nav (u/get-nav-by-id db nav-id)]
    (when focused-nav
      [:div
       (focused-block-breadcrumbs database-name note-id note-title parent-breadcrumbs)
       [:div {:style {:padding-left "12px"}}
        (render/nav-input db nav-id note-id database-name)]])))

(defn note-page-content
  [db database note-id note-title root-nav-id]
  [:<>
   [:div.note-title-wrapper
    {:style {:margin-bottom "24px"}}
    (note-title-editor note-id note-title root-nav-id database)]
   [:div {:style {:padding-left "12px"}}
    (render/render-navs db root-nav-id note-id database)]
   (linked-references db note-title note-id database)])

(defn start-editing-title!
  "Start editing a note title"
  [note-id title]
  (if (db/is-daily-title title)
    (u/alert "Diary Title Cannot be Changed")
    (do
      (reset! editing-note-id note-id)
      (reset! editing-note-title (or title "")))))

(defn cancel-editing-title!
  "Cancel editing title"
  []
  (reset! editing-note-id nil)
  (reset! editing-note-title ""))

(defn save-note-title!
  "Save the edited note title"
  [note-id]
  (let [new-title @editing-note-title]
    (when (not (empty? new-title))
      ;; Update local datascript
      (d/transact! db/dsdb
        [[:db/add [:hulunote-notes/id note-id] :hulunote-notes/title new-title]])
      ;; Sync to backend
      (re-frame/dispatch-sync
        [:update-note
         {:note-id note-id
          :title new-title}]))
    ;; Clear editing state
    (cancel-editing-title!)))

(defn handle-title-key-down
  "Handle keyboard events for title editing"
  [e note-id]
  (let [key-code (.-keyCode e)]
    (cond
      ;; Enter key - save
      (= key-code 13)
      (do
        (.preventDefault e)
        (save-note-title! note-id))
      ;; Escape key - cancel
      (= key-code 27)
      (cancel-editing-title!))))

;; ==================== Title Context Menu Functions ====================

(defn show-title-menu!
  "Show title context menu at specified position"
  [e note-id note-title root-nav-id database-name]
  (.preventDefault e)
  (.stopPropagation e)
  (reset! title-menu-state
    {:visible true
     :x (.-clientX e)
     :y (.-clientY e)
     :note-id note-id
     :note-title note-title
     :root-nav-id root-nav-id
     :database-name database-name}))

(defn hide-title-menu!
  "Hide title context menu"
  []
  (swap! title-menu-state assoc :visible false))

(defn get-all-navs-content
  "Recursively get all nav content as markdown with proper indentation"
  [db nav-id depth]
  (let [nav (u/get-nav-sub-navs-sorted db nav-id)
        children (:parid nav)
        nav-info (u/get-nav-by-id db nav-id)
        content (:content nav-info)
        indent (apply str (repeat depth "  "))]
    (if (seq children)
      (str
        (when (and content (not= content "ROOT"))
          (str indent "- " content "\n"))
        (apply str
          (map #(get-all-navs-content db (:id %) (if (= content "ROOT") depth (inc depth)))
               children)))
      (when (and content (not= content "ROOT"))
        (str indent "- " content "\n")))))

(defn copy-note-as-markdown!
  "Copy entire note content as markdown to clipboard"
  [note-title root-nav-id]
  (let [content (get-all-navs-content @db/dsdb root-nav-id 0)
        markdown (str "# " note-title "\n\n" content)
        textarea (.createElement js/document "textarea")]
    (set! (.-value textarea) markdown)
    (set! (.-style textarea) "position: fixed; left: -9999px;")
    (.appendChild (.-body js/document) textarea)
    (.select textarea)
    (.execCommand js/document "copy")
    (.removeChild (.-body js/document) textarea)
    (u/alert "Note copied as Markdown!")))

(defn delete-note!
  "Delete a note and navigate back"
  [note-id database-name]
  ;; Update backend
  (re-frame/dispatch-sync
    [:update-note
     {:note-id note-id
      :is-delete true
      :op-fn (fn [data]
               (prn "Note deleted:" data))}])
  ;; Remove from local datascript
  (d/transact! db/dsdb
    [[:db/retractEntity [:hulunote-notes/id note-id]]])
  ;; Navigate back to diaries/all notes
  (router/go-to-all-notes! database-name))

(defn confirm-and-delete-note!
  [note-id note-title database-name]
  (comps/show-confirm-dialog!
    {:title "Delete Page"
     :message (str "Delete \"" note-title "\"? This action cannot be undone.")
     :confirm-text "Delete"
     :cancel-text "Cancel"
     :danger? true
     :on-confirm #(delete-note! note-id database-name)}))

(defn set-note-shortcut!
  [note-id shortcut?]
  (d/transact! db/dsdb
    [[:db/add [:hulunote-notes/id note-id] :hulunote-notes/is-shortcut shortcut?]])
  (re-frame/dispatch-sync
    [:update-note
     {:note-id note-id
      :is-shortcut shortcut?
      :op-fn (fn [data]
               (prn "Note shortcut updated:" data))}]))

(defn toggle-note-shortcut!
  [note-id current-shortcut?]
  (set-note-shortcut! note-id (not current-shortcut?)))

(rum/defc title-context-menu < rum/reactive
  "Context menu component for note title"
  []
  (let [{:keys [visible x y note-id note-title root-nav-id database-name]} (rum/react title-menu-state)]
    (when visible
      (menu/menu-popover
        {:class "title-context-menu"
         :style {:position "fixed"
                 :left (str x "px")
                 :top (str y "px")
                 :min-width "180px"
                 :border-radius "6px"
                 :box-shadow "0 4px 12px rgba(0,0,0,0.3)"
                 :padding "4px 0"}
         :on-mouse-leave hide-title-menu!}
        (menu/menu-header note-title)
        (menu/menu-item
          {:on-click (fn [e]
                       (.stopPropagation e)
                       (start-editing-title! note-id note-title)
                       (hide-title-menu!))}
          "Edit Title")
        (menu/menu-item
          {:on-click (fn [e]
                       (.stopPropagation e)
                       (db/open-note-in-right-sidebar! note-id note-title root-nav-id database-name)
                       (hide-title-menu!))}
          "Open in Sidebar")
        (menu/menu-item
          {:on-click (fn [e]
                       (.stopPropagation e)
                       (copy-note-as-markdown! note-title root-nav-id)
                       (hide-title-menu!))}
          "Copy as Markdown")
        (menu/menu-item
          {:danger? true
           :style {:color "#ff6b6b"}
           :on-click (fn [e]
                       (.stopPropagation e)
                       (confirm-and-delete-note! note-id note-title database-name)
                       (hide-title-menu!))}
          "Delete Note")))))

(rum/defc note-title-editor < rum/reactive
  "Editable note title component with right-click context menu.
   Shift+click opens the note in the right sidebar."
  [note-id note-title root-nav-id database-name]
  (let [is-editing (= note-id (rum/react editing-note-id))]
    (if is-editing
      [:input.note-title-input
       {:type "text"
        :auto-focus true
        :value (rum/react editing-note-title)
        :on-change #(reset! editing-note-title (.. % -target -value))
        :on-key-down #(handle-title-key-down % note-id)
        :on-blur #(save-note-title! note-id)}]
      [:div.note-title
       {:on-click (fn [e]
                    (if (.-shiftKey e)
                      (do
                        (u/stop-click-bubble e)
                        (db/open-note-in-right-sidebar! note-id note-title root-nav-id database-name))
                      (start-editing-title! note-id note-title)))
        :on-context-menu (fn [e]
                           (show-title-menu! e note-id note-title root-nav-id database-name))
        }
       note-title])))

;; ==================== Backlinks (Linked References) ====================

(defonce backlinks-collapsed? (atom {}))
(defonce backlink-nav-collapsed? (atom {}))

(defn pure-page-link-node?
  "Return true when a block is essentially just a page reference like [[Page]]
   or #[[Page]]. Used to decide whether backlink children should be expanded by default."
  [content]
  (boolean
    (and (string? content)
         (re-matches #"\s*#?\[\[[^\]]+\]\]\s*" content))))

(rum/defc backlink-nav-item < rum/reactive
  "Render a backlinked nav block and, when present, its child subtree.
   Pure page-link blocks default to expanded children; sentence-style references default collapsed."
  [source-note-id database-name nav]
  (let [nav-tree (u/get-nav-sub-navs-sorted @db/dsdb (:id nav))
        nav-id (:id nav-tree)
        children (:parid nav-tree)
        has-children? (seq children)
        collapsed-map (rum/react backlink-nav-collapsed?)
        editing-nav-id (rum/react render/editing-nav-id)
        is-editing (= nav-id editing-nav-id)
        default-collapsed? (and has-children?
                                (not (pure-page-link-node? (:content nav-tree))))
        collapsed? (if (contains? collapsed-map nav-id)
                     (get collapsed-map nav-id)
                     default-collapsed?)]
    [:div.backlink-nav-item
     (when-let [parent-content (:parent-content nav)]
       [:div
        {:style {:padding-left "29px"
                 :padding-bottom "4px"
                 :font-size "inherit"
                 :line-height "inherit"
                 :color "rgba(255,255,255,0.42)"}}
        parent-content])
     [:div {:class "head-dot flex backlink-outline-node"
            :style {:padding-left "13px"
                    :padding-top "5px"
                    :padding-bottom "5px"
                    :cursor "default"}}
      (render/nav-bullet
        @db/dsdb
        nav-id
        (not collapsed?)
        source-note-id
        database-name
        (:content nav-tree)
        is-editing
        {:forced-has-children? has-children?
         :collapsed? collapsed?
         :on-toggle (fn [e]
                      (u/stop-click-bubble e)
                      (swap! backlink-nav-collapsed? assoc nav-id (not collapsed?)))})
      (render/nav-content-editor nav-id (:content nav-tree) source-note-id database-name)]
     (when (and has-children? (not collapsed?))
       [:div.content-box
        {:style {:margin-left "22px"
                 :padding-left "0"
                 :position "relative"}}
        [:div.content-box.outline-line.night-outline-line]
        (for [child children]
          (rum/with-key
            (backlink-nav-item source-note-id database-name child)
            (:id child)))])]))

(rum/defc backlink-note-group < rum/reactive
  "Render a group of backlinks from a single source note"
  [database-name source-note-id source-title navs]
  (let [collapsed-map (rum/react backlinks-collapsed?)
        collapsed? (get collapsed-map source-note-id false)]
    [:div.backlink-note-group
     {:style {:margin-bottom "12px"}}
     ;; Source note title (clickable)
     [:div.backlink-note-title
      {:style {:display "flex"
               :align-items "center"
               :gap "10px"
               :cursor "default"
               :padding "6px 0"
               :border-radius "4px"}
       :on-click (fn [e]
                   (u/stop-click-bubble e)
                   (swap! backlinks-collapsed? update source-note-id not))}
      ;; Collapse/expand indicator
      [:span {:style {:font-size "10px"
                      :color "rgba(255,255,255,0.4)"
                      :transition "transform 0.15s"
                      :display "inline-block"
                      :cursor "pointer"
                      :transform (if collapsed? "rotate(0deg)" "rotate(90deg)")}}
       "\u25B6"]
      ;; Note title link (shift+click opens in right sidebar)
      [:span {:class "backlink-note-link-title"
              :style {:font-weight "500"
                      :font-size "inherit"
                      :line-height "inherit"
                      :color "rgba(255,255,255,0.78)"} 
              :on-click (fn [e]
                          (u/stop-click-bubble e)
                          (if (.-shiftKey e)
                            (let [root-nav-id (d/q '[:find ?rnid .
                                                     :in $ ?nid
                                                     :where
                                                     [?e :hulunote-notes/id ?nid]
                                                     [?e :hulunote-notes/root-nav-id ?rnid]]
                                                @db/dsdb source-note-id)]
                              (db/open-note-in-right-sidebar! source-note-id source-title root-nav-id database-name))
                            (router/go-to-note! database-name source-note-id)))}
       source-title]
      ;; Count badge
      [:span {:style {:display "inline-flex"
                      :align-items "center"
                      :font-size "11px"
                      :line-height "1"
                      :color "rgba(255,255,255,0.4)"
                      :margin-left "4px"}}
       (str (count navs))]]
     ;; Nav content blocks
     (when-not collapsed?
       [:div.backlink-navs
        {:style {:padding-left "0"}}
        (for [nav navs]
          (rum/with-key
            (backlink-nav-item source-note-id database-name nav)
            (:id nav)))])]))

(rum/defcs linked-references < rum/reactive
  (rum/local false ::linked-references-collapsed?)
  (rum/local false ::linked-references-header-hovered?)
  "Linked References panel - shows all notes that reference the current note"
  [state db note-title note-id database-name]
  (let [backlinks (db/find-backlinks db note-title)
        ;; Filter out self-references
        backlinks (remove (fn [{:keys [source-note-id]}] (= source-note-id note-id)) backlinks)
        grouped (db/group-backlinks-by-note backlinks)
        total-count (count backlinks)
        collapsed? (rum/react (::linked-references-collapsed? state))
        header-hovered? (rum/react (::linked-references-header-hovered? state))]
    (when (pos? total-count)
      [:div.linked-references
       {:style {:margin-top "20px"
                :margin-left "var(--note-content-align-left)"
                :padding-top "20px"}}
       ;; Section header
       [:div.linked-references-header
        {:style {:display "flex"
                 :align-items "center"
                 :gap "8px"
                 :margin-bottom "16px"
                 :padding-bottom "12px"
                 :border-bottom "1px solid rgba(255,255,255,0.1)"
                 :cursor "pointer"}
         :on-mouse-enter #(reset! (::linked-references-header-hovered? state) true)
         :on-mouse-leave #(reset! (::linked-references-header-hovered? state) false)
         :on-click (fn [e]
                     (u/stop-click-bubble e)
                     (swap! (::linked-references-collapsed? state) not))}
        [:span
         {:style {:font-size "10px"
                  :color "rgba(255,255,255,0.42)"
                  :transition "transform 0.15s ease, opacity 0.15s ease"
                  :display "inline-block"
                  :width "10px"
                  :opacity (if header-hovered? 1 0)
                  :transform (if collapsed? "rotate(0deg)" "rotate(90deg)")}}
         "\u25B6"]
        [:span {:style {:font-size "15px"
                        :font-weight "600"
                        :color "rgba(255,255,255,0.5)"}}
         (str total-count " Linked References")]]
       ;; Grouped backlinks
       (when-not collapsed?
         [:div.linked-references-body
          (for [[source-note-id {:keys [title note-id navs]}] grouped]
            (rum/with-key
              (backlink-note-group database-name source-note-id title navs)
              source-note-id))])])))

(rum/defc single-note-page < rum/reactive
  [db]
  (let [{:keys [database note-id nav-id]} (get-route-params db)
        route-name (:route-name (db/get-route db))
        focused-view? (= route-name :block-focus)
        note-info (when note-id (get-note-by-id db note-id))
        sidebar-collapsed? (rum/react sidebar/sidebar-collapsed?)
        right-sidebar-open? (rum/react db/right-sidebar-open?)]
    [:div.night-center-boxBg.night-textColor-2
     (sidebar/app-top-bar
       {:title (if note-info (first note-info) "Note")
        :more-menu-items
        (when note-info
          (let [[note-title _root-nav-id is-shortcut] note-info]
            [{:label (if is-shortcut
                       "Remove from Shortcuts"
                       "Add to Shortcuts")
              :icon (if is-shortcut
                      (sidebar/star-menu-icon-filled)
                      (sidebar/star-menu-icon))
              :on-click (fn [_]
                          (toggle-note-shortcut! note-id is-shortcut))}
             {:label "Delete Page"
              :icon (sidebar/delete-menu-icon)
              :danger? true
              :on-click (fn [_]
                          (confirm-and-delete-note! note-id note-title database))}]))})
     [:div.page-wrapper
      ;; Left sidebar
      (sidebar/left-sidebar db database)
      ;; Main content area
      [:div.main-content-area
       {:class (str (when sidebar-collapsed? "sidebar-collapsed")
                    (when right-sidebar-open? " right-sidebar-open"))}
       [:div.flex.flex-column.overflow-scroll-new
        {:style {:padding "20px"
                 :max-width "900px"
                 :margin "0 auto"
                 :min-height "100vh"}}

        (if note-info
          (let [[note-title root-nav-id] note-info]
            [:div
             (if focused-view?
               (if-let [focused-view (focused-block-view db database note-id note-title nav-id)]
                 focused-view
                 [:<>
                  [:div
                   {:style {:margin-bottom "20px"
                            :padding "10px 14px"
                            :border-radius "10px"
                            :background "rgba(255,255,255,0.04)"
                            :color "rgba(255,255,255,0.56)"
                            :font-size "14px"}}
                   "Focused block no longer exists. Showing the full note."]
                  (note-page-content db database note-id note-title root-nav-id)])
               (note-page-content db database note-id note-title root-nav-id))])

          ;; Note not found
          [:div.flex.flex-column.items-center.justify-center
           {:style {:height "50vh"}}
           [:div {:style {:font-size "18px" :margin-bottom "16px"}}
            "Note not found"]
           [:div {:style {:color "rgba(255,255,255,0.5)" :margin-bottom "20px"}}
            (str "Note ID: " note-id)]
           [:button
            {:on-click #(router/go-to-diaries! database)
             :style {:background "var(--theme-accent)"
                     :border "none"
                     :color "#fff"
                     :padding "10px 20px"
                     :border-radius "6px"
                     :cursor "pointer"}}
            "Go to Diaries"]])

        [:div {:style {:height "100px"}}]]]
      ;; Global title context menu
      (title-context-menu)
      ;; Global nav context menu (from render.cljs)
      (render/global-context-menu)
      ;; Slash command dropdown menu
      (render/slash-command-menu)]]))
