(ns roesc.notifier.smtp
  (:require [postal.core :as postal]
            [clojure.set :refer [rename-keys]]
            [clojure.tools.logging :as logger]
            [roesc.config :as config]
            [roesc.util :refer [with-exception-logging]]
            [roesc.notifier.common :as common])
  (:import java.util.concurrent.ExecutorService))

(defn- make-send-fn [smtp-cfg email-cfg]
  (fn [notification]
    (with-exception-logging
      (logger/info "Sending email to" (:email notification))
      (postal/send-message (rename-keys smtp-cfg {:username :user, :password :pass})
                           (merge email-cfg {:to (:email notification)})))))

(defn make-executor-based-notifier [configuration]
  {:pre [every? #(contains? configuration %) [:executor :smtp :email]]}
  (common/make-executor-based-handler (:executor configuration)
                                      (make-send-fn (:smtp configuration) (:email configuration))))
