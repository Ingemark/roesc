(ns roesc.escalation-process-repository.postgresql-test
  (:require [roesc.escalation-process-repository :as repository]
            [roesc.escalation-process-repository.postgresql :as postgresql]
            [clojure.test :as t]
            [clojure.java.jdbc :as jdbc]
            [roesc.config :as config]
            [roesc.spec]
            [orchestra.spec.test])
  (:import java.time.Instant))

(defn with-instrumentation [f]
  (orchestra.spec.test/instrument)
  (f))

(t/use-fixtures :once with-instrumentation)

(t/deftest ^:integration operations
  (t/testing "inserting records"
    (jdbc/with-db-connection [db config/db-spec]
      (let [r (postgresql/make-repository db)
            process-id (str (java.util.UUID/randomUUID))]
        (repository/insert r process-id [{:at 1 :channel "phone" :phone-number "1"}])
        (t/is (repository/exists? r process-id)))))
  (t/testing "overdue notifications"
    (jdbc/with-db-connection [db config/db-spec]
      (let [r (postgresql/make-repository db)]
        (jdbc/delete! db :process nil)
        (repository/insert r "1" [{:at 1 :channel "phone" :phone-number "1"}])
        (repository/insert r "2" [{:at 2 :channel "phone" :phone-number "1"}])
        (repository/insert r "3" [{:at 3 :channel "phone" :phone-number "1"}])
        (t/is (= 2 (count (repository/find-overdue r (Instant/ofEpochSecond 2))))))))
  (t/testing "updating records"
    (jdbc/with-db-connection [db config/db-spec]
      (let [r (postgresql/make-repository db)
            process-id (str (java.util.UUID/randomUUID))]
        (repository/insert r process-id [{:at 1 :channel "phone" :phone-number "1"}
                                         {:at 2 :channel "phone" :phone-number "3"}])
        (repository/update r process-id [{:at 3 :channel "phone" :phone-number "3"}])
        (let [result (jdbc/query (:connection r)
                                 ["select at from process where id=?" process-id])]
          (t/is (= 1 (count result)))
          (t/is (= {:at 3} (first result))))))))
