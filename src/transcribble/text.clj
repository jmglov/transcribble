(ns transcribble.text
  (:require [clojure.string :as str]))

(defn process-text [text f]
  (->> (str/split text #" ")
       (map f)
       (remove empty?)
       (str/join " ")))

(defn fix-case [config text]
  (let [cap (reduce (fn [text' word]
                      (process-text text'
                                    #(str/replace % (re-pattern word)
                                                  (str/capitalize word))))
                    text (:capitalise config))]
    (reduce (fn [text' word]
              (process-text text'
                            #(str/replace % (re-pattern (str "(?i)" word))
                                          (str/lower-case word))))
            cap (:downcase config))))

(defn remove-fillers [config text]
  (reduce (fn [text' word]
            (process-text text'
                          #(when-not (re-matches
                                      (re-pattern (str "(?i)" word "[,.]?"))
                                      %)
                             %)))
          text (:remove-fillers config)))

(defn replace-words [config text]
  (->> (:replace config)
       (map (fn [[k v]] [(name k) v]))
       (reduce (fn [text' [match replacement]]
                 (process-text text'
                               #(str/replace % (re-pattern (str "^" match "([\\W]?)"))
                                             (str replacement "$1"))))
               text)))
