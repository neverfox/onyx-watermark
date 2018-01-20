(ns onyx-watermark.impl
  (:require
   [cheshire.core :as json]
   [clj-time.coerce :as c]
   [clojure.tools.logging :as log]))

;; We don't want to use the timestamp of the kafka message
;; Instead we want to assign it from the message itself
(defn watermark-fn [s]
  (let [w (c/to-long (get-in s [:message :ts]))]
    (log/debug "Watermark on input:" w)
    w))

(defn deserialize-key-string [bytes]
  (String. bytes "UTF-8"))

(defn deserialize-message-json [bytes]
  (try
    (json/parse-string (String. bytes "UTF-8") true)
    (catch Exception e
      {:error e})))

(defn xform-traffic
  [{:keys [key message]}]
  (-> message
      (assoc :id key)
      (update :ts c/to-date)))

(defn count-init-fn [_]
  {})

(defn count-create-fn [_ segment]
  [:merge {(:id segment) 1}])

(defn count-apply-fn [_ state [changelog-type value]]
  (case changelog-type
    :merge (merge-with + state value)))

(def count-traffic
  {:aggregation/init                count-init-fn
   :aggregation/create-state-update count-create-fn
   :aggregation/apply-state-update  count-apply-fn})

(defn trigger-pred [trigger state-event]
  (case (:event-type state-event)
    :new-segment (log/debug "New segment:" (:segment state-event))
    :watermark   (log/debug "Watermarks:" (:watermarks state-event))
    nil)
  true)

(defn sync-traffic!
  [event window trigger {:keys [lower-bound upper-bound] :as window-data} state]
  (log/info (format "Window extent [%s - %s] contents: %s" lower-bound upper-bound state)))

(def no-op (constantly nil))
