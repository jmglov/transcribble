(ns transcribble.core
  (:require [clojure.string :as string]
            [transcribble.format :as format]
            [transcribble.speakers :as speakers]
            [transcribble.util :refer [load-json-file]]))

(defn load-config [config-filename]
  (let [config (merge {:formatter :otr
                       :split-duration-secs 30}
                      (when config-filename (load-json-file config-filename)))]
    (-> config
        (update :formatter keyword)
        (update :remove-fillers set))))

(defn ->float [time-str]
  (try (Float. time-str)
       (catch Exception _ 0.0)))

(defn punctuation? [pronunciation]
  (= "punctuation" (:type pronunciation)))

(defn first-start-time [pronunciations]
  (->> pronunciations
       (drop-while (complement :start_time))
       first
       :start_time
       ->float))

(defn ->word [pronunciation]
  (-> pronunciation :alternatives first :content))

(defn update-word [pronunciation f]
  (update-in pronunciation [:alternatives 0 :content] f))

(defn ->sentence [acc pronunciation]
  (let [delimiter (if (or (nil? acc) (punctuation? pronunciation)) "" " ")]
    (str acc delimiter (->word pronunciation))))

(defn remove-fillers-reducer [config]
  (let [filler? (comp (->> (:remove-fillers config) (map string/lower-case) set)
                      string/lower-case)]
    (fn [{:keys [prev] :as acc} pronunciation]
      (let [conj-pronunciation
            (cond
              (filler? (->word pronunciation))
              identity

              (and (punctuation? pronunciation)
                   (filler? (->word prev)))
              identity

              :else
              #(update % :pronunciations conj pronunciation))]
        (-> acc
            conj-pronunciation
            (assoc :prev pronunciation))))))

(defn remove-fillers [config pronunciations]
  (->> pronunciations
       (reduce (remove-fillers-reducer config)
               {:pronunciations []
                :prev nil})
       :pronunciations))

(defn apply-replacements [config word]
  (let [replacements (map (fn [[match replacement]] [(re-pattern (name match)) replacement])
                          (:replace config))]
    (reduce (fn [acc [match replacement]] (string/replace acc match replacement))
            word replacements)))

(defn fixup-word-reducer [config]
  (let [capitalise? (comp (->> (:capitalise config) (map string/lower-case) set)
                          string/lower-case)
        downcase? (comp (->> (:downcase config) (map string/lower-case) set)
                        string/lower-case)]
    (fn [{:keys [prev] :as acc} pronunciation]
      (let [update-fn
            (comp (partial apply-replacements config)
                  (cond
                    (nil? prev)
                    string/capitalize

                    (capitalise? (->word pronunciation))
                    string/capitalize

                    (downcase? (->word pronunciation))
                    string/lower-case

                    :else
                    identity))]
        (-> acc
            (update :pronunciations conj (update-word pronunciation update-fn))
            (assoc :prev pronunciation))))))

(defn fixup-word [config pronunciations]
  (->> pronunciations
       (reduce (fixup-word-reducer config)
               {:pronunciations []
                :prev nil})
       :pronunciations))

(defn process-sentences [config sentences]
  (->> sentences
       (map (comp (partial fixup-word config)
                  (partial remove-fillers config)))))

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
       (partition-by #(and (punctuation? %)
                           (= "." (->word %))))
       (partition-all 2)
       (map (partial apply concat))))

(defn partition-durations [{:keys[ split-duration-secs] :as config}
                           start-time pronunciations]
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
         (partition-by time-chunk))))

(defn partition-speakers [config speaker-at pronunciations]
  (println "Splitting by speaker")
  (->> pronunciations
       (reduce (fn [{:keys [current-speaker pronunciations] :as acc} p]
                 (let [speaker (or (speaker-at (:start_time p)) current-speaker)]
                   {:current-speaker speaker
                    :pronunciations (conj pronunciations (assoc p :speaker speaker))}))
               {:current-speaker nil
                :pronunciations []})
       :pronunciations
       (partition-by :speaker)
       (map partition-sentences)))

(defn ->part [config pronunciations]
  (let [pronunciations pronunciations]
    {:speaker (:speaker (first pronunciations))
     :start-time (:start_time (first pronunciations))
     :words (reduce ->sentence nil pronunciations)}))

(defn load-transcribe-json [config filename]
  (let [{:keys [results]} (load-json-file filename)
        speaker-at (speakers/build-speaker-at config results)
        pronunciations (:items results)
        split (if (> (count (:speakers config)) 1)
                (partial partition-speakers config speaker-at)
                (partial partition-durations config (first-start-time pronunciations)))]
    (->> pronunciations
         split
         (map (comp (partial ->part config)
                    (partial apply concat)
                    (partial process-sentences config))))))
