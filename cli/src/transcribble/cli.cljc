(ns transcribble.cli
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [clojure.set :as set]
            [clojure.string :as str]
            [transcribble.job :as job]))

(def ^:private main-cmd-name "transcribble")

(def ^:private specs (get-in (meta (the-ns 'transcribble.job))
                             [:org.babashka/cli :spec]))

(defn- apply-defaults [default-opts spec]
  (->> spec
       (map (fn [[k v]]
              (if-let [default-val (default-opts k)]
                [k (assoc v :default default-val)]
                [k v])))
       (into {})))

(defn- ->subcommand-help [{:keys [cmds cmd-opts desc]}]
  (let [cmd-name (first cmds)
        opts-str (if (empty? cmd-opts)
                   ""
                   (str "\n" (cli/format-opts {:spec cmd-opts, :indent 4})))]
    (format "  %s: %s%s" cmd-name desc opts-str)))

(defn- ->group-name [group]
  (-> group
      name
      str/capitalize
      (str/replace "-" " ")))

(defn- format-opts [global-specs]
  (->> global-specs
       (group-by (fn [[_opt spec]] (:group spec)))
       (map (fn [[group opts]]
              (let [spec (into {} opts)]
                (format "%s\n%s"
                        (->group-name group)
                        (cli/format-opts {:spec spec})))))
       (str/join "\n\n")))

(defn- print-help [global-specs cmds]
  (println
   (format
    "Usage: bb %s <subcommand> <options>

Subcommands:

%s

Options:

%s
"
    main-cmd-name
    (->> cmds
         (map ->subcommand-help)
         (str/join "\n"))
    (format-opts global-specs)))
  (System/exit 0))

(defn- print-command-help [cmd-name specs cmd-opts]
  (let [opts-str (if (empty? cmd-opts)
                   ""
                   (format "Options:\n%s\n\n"
                           (cli/format-opts {:spec cmd-opts})))]
    (println
     (format "Usage: bb %s %s <options>\n\n%sGlobal options:\n\n%s"
             main-cmd-name cmd-name opts-str (format-opts specs)))))

(defn- mk-cmd [global-specs default-opts [cmd-name desc fn-var]]
  (let [cmd-opts (get-in (meta fn-var) [:org.babashka/cli :spec])]
    {:cmds [cmd-name]
     :cmd-opts cmd-opts
     :desc desc
     :spec (merge global-specs (apply-defaults default-opts cmd-opts))
     :error-fn
     (fn [{:keys [type cause msg option] :as data}]
       (if (= :org.babashka/cli type)
         (throw (ex-info
                 (case cause
                   :require
                   (format "Missing required argument --%s:\n%s"
                           (name option)
                           (cli/format-opts {:spec cmd-opts}))
                   msg)
                 {:babashka/exit 1}))
         (throw (ex-info msg (assoc data :babashka/exit 1)))))
     :fn (fn [{:keys [opts]}]
           (when (:help opts)
             (print-command-help cmd-name global-specs cmd-opts)
             (System/exit 0))
           ;; If we have a var, we need to deref it to get the function out
           (println
            (if (var? fn-var)
              (@fn-var opts)
              (fn-var opts))))}))

(defn- mk-table [default-opts]
  (let [global-specs (apply-defaults default-opts specs)
        cmds
        (mapv (partial mk-cmd global-specs default-opts)
              [["init-job"
                "Set up files for transcription"
                #'job/init-job!]

               ["start-job"
                "Start transcription job"
                #'job/start-job!]

               ["job-status"
                "Get transcription job status"
                #'job/job-status]

               ["download-transcript"
                "Download completed transcript"
                #'job/download-transcript]

               ["convert-transcript"
                "Convert downloaded transcript"
                #'job/convert-transcript]

               ["process-file"
                "Initialise and start job, then download and convert transcript"
                #'job/process-file]

               ["save-otr"
                "Save the latest OTR file to S3"
                #'job/save-otr!]
               ])]
    (conj cmds
          {:cmds [], :fn (fn [m] (print-help global-specs cmds))})))

(defn dispatch
  ([]
   (dispatch {}))
  ([default-opts & args]
   (cli/dispatch (mk-table (job/apply-defaults default-opts))
                 (or args
                     (seq *command-line-args*)))))

(defn -main [& args]
  (apply dispatch {} args))
