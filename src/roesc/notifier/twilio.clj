(ns roesc.notifier.twilio
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as logger]
            [clojure.string :refer [join]]
            [cognitect.http-client :as http]
            [roesc.util :refer [with-time-logging skipping-exceptions with-exception-logging]]
            [roesc.notifier.common :as common])
  (:import java.util.Base64
           java.net.URLEncoder
           java.nio.ByteBuffer
           java.util.concurrent.ExecutorService
           java.util.Arrays))

(defn- find-caller-id
  "Find caller-id which should be used for the outgoing call to `phone-number`."
  [caller-id-registry ^String phone-number]
  (let [area-codes-prefix (keys caller-id-registry)
        matching-area-code (first (filter #(.startsWith phone-number %) area-codes-prefix))]
    (get caller-id-registry (or matching-area-code "default"))))

(defn- ->bbuf [^String content]
  (ByteBuffer/wrap (.getBytes content)))

(defn- url-encode [^String s]
  (URLEncoder/encode s "UTF-8"))

(defn- form-url-encode
  "URL-encode keys and values from the map."
  [m]
  (->> m
       (map (fn [[k v]] (str (url-encode (str k)) "=" (url-encode (str v)))))
       (join "&")))

(defn- basic-auth-token [username password]
  (.encodeToString (Base64/getEncoder) (.getBytes (format "%s:%s" username password))))

(defn- prepare-request [{:keys [account-sid auth-token host phone-number caller-id url]}]
  {:server-name    host
   :server-port    443
   :scheme         :https
   :request-method :post
   :uri            (format "/2010-04-01/Accounts/%s/Calls.json" account-sid)
   :headers        {"authorization" (format "Basic %s" (basic-auth-token account-sid auth-token))
                    "content-type"  "application/x-www-form-urlencoded"}
   :body           (->bbuf (form-url-encode {"Method" "GET"
                                             "To" phone-number
                                             "From" caller-id
                                             "Url" url}))})
(defn- ok? [http-status]
  (<= 200 http-status 299))

(defn- ->printable [byte-buffer]
  (try (str (.decode java.nio.charset.StandardCharsets/UTF_8 byte-buffer))
       (catch Exception _ "<unable to decode>")))

(defn- make-http-send-fn [client]
  (fn twilio-http-send [request]
    (let [response (-> (http/submit client request) async/<!!)]
      (if (-> response :status ok?)
        (logger/log :info (format "Twilio replied with status %s"
                                  (:status response)))
        (logger/log :error (format "Twilio replied with status %s, body was: %s"
                                   (:status response) (-> response :body ->printable)))))))

(defn- make-call-fn [{:keys [http-send-fn account-sid auth-token host url caller-id-registry] :as configuration}]
  (fn twilio-call-fn [notification]
    (with-exception-logging
      (if-let [caller-id (find-caller-id caller-id-registry (:phone-number notification))]
        (let [request (prepare-request (merge (select-keys configuration
                                                           [:account-sid :auth-token :host :url])
                                              {:phone-number (:phone-number notification)
                                               :caller-id    caller-id}))]
          (logger/info "Sending request to call" (:phone-number notification)
                       "using caller-id" caller-id "for process" (:process-id notification))
          (with-time-logging "Twilio http communication"
            (http-send-fn request)))
        (logger/error "Failed to make a call, unable to find caller id for"
                      (:phone-number notification) "in registry" (pr-str caller-id-registry))))))

(defn make-executor-based-notifier [configuration]
  {:pre [(contains? configuration :executor)]}
  (with-exception-logging
    (common/make-executor-based-handler
     (:executor configuration)
     (make-call-fn (merge configuration
                          {:http-send-fn (make-http-send-fn (http/create {}))})) )))
