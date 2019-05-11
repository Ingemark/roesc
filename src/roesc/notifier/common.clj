(ns roesc.notifier.common
  (:import java.util.concurrent.ExecutorService))

(defn make-executor-based-handler [^ExecutorService executor f]
  (fn [notifications]
    (doseq [future (.invokeAll executor (map (fn [n] #(f n)) notifications))]
      (.get future))))
