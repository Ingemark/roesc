(ns roesc.core
  "Component entry point. Creates initiator, activator, notifiers and all other
  system state and runs them in appropriate order. This is the only place where
  we create system state (i.e. database connections, HTTP clients, configuration
  fetching etc.)"
  (:require [cognitect.aws.credentials :as credentials]
            [cognitect.aws.client.api :as aws]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logger]
            [roesc.config :as config]
            [roesc.initiator :as initiator]
            [roesc.activator :as activator]
            [roesc.escalation-process-repository :as process-repository]
            [roesc.escalation-process-repository.postgresql :as postgresql]
            [roesc.initiator.sqs :as sqs]
            [roesc.notifier.twilio :as twilio]
            [roesc.notifier.smtp :as smtp])
  (:import [java.util.concurrent Executors])
  (:gen-class
   :methods [^:static [lambdahandler [java.util.Map] String]]))

(defn fetch-caller-id-registry [db]
  (->> (jdbc/query db ["select property_value from property where property_key='caller-id-registry'"])
       first :property_value json/read-str))

(defn run []
  (logger/info "Component started.")
  (jdbc/with-db-connection [db config/db-spec]
    (let [caller-id-registry (fetch-caller-id-registry db)
          twilio-executor   (Executors/newFixedThreadPool config/max-calling-threads)
          twilio-notifier   (twilio/make-executor-based-notifier
                             (merge config/twilio
                                    {:caller-id-registry caller-id-registry
                                     :executor twilio-executor}))
          smtp-executor     (Executors/newFixedThreadPool config/max-smtp-threads)
          smtp-notifier     (smtp/make-executor-based-notifier {:executor smtp-executor
                                                                :smtp config/smtp
                                                                :email config/email})
          notifier-registry {"phone" twilio-notifier, "email" smtp-notifier}
          sqscli            (aws/client {:api :sqs
                                         :credentials-provider (credentials/default-credentials-provider)})
          repository        (postgresql/make-repository db)
          initiator-fn      (initiator/make-initiator-fn
                             {:message-fetching-fn   (sqs/make-sqs-request-fetching-fn
                                                      sqscli config/request-queue config/sqs-read-wait-time-seconds)
                              :payload-extracting-fn sqs/extract-payload
                              :request-processing-fn (initiator/make-request-processing-fn repository)
                              :message-cleanup-fn    (sqs/make-sqs-request-cleanup-fn sqscli config/request-queue)
                              :max-run-time          config/initiator-max-run-time-millis})
          activator-fn      (activator/make-activator-function repository notifier-registry)]
      (logger/info "Processing started.")
      (initiator-fn)
      (activator-fn)
      (.shutdown twilio-executor)
      (.shutdown smtp-executor)
      (logger/info "Processing finished."))))

(defn -lambdahandler [_]
  (run))

(defn -main
  [& _]
  (run))
