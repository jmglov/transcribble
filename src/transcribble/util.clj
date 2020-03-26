(ns transcribble.util
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]))

(defn load-json-file [filename]
  (with-open [f (io/reader filename)]
    (json/parse-stream f true)))
