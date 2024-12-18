(ns transcribble.main
  (:require [clojure.string :as str]
            [transcribble.core :as core]
            [transcribble.format :as format]
            [transcribble.otr :as otr]
            [transcribble.pdf :as pdf]
            [transcribble.speakers :as speakers])
  (:gen-class))

(defn parse-args [[_ & opts-and-args]]
  (let [opts (->> opts-and-args
                  (partition-all 2)
                  (take-while (fn [[opt _v]] (str/starts-with? opt "--"))))
        args (->> opts-and-args
                  (partition-all 2)
                  (drop-while (fn [[opt _v]] (str/starts-with? opt "--")))
                  flatten)
        opts-map (->> opts
                      (map (fn [[k v]]
                             [(-> k (str/replace "--" "") keyword) v]))
                      (into {}))]
    {:args args, :opts opts-map}))

(defn load-config [opts config-filename]
  (merge opts
         (when (not-empty config-filename)
           (core/load-config config-filename))))

(defn -main [& args]
  (cond
    (= "--convert-otr" (first args))
    (let [[_ otr-filename pdf-filename title & [config-filename]] args
          config (core/load-config config-filename)
          paragraphs (otr/load-otr otr-filename)]
      (pdf/write-pdf! config title paragraphs pdf-filename))

    (= "--fixup-otr" (first args))
    (let [{:keys [opts args]} (parse-args args)
          [infile outfile config-filename] args
          config (load-config opts config-filename)]
      (when-not (and infile outfile)
        (binding [*out* *err*]
          (println "Usage: transcribble --fixup-otr INFILE OUTFILE")
          (System/exit 1)))
      (otr/fixup-otr! config infile outfile))

    (= "--zencastr-to-otr" (first args))
    (let [{:keys [opts args]} (parse-args args)
          [infile outfile config-filename speakers] args
          config (load-config opts config-filename)
          speakers (if (empty? speakers)
                     {}
                     (->> (str/split speakers #",")
                          (mapv #(str/split % #"="))
                          (into {})))]
      (when-not (and infile outfile)
        (binding [*out* *err*]
          (println "Usage: transcribble --zencastr-to-otr INFILE OUTFILE")
          (System/exit 1)))
      (otr/zencaster->otr! infile outfile config speakers))

    :else
    (let [[transcribe-filename output-filename media-filename speaker-names
           & [config-filename]] args
          speakers (speakers/make-speakers-map speaker-names)
          config (-> (core/load-config config-filename)
                     (assoc :speakers speakers))
          parts (core/load-transcribe-json config transcribe-filename)
          formatted-parts (format/format-parts config media-filename parts)]
      (spit output-filename formatted-parts))))
