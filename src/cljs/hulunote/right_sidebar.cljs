(ns hulunote.right-sidebar
  (:require [datascript.core :as d]
            [rum.core :as rum]
            [hulunote.db :as db]
            [hulunote.util :as u]
            [hulunote.router :as router]
            [hulunote.render :as render]
            [hulunote.components :as comps]))

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
        notes (rum/react db/right-sidebar-notes)]
    (when (and open? (seq notes))
      [:div.right-sidebar
       ;; Header
       [:div.right-sidebar-header
        [:span.right-sidebar-title
         (str "Sidebar (" (count notes) ")")]
        [:div.right-sidebar-close
         {:on-click db/close-right-sidebar!
          :title "Close sidebar"}
         "\u00D7"]]
       ;; Note panels
       [:div.right-sidebar-body
        (for [note notes]
          (rum/with-key
            (sidebar-note-panel db note)
            (:note-id note)))]])))
