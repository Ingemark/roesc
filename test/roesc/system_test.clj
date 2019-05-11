(ns roesc.system-test
  (:require  [clojure.test :as t]
             [roesc.core :as core]
             [roesc.spec]
             [cognitect.aws.credentials :as credentials]
             [cognitect.aws.client.api :as aws]
             [clojure.data.json :as json]
             [roesc.config :as config]
             [orchestra.spec.test :as st]))

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

(t/deftest ^:integration system-test
  (t/testing "running the component"
    (st/instrument)
    (send-requests [{:process-id "restaurant1",
                     :action "start",
                     :notifications [{:channel "phone", :phone-number "+15005550006", :at 1557568911}
                                     {:channel "email", :email "success@simulator.amazonses.com", :at 1557568911}]}
                    {:process-id "restaurant2",
                     :action "start",
                     :notifications [{:channel "phone", :phone-number "+15005550006", :at 1557568911}
                                     {:channel "email", :email "me@example.com", :at 1557568911}]}])
    (core/run)
    (t/is true)))
