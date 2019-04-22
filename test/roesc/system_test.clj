(ns roesc.system-test
  (:require  [clojure.test :as t]
             [roesc.core :as core]
             [roesc.spec]
             [orchestra.spec.test :as st]))

(t/deftest ^:integration system-test
  (t/testing "running the component"
    (st/instrument)
    (core/run)
    (t/is true)))
