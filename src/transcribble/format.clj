(ns transcribble.format
  (:require [clojure.string :as string]
            [cheshire.core :as json]
            [hiccup.core :as hiccup]
            [transcribble.speakers :as speakers]))

(defn ->timestamp [seconds-str]
  (let [seconds-num (Float. seconds-str)
        hours (int (/ seconds-num 3600))
        minutes (int (/ seconds-num 60))
        seconds (int (mod seconds-num 60))]
    (->> [(when (pos? hours) hours) minutes seconds]
         (filter identity)
         (map (partial format "%02d"))
         (string/join ":"))))

(defn otr-reducer [acc {:keys [start-time speaker words]}]
  (-> acc
      (conj [:p
             [:span {:class "timestamp" :data-timestamp start-time}
              (->timestamp start-time)]
             (str speaker ": " words)])
      (conj [:br])))

(def formatters
  {:plaintext
   (fn [_ data]
                (->> data
                     (map (fn [{:keys [start-time speaker words]}]
                            (str (->timestamp start-time) "\n\n" speaker ": " words)))
                     (string/join "\n\n")))

   :otr
   (fn [media-file data]
          (let [text (->> data
                          (reduce otr-reducer [])
                          (map (fn [el] (hiccup/html el)))
                          (string/join "\n"))]
            (-> {:text text
                 :media media-file
                 :media-time 0.0}
                (json/generate-string {:pretty true}))))})

(defn remove-fillers [filler-words part]
  (let [fillers (str "(?:" (string/join "|" filler-words) ")")
        starting-pattern (re-pattern (str "(?i)(^|[.]\\s+)" fillers ", (.)"))
        comma-pattern (re-pattern (str "(?i),?\\s+" fillers ",?"))]
    (update part :words
            (fn [words]
              (-> words
                  (string/replace starting-pattern (fn [[_ punc first-letter]]
                                                     (str punc (string/upper-case first-letter))))
                  (string/replace comma-pattern ""))))))

(defn format-data [{:keys [abbreviate-after formatter] :as config}
                   media-filename speakers data]
  (let [num-speakers (->> data
                          (group-by :speaker)
                          (filter (fn [[speaker _]] speaker))
                          count)
        abbreviated-speakers (speakers/abbreviate speakers)
        label-speakers (fn [acc part]
                         (let [abbreviate? (and abbreviate-after
                                                (>= (count acc) (* abbreviate-after num-speakers)))]
                           (conj acc
                                 (update part :speaker
                                         #(speakers/label-speaker % (if abbreviate? abbreviated-speakers speakers))))))
        format-fn (formatters formatter)]
    (when-not format-fn
      (throw (ex-info "Invalid formatter" {:transcribble/formatter formatter})))
    (->> data
         (drop-while #(nil? (:speaker %)))
         (reduce label-speakers [])
         (map (partial remove-fillers (:remove-fillers config)))
         (format-fn media-filename))))
