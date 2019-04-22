(ns roesc.debug
  (:require  [clojure.test :as t]
             [clojure.core.async :as async]
             [cognitect.aws.credentials :as credentials]
             [cognitect.aws.client.api :as aws]
             [clojure.java.jdbc :as jdbc]
             [clojure.data.json :as json]
             [roesc.config :as config]
             [roesc.initiator.sqs :as sqs]
             [clojure.tools.logging :as logger]
             [roesc.escalation-process-repository :as repository]
             [roesc.notifier.twilio :as twilio]
             [roesc.escalation-process-repository.postgresql :as postgresql]
             [cognitect.http-client :as http])
  (:import java.time.Instant))

(comment

  (def credentials
    (credentials/default-credentials-provider))

  (def sqs (aws/client {:api :sqs :credentials-provider credentials}))
  (keys (aws/ops sqs))

  (aws/doc sqs :ReceiveMessage)
  (aws/doc sqs :SendMessage)
  (aws/doc sqs :DeleteMessageBatch)

  (def queue (first (:QueueUrls (aws/invoke sqs {:op :ListQueues}))))
  (def response (aws/invoke sqs {:op :ReceiveMessage
                                 :request {:QueueUrl config/request-queue
                                           :WaitTimeSeconds 1}}))

  (let [message (-> (aws/invoke sqs {:op :ReceiveMessage
                                     :request {:QueueUrl config/request-queue
                                               :WaitTimeSeconds 1
                                               :MaxNumberOfMessages 10}})
                    :Messages
                    first)]
    (when message (sqs/extract-payload message)))

  (println response)
  (aws/invoke sqs  {:op :DeleteMessage
                    :request {:QueueUrl queue
                              :ReceiptHandle (-> response :Messages first :ReceiptHandle)}})

  (def db-spec
    {:dbtype "postgresql"
     :dbname "roesc"
     :host "localhost"
     :user "roesc"
     :password "roesc"})

  (def sample-start-request {:process-id "restaurant1"
                             :action "start"
                             :notifications [{:at 2
                                              :channel "phone"
                                              :phone-number "+38591111111"}]})
  (println (clojure.data.json/write-str sample-start-request))
  (def sample-start-request-json-string (clojure.data.json/write-str sample-start-request))
  (clojure.data.json/read-str sample-start-request-json-string :bigdec true :key-fn keyword)

  ((sqs/make-sqs-request-fetching-fn (aws/client {:api :sqs
                                                  :credentials-provider (credentials/default-credentials-provider)})
                                     config/request-queue))

  ;;; Generating sample messages in SQS
  (defn generate-sample-requests [n action]
    (let [seed (rand-int 100000)
          r (java.util.Random. seed)
          generator (fn []
                      (let [base-request {:process-id (rand-nth (map #(format "restaurant%d" %) (range 30)))
                                          :action action}]
                        (if (= "stop" action)
                          base-request
                          (merge base-request
                                 {:notifications (repeatedly (inc (rand-int 2))
                                                             (fn [] {:at (+ (.getEpochSecond (Instant/now))
                                                                           (inc (rand-int 20)))
                                                                    :channel "phone"
                                                                    :phone-number "+15005550006"}))}))))]
      (logger/info "Generating samples using seed" seed)
      (repeatedly n generator)))

  (generate-sample-requests 1 "start")

  (defn send-requests [requests]
    (let [sqs (aws/client {:api :sqs
                           :credentials-provider (credentials/default-credentials-provider)})]
      (doall (map #(aws/invoke sqs
                               {:op :SendMessage
                                :request {:QueueUrl config/request-queue
                                          :MessageGroupId (:process-id %)
                                          :MessageDeduplicationId (str (java.util.UUID/randomUUID))
                                          :MessageBody (json/write-str %)}})
                  requests))))

  (do (send-requests (generate-sample-requests 1 "start"))
      (send-requests (generate-sample-requests 1 "stop"))
      nil)

  (jdbc/with-db-connection [db config/db-spec]
    (let [r (postgresql/make-repository db)]
      (jdbc/delete! db :process nil)
      (repository/insert r "1" [{:at 1 :channel "phone" :phone-number "1"}
                                {:at 1 :channel "phone" :phone-number "2"}])
      (repository/insert r "3" [{:at 3 :channel "phone" :phone-number "1"}])
      (repository/find-overdue r (Instant/ofEpochSecond 2))))

  (def messages (-> (aws/invoke sqs {:op :ReceiveMessage
                                     :request {:QueueUrl config/request-queue
                                               :WaitTimeSeconds 5
                                               :MaxNumberOfMessages 10}})
                    :Messages))
  (def response (aws/invoke sqs
                            {:op :DeleteMessageBatch
                             :request {:QueueUrl config/request-queue
                                       :Entries (->> messages
                                                     (map #(select-keys % [:MessageId :ReceiptHandle]))
                                                     (map #(clojure.set/rename-keys % {:MessageId :Id})))}}))

  (map #(deref % 500 :not-finished)
       (doall
        (map #(future (println %) (throw (Exception. "F")))
             (range 2))))

  (def resp (let [client (http/create {})
                  url (java.net.URL. config/caller-id-registry-configuration)]
              (async/<!!
               (http/submit client {:server-name (.getHost url)
                                    :server-port (if (= -1 (.getPort url)) (.getDefaultPort url) (.getPort url))
                                    :scheme (.getProtocol url)
                                    :uri (.getPath url)
                                    :request-method :get}))))
  (String. (.array (:body resp)))

  (roesc.core/run)

  )
