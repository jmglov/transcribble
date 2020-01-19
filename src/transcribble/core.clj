(ns transcribble.core
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [cheshire.core :as json]
            [transcribble.format :as format]
            [transcribble.speakers :as speakers]))

(defn load-json-file [filename]
  (with-open [f (io/reader filename)]
    (json/parse-stream f true)))

(defn load-config [config-filename]
  (let [config (merge {:formatter :otr
                       :remove-fillers #{}}
                      (when config-filename (load-json-file config-filename)))]
    (-> config
        (update :formatter keyword)
        (update :remove-fillers set))))

(defn ->float [time-str]
  (try (Float. time-str)
       (catch Exception _ 0.0)))

(defn first-start-time [pronunciations]
  (->> pronunciations
       (drop-while (complement :start_time))
       first
       :start_time
       ->float))

(defn ->word [pronunciation]
  (-> pronunciation :alternatives first :content))

(defn ->sentence [acc pronunciation]
  (let [delimiter (if (or (nil? acc) (= "punctuation" (:type pronunciation))) "" " ")]
    (str acc delimiter (->word pronunciation))))

(defn ->part [pronunciations]
  {:speaker (:speaker (first pronunciations))
   :start-time (:start_time (first pronunciations))
   :words (reduce ->sentence nil pronunciations)})

(defn fixup-punctuation-timestamps [{:keys [last-end-time] :as acc}
                                    {:keys [end_time] :as pronunciation}]
  (if end_time
    (-> acc
        (update :pronunciations conj pronunciation)
        (assoc :last-end-time end_time))
    (update acc :pronunciations
            conj (-> pronunciation
                     (assoc :start_time (str last-end-time))
                     (assoc :end_time (str last-end-time))))))

(defn partition-sentences [pronunciations]
  (->> pronunciations
       (partition-by #(and (= "punctuation" (:type %))
                           (= "." (->word %))))
       (partition-all 2)
       (map (partial apply concat))))

(defn partition-durations [start-time split-duration-secs pronunciations]
  (println "Splitting by duration")
  (let [time-chunk (comp int
                         #(/ (- % start-time) split-duration-secs)
                         ->float
                         :start_time
                         first)]
    (->> pronunciations
         (reduce fixup-punctuation-timestamps
                 {:pronunciations []
                  :last-end-time start-time})
         :pronunciations
         partition-sentences
         (partition-by time-chunk)
         (map (partial apply concat)))))

(defn partition-speakers [speaker-at pronunciations]
  (println "Splitting by speaker")
  (->> pronunciations
       (reduce (fn [{:keys [current-speaker pronunciations] :as acc} p]
                 (let [speaker (or (speaker-at (:start_time p)) current-speaker)]
                   {:current-speaker speaker
                    :pronunciations (conj pronunciations (assoc p :speaker speaker))}))
               {:current-speaker nil
                :pronunciations []})
       :pronunciations
       (partition-by :speaker)))

(defn load-transcribe-json [config filename]
  (let [{:keys [results]} (load-json-file filename)
        speaker-at (speakers/build-speaker-at config results)
        pronunciations (:items results)
        split (if (> (count (:speakers config)) 1)
                (partial partition-speakers speaker-at)
                (partial partition-durations
                         (first-start-time pronunciations)
                         (:split-duration-secs config)))]
    (->> pronunciations
         split
         (map ->part))))
