(ns roesc.escalation-process-repository
  (:refer-clojure :exclude [update])
  (:require [clojure.tools.logging :as logger])
  (:import java.util.UUID
           java.time.Instant))

(defprotocol EscalationProcessRepository
  (-find-overdue [_ now])
  (-insert [_ process-id notifications])
  (-update [_ process-id notifications])
  (-delete [_ process-id])
  (-exists? [_ process-id]))

(defn find-overdue
  "Returns overdue process entries relative to time `now`."
  [repository ^Instant now]
  (-find-overdue repository now))

(defn insert
  "Insert an escalation process with `process-id`."
  [repository process-id notifications]
  (logger/debug "inserting the process" process-id)
  (-insert repository process-id notifications))

(defn update
  "Updates notifications for a process"
  [repository process-id notifications]
  (-update repository process-id notifications))

(defn delete
  "Deletes escalation process by `process-id`."
  [repository process-id]
  (logger/debug "deleting escalation process" process-id)
  (-delete repository process-id))

(defn exists?
  "Returns non-nil if escalation process exists."
  [repository process-id]
  (-exists? repository process-id))

