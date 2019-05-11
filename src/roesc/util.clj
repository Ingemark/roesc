(ns roesc.util
  (:require [clojure.tools.logging :as logger])
  (:import (java.time ZonedDateTime ZoneId Instant)))

(defmacro skipping-exceptions [& body]
  `(try ~@body (catch Exception e# (logger/error "skipping exception:" e#))))

(defmacro skipping-exceptions-but-with-sleep [delay-millis & body]
  `(try ~@body (catch Exception e#
                 (logger/error "skipping exception:" e#)
                 (Thread/sleep ~delay-millis))))

(defmacro with-exception-logging [& body]
  `(try ~@body (catch Exception e# (logger/error e#))))

(defmacro with-time-logging [name & body]
  `(let [start-time# (System/currentTimeMillis)]
     ~@body
     (logger/info ~name "took" (- (System/currentTimeMillis) start-time#) "ms")))
