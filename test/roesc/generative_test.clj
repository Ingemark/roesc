(ns roesc.generative-test
  (:require [roesc.activator :as activator]
            [roesc.initiator :as initiator]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest testing is]]))

(def num-tests 100)

(deftest generative-activator-tests
  (testing "generative tests of activator functions"
    (let [check (fn [sym]
              (let [result (first (stest/check
                                   sym
                                   {:clojure.spec.test.check/opts {:num-tests num-tests}
                                    :assert-checkable true}))]
                (if (:failure result)
                  (throw (:failure result))
                  true)))]
      (is (check `activator/find-notifier))
      (is (check `activator/notifications-by-notifier))
      (is (check `initiator/valid?)))))
