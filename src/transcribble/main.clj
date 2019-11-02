(ns transcribble.main
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [cheshire.core :as json]
            [hiccup.core :as hiccup]))

(defn load-json-file [filename]
  (with-open [f (io/reader filename)]
    (json/parse-stream f true)))

(defn load-config [config-filename]
  (merge {:formatter :otr}
         (when config-filename (load-json-file config-filename))))

(defn make-speakers-map [speakers]
  (when speakers
    (->> (string/split speakers #",")
         (map-indexed (fn [i speaker-name] [(str "spk_" i) speaker-name]))
         (into {}))))

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
    (->> [(when (pos? hours) hours) minutes seconds]
         (filter identity)
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
                         (assoc current-part :start-time start_time))]
      (cond
        (= "punctuation" type)
        [parts (update current-part :words append-punctuation word)]

        (or (= (:speaker current-part) speaker) (empty? (:words current-part)))
        [parts (update current-part :words conj word)]

        :else
        [(conj parts (finalise-part current-part))
         {:speaker speaker, :words [word]}]))))

(defn load-transcribe-json [config filename speakers]
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

(defn format-data [media-filename formatter data]
  (let [format-fn (formatters formatter)]
    (when-not format-fn
      (throw (ex-info "Invalid formatter" {:transcribble/formatter formatter})))
    (->> data
         (drop-while #(nil? (:speaker %)))
         (format-fn media-filename))))

(defn write-transcript [output-filename media-filename formatter data]
  (spit output-filename (format-data media-filename formatter data)))

(defn -main [transcribe-filename output-filename media-filename speaker-names
             & [config-filename]]
  (let [config (load-config config-filename)
        speakers (make-speakers-map speaker-names)
        data (load-transcribe-json config transcribe-filename speakers)]
    (write-transcript output-filename media-filename (keyword (:formatter config)) data)))
