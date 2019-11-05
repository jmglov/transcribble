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

(defn speaker-splitter [speaker-at]
  (fn [[parts current-part] {:keys [type alternatives start_time]}]
    (let [word (-> alternatives first :content)
          speaker (speaker-at start_time)
          current-part (if (or (:start-time current-part) (nil? start_time))
                         current-part
                         (assoc current-part :start-time start_time))]
      (cond
        (= "punctuation" type)
        [parts (update current-part :words append-punctuation word)]

        (or (= (:speaker current-part) speaker) (empty? (:words current-part)))
        [parts (update current-part :words conj word)]

        :else
        [(conj parts (finalise-part current-part))
         {:speaker speaker, :words [word]}]))))

(defn load-transcribe-json [config filename]
  (let [{:keys [results]} (load-json-file filename)
        speaker-at (speakers/build-speaker-at config results)
        pronunciations (:items results)
        [parts last-part] (->> pronunciations
                               (reduce (speaker-splitter speaker-at)
                                       [[] {:words []}]))
        all-parts (conj parts (finalise-part last-part))]
    (if (= 1 (count all-parts))
      [(assoc (first all-parts) :speaker (speakers/->speaker-label 0))]
      all-parts)))
