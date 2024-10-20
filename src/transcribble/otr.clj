(ns transcribble.otr
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [hiccup2.core :as hiccup]
            [hickory.core :as hickory]
            [transcribble.text :as text]
            [transcribble.util :refer [->map concatv] :as util]))

(defn ts->sec [ts]
  (if (str/includes? ts ":")
    (let [ts (if (re-matches #"^.+[.]\d+$" ts) ts (format "%s.0" ts))
          [[_ hh mm ss frac]] (re-seq #"^(?:(\d{2}):)?(\d{2}):(\d{2})[.](\d+)$" ts)]
      (format "%.6f"
              (+ (-> hh util/parse-int (* 3600))
                 (-> mm util/parse-int (* 60))
                 (-> ss util/parse-int)
                 (-> frac util/parse-fractional))))
    ts))

(defn sec->ts [sec]
  (let [sec (if (re-matches #"^.+[.]\d+$" (str sec))
              (str sec)
              (format "%s.0" (str sec)))
        [[_ secs sss]] (re-seq #"^(\d+)[.]\d+$" sec)
        secs-int (util/parse-int secs)
        HH (-> secs-int (/ 3600) int)
        mm (-> secs-int (/ 60) int (mod 60))
        ss (-> secs-int (mod 60))]
    (if (zero? HH)
      (format "%02d:%02d" mm ss)
      (format "%02d:%02d:%02d" HH mm ss))))

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

(defn fixup-html-quotes [hiccup]
  (walk/postwalk (fn [node]
                   (if (and (string? node) (str/includes? node "&"))
                     (str/replace node "&quot;" "\"")
                     node))
                 hiccup))

(defn fixup-paragraph [config [p-tag p-attrs & p-contents :as p]]
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

(defn fixup-hiccup [config hiccup]
  (->> hiccup
       fixup-html-quotes
       remove-empty-paragraphs
       (map (partial fixup-paragraph config))))

(defn hiccup->otr
  ([hiccup]
   (hiccup->otr {} hiccup))
  ([config hiccup]
   (hiccup->otr config {} hiccup))
  ([config otr hiccup]
   (->> hiccup
        hiccup/html
        str
        (text/fix-case config)
        (text/remove-fillers config)
        (text/replace-words config)
        (assoc otr :text)
        json/generate-string)))

(defn rebase-time
  ([hiccup]
   ;; We don't have config, so just return as it
   hiccup)
  ([{:keys [old-start new-start] :as config} hiccup]
   (if (and old-start new-start)
     (let [old-sec (-> old-start ts->sec Float/parseFloat)
           new-sec (-> new-start ts->sec Float/parseFloat)]
       (->> hiccup
            (remove (fn [[_p _attrs
                          [_span {:keys [:data-timestamp]} _ts]
                          _speaker _text]]
                      (< (Float/parseFloat data-timestamp) old-sec)))
            (map (fn [[p attrs
                       [_span {:keys [:data-timestamp]} ts]
                       speaker text]]
                   (let [new-sec (-> (Float/parseFloat data-timestamp)
                                     (- old-sec)
                                     (+ new-sec))]
                     [p attrs
                      [:span {:class "timestamp", :data-timestamp (str new-sec)}
                       (sec->ts new-sec)]
                      speaker text])))))
     ;; We don't have an old and new start timestamp, so just return as it
     hiccup)))

(defn fixup-otr!
  ([infile outfile]
   (fixup-otr! {} infile outfile))
  ([config infile outfile]
   (let [otr (-> infile slurp (json/parse-string keyword))]
     (->> otr
          :text
          hickory/parse-fragment
          (map hickory/as-hiccup)
          (fixup-hiccup config)
          (rebase-time config)
          (hiccup->otr config otr)
          (spit outfile)))))

(defn load-zencastr [filename]
  (-> (slurp filename)
      (str/replace "\r\n" "\n")
      (str/split #"\n\n")))

(defn zencastr->hiccup
  ([paragraphs]
   (zencastr->hiccup {} paragraphs))
  ([config paragraphs]
   (zencastr->hiccup {} {} paragraphs))
  ([config speakers paragraphs]
   (->> paragraphs
        (map #(let [[ts speaker paragraph] (str/split-lines %)]
                [:p {}
                 [:span {:class "timestamp", :data-timestamp (ts->sec ts)}
                  (str/replace ts #"[.]\d+$" "")]
                 [:b {} (get speakers speaker speaker)]
                 (str ": " paragraph)]))
        (fixup-hiccup config)
        (rebase-time config))))

(defn zencaster->otr! [infile outfile config speakers]
  (->> (load-zencastr infile)
       (zencastr->hiccup config speakers)
       (hiccup->otr config
                    {:media-file (str/replace outfile #"^.+/([^/]+)$" "$1")
                     :media-time 0.0})
       (spit outfile)))
