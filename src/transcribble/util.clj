(ns transcribble.util
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cheshire.core :as json]))

(defmacro ->map [& ks]
  (assert (every? symbol? ks))
  (zipmap (map keyword ks)
          ks))

(defn concatv [& args]
  (->> args
       (apply concat)
       vec))

(defn load-json-file [filename]
  (with-open [f (io/reader filename)]
    (json/parse-stream f true)))

(defn parse-int [s]
  (if s
    (-> (str/replace s #"^0" "") Integer/parseInt)
    0))

(defn parse-fractional [s]
  (if s
    (-> (str "0." s) Float/parseFloat)
    0.0))
