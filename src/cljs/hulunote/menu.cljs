(ns hulunote.menu)

(def default-popover-style
  {:background "var(--ui-surface-popover)"
   :border "1px solid var(--ui-border-strong)"
   :border-radius "8px"
   :box-shadow "var(--ui-shadow-popover)"
   :z-index 10000
   :padding "6px 0"})

(def default-header-style
  {:padding "8px 12px"
   :color "var(--ui-text-subtle)"
   :font-size "11px"
   :border-bottom "1px solid var(--ui-border-strong)"
   :overflow "hidden"
   :text-overflow "ellipsis"
   :white-space "nowrap"})

(def default-item-style
  {:padding "7px 12px"
   :cursor "pointer"
   :color "var(--ui-text-strong)"
   :font-size "12px"
   :display "flex"
   :align-items "center"
   :gap "10px"})

(def danger-item-style
  {:color "var(--ui-danger-text)"})

(defn menu-popover
  [{:keys [class style on-click on-mouse-leave]} & children]
  (into
    [:div
     {:class (str "nav-context-menu menu-popover"
                  (when class (str " " class)))
      :style (merge default-popover-style style)
      :on-click on-click
      :on-mouse-leave on-mouse-leave}]
    children))

(defn menu-header
  ([text]
   (menu-header {} text))
  ([{:keys [class style]} text]
   [:div
    {:class (str "context-menu-header menu-popover-header"
                 (when class (str " " class)))
     :style (merge default-header-style style)}
    text]))

(defn menu-item
  ([opts text]
   (let [{:keys [class style on-click danger? icon]} opts]
     [:div
      {:class (str "context-menu-item menu-popover-item"
                   (when danger? " menu-popover-item-danger")
                   (when class (str " " class)))
       :style (merge default-item-style
                     (when danger? danger-item-style)
                     style)
       :on-click on-click}
      (when icon
        [:span.menu-popover-item-icon icon])
      [:span.menu-popover-item-label text]])))
