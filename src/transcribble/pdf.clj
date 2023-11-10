(ns transcribble.pdf
  (:require [clj-pdf.core :as pdf]))

(defn write-pdf! [config title paragraphs outfile]
  (let [styles (get-in config [:pdf :styles])]
    (pdf/pdf
     [{:title title}

      [:heading title]

      (for [{:keys [timestamp text]} paragraphs
            :let [ts-str (if timestamp (format "[%s]\n" timestamp) "")]]
        [:paragraph (get styles :paragraph {}) (format "%s%s" ts-str text)])]
     outfile)))
