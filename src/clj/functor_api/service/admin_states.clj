(ns functor-api.service.admin-states)

;; 今日封禁的用户
(def today-banned-user-tag (atom nil))
(def today-banned-user (atom #{}))
