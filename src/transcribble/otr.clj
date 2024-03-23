(ns transcribble.otr
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [hickory.core :as hickory]
            [transcribble.util :refer [->map]]))

(defn explode-hiccup [[tag attrs content & rest]]
    (let [content (if rest (cons content rest) content)]
      (->map tag attrs content)))

(defn parse-paragraph [hiccup]
  (let [{:keys [tag attrs content] :as t} (explode-hiccup hiccup)]
    (when (= :p tag)
      (case (count content)
        1
        {:text (first content)}

        2
        (let [[timestamp text] (map (comp :content explode-hiccup) content)]
          (->map timestamp text))

        3
        (let [[timestamp speaker] (->> content
                                       (take 2)
                                       (map (comp :content explode-hiccup)))
              text (str/replace (last content) #"^: " "")]
          (->map timestamp speaker text))))))

(defn parse-paragraphs [hiccup]
  (->> hiccup
       (map parse-paragraph)
       (remove nil?)))

(defn parse-otr [otr]
  (let [as-hiccup (partial map hickory/as-hiccup)]
    (-> otr
        (json/parse-string keyword)
        :text
        hickory/parse-fragment
        as-hiccup
        parse-paragraphs)))

(defn load-otr [filename]
  (-> (slurp filename)
      parse-otr))
