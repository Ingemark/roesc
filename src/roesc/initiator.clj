(ns roesc.initiator
  "Initiator handles incoming requests and initiates escalation processes."
  (:require [roesc.util :refer [skipping-exceptions skipping-exceptions-but-with-sleep]]
            [roesc.escalation-process-repository :as process-repository]
            [clojure.tools.logging :as logger])
  (:import java.time.Instant))

(def supported-channels #{"phone"})

(defn valid-channel? [notification]
  (contains? supported-channels (:channel notification)))

(defn valid? [request]
  (and (:process-id request)
       (or (= "stop" (:action request))
           (and (= "start" (:action request))
                (seq (:notifications request))
                (every? valid-channel? (:notifications request))))))

(defn- decide [request process-exists?]
  (cond
    (and (= "start" (:action request))
         (not process-exists?))
    :create-new-process

    (and (= "stop" (:action request))
         process-exists?)
    :cancel-process

    :else
    :ignore-request))

(defn- update-state [repository request decision]
  (case decision
    :create-new-process
    (process-repository/insert repository (:process-id request) (:notifications request))

    :cancel-process
    (process-repository/delete repository (:process-id request))

    (logger/debug "no need to do anything on" decision)))

(defn- process-request [repository request]
  (let [process-exists? (boolean (process-repository/exists? repository (:process-id request)))
        decision     (decide request process-exists?)]
    (update-state repository request decision)))

(defn make-request-processing-fn [repository]
  (fn request-processing-fn [request]
    (process-request repository request)))

(defn- within-time-limit [start-time-millis time-limit-millis]
  (if (nil? time-limit-millis)
    true
    (< (- (System/currentTimeMillis) start-time-millis)
       time-limit-millis)))

(defn make-initiator-fn
  "Construct the initiator main input function.

  The function `message-fetching-fn` is called without arguments and is expected
  to return a sequence of requests which need to be processed.

  The function `payload-extracting-fn` is called on request to extract the
  payload. E.g. SQS returns a wrapper message in which there is a message id
  which is used to delete messages. This method extracts a payload from the
  message.

  The function `request-processing-fn` is called with single
  argument being request and its return value is ignored.

  The function `message-cleanup-fn` is called with the request argument and it
  performs cleanup (e.g. deleting processed messages from SQS).

  Variable `max-run-time-millis` sets the limit on the amount of time the loop
  function will run."
  [{:keys [message-fetching-fn
           payload-extracting-fn
           request-processing-fn
           message-cleanup-fn
           max-run-time-millis]}]
  (fn initiator-input-fn []
    (logger/info "Initiator started.")
    (let [start-time (System/currentTimeMillis)]
      (loop [messages (message-fetching-fn)]
        (if (empty? messages)
          (logger/info "No more messages to fetch.")
          (do
            (doseq [msg messages]
              (logger/info "processing" (pr-str (dissoc msg :ReceiptHandle)))
              (skipping-exceptions
               (let [request (payload-extracting-fn msg)]
                 (if-not (valid? request)
                   (logger/warn "skipping invalid request" request)
                   (request-processing-fn request)))))
            (skipping-exceptions
             (message-cleanup-fn messages))
            (if (within-time-limit start-time max-run-time-millis)
              (recur (message-fetching-fn))
              (logger/info "Exiting due to exceeding initiator max run time limit of"
                           max-run-time-millis))))))
    (logger/info "Initiator finished.")))