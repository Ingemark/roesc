(ns roesc.notifier.common
  (:import java.util.concurrent.ExecutorService))

(defn make-executor-based-handler
  "Returns a function which can be used to process/deliver notifications. The
  returned function takes a collection of notifications, applies function `f`
  to each of them in parallel using the ExecutorService `executor`, and returns
  the result of every `f`."
  [^ExecutorService executor f]
  (fn [notifications]
    (doall
      (map #(.get %)
           (.invokeAll executor (map (fn [n] #(f n)) notifications))))))

(defn resolve-port [^java.net.URL url]
  (let [protocol (keyword (.getProtocol url))]
    (cond
      (not= -1 (.getPort url)) (.getPort url)
      (= protocol :https) 443
      (= protocol :http) 80
      :else (throw (Exception. (format "Unable to resolve port in URL %s" url))))))
