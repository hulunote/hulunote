(ns hulunote.icon
  (:require [hulunote.util :as u]))

(defn svg-icon
  [{:keys [name class style alt] :or {alt ""}}]
  (let [icon-path (u/asset-path (str "/img/icons/" name ".svg"))
        merged-class (str "ui-svg-icon" (when class (str " " class)))
        cleaned-style (dissoc style :filter :object-fit)]
    [:span {:class merged-class
            :style (merge {"--icon-mask" (str "url('" icon-path "')")}
                          cleaned-style)
            :title alt
            :role "img"
            :aria-label alt
            :aria-hidden (if (seq alt) nil true)}]))
