(ns hulunote.dom
  (:require
    [clojure.string :as str]))

(defn q [selector]
  (js/document.querySelector selector))

(defn set-value! [el value]
  (set! (.-value el) value))

(defn value [el]
  (let [val (.-value el)]
    (when-not (str/blank? val)
      (str/trim val))))

(defn date-value [el]
  (when-let [val (value el)]
    (let [val (js/Date.parse val)]
      (when-not (js/isNaN val)
        (js/Date. val)))))

(defn array-value [el]
  (when-let [val (value el)]
    (str/split val #"\s+")))

