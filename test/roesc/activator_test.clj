(ns roesc.activator-test
  (:require [roesc.activator :as sut]
            [clojure.test :as t :refer [deftest testing are]])
  (:import java.time.Instant))

(deftest deleting-and-updating-logic
  (testing "criteria for update of entries in db"
    (let [entry {:notifications [{:at 1} {:at 2} {:at 3}]}]
      (are [now should-delete?]
        (= (sut/should-be-deleted? (Instant/ofEpochSecond now) entry)
           should-delete?)
        0 false
        1 false
        2 false
        3 true
        4 true))))
