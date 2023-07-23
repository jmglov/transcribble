(ns transcribble.util
  (:require [clojure.pprint :refer [pprint]]))

(defmacro do-or-dry-run [{:keys [dry-run verbose] :as opts} & forms]
  (when (or dry-run verbose)
    (prn (cons "Running commands:" forms)))
  (when-not dry-run
    `(do
       ~@forms)))

(comment

  (macroexpand '(do-or-dry-run {:dry-run true}
                               (println "Stuff")
                               (println "and things")))
  ;; => nil

  (macroexpand '(do-or-dry-run {:verbose true}
                               (println "Stuff")
                               (println "and things")))
  ;; => (do (println "Stuff") (println "and things"))

  (macroexpand '(do-or-dry-run {}
                               (println "Stuff")
                               (println "and things")))
  ;; => (do (println "Stuff") (println "and things"))

  )
