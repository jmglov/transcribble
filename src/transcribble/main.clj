(ns transcribble.main
  (:require [transcribble.core :as core]
            [transcribble.format :as format]
            [transcribble.speakers :as speakers]))

(defn -main [transcribe-filename output-filename media-filename speaker-names
             & [config-filename]]
  (let [speakers (speakers/make-speakers-map speaker-names)
        config (-> (core/load-config config-filename)
                   (assoc :speakers speakers))
        data (core/load-transcribe-json config transcribe-filename)
        formatted-data (format/format-data config media-filename data)]
    (spit output-filename formatted-data)))
