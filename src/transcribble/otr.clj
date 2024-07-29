(ns transcribble.otr
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [hiccup2.core :as hiccup]
            [hickory.core :as hickory]
            [transcribble.util :refer [->map concatv] :as util]))

(defn ts->sec [ts]
  (let [[[_ hh mm ss frac]] (re-seq #"^(?:(\d{2}):)?(\d{2}):(\d{2})[.](\d+)$" ts)]
    (format "%.6f"
            (+ (-> hh util/parse-int (* 3600))
               (-> mm util/parse-int (* 60))
               (-> ss util/parse-int)
               (-> frac util/parse-fractional)))))

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

(defn hiccup->otr
  ([hiccup]
   (hiccup->otr {} hiccup))
  ([otr hiccup]
   (->> hiccup
        hiccup/html
        str
        (assoc otr :text)
        json/generate-string)))

(defn fixup-otr! [infile outfile]
  (let [otr (-> infile slurp (json/parse-string keyword))]
    (->> otr
         :text
         hickory/parse-fragment
         (map hickory/as-hiccup)
         fixup-hiccup
         remove-empty-paragraphs
         (map fixup-paragraph)
         (hiccup->otr otr)
         (spit outfile))))

(defn load-zencastr [filename]
  (-> (slurp filename)
      (str/replace "\r\n" "\n")
      (str/split #"\n\n")))

(defn zencastr->hiccup
  ([paragraphs]
   (zencastr->hiccup {} paragraphs))
  ([speakers paragraphs]
   (->> paragraphs
        (map #(let [[ts speaker paragraph] (str/split-lines %)]
                [:p {}
                 [:span {:class "timestamp", :data-timestamp (ts->sec ts)} ts]
                 [:b {} (get speakers speaker speaker)]
                 (str ": " paragraph)])))))

(defn zencaster->otr!
  ([infile outfile]
   (zencaster->otr! infile outfile {}))
  ([infile outfile speakers]
   (->> (load-zencastr infile)
        (zencastr->hiccup speakers)
        (hiccup->otr {:media-file (str/replace outfile #"^.+/([^/]+)$" "$1")
                      :media-time 0.0})
        (spit outfile))))
