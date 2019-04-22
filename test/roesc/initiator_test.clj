(ns roesc.initiator-test
  (:require [roesc.initiator :as initiator]
            [roesc.escalation-process-repository :as process-repository]
            [clojure.test :as t]
            [clojure.set :refer [subset?]]
            [clojure.tools.logging :as logger]
            [roesc.spec]
            [orchestra.spec.test]))

(defn with-instrumentation [f]
  (orchestra.spec.test/instrument)
  (f))

(t/use-fixtures :once with-instrumentation)

(defrecord MockRepository [overdue-notifs set-of-existing-process-ids called-methods])

(extend-type MockRepository
  process-repository/EscalationProcessRepository
  (-find-overdue [repository now]
    (swap! (:called-methods repository) conj :find-overdue)
    (:overdue-notifs repository))
  (-insert [repository process-id notifications]
    (swap! (:called-methods repository) conj :insert))
  (-delete [repository process-id]
    (swap! (:called-methods repository) conj :delete))
  (-exists? [repository process-id]
    (swap! (:called-methods repository) conj :exists?)
    (contains? (:set-of-existing-process-ids repository) process-id)))

(def sample-start-request {:process-id "restaurant1"
                           :action "start"
                           :notifications [{:at 2
                                            :channel "phone"
                                            :phone-number "+38591111111"}]})

(defn- called? [repository methods]
  (subset? (set (if (keyword? methods) [methods] methods))
           (set (deref (:called-methods repository)))))

(defn make-mock-repository [overdue-notifs set-of-existing-process-ids]
  (MockRepository. overdue-notifs set-of-existing-process-ids (atom [])))

(defn run-initiator [r message-fetcher-fn]
  (let [initiator-fn (initiator/make-initiator-fn
                      {:message-fetching-fn message-fetcher-fn
                       :payload-extracting-fn identity
                       :request-processing-fn (initiator/make-request-processing-fn r)
                       :message-cleanup-fn (fn [& _])})]
    (initiator-fn)))

(defn fetcher [requests]
  (let [passed (atom false)]
    (fn []
      (when-not @passed
        (reset! passed true)
        requests))))

(t/deftest handling-requests
  (t/testing "successful initiation of new process"
    (let [r (make-mock-repository [] #{})]
      (run-initiator r (fetcher [sample-start-request]))
      (t/is (called? r :insert))))
  (t/testing "ignoring a request which tries to initiate an already existing process"
    (let [r (make-mock-repository [] #{"restaurant1"})]
      (run-initiator r (fetcher [sample-start-request]))
      (t/is (not (called? r :insert)))))
  (t/testing "successful stopping of a process"
    (let [r (make-mock-repository [] #{"restaurant1" "restaurant2"})]
      (run-initiator r (fetcher [{:action "stop" :process-id "restaurant1"}]))
      (t/is (called? r :delete))))
  (t/testing "ignoring a request which tries to stop a process which does not exist"
    (let [r (make-mock-repository [] #{"other1"})]
      (run-initiator r (fetcher [{:action "stop" :process-id "restaurant1"}]))
      (t/is (not (called? r :delete))))))

(defrecord FaultingMockRepository [])

(extend-type FaultingMockRepository
  process-repository/EscalationProcessRepository
  (-find-overdue [repository now] nil)
  (-insert [repository process-id notifications]
    (throw (Exception. "deliberately throwing an exception in `insert`")))
  (-delete [repository process-id] nil)
  (-exists? [repository process-id] nil))

(t/deftest handling-errors
  (t/testing "must skip requests if an error is thrown in the request processing function"
    (let [r (FaultingMockRepository.)
          initiator (initiator/make-initiator-fn
                     {:message-fetching-fn (fetcher [sample-start-request])
                      :payload-extracting-fn identity
                      :request-processing-fn (fn [& _]
                                               (throw (Exception. "deliberately throwing an exception")))
                      :message-cleanup-fn (fn [& _])})]
      (initiator)
      (t/is true)))
  (t/testing "invalid requests must be deleted and must not affect other requests"
    (let [r (FaultingMockRepository.)]
      (run-initiator r (fetcher [{:invalid-request -1}]))
      (t/is true))))
