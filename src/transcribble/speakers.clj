(ns transcribble.speakers
  (:require [clojure.string :as string]))

(defn ->speaker-label [speaker-num]
  (str "spk_" speaker-num))

(defn make-speakers-map [speakers]
  (when speakers
    (->> (string/split speakers #",")
         (map-indexed (fn [i speaker-name] [(->speaker-label i) speaker-name]))
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
      (= (count unique-initials) (count speakers)) initials
      (= (count unique-first-names) (count speakers)) first-names
      :else speakers)))

(defn label-speaker [speaker speakers]
  (get speakers speaker speaker))

(defn build-speaker-at [config results]
  (if (:speaker_labels results)
    (->> (get-in results [:speaker_labels :segments])
         (reduce (fn [acc {:keys [items]}]
                   (->> items
                        (map (fn [{:keys [start_time speaker_label]}]
                               [start_time speaker_label]))
                        (into {})
                        (merge acc)))
                 {}))
    {}))
