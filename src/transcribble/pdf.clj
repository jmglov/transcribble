(ns transcribble.pdf
  (:require [clj-pdf.core :as pdf]))

(defn write-pdf! [config title paragraphs outfile]
  (let [styles (get-in config [:pdf :styles])]
    (pdf/pdf
     [{:title title}

      [:heading title]

      (for [p paragraphs]
        [:paragraph (get styles :paragraph {}) p])]
     outfile)))
