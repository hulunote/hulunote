(ns hulunote.right-sidebar
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [goog.events :as events]
            [hulunote.db :as db]
            [hulunote.icon :as icon]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.render :as render]
            [hulunote.components :as comps]))

(defonce resize-session (atom nil))

(defn sync-right-sidebar-width-css!
  [width]
  (.setProperty (.-style (.-documentElement js/document))
                "--right-sidebar-width"
                (str width "px")))

(defn stop-resizing!
  []
  (.remove (.-classList (.-documentElement js/document)) "right-sidebar-resizing")
  (when-let [{:keys [move-key up-key]} @resize-session]
    (events/unlistenByKey move-key)
    (events/unlistenByKey up-key)
    (reset! resize-session nil)))

(defn begin-resize!
  [evt]
  (.preventDefault evt)
  (.stopPropagation evt)
  (stop-resizing!)
  (.add (.-classList (.-documentElement js/document)) "right-sidebar-resizing")
  (let [move-key
        (events/listen js/window "mousemove"
          (fn [move-evt]
            (let [next-width (- (.-innerWidth js/window)
                                (.-clientX move-evt))
                  clamped-width (db/clamp-right-sidebar-width next-width)]
              (sync-right-sidebar-width-css! clamped-width)
              (db/set-right-sidebar-width! clamped-width))))
        up-key
        (events/listen js/window "mouseup"
          (fn [_]
            (stop-resizing!)))]
    (reset! resize-session {:move-key move-key
                            :up-key up-key})))

(rum/defc sidebar-note-panel < rum/reactive
  "A single note panel in the right sidebar with editable nav tree"
  [db {:keys [note-id note-title root-nav-id database-name]}]
  (let [;; Get live data from DataScript
        live-info (d/q '[:find ?title ?root-nav-id
                         :in $ ?note-id
                         :where
                         [?e :hulunote-notes/id ?note-id]
                         [?e :hulunote-notes/title ?title]
                         [?e :hulunote-notes/root-nav-id ?root-nav-id]]
                    db note-id)
        [current-title current-root-nav-id] (or (first live-info)
                                                 [note-title root-nav-id])]
    [:div.right-sidebar-note
     ;; Note header with title and close button
     [:div.right-sidebar-note-header
      [:div.right-sidebar-note-title
       {:on-click #(router/go-to-note! database-name note-id)
        :title (str "Go to: " current-title)}
       current-title]
      [:div.right-sidebar-note-close
       {:on-click (fn [e]
                    (.stopPropagation e)
                    (db/close-note-in-right-sidebar! note-id))
        :title "Close"}
       "\u00D7"]]
     ;; Nav content (editable outline tree)
     (when current-root-nav-id
       [:div.right-sidebar-note-content
        (render/render-navs db current-root-nav-id note-id database-name)])]))

(rum/defc right-sidebar < rum/reactive
  "Right sidebar component showing multiple notes side by side"
  [db]
  (let [open? (rum/react db/right-sidebar-open?)
        notes (rum/react db/right-sidebar-notes)
        width (rum/react db/right-sidebar-width)]
    (prn "[right-sidebar render] open?:" open? "notes:" (count notes))
    (sync-right-sidebar-width-css! width)
    (when open?
      [:div.right-sidebar
       [:div.right-sidebar-resize-handle
        {:on-mouse-down begin-resize!
         :title "Resize sidebar"}]
       [:div.right-sidebar-header
        [:div.right-sidebar-header-main]
        [:div.right-sidebar-header-actions
         (when (seq notes)
           [:button.right-sidebar-topbar-btn
            {:title "Close All Sidebar Notes"
             :on-click db/close-right-sidebar!}
            (icon/svg-icon {:name "right_sidebar_clear" :class "app-topbar-icon"})])
         [:button.right-sidebar-topbar-btn
          {:title "Hide Right Sidebar"
           :on-click #(db/toggle-right-sidebar-visibility!)}
          (icon/svg-icon {:name "dock_to_left" :class "app-topbar-icon"})]]]
       [:div.right-sidebar-body
        (if (seq notes)
          (for [note notes]
            (rum/with-key
              (sidebar-note-panel db note)
              (:note-id note)))
          [:div.right-sidebar-empty
           "Shift-click bidirectional links, blocks, or block references to open them here."])]])))
