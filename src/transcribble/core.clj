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

(defn append-punctuation [words punctuation]
  (if (empty? words)
    (conj words punctuation)
    (let [words-reversed (reverse words)
          last-word (str (first words-reversed) punctuation)]
      (-> (cons last-word (drop 1 words-reversed))
          reverse
          vec))))

(defn finalise-part [part]
  (update part :words #(string/join " " %)))

(defn split-parts [{:keys [split-duration-secs speakers] :as config}
                   speaker-at pronunciations]
  (println "Number of speakers:" (count speakers))
  (println "Splitting every" split-duration-secs "seconds")
  (let [->float
        #(try (Float. %) (catch Exception _ 0.0))

        within-split-duration?
        (fn [{:keys [start-time]} pronunciation]
          (if (:start_time pronunciation)
            (< (- (->float (:start_time pronunciation)) start-time)
               split-duration-secs)
            true))

        splitter
        (fn [[parts current-part following-punctuation?]
             {:keys [type alternatives start_time] :as pronunciation}]
          (let [word (-> alternatives first :content)
                current-part (if (or (:start-time current-part) (nil? start_time))
                               current-part
                               (do
                                 (println "Setting start time to" start_time)
                                 (assoc current-part :start-time (->float start_time))))]
            (cond
              (= "punctuation" type)
              [parts (update current-part :words append-punctuation word) true]

              (empty? (:words current-part))
              [parts (update current-part :words conj word) false]

              (and (= (count speakers) 1)
                   (not following-punctuation?))
              [parts (update current-part :words conj word) false]

              (and (= (count speakers) 1)
                   (within-split-duration? current-part pronunciation))
              [parts (update current-part :words conj word) false]

              (and (> (count speakers) 1)
                   (= (:speaker current-part) (speaker-at (:start_time pronunciation))))
              [parts (update current-part :words conj word) false]

              :else
              [(conj parts (finalise-part current-part))
               {:speaker (speaker-at start_time)
                :start-time (->float start_time)
                :words [word]}
               false])))]
    (reduce splitter
            [[] {:words []} false]
            pronunciations)))

(defn load-transcribe-json [config filename]
  (let [{:keys [results]} (load-json-file filename)
        speaker-at (speakers/build-speaker-at config results)
        pronunciations (:items results)
        [parts last-part] (split-parts config speaker-at pronunciations)
        all-parts (conj parts (finalise-part last-part))]
    (if (= 1 (count all-parts))
      [(assoc (first all-parts) :speaker (speakers/->speaker-label 0))]
      all-parts)))
