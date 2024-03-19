(ns hulunote.storage
  (:require [reagent.core :as reagent]
            [alandipert.storage-atom :refer [local-storage]]
            [cognitect.transit :as t]))

(def jwt-auth (local-storage (atom {}) :jwt-auth))
