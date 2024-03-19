(ns hulunote.styles)

(defn button-hover-orange
  []
  ^{:pseudo
    {:hover
     {:background "red" ;; c/o3
      :cursor "pointer"}}}
  {:display "block"})
