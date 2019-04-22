(ns roesc.notifier.twilio-test
  (:require [roesc.notifier.twilio :as twilio]
            [clojure.test :as t]
            [clojure.tools.logging :as logger]
            [roesc.spec]
            [orchestra.spec.test]))

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

(t/deftest using-handler
  (t/testing "must run call-fn on each notification"
    (let [recorder (make-recorder)
          handler (#'twilio/make-handler (get-fn recorder))]
      (handler [{:process-id "p1" :phone-number "+1"}
                {:process-id "p2" :phone-number "+2"}])
      (t/is (= #{["p1" "+1"] ["p2" "+2"]}
               (set (get-recording recorder)))))))

(t/deftest using-call-fn
  (t/testing "call-fn must generate valid Twilio API requests"
    (let [recorder (make-recorder)
          call-fn (#'twilio/make-call-fn {:http-send-fn (get-fn recorder)
                                          :account-sid "SID"
                                          :auth-token "TOKEN"
                                          :host "HOST"
                                          :url "URL"
                                          :caller-id-registry {"+385" "+385123"}})]
      (call-fn "p1" "+385777")
      (let [request (-> (get-recording recorder) ffirst)]
        (t/is (= {:server-name "HOST"
                  :server-port 443,
                  :scheme :https
                  :request-method :post
                  :uri "/2010-04-01/Accounts/SID/Calls.json"
                  :headers {"authorization" "Basic U0lEOlRPS0VO",
                            "content-type" "application/x-www-form-urlencoded"}}
                 (-> request (dissoc :body))))
        (t/is (= "Method=GET&To=%2B385777&From=%2B385123&Url=URL"
                 (-> request :body .array String.)))))))
