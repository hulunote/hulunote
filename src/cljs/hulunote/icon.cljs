(ns hulunote.icon
  (:require [hulunote.util :as u]))

(defn svg-icon
  [{:keys [name class style alt] :or {alt ""}}]
  [:img {:src (u/asset-path (str "/img/icons/" name ".svg"))
         :class class
         :style style
         :alt alt
         :draggable false
         :aria-hidden true}])
