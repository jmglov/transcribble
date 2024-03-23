(ns transcribble.util
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]))

(defmacro ->map [& ks]
  (assert (every? symbol? ks))
  (zipmap (map keyword ks)
          ks))

(defn load-json-file [filename]
  (with-open [f (io/reader filename)]
    (json/parse-stream f true)))
