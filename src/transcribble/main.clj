(ns transcribble.main
  (:require [transcribble.core :as core]
            [transcribble.format :as format]
            [transcribble.speakers :as speakers])
  (:gen-class))

(defn -main [& args]
  (if (= "--fixup-otr" (first args))
    (let [[_ filename] args]
      (format/fixup-otr filename))
    (let [[transcribe-filename output-filename media-filename speaker-names
           & [config-filename]] args
          speakers (speakers/make-speakers-map speaker-names)
          config (-> (core/load-config config-filename)
                     (assoc :speakers speakers))
          parts (core/load-transcribe-json config transcribe-filename)
          formatted-parts (format/format-parts config media-filename parts)]
      (spit output-filename formatted-parts))))
