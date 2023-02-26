(ns transcribble.main
  (:require [transcribble.core :as core]
            [transcribble.format :as format]
            [transcribble.otr :as otr]
            [transcribble.pdf :as pdf]
            [transcribble.speakers :as speakers])
  (:gen-class))

(defn -main [& args]
  (cond
    (= "--fixup-otr" (first args))
    (let [[_ filename] args]
      (format/fixup-otr filename))

    (= "--convert-otr" (first args))
    (let [[_ otr-filename pdf-filename title & [config-filename]] args
          config (core/load-config config-filename)
          paragraphs (otr/load-otr otr-filename)]
      (pdf/write-pdf! config title paragraphs pdf-filename))

    :else
    (let [[transcribe-filename output-filename media-filename speaker-names
           & [config-filename]] args
          speakers (speakers/make-speakers-map speaker-names)
          config (-> (core/load-config config-filename)
                     (assoc :speakers speakers))
          parts (core/load-transcribe-json config transcribe-filename)
          formatted-parts (format/format-parts config media-filename parts)]
      (spit output-filename formatted-parts))))
