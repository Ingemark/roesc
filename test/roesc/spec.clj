(ns roesc.spec
  (:require [clojure.test :as t]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as string]
            [roesc.escalation-process-repository :as process-repository]
            [roesc.initiator :as initiator])
  (:import (java.time ZonedDateTime ZoneId Instant)
           java.util.concurrent.ExecutorService))

(s/def :common/non-empty-string
  (s/with-gen
    (s/and string? (complement string/blank?))
    #(gen/not-empty (gen/string-alphanumeric))))

(s/def :common/instant (s/with-gen
                         #(instance? Instant %)
                         (fn [] (gen/fmap #(Instant/ofEpochSecond %)
                                         (s/gen (s/int-in (.getEpochSecond Instant/MIN)
                                                          (.getEpochSecond Instant/MAX)))))))

(s/def :common/email
  (s/with-gen
    (s/and :common/non-empty-string #(re-matches #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$" %))
    (fn [] (gen/fmap (fn [[username subdomain]]
                      (str username "@" subdomain ".com"))
                    (s/gen (s/tuple :common/non-empty-string
                                    :common/non-empty-string))))))

(s/def :common/phone-number             :common/non-empty-string)
(s/def :notification/channel            initiator/supported-channels)
(s/def :notification/at                 pos-int?)
(s/def :roesc/process-id                :common/non-empty-string)
(s/def :roesc.request/notification      (s/or :phone (s/keys :req-un [:notification/at
                                                                      :notification/channel
                                                                      :common/phone-number])
                                              :email (s/keys :req-un [:notification/at
                                                                      :notification/channel
                                                                      :common/email])))
(s/def :roesc.request/notifications     (s/coll-of :roesc.request/notification :min-count 1))
(s/def :roesc.request/action            #{"start" "stop"})
(s/def :roesc.request/start-request     (s/keys :req-un [:roesc/process-id
                                                         :roesc.request/action
                                                         :roesc.request/notifications]))
(s/def :roesc.request/stop-request      (s/keys :req-un [:roesc/process-id
                                                         :roesc.request/action]))
(s/def :roesc/request                   (s/or :start :roesc.request/start-request
                                              :stop :roesc.request/stop-request))

(s/def :roesc/notifier-fn               (s/with-gen ifn? #(s/gen #{(fn [n])})))

(s/def :roesc/notifier-registry         (s/map-of :notification/channel :roesc/notifier-fn))

(s/def :roesc.activator/entry           (s/keys :req-un [:roesc/process-id
                                                         :roesc.request/notifications]))

(s/def :roesc.notifier.twilio/caller-id-registry (s/map-of string? :common/phone-number))

(s/def :roesc.escalation-process-repository/repository #(satisfies? process-repository/EscalationProcessRepository %))

(s/fdef :roesc.initiator/process-request
  :args (s/cat :repository :roesc.escalation-process-repository/repository
               :request :roesc/request))

(s/fdef :roesc.initiator/valid?
  :args (s/cat :request :roesc/request)
  :ret boolean?)

(s/fdef :roesc.escalation-process-repository/find-overdue
  :args (s/cat :repository :roesc.escalation-process-repository/repository)
  :ret (s/coll-of :roesc.activator/entry))

(s/fdef roesc.activator/find-notifier
  :args (s/cat :notifier-registry :roesc/notifier-registry
               :channel :notification/channel)
  :ret :roesc/notifier-fn)

(s/fdef roesc.activator/notifications-by-notifier
  :args (s/cat :notifier-registry :roesc/notifier-registry
               :entries (s/coll-of :roesc.activator/entry)
               :now :common/instant)
  :ret (s/map-of ifn? (s/coll-of :roesc.request/notification :min-count 1)))

(s/fdef roesc.activator/process
  :args (s/cat :executor #(instance? ExecutorService %)
               :repository :roesc.escalation-process-repository/repository
               :notifier-registry :roesc/notifier-registry
               :entries (s/coll-of :roesc.activator/entry)
               :now :common/instant))

(s/fdef roesc.notifier.twilio/find-caller-id
  :args (s/cat :caller-id-registry :roesc.notifier.twilio/caller-id-registry
               :phone-number :common/phone-number)
  :ret :common/phone-number)
