(ns hulunote.chat-state)

(def default-model "anthropic/claude-sonnet-4.6")

(def default-chat-state
  {:messages []
   :input ""
   :loading? false
   :api-key ""
   :api-key-set? false
   :model default-model
   :use-tools? true
   :show-settings? false
   :available-models []
   :models-loading? false
   :database-name nil
   :error nil})

(defonce chat-state
  (atom default-chat-state))
