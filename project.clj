(defproject roesc "0.1.0-SNAPSHOT"
  :description "RoomOrders Escalation System"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1-beta2"]
                 [org.clojure/core.async "0.4.490"]
                 [com.cognitect/http-client "0.1.87"]
                 [com.cognitect.aws/api "0.8.289"]
                 [com.cognitect.aws/endpoints "1.1.11.526"]
                 [com.cognitect.aws/sqs "697.2.391.0"]
                 [org.clojure/java.jdbc "0.7.9"]
                 [org.clojure/data.json "0.2.6"]
                 [org.postgresql/postgresql "42.2.5"]
                 [com.amazonaws/aws-lambda-java-log4j2 "1.1.0"]
                 [org.apache.logging.log4j/log4j-core "2.11.2"]
                 [org.apache.logging.log4j/log4j-api "2.11.2"]
                 [org.clojure/tools.logging "0.4.1"]
                 [environ "1.1.0"]]
  :aliases {"test" ["run" "-m" "circleci.test/dir" :project/test-paths]
            "tests" ["run" "-m" "circleci.test"]
            "retest" ["run" "-m" "circleci.test.retest"]}
  :main ^:skip-aot roesc.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[orchestra "2018.12.06-2"]
                                  [org.clojure/test.check "0.9.0"]
                                  [circleci/circleci.test "0.4.1"]]}})
