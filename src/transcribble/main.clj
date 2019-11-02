(ns transcribble.main
  (:require [transcribble.core :as core]
            [transcribble.format :as format]
            [transcribble.speakers :as speakers]))

(defn -main [transcribe-filename output-filename media-filename speaker-names
             & [config-filename]]
  (let [config (core/load-config config-filename)
        speakers (speakers/make-speakers-map speaker-names)
        data (core/load-transcribe-json config transcribe-filename)
        formatted-data (format/format-data config media-filename speakers data)]
    (spit output-filename formatted-data)))
