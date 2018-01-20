(ns onyx-watermark.core
  (:require
   [onyx-watermark.impl :as impl]))

(def workflow
  [[:read-traffic :xform-traffic]])

(defn catalog [kafka-zookeeper traffic-topic num-partitions]
  [{:onyx/name                 :read-traffic
    :onyx/type                 :input
    :onyx/medium               :kafka
    :onyx/plugin               :onyx.plugin.kafka/read-messages
    :onyx/n-peers              num-partitions
    :onyx/batch-size           50
    :kafka/zookeeper           kafka-zookeeper
    :kafka/topic               traffic-topic
    :kafka/key-deserializer-fn ::impl/deserialize-key-string
    :kafka/deserializer-fn     ::impl/deserialize-message-json
    :kafka/wrap-with-metadata? true
    :kafka/offset-reset        :earliest
    :onyx/assign-watermark-fn  ::impl/watermark-fn}

   {:onyx/name       :xform-traffic
    :onyx/type       :output
    :onyx/medium     :function
    :onyx/plugin     :onyx.peer.function/function
    :onyx/n-peers    1
    :onyx/batch-size 50
    :onyx/fn         ::impl/xform-traffic}])

(def lifecycles
  [{:lifecycle/task  :read-traffic
    :lifecycle/calls :onyx.plugin.kafka/read-messages-calls}])

;; The specified aggregation performs each of the commands and maintains
;; exactly-once semantics on the window.
(def windows
  [{:window/id          :count-traffic
    :window/task        :xform-traffic
    :window/type        :sliding
    :window/aggregation ::impl/count-traffic
    :window/window-key  :ts
    :window/range       [5 :minutes]
    :window/slide       [1 :minute]}])

;; Periodically send window data to the materialized view to be updated.
(def triggers
  [{:trigger/id            :sync-traffic
    :trigger/window-id     :count-traffic
    :trigger/on            :onyx.triggers/watermark
    :trigger/state-context [:window-state]
    :trigger/post-evictor  [:all]
    :trigger/sync          ::impl/sync-traffic!}
   {:trigger/id            :no-op
    :trigger/window-id     :count-traffic
    :trigger/on            :onyx.triggers/punctuation
    :trigger/pred          ::impl/trigger-pred
    :trigger/post-evictor  [:none]
    :trigger/sync          ::impl/no-op}])
