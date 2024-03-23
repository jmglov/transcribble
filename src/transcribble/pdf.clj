(ns transcribble.pdf
  (:require [clj-pdf.core :as pdf]))

(defn write-pdf! [config title paragraphs outfile]
  (let [{:keys [metadata styles]} (:pdf config)]
    (pdf/pdf
     [(assoc metadata :title title)

      [:heading title]

      (for [{:keys [timestamp speaker text]} paragraphs
            :let [ts (when timestamp [(format "[%s]\n" timestamp)])
                  spk (when speaker [[:chunk (get styles :speaker {})
                                      (format "%s\n" speaker)]])]]
        [:paragraph (get styles :paragraph {})
         (concat ts spk [text])])]
     outfile)))
