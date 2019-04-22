(ns roesc.initiator.sqs
  (:require [cognitect.aws.client.api :as aws]
            [clojure.set :refer [rename-keys]]
            [clojure.data.json :refer [read-str]]
            [clojure.tools.logging :as logger]))

(defn make-sqs-request-fetching-fn [sqs request-queue sqs-read-wait-time-seconds]
  (fn sqs-request-fetching-fn []
    (logger/info "Starting fetch from SQS...")
    (let [result (-> (aws/invoke sqs {:op      :ReceiveMessage
                                      :request {:QueueUrl            request-queue
                                                :MaxNumberOfMessages 10
                                                :WaitTimeSeconds     sqs-read-wait-time-seconds}})
                     :Messages)]
      (logger/info "Finished fetching from SQS, received" (count result) "messages")
      result)))

(defn- batch-delete-request-entries [messages]
  (->> messages
       (map #(select-keys % [:MessageId :ReceiptHandle]))
       (map #(rename-keys % {:MessageId :Id}))))

(defn make-sqs-request-cleanup-fn [sqs request-queue]
  (fn sqs-request-cleanup-fn [messages]
    (logger/info "Deleting messages" (map :MessageId messages))
    (let [response (aws/invoke sqs
                               {:op      :DeleteMessageBatch
                                :request {:QueueUrl request-queue
                                          :Entries  (batch-delete-request-entries messages)}})]
      (when (seq (:Failed response))
        (logger/error "Failed to delete some messages!" (:Failed response))))))

(defn extract-payload [message]
  (let [payload (:Body message)]
    (when payload
      (read-str payload :bigdec true :key-fn keyword))))
