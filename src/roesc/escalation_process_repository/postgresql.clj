(ns roesc.escalation-process-repository.postgresql
  (:require [roesc.escalation-process-repository :as process-repository]
            [clojure.java.jdbc :as jdbc]
            [clojure.edn :as edn]
            [clojure.set :refer [rename-keys]])
  (:import java.time.Instant))

(defrecord PostgresqlEscalationProcessRepository [connection])

(defn make-repository [connection]
  (PostgresqlEscalationProcessRepository. connection))

(defn- serialize [notifications]
  (pr-str notifications))

(defn- deserialize [notifications]
  (edn/read-string notifications))

(defn- make-db-entry [id notifications]
  {:id id
   :at (->> notifications (map :at) sort first)
   :notifications (serialize notifications)})

(extend-type PostgresqlEscalationProcessRepository
  process-repository/EscalationProcessRepository
  (-find-overdue [repository ^Instant now]
    (->> (jdbc/query (:connection repository)
                     ["select * from process where at<=? order by at asc" (.getEpochSecond now)])
         (map #(update-in % [:notifications] deserialize))
         (map #(rename-keys % {:id :process-id}))))
  (-insert [repository process-id notifications]
    (jdbc/insert! (:connection repository) :process
                  (make-db-entry process-id notifications)))
  (-update [repository process-id notifications]
    (jdbc/update! (:connection repository) :process
                  (-> (make-db-entry process-id notifications)
                      (select-keys [:at :notifications]))
                  ["id=?" process-id]))
  (-delete [repository process-id]
    (jdbc/delete! (:connection repository) :process ["id=?" process-id]))
  (-exists? [repository process-id]
    (seq (jdbc/query (:connection repository)
                     ["select id from process where id=?" process-id]))))
