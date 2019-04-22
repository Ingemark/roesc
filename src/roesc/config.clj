(ns roesc.config
  (:require [environ.core :refer [env]]))

(def request-queue (env :request-queue))

(def initiator-max-run-time-millis (Integer/parseInt (env :initiator-max-run-time-millis "120000")))

(def sqs-read-wait-time-seconds (Integer/parseInt (env :sqs-read-wait-time-seconds "3")))

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
