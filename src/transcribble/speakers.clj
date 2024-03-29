(ns transcribble.speakers
  (:require [clojure.string :as string]))

(def titles-re #"(Dr|Mr|Mrs|Ms)([.])?")

(def abbreviators
  {:initials
   (fn [speaker]
     (when speaker
       (let [names (string/split speaker #"\s")]
         (if (> (count names) 1)
           (->> names (map first) string/join)
           speaker))))

   :first-name-or-title
   (fn [speaker]
     (when speaker
       (let [names (string/split speaker #"\s")]
         (cond
           (= (count names) 1)
           speaker

           (re-matches titles-re (first names))
           (string/join " " [(string/replace (first names) titles-re "$1.")
                             (last names)])

           :else
           (first names)))))})

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

(defn num-speakers [config results]
  (min (count (:speakers config))
       (get-in results [:speaker_labels :speakers] 1)))

(defn reposition
  "Ensure that the person speaking first is labelled as spk_0"
  [speakers parts]
  (if (= "spk_0" (->> parts first :speaker))
    speakers
    (assoc speakers
           "spk_0" (speakers "spk_1")
           "spk_1" (speakers "spk_0"))))
