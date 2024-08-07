(ns transcribble.format
  (:require [clojure.string :as string]
            [cheshire.core :as json]
            [hiccup.core :as hiccup]
            [transcribble.speakers :as speakers]
            [transcribble.util :refer [load-json-file]]))

(defn ->timestamp [seconds-str]
  (let [seconds-num (Float. seconds-str)
        hours (int (/ seconds-num 3600))
        minutes (int (mod (/ seconds-num 60) 60))
        seconds (int (mod seconds-num 60))]
    (->> [(when (pos? hours) hours) minutes seconds]
         (filter identity)
         (map (partial format "%02d"))
         (string/join ":"))))

(defn otr-reducer [acc {:keys [start-time speaker words] :as section}]
  (try
    (-> acc
        (conj [:p
               [:span {:class "timestamp" :data-timestamp start-time}
                (->timestamp start-time)]
               (if speaker
                 (format "<b>%s</b>: %s" speaker words)
                 words)])
        (conj [:br]))
    (catch Exception e
      (println "Failed to format" (pr-str section) (class e) (.getMessage e))
      (println "Last successful section" (->> acc
                                              reverse
                                              (drop 1)
                                              first))
      (throw e))))

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
           (json/generate-string {:pretty true}))))

   :paragraphs
   (fn [_ data]
     (->> data
          (map (fn [{:keys [_start-time speaker words]}]
                 (format "%s%s" (if speaker (format "%s: " speaker) "") words)))))})

(defn format-parts [{:keys [abbreviate-after abbreviator formatter speakers] :as config}
                    media-filename parts]
  (let [num-speakers (->> parts
                          (group-by :speaker)
                          (filter (fn [[speaker _]] speaker))
                          count)
        format-fn (formatters formatter)
        speakers (speakers/reposition speakers parts)
        abbreviated-speakers (speakers/abbreviate abbreviator speakers)
        label-speakers
        (fn [parts]
          (if (> (count speakers) 1)
            (reduce
             (fn [acc part]
               (let [abbreviate? (and abbreviate-after
                                      (>= (count acc) (* abbreviate-after num-speakers)))]
                 (conj acc
                       (update part :speaker
                               #(speakers/label-speaker % (if abbreviate? abbreviated-speakers speakers))))))
             [] parts)
            parts))]
    (when-not format-fn
      (throw (ex-info "Invalid formatter" {:transcribble/formatter formatter})))
    (->> parts
         (drop-while #(and (> num-speakers 1) (nil? (:speaker %))))
         label-speakers
         (format-fn media-filename))))
