(ns transcribble.speakers
  (:require [clojure.string :as string]))

(defn make-speakers-map [speakers]
  (when speakers
    (->> (string/split speakers #",")
         (map-indexed (fn [i speaker-name] [(str "spk_" i) speaker-name]))
         (into {}))))

(defn ->initials [speaker]
  (let [names (string/split speaker #"\s")]
    (if (> (count names) 1)
      (->> names (map first) string/join)
      speaker)))

(defn abbreviate [speakers]
  (let [initials (->> speakers
                      (map (fn [[label speaker]] [label (->initials speaker)]))
                      (into {}))
        unique-initials (set (vals initials))
        first-names (->> speakers
                         (map (fn [[label speaker]] [label (first (string/split speaker #"\s"))]))
                         (into {}))
        unique-first-names (set (vals first-names))]
    (cond
      (> (count unique-initials) 1) initials
      (> (count unique-first-names) 1) first-names
      :else speakers)))

(defn label-speaker [speaker speakers]
  (get speakers speaker speaker))
