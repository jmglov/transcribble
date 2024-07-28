(ns transcribble.otr
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [hiccup2.core :as hiccup]
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

(defn concatv [& args]
  (->> args
       (apply concat)
       vec))

(defn remove-empty-paragraphs [hiccup]
  (->> hiccup
       (remove #(contains? #{[:br {}]
                             [:p {} [:br {}]]
                             "\n"}
                           %))))

(defn fixup-hiccup [hiccup]
  (walk/postwalk (fn [node]
                   (if (and (string? node) (str/includes? node "&"))
                     (str/replace node "&quot;" "\"")
                     node))
                 hiccup))

(defn fixup-paragraph [[p-tag p-attrs & p-contents :as p]]
  (let [p-contents (remove-empty-paragraphs p-contents)]
    (if (= :b (ffirst p-contents))
      (let [[[b-tag b-attrs & b-contents] & p-contents] p-contents]
        (if (= :span (ffirst b-contents))
          (let [[span-tag speaker] b-contents]
            (concatv [p-tag p-attrs
                      span-tag
                      [:b b-attrs (str/replace speaker #"^\s+" "")]]
                     p-contents))
          p))
      p)))

(defn fixup-otr! [infile outfile]
  (let [otr (-> infile slurp (json/parse-string keyword))]
    (->> otr
         :text
         hickory/parse-fragment
         (map hickory/as-hiccup)
         fixup-hiccup
         remove-empty-paragraphs
         (map fixup-paragraph)
         hiccup/html
         str
         (assoc otr :text)
         json/generate-string
         (spit outfile))))
