(ns roesc.config
  (:require [environ.core :refer [env]]))

(def request-queue (env :request-queue))

(def initiator-max-run-time-millis (Integer/parseInt (env :initiator-max-run-time-millis "120000")))

(def sqs-read-wait-time-seconds (Integer/parseInt (env :sqs-read-wait-time-seconds "3")))

(def max-calling-threads (Integer/parseInt (env :max-calling-threads "3")))

(def max-smtp-threads (Integer/parseInt (env :max-calling-threads "3")))

(def db-spec {:dbtype   "postgresql"
              :port     (env :db-port 5432)
              :dbname   (env :db-name "roesc")
              :host     (env :db-host "localhost")
              :user     (env :db-user "roesc")
              :password (env :db-password "roesc")})

(def twilio {:account-sid (env :twilio-account-sid "UNSET")
             :auth-token  (env :twilio-auth-token "UNSET")
             :host        (env :twilio-host "api.twilio.com")
             :url         (env :twilio-url)})

(def smtp {:username (env :smtp-username)
           :password (env :smtp-password)
           :host     (env :smtp-host "localhost")
           :port     (Integer/parseInt (env :smtp-port "587"))})

(def email {:from (env :mail-from)
            :subject (env :mail-subject "There are new orders in the RoomOrders service")
            :body (env :mail-body "")})
