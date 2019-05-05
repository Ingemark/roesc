(ns roesc.notifier.twilio
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as logger]
            [clojure.string :refer [join]]
            [cognitect.http-client :as http]
            [roesc.util :refer [with-time-logging skipping-exceptions]])
  (:import java.util.Base64
           java.net.URLEncoder
           java.nio.ByteBuffer))

(defn- find-caller-id
  "Find caller-id which should be used for the outgoing call to `phone-number`."
  [caller-id-registry ^String phone-number]
  (let [area-codes-prefix (keys caller-id-registry)
        matching-area-code (first (filter #(.startsWith phone-number %) area-codes-prefix))]
    (get caller-id-registry (or matching-area-code "default"))))

(defn- ->bbuf [^String content]
  (ByteBuffer/wrap (.getBytes content)))

(defn- url-encode [s]
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

(defn- make-http-send-fn [client]
  (fn twilio-http-send [request]
    (let [response-status (-> (http/submit client request) async/<!! :status)]
      (logger/log (if (<= 200 response-status 299) :info :error)
                  (format "Twilio request received a response with status %s"
                          response-status)))))

(defn- make-call-fn [{:keys [http-send-fn account-sid auth-token host url caller-id-registry] :as configuration}]
  (fn twilio-call-fn [process-id phone-number]
    (if-let [caller-id (find-caller-id caller-id-registry phone-number)]
      (let [request (prepare-request (merge (select-keys configuration
                                                         [:account-sid :auth-token :host :url])
                                            {:phone-number phone-number
                                             :caller-id    caller-id}))]
        (logger/info "Sending request to call" phone-number "using caller-id" caller-id "for process" process-id)
        (with-time-logging "Twilio http communication"
          (http-send-fn request)))
      (logger/error "Failed to make a call, unable to find caller id for"
                    phone-number "in registry" (pr-str caller-id-registry)))))

(defn- make-handler [call-fn]
  (fn twilio-handler-fn [notifications]
    (logger/info "Preparing to make" (count notifications) "phone call(s)" (map :phone-number notifications))
    (->> notifications
         (map #(skipping-exceptions (future (call-fn (:process-id %) (:phone-number %)))))
         doall                    ; must do this to start all futures
         (filter future?)
         (map #(deref % 10000 :failed))
         doall)))

(defn make-notifier [configuration]
  (make-handler
   (make-call-fn (merge configuration
                        {:http-send-fn (make-http-send-fn (http/create {}))}))))
