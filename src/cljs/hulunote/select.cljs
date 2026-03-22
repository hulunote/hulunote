(ns hulunote.select
  (:require [rum.core :as rum]
            [hulunote.icon :as icon]
            [hulunote.menu :as menu]
            [hulunote.util :as u]))

(defn- option-value [{:keys [value id]}]
  (or value id))

(defn- option-label [{:keys [label]}]
  label)

(defn- selected-option [options value]
  (some #(when (= (option-value %) value) %) options))

(defn- estimate-width [options value placeholder]
  (let [labels (->> options
                    (map option-label)
                    (remove nil?)
                    (cons placeholder)
                    (map str))
        longest-label (apply max 0 (map count labels))
        selected-label (or (some-> (selected-option options value) option-label str)
                           (str placeholder))
        basis (max longest-label (count selected-label))
        width (+ 74 (* basis 8))]
    (str (min 220 (max 136 width)) "px")))

(rum/defcs select-dropdown
  < rum/reactive
    (rum/local false ::open?)
    (rum/local nil ::root-el)
    (rum/local nil ::outside-click-handler)
    {:did-mount
     (fn [state]
       (let [handler (fn [event]
                       (let [root @(::root-el state)
                             target (.-target event)]
                         (when (and root (not (.contains root target)))
                           (reset! (::open? state) false))))]
         (.addEventListener js/document "mousedown" handler)
         (reset! (::outside-click-handler state) handler)
         state))
     :will-unmount
     (fn [state]
       (when-let [handler @(::outside-click-handler state)]
         (.removeEventListener js/document "mousedown" handler))
       state)}
  [state {:keys [options value on-change placeholder width class style menu-style disabled?]
          :or {placeholder "Select"}}]
  (let [open? (rum/react (::open? state))
        selected (selected-option options value)
        selected-label (or (some-> selected option-label) placeholder)
        resolved-width (or width (estimate-width options value placeholder))]
    (into
      [:div.custom-select
       {:class (str
                 (when open? " open")
                 (when disabled? " disabled")
                 (when class (str " " class)))
        :style (merge {:width resolved-width} style)
        :ref #(reset! (::root-el state) %)}
       [:button.custom-select-trigger
        {:type "button"
         :disabled disabled?
         :on-click (fn [e]
                     (u/stop-click-bubble e)
                     (when-not disabled?
                       (swap! (::open? state) not)))}
        [:span.custom-select-trigger-label selected-label]
        (icon/svg-icon {:name "keyboard_arrow_down"
                        :class "custom-select-trigger-icon"})]]
      (when open?
        [(into
           (menu/menu-popover
             {:class "custom-select-menu"
              :style (merge {:position "absolute"
                             :top "calc(100% + 2px)"
                             :left "0"
                             :width "100%"
                             :padding "8px"
                             :display "flex"
                             :flex-direction "column"
                             :gap "6px"
                             :box-sizing "border-box"}
                            menu-style)})
           (for [option options]
             (let [option-id (option-value option)
                   active? (= option-id value)]
               (menu/menu-item
                 {:class (str "custom-select-option"
                              (when active? " active"))
                  :on-click (fn [e]
                              (u/stop-click-bubble e)
                              (reset! (::open? state) false)
                              (when on-change
                                (on-change option-id)))}
                 (option-label option)))))]))))
