(ns roesc.activator
  "Activator periodically scans repository and activates notifiers to send
  notifications."
  (:require [clojure.tools.logging :as logger]
            [roesc.escalation-process-repository :as process-repository])
  (:import java.time.Instant))

(defn due?
  "Returns true if notification is due to be delivered."
  [^Instant now notification]
  (>= (.getEpochSecond now) (:at notification)))

(defn- find-notifier [notifier-registry notification-channel]
  (get notifier-registry
       notification-channel
       (fn unknown-channel-notifier [notifications]
         (logger/error "No notifier for notification channel" notification-channel
                       "found in notifications" notifications))))

(defn- should-be-deleted? [now entry]
  (every? #(due? now %) (:notifications entry)))

(def should-be-updated? (complement should-be-deleted?))

(defn- notifications-enriched-with-process-id [entries]
  (reduce (fn [acc entry]
            (concat acc (->> (:notifications entry)
                             (map #(assoc % :process-id (:process-id entry))))))
          []
          entries))

(defn- notifications-by-notifier [notifier-registry entries now]
  (let [notifications-by-channel (->> (notifications-enriched-with-process-id entries)
                                      (filter #(due? now %))
                                      (group-by :channel))]
    (into {}
          (for [[channel notifications] notifications-by-channel]
            [(find-notifier notifier-registry channel) notifications]))))

(defn process [repository notifier-registry entries now]
  (doseq [[notifier notifications] (notifications-by-notifier notifier-registry entries now)]
    (notifier notifications))
  (doseq [entry (->> entries (filter #(should-be-deleted? now %)))]
    (logger/info "Process for" (:process-id entry) "exhausted all notifications and completed.")
    (process-repository/delete repository (:process-id entry)))
  (doseq [entry (->> entries (filter #(should-be-updated? now %)))]
    (let [remaining-notifs (filter #(not (due? now %)) (:notifications entry))]
      (logger/info "Process" (:process-id entry) "has" (count remaining-notifs) "remaining notification(s)." )
      (process-repository/update repository (:process-id entry) remaining-notifs))))

(defn make-activator-function [repository notifier-registry]
  (fn activator []
    (logger/info "Activator started.")
    (let [now (Instant/now)]
      (process repository
               notifier-registry
               (process-repository/find-overdue repository now)
               now))
    (logger/info "Activator finished.")))
