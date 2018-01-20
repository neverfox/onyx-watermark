(ns onyx-watermark.core-test
  (:require
   [cheshire.core :as json]
   [clj-time.coerce :as c]
   [clj-time.core :as t]
   [clojure.core.async :as a]
   [clojure.test :refer :all]
   [gregor.core :as g]
   [onyx.api]
   [onyx.plugin.kafka]
   [onyx.test-helper :refer [with-test-env load-config]]
   [onyx-watermark.core :refer :all]
   [onyx-watermark.impl]
   [taoensso.timbre :as log])
  (:import
   (java.util UUID)
   (kafka.common TopicAlreadyMarkedForDeletionException)
   (org.apache.kafka.common.errors TopicExistsException)
   (org.apache.kafka.common.errors UnknownTopicOrPartitionException)))

(def docker-ip "127.0.0.1")

(def kafka-zookeeper (format "%s:2181" docker-ip))

(def kafka-brokers (format "%s:9092" docker-ip))

;; change this to > 1 to see watermark trigger failure
(def num-partitions 2)

(defn make-topic! [zk topic-name num-partitions]
  (try
    (g/create-topic {:connection-string zk} topic-name {:partitions num-partitions})
    (catch TopicExistsException e)))

(defn delete-topic! [zk topic-name]
  (try
    (g/delete-topic {:connection-string zk} topic-name)
    (catch TopicAlreadyMarkedForDeletionException e)
    (catch UnknownTopicOrPartitionException e)))

(def traffic
  [["d2c600c2-0656-53fb-a185-fa2040f2a019"
    {:ts "20180119T044848.475Z"}]
   ["d2c600c2-0656-53fb-a185-fa2040f2a019"
    {:ts "20180119T044948.475Z"}]
   ["d2c600c2-0656-53fb-a185-fa2040f2a019"
    {:ts "20180119T045048.475Z"}]
   ["d2c600c2-0656-53fb-a185-fa2040f2a019"
    {:ts "20180119T045148.475Z"}]
   ["d2c600c2-0656-53fb-a185-fa2040f2a019"
    {:ts "20180119T045248.475Z"}]])

(defn throttle [f time]
  (let [c (a/chan 100)]
    (a/go-loop []
      (when-let [v (a/<! c)]
        (f v)
        (a/<! (a/timeout time))
        (recur)))
    (fn [coll]
      (a/onto-chan c coll))))

;; To better simulate a real job, make messages arrive over some time
(defn write-traffic! [brokers topic traffic-sequence]
  (let [p (g/producer brokers)
        c (a/chan 100)]
    (a/go
      (loop []
        (when-let [[k v] (a/<! c)]
          (g/send p topic k (json/generate-string v))
          (a/<! (a/timeout 5000))
          (recur)))
      (.close p))
    (a/onto-chan c traffic-sequence)))

(deftest traffic-test
  (testing "Test a sequence of traffic events"
    (let [traffic-topic (str "traffic-" (UUID/randomUUID))
          tenancy-id    (UUID/randomUUID)
          config        (load-config "dev-config.edn")
          env-config    (assoc (:env-config config) :onyx/tenancy-id tenancy-id)
          peer-config   (assoc (:peer-config config) :onyx/tenancy-id tenancy-id)
          job           {:workflow       workflow
                         :catalog        (catalog kafka-zookeeper traffic-topic num-partitions)
                         :lifecycles     lifecycles
                         :windows        windows
                         :triggers       triggers
                         :task-scheduler :onyx.task-scheduler/balanced}]

      (make-topic! kafka-zookeeper traffic-topic num-partitions)

      (with-test-env [test-env [(inc num-partitions) env-config peer-config]]
        (onyx.api/submit-job peer-config job)
        (Thread/sleep 5000)
        (write-traffic! kafka-brokers traffic-topic traffic)
        (Thread/sleep (* 6 5000))

        (delete-topic! kafka-zookeeper traffic-topic)))))
