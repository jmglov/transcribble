(ns transcribble.otr
  (:require [cheshire.core :as json]
            [hickory.core :as hickory]))

(defn parse-paragraph [[_ _ ts-or-contents contents]]
  (cond
    (and (vector? ts-or-contents) (= :span (first ts-or-contents)))
    (format "[%s] %s" (nth ts-or-contents 2) contents)

    (string? ts-or-contents)
    ts-or-contents

    :else
    nil))

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
