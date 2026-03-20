(ns hulunote.settings-state)

(defonce settings-modal-state
  (atom {:open? false
         :tab :profile
         :version 0}))

(defn open-settings!
  ([] (open-settings! :profile))
  ([tab]
   (swap! settings-modal-state
          (fn [{:keys [version]}]
            {:open? true
             :tab (or tab :profile)
             :version (inc (or version 0))}))))

(defn close-settings! []
  (swap! settings-modal-state assoc :open? false))
