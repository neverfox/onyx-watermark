(defproject onyx-watermark "1.0.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                 [cheshire "5.8.0"]
                 [clj-time "0.14.2"]
                 [io.weft/gregor "0.6.0"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.onyxplatform/onyx "0.12.0"
                  :exclusions [commons-logging
                               org.slf4j/slf4j-nop]]
                 [org.onyxplatform/onyx-kafka "0.12.2.0"
                  :exclusions [commons-logging
                               org.slf4j/slf4j-log4j12]]
                 [org.onyxplatform/lib-onyx "0.12.2.0"]
                 [org.apache.kafka/kafka_2.11 "0.11.0.0"
                  :exclusions [org.slf4j/slf4j-log4j12]]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [com.taoensso/timbre "4.10.0"]]
  :main ^:skip-aot onyx-watermark.core
  :target-path "target/%s"
  :profiles {:dev {:jvm-opts     ["-XX:-OmitStackTraceInFastThrow"]
                   :global-vars  {*assert* true}
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]]}

             :uberjar {:aot          [lib-onyx.media-driver
                                      onyx-watermark.core]
                       :uberjar-name "peer.jar"
                       :global-vars  {*assert* false}}})
