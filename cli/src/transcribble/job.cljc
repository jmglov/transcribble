(ns transcribble.job
  {:org.babashka/cli
   {:spec
    {
     :aws-region
     {:desc "AWS region"
      :ref "<region-name>"
      :default "eu-west-1"
      :require true
      :group :aws-config}

     :media-dir
     {:desc "Path to media files on local filesystem (defaults to current dir)"
      :ref "<dir>"
      :group :basic-config}

     :s3-bucket
     {:desc "S3 bucket in which to store media files and transcripts"
      :ref "<bucket-name>"
      :require true
      :group :aws-config}

     :s3-media-path
     {:desc "S3 prefix for media files"
      :ref "<prefix>"
      :require true
      :group :aws-config}

     :s3-transcripts-path
     {:desc "S3 prefix for transcript files (defaults to s3-media-path)"
      :ref "<prefix>"
      :require false
      :group :aws-config}

     :media-separator
     {:desc "Replace spaces in media files with this string"
      :ref "<string>"
      :default "_"
      :require true
      :group :basic-config}

     :dry-run
     {:desc "Just print what would be done"
      :group :basic-config}

     :verbose
     {:desc "Print config and extra debugging information"
      :alias :v
      :group :basic-config}
     }}}
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [com.grzm.awyeah.client.api :as aws]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]))

;; Binding at top-level in order to make it stringable. I honestly don't why
;; this is necessary, but I guess you can ask on #babashka if you're curious.
(def this-file *file*)

(defn apply-defaults [{:keys [media-file] :as opts}]
  (let [opts (merge {:downloads-dir (fs/file (fs/home) "Downloads")
                     :media-dir (fs/cwd)}
                    opts)
        opts (if (and media-file (not (fs/exists? media-file)))
               (assoc opts :media-file (fs/file (:media-dir opts) media-file))
               opts)]
    opts))

(defn mk-s3-client [{:keys [aws-region] :as opts}]
  (aws/client {:api :s3, :region aws-region}))

(defn mk-transcribe-client [{:keys [aws-region] :as opts}]
  (aws/client {:api :transcribe, :region aws-region}))

(defn absolute-path [{:keys [media-dir]} filename]
  (if (fs/absolute? filename)
    filename
    (fs/file media-dir filename)))

(defn job-name [media-file]
  (str/replace (fs/file-name media-file) #"[.][^.]+$" ""))

(defn mk-media-key [{:keys [s3-media-path media-file] :as opts}]
  (format "%s/%s" s3-media-path media-file))

(defn mk-media-path [{:keys [s3-bucket media-file] :as opts}]
  (format "s3://%s/%s" s3-bucket (mk-media-key opts)))

(defn mk-s3-key [{:keys [media-dir s3-media-path] :as opts} filename]
  (->> (str/replace-first (str filename)
                          (re-pattern (format "^%s/" (str/replace media-dir #"/$" "")))
                          "")
       (fs/file s3-media-path)
       str))

(defn print-config [{:keys [verbose] :as opts}]
  (when verbose
    (pprint opts)))

(defn log [obj msg {:keys [verbose] :as opts}]
  (when verbose
    (println msg)
    (pprint obj))
  obj)

(defn upload-to-s3! [{:keys [s3 s3-bucket replace-existing
                             dry-run] :as opts} filename]
  (let [s3 (or s3 (mk-s3-client opts))
        s3-key (log (mk-s3-key opts filename)
                    "Checking to see if object exists:" opts)
        s3-obj (when-not dry-run
                 (aws/invoke s3 {:op :HeadObject
                                 :request {:Bucket s3-bucket
                                           :Key s3-key}}))]
    (when (and (not dry-run)
               (or (not (:ETag s3-obj)) replace-existing))
      (when-not (:ETag s3-obj)
        (println (format "Object does not exist at %s; uploading %s" s3-key filename)))
      (with-open [is (io/input-stream filename)]
        (aws/invoke s3 {:op :PutObject
                        :request {:Bucket s3-bucket
                                  :Key s3-key
                                  :Body is}})))))

(defn ensure-media! [opts]
  (let [media-path (mk-media-path opts)]
    (upload-to-s3! opts (mk-media-key opts))
    media-path))

(defn init-job!
  {:org.babashka/cli
   {:spec
    {:media-file
     {:desc "Media file to transcribe"
      :ref "<file>"
      :require true}
     }}}
  [{:keys [media-file media-separator] :as opts}]
  (let [tgt-media-file (-> media-file fs/file-name (str/replace " " media-separator))
        job-name (job-name tgt-media-file)
        tgt-dir (fs/file job-name)]
    (fs/create-dirs tgt-dir)
    (-> (fs/copy media-file (fs/file tgt-dir tgt-media-file)
                 {:replace-existing true})
        str)))

(defn start-job!
  {:org.babashka/cli
   {:spec
    {:media-file
     {:desc "Media file to transcribe"
      :ref "<file>"
      :require true}

     :language-code
     {:desc "IETF language tag"
      :ref "<lang>"
      :default "en-US"
      :require true}

     :speakers
     {:desc "List of speakers"
      :ref "<speakers>"
      :coerce []}

     :speakers-fn
     {:desc "Function which called with opts returns a list of speakers"
      :ref "<fn>"}
     }}}
  [{:keys [s3-bucket s3-media-path
           media-file language-code speakers speakers-fn
           transcribe]
    :as opts}]
  (let [transcribe (or transcribe (mk-transcribe-client opts))
        media-path (ensure-media! opts)
        speakers (if speakers-fn (speakers-fn opts) speakers)
        request (merge {:TranscriptionJobName (job-name media-file)
                        :Media {:MediaFileUri media-path}
                        :LanguageCode language-code
                        :OutputBucketName s3-bucket
                        :OutputKey (format "%s/output/" s3-media-path)}
                       (when (> (count speakers) 1)
                         {:Settings {:ShowSpeakerLabels true
                                     :MaxSpeakerLabels (count speakers)}}))]
    (aws/invoke transcribe {:op :StartTranscriptionJob
                            :request request})))

(defn job-status
  {:org.babashka/cli
   {:spec
    {:media-file
     {:desc "Media file being transcribed"
      :ref "<file>"
      :require true}
     }}}
  [{:keys [transcribe media-file] :as opts}]
  (let [transcribe (or transcribe (mk-transcribe-client opts))]
    (-> (aws/invoke transcribe {:op :GetTranscriptionJob
                                :request {:TranscriptionJobName (job-name media-file)}})
        :TranscriptionJob)))

(defn download-transcript
  {:org.babashka/cli
   {:spec
    {:media-file
     {:desc "Media file being transcribed"
      :ref "<file>"
      :require true}
     }}}
  [{:keys [s3 transcribe s3-bucket media-file] :as opts}]
  (let [s3 (or s3 (mk-s3-client opts))
        transcribe (or transcribe (mk-transcribe-client opts))
        {:keys [Transcript TranscriptionJobStatus] :as res} (job-status opts)]
    (if (= "COMPLETED" TranscriptionJobStatus)
      (let [{:keys [TranscriptFileUri]} Transcript
            transcript-key (->> TranscriptFileUri
                                (re-find #"^https://[^/]+/[^/]+/(.+)$")
                                second)
            job-name (job-name media-file)
            out-file (format "%s/%s.json" job-name job-name)]
        (println "Downloading" TranscriptFileUri "to" out-file)
        (-> (aws/invoke s3 {:op :GetObject
                        :request {:Bucket s3-bucket
                                  :Key transcript-key}})
        :Body
        (io/copy (io/file out-file))))
      (println "Transcription job not completed:" res))))

(defn convert-transcript
  {:org.babashka/cli
   {:spec
    {:media-file
     {:desc "Media file which has been transcribed"
      :ref "<file>"
      :require true}

     :speakers
     {:desc "List of speakers"
      :ref "<speakers>"
      :coerce []}

     :speakers-fn
     {:desc "Function which called with opts returns a list of speakers"
      :ref "<fn>"}
     }}}
  [{:keys [media-file speakers speakers-fn transcribble-jar] :as opts}]
  (let [absolutize (comp fs/absolutize fs/file)
        cmd (if transcribble-jar
              (format "java -jar %s" transcribble-jar)
              "clojure -m transcribble.main")
        transcript-file (str/replace media-file #".[^.]+$" ".json")
        otr-file (str/replace media-file #".[^.]+$" ".otr")
        _ (println "speakers-fn:" speakers-fn)
        speakers (if speakers-fn (speakers-fn opts) speakers)
        _ (println "Speakers is now:" speakers)
        speakers-str (str/join "," speakers)
        args (concat (map absolutize [transcript-file otr-file media-file])
                     [speakers-str]
                     (when (fs/exists? "config.json")
                       [(absolutize "config.json")]))
        transcribble-root (->> this-file
                               (re-find #"^(.+)/cli/src/transcribble/.+")
                               second
                               fs/file)
        _ (println (format "Running '%s %s' in dir %s"
                           cmd (str/join " " args) transcribble-root))
        {:keys [out err exit] :as res}
        (apply shell {:dir transcribble-root
                      :out :string
                      :err :string
                      :continue true}
               cmd args)]
    (doseq [s [out err]]
      (when-not (empty? s)
        (println s)))
    (if (= 0 exit)
      otr-file
      res)))

(defn process-file
  {:org.babashka/cli
   {:spec
    {:media-file
     {:desc "Media file to transcribe"
      :ref "<file>"
      :require true}

     :language-code
     {:desc "IETF language tag"
      :ref "<lang>"
      :default "en-US"
      :require true}

     :speakers
     {:desc "List of speakers"
      :ref "<speakers>"
      :coerce []}

     :speakers-fn
     {:desc "Function which called with opts returns a list of speakers"
      :ref "<fn>"}
     }}}
  [{:keys [media-file wait-secs] :as opts}]
  (let [s3 (mk-s3-client opts)
        transcribe (mk-transcribe-client opts)
        wait-secs (or wait-secs 30)
        _ (println "Initialising job for file" media-file)
        media-file (init-job! opts)
        opts (assoc opts
                    :media-file media-file
                    :s3 s3
                    :transcribe transcribe)
        _ (println "Starting transcription job for file" (str media-file))
        res (start-job! opts)
        _ (println "Job started:" res)
        otr-file (loop []
                   (let [{:keys [TranscriptionJobStatus] :as res} (job-status opts)]
                     (case TranscriptionJobStatus
                       "IN_PROGRESS"
                       (do
                         (println "Job still in progress; waiting" wait-secs "seconds")
                         (Thread/sleep (* wait-secs 1000))
                         (recur))

                       "COMPLETED"
                       (do
                         (download-transcript opts)
                         (convert-transcript opts))

                       (println "Transcription finished with error:" res))))]
    (println "Transcription completed; OTR file:" otr-file)))

(defn save-otr!
  {:org.babashka/cli
   {:spec
    {:otr-dir
     {:desc "Path to OTR backups on local filesystem (defaults to ~/Downloads)"
      :ref "<dir>"}

     :media-file
     {:desc "Media file to transcribe"
      :ref "<file>"
      :require true}
     }}}
  [opts]
  (let [{:keys [downloads-dir media-dir media-file
                dry-run verbose] :as opts} (apply-defaults opts)
        _ (print-config opts)
        sort-by-mtime #(sort-by fs/last-modified-time %)
        job-dir (fs/parent media-file)
        job-name' (job-name media-file)
        tgt-file (fs/file media-dir job-dir (format "%s.otr" job-name'))
        src-file (-> (fs/glob downloads-dir "*.otr")
                     sort-by-mtime
                     (log "Considering OTR files:" opts)
                     last)]
    (println "Copying" (str src-file) "to" (str tgt-file))
    (when-not dry-run
      (fs/copy src-file tgt-file {:replace-existing true}))
    (upload-to-s3! (assoc opts :replace-existing true) (str tgt-file))))

(defn convert-pdf!
  {:org.babashka/cli
   {:spec
    {:otr-file
     {:desc "OTR file to convert"
      :ref "<file>"
      :require true}

     :transcribble-jar
     {:desc "Path to Transcribble JAR file to use for conversion"
      :ref "<path>"
      :require true}
     }}}
  [{:keys [media-dir media-separator otr-file transcribble-jar
           dry-run] :as opts}]
  (let [otr-file (absolute-path opts otr-file)
        pdf-filename (str/replace (str otr-file) #"[.]otr$" ".pdf")
        title (str/replace (job-name otr-file) media-separator " ")
        config-filename (absolute-path opts "config.json")
        args (concat ["java" "-jar" (str transcribble-jar)
                      "--convert-otr"
                      (str otr-file) (str pdf-filename) title]
                     (when (fs/exists? config-filename)
                       [(str config-filename)]))]
    (println (str/join " " args))
    (when-not dry-run
      (shell args))))
