(ns roesc.notifier.pubnub
  (:require [roesc.notifier.common :as common]
            [roesc.util :refer [with-exception-logging with-time-logging]]
            [roesc.config :as config]
            [clojure.core.async :as async]
            [cognitect.http-client :as http]
            [clojure.tools.logging :as logger]))

(defn ->request [{:keys [^java.net.URL service-url pub-key sub-key uuid channel]}]
  {:server-name                        (.getHost service-url)
   :server-port                        (common/resolve-port service-url)
   :scheme                             (keyword (.getProtocol service-url))
   :request-method                     :post
   :uri                                (format "/publish/%s/%s/0/%s/0?store=0&uuid=%s"
                                               pub-key sub-key channel uuid)
   :cognitect.http-client/timeout-msec 10000
   :headers                            {"content-type" "application/json"}})

(defn- make-http-send-fn [client configuration]
  (fn pubnub-http-send [notification]
    (with-time-logging "Pubnub HTTP communication"
      (let [request (->request (merge configuration {:channel (:pubnub-channel notification)}))
            response-status (-> (http/submit client request) async/<!! :status)]
        (logger/log (if (<= 200 response-status 299) :info :error)
                    (format "Pubnub request received a response with status %s"
                            response-status))))))

(defn make-executor-based-notifier [configuration]
  {:pre [(every? #(contains? configuration %) [:executor :service-url :uuid :pub-key :sub-key])]}
  (with-exception-logging
    (common/make-executor-based-handler
     (:executor configuration)
     (make-http-send-fn (http/create {}) configuration))))
