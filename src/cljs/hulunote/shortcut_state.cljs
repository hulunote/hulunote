(ns hulunote.shortcut-state
  (:require [alandipert.storage-atom :refer [local-storage]]))

(defonce user-shortcut-overrides
  (local-storage (atom {}) :shortcut-overrides))

(defonce plugin-shortcut-overrides
  (atom {}))

(defonce shortcut-recording-state
  (atom {:command-id nil
         :error nil}))

(defn get-user-shortcut
  [command-id]
  (get @user-shortcut-overrides command-id))

(defn has-user-shortcut-override?
  [command-id]
  (contains? @user-shortcut-overrides command-id))

(defn set-user-shortcut!
  [command-id shortcut]
  (swap! user-shortcut-overrides assoc command-id shortcut))

(defn clear-user-shortcut!
  [command-id]
  (swap! user-shortcut-overrides dissoc command-id))

(defn blank-user-shortcut!
  [command-id]
  (swap! user-shortcut-overrides assoc command-id ""))

(defn set-plugin-shortcut!
  [command-id shortcut]
  (swap! plugin-shortcut-overrides assoc command-id shortcut))

(defn clear-plugin-shortcut!
  [command-id]
  (swap! plugin-shortcut-overrides dissoc command-id))

(defn start-recording!
  [command-id]
  (reset! shortcut-recording-state
          {:command-id command-id
           :error nil}))

(defn stop-recording!
  []
  (reset! shortcut-recording-state
          {:command-id nil
           :error nil}))

(defn set-recording-error!
  [message]
  (swap! shortcut-recording-state assoc :error message))
