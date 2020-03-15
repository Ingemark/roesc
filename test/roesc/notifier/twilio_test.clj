(ns roesc.notifier.twilio-test
  (:require [roesc.notifier.twilio :as twilio]
            [clojure.test :as t :refer [deftest testing is]]
            [clojure.tools.logging :as logger]
            [roesc.spec]
            [roesc.notifier.common :as common]
            [orchestra.spec.test])
  (:import java.util.concurrent.Executors))

(defn with-instrumentation [f]
  (orchestra.spec.test/instrument)
  (f))

(t/use-fixtures :once with-instrumentation)

(defprotocol Recorder
  (reset-recording [_])
  (get-fn [_])
  (get-recording [_]))

(defrecord FunctionCallRecorder [calls])

(defn make-recorder []
  (FunctionCallRecorder. (atom [])))

(extend-type FunctionCallRecorder
  Recorder
  (reset-recording [this]
    (reset! (:calls this) []))
  (get-fn [this]
    (fn [& args]
      (swap! (:calls this) conj args)))
  (get-recording [this] (deref (:calls this))))

(deftest using-handler
  (testing "must run call-fn on each notification"
    (let [executor (Executors/newFixedThreadPool 3)]
      (try
        (let [recorder (make-recorder)
              handler (#'common/make-executor-based-handler executor (get-fn recorder))
              notifications [{:process-id "p1" :phone-number "+1"}
                             {:process-id "p2" :phone-number "+2"}]]
          (handler notifications)
          (is (= #{[(first notifications)] [(second notifications)]}
                   (set (get-recording recorder)))))
        (finally (.shutdown executor))))))

(deftest using-call-fn
  (testing "call-fn must generate valid Twilio API requests"
    (let [recorder (make-recorder)
          call-fn (#'twilio/make-call-fn {:http-send-fn (get-fn recorder)
                                          :account-sid "SID"
                                          :auth-token "TOKEN"
                                          :host "HOST"
                                          :url "URL"
                                          :caller-id-registry {"+385" "+385123"}})]
      (call-fn {:process-id "p1" :phone-number "+385777"})
      (let [request (-> (get-recording recorder) ffirst)]
        (is (= {:server-name "HOST"
                  :server-port 443,
                  :scheme :https
                  :request-method :post
                  :uri "/2010-04-01/Accounts/SID/Calls.json"
                  :headers {"authorization" "Basic U0lEOlRPS0VO",
                            "content-type" "application/x-www-form-urlencoded"}}
                 (-> request (dissoc :body))))
        (is (= "Method=GET&To=%2B385777&From=%2B385123&Url=URL"
                 (-> request :body .array String.)))))))
