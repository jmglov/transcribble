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

(defn mk-replacement-str [replacement]
  (str replacement "$"
       (-> (re-seq #"[$]\d+" replacement)
           last
           (or "$0")
           (str/replace "$" "")
           Integer/parseInt
           inc)))

(defn replace-words [config text]
  (->> (:replace config)
       (map (fn [[k v]] [(name k) v]))
       (reduce (fn [text' [match replacement]]
                 (process-text
                  text'
                  #(str/replace % (re-pattern (str "^" match "([\\W]?)"))
                                (mk-replacement-str replacement))))
               text)))

(defn active-listening? [{:keys [remove-active-listening] :as config} text]
  (let [words (->> (str/split text #"\W+")
                   (map str/lower-case)
                   (remove empty?))]
    (every? remove-active-listening words)))

(defn repetition-word [{:keys [remove-repeated-words] :as config} word]
  (when word
    (let [word (str/lower-case (str/replace word #"\W+$" ""))]
      (when (contains? remove-repeated-words word)
        word))))

(defn remove-repetitions [config text]
  (->> (str/split text #"\s+")
       (reduce (fn [words word]
                 (let [[last-word & prev-words] (reverse words)
                       r-word (repetition-word config word)
                       r-last-word (repetition-word config last-word)]
                   (if (and r-word (= r-word r-last-word))
                     (conj (vec (reverse prev-words)) last-word)
                     (conj words word))))
               [])
       (str/join " ")))
