(ns transcribble.speakers
  (:require [clojure.string :as string]))

(def abbreviators
  {:initials
   (fn [speaker]
     (let [names (string/split speaker #"\s")]
       (if (> (count names) 1)
         (->> names (map first) string/join)
         speaker)))

   :first-name-or-title
   (fn [speaker]
     (let [names (string/split speaker #"\s")]
       (cond
         (= (count names) 1) speaker
         (= "Dr." (first names)) (string/join " " [(first names) (last names)])
         :else (first names))))})

(defn ->speaker-label [speaker-num]
  (str "spk_" speaker-num))

(defn make-speakers-map [speakers]
  (when speakers
    (->> (string/split speakers #",")
         (map-indexed (fn [i speaker-name] [(->speaker-label i) speaker-name]))
         (into {}))))

(defn abbreviate [abbreviator speakers]
  (let [abbreviator-fn (-> abbreviator (or :first-name-or-title) keyword abbreviators)
        abbreviations (->> speakers
                           (map (fn [[label speaker]] [label (abbreviator-fn speaker)]))
                           (into {}))
        unique-abbreviations (set (vals abbreviations))]
    (if (= (count unique-abbreviations) (count speakers))
      abbreviations
      speakers)))

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
