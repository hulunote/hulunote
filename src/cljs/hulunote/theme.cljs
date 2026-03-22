(ns hulunote.theme
  (:require [clojure.string :as str]))

(def ^:private storage-key "hulunote-ui-theme")
(def ^:private supported-theme-modes #{"dark" "light" "auto"})

(defonce current-theme (atom "dark"))
(defonce current-theme-mode (atom "dark"))
(defonce ^:private system-theme-listener (atom nil))

(defn- theme-name [theme]
  (cond
    (keyword? theme) (name theme)
    (string? theme) theme
    :else ""))

(defn normalize-theme-mode [theme-mode]
  (let [normalized (str/lower-case (theme-name theme-mode))]
    (if (supported-theme-modes normalized) normalized "dark")))

(defn normalize-theme [theme]
  (let [normalized (str/lower-case (theme-name theme))]
    (if (#{"dark" "light"} normalized) normalized "dark")))

(defn current-theme-mode-name []
  @current-theme-mode)

(defn current-theme-name []
  @current-theme)

(defn- persist-theme-mode! [theme-mode]
  (try
    (.setItem js/localStorage storage-key theme-mode)
    (catch :default _
      nil)))

(defn read-stored-theme-mode []
  (try
    (normalize-theme-mode (.getItem js/localStorage storage-key))
    (catch :default _
      "dark")))

(defn- system-prefers-dark? []
  (boolean (some-> js/window
                   (.matchMedia "(prefers-color-scheme: dark)")
                   .-matches)))

(defn resolve-theme [theme-mode]
  (let [normalized-mode (normalize-theme-mode theme-mode)]
    (if (= normalized-mode "auto")
      (if (system-prefers-dark?) "dark" "light")
      (normalize-theme normalized-mode))))

(defn- dispatch-theme-change! [theme-mode theme-name]
  (.dispatchEvent js/window
    (js/CustomEvent. "hulunote:theme-change"
      #js {:detail #js {:theme theme-name
                        :themeMode theme-mode}})))

(defn apply-theme! [theme-mode]
  (let [mode-name (normalize-theme-mode theme-mode)
        theme-name (resolve-theme mode-name)
        root (.-documentElement js/document)]
    (.setAttribute root "data-theme" theme-name)
    (.setAttribute root "data-theme-mode" mode-name)
    (set! (.. root -style -colorScheme) theme-name)
    (reset! current-theme theme-name)
    (reset! current-theme-mode mode-name)
    theme-name))

(defn set-theme! [theme-mode]
  (let [previous-theme @current-theme
        previous-mode @current-theme-mode
        mode-name (normalize-theme-mode theme-mode)
        theme-name (apply-theme! mode-name)]
    (persist-theme-mode! mode-name)
    (when (or (not= previous-theme theme-name)
              (not= previous-mode mode-name))
      (dispatch-theme-change! mode-name theme-name))
    theme-name))

(defn next-theme-mode []
  (case @current-theme-mode
    "dark" "light"
    "light" "auto"
    "auto" "dark"
    "dark"))

(defn- bind-system-theme-listener! []
  (when (and (exists? js/window)
             (exists? (.-matchMedia js/window))
             (nil? @system-theme-listener))
    (let [media-query (.matchMedia js/window "(prefers-color-scheme: dark)")
          listener (fn [_]
                     (when (= @current-theme-mode "auto")
                       (let [previous-theme @current-theme
                             theme-name (apply-theme! "auto")]
                         (when (not= previous-theme theme-name)
                           (dispatch-theme-change! "auto" theme-name)))))]
      (if (exists? (.-addEventListener media-query))
        (.addEventListener media-query "change" listener)
        (.addListener media-query listener))
      (reset! system-theme-listener {:media-query media-query
                                     :listener listener}))))

(defn init-theme! []
  (let [root (.-documentElement js/document)
        theme-mode (normalize-theme-mode (or (.getAttribute root "data-theme-mode")
                                             (.getAttribute root "data-theme")
                                             (read-stored-theme-mode)))]
    (bind-system-theme-listener!)
    (apply-theme! theme-mode)))
