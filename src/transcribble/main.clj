(ns transcribble.main
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [cheshire.core :as json]))

(defn load-json-file [filename]
  (with-open [f (io/reader filename)]
    (json/parse-stream f true)))

(defn append-punctuation [words punctuation]
  (if (empty? words)
    (conj words punctuation)
    (let [words-reversed (reverse words)
          last-word (str (first words-reversed) punctuation)]
      (-> (cons last-word (drop 1 words-reversed))
          reverse
          vec))))

(defn ->timestamp [seconds-str]
  (let [seconds-num (Float. seconds-str)
        hours (int (/ seconds-num 3600))
        minutes (int (/ seconds-num 60))
        seconds (int (mod seconds-num 60))]
    (->> [hours minutes seconds]
         (filter pos?)
         (map (partial format "%02d"))
         (string/join ":"))))

(defn finalise-part [part]
  (update part :words #(string/join " " %)))

(defn speaker-splitter [speaker-at]
  (fn [[parts current-part] {:keys [type alternatives start_time]}]
    (let [word (-> alternatives first :content)
          speaker (speaker-at start_time)
          current-part (if (or (:start-time current-part) (nil? start_time))
                         current-part
                         (assoc current-part :start-time (->timestamp start_time)))]
      (cond
        (= "punctuation" type)
        [parts (update current-part :words append-punctuation word)]

        (or (= (:speaker current-part) speaker) (empty? (:words current-part)))
        [parts (update current-part :words conj word)]

        :else
        [(conj parts (finalise-part current-part))
         {:speaker speaker, :words [word]}]))))

(defn load-transcribe-json [filename speakers]
  (let [{:keys [results]} (load-json-file filename)
        speaker-at (->> (get-in results [:speaker_labels :segments])
                        (reduce (fn [acc {:keys [items]}]
                                  (->> items
                                       (map (fn [{:keys [start_time speaker_label]}]
                                              [start_time (get speakers speaker_label speaker_label)]))
                                       (into {})
                                       (merge acc)))
                                {}))
        pronunciations (:items results)
        [parts last-part] (->> pronunciations
                               (reduce (speaker-splitter speaker-at)
                                       [[] {:words []}]))]
    (conj parts (finalise-part last-part))))
