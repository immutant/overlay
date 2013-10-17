(ns overlay.core
  (:require [clojure.java.io      :as io]
            [clojure.java.shell   :as shell]
            [clojure.data.json    :as json]
            [digest               :as digest]
            [overlay.filesystem   :as fs]
            [overlay.extract      :as ex]
            [overlay.xml          :as xml]
            [overlay.options      :as opts]
            [progress.file        :as progress]
            [clj-http.lite.client :as http]
            overlay.jboss)
  (:use [clojure.string :only [split]]))

(def ^{:doc "The output dir used by overlay operations. Root binding ./target/"
       :dynamic true}
  *output-dir* (io/file "target"))

(def ^{:doc "The dir where artifacts will be extracted. If nil, *output-dir* is used as a fallback. Root binding is nil"
       :dynamic true}
  *extract-dir* nil)

(def ^{:doc "Should sha1 checksums be validated if available? Root binding is false"
       :dynamic true}
  *verify-sha1-sum* false)

(def repository "http://downloads.immutant.org")
(def overlayable-features #{:immutant :torquebox :hotrod})
(def config-files ["standalone/configuration/standalone.xml"
                   "standalone/configuration/standalone-ha.xml"
                   "standalone/configuration/standalone-full.xml"
                   "domain/configuration/domain.xml"])

(def type-size-keys
  {"slim" :slim_dist_size
   "full" :full_dist_size
   "bin"  :dist_size
   nil    :dist_size})

(defn get-json* [url]
  (with-open [r (io/reader url)]
    (json/read-str (slurp r) :key-fn keyword)))

(def get-json (memoize get-json*))

(defn println-err [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn metadata-url
  "Return the metadata url, but only for full binary distributions"
  [url]
  (if (re-find #"dist-.*\.zip$" url)
    (.replaceFirst url "/[^/]*$" "/build-metadata.json")))

(defn content-length
  [url]
  (-> (http/head url)
      :headers
      (get "content-length")
      Integer.))

(defprotocol BinArtifact
  "Collects relevant information for a particular binary artifact to be overlayed."
  (url [_] "The URL from which the artifact may be downloaded.")
  (filesize [_] "The size of the downloaded artifact.")
  (version [_] "The version of the artifact.")
  (feature [_] "The feature provided by the artifact."))

(defn incremental-url [feature version file]
  (format "%s/incremental/%s/%s/%s" repository (name feature) version file))

(defrecord Incremental [feature vers type]
  BinArtifact
  (url [this]
    (let [file (format "%s-dist-%s.zip" (name feature) type)]
      (incremental-url feature (version this) file)))
  (filesize [this]
    ((type-size-keys type) (get-json (metadata-url (url this)))))
  (version [this]
    (if (or (not vers) (= "LATEST" vers))
      (:build_number (get-json (incremental-url feature
                                                "LATEST"
                                                "build-metadata.json")))
      vers))
  (feature [_]
    feature))

(defrecord Release [feature version type]
  BinArtifact
  (url [_]
       (let [feature-name (name feature)
             file (format "%s-dist-%s-%s.zip" feature-name version type)]
         (format "%s/release/org/%s/%s-dist/%s/%s" repository feature-name feature-name version file)))
  (filesize [this]
    (content-length (url this)))
  (version [_] version)
  (feature [_] feature))


(defrecord HotRodIncremental [version]
  BinArtifact
  (url [_]
    (format "https://projectodd.ci.cloudbees.com/job/hotrod-overlay/%s/artifact/hotrod-overlay.zip"
            (or version "lastSuccessfulBuild")))
  (filesize [this] (content-length (url this)))
  (version [_]
    (or version
        (-> "https://projectodd.ci.cloudbees.com/job/hotrod-overlay/api/json"
            get-json
            :lastSuccessfulBuild
            :number
            str)))
  (feature [_]
    "hotrod"))

(defrecord ArbitraryURL [address]
  BinArtifact
  (url [_] address)
  (filesize [_] (content-length address))
  (version [_]
    "unknown")
  (feature [_]
    "unknownURL"))

(defn released-version? [version]
  (and version (.contains version ".")))

(defn default-dist-type [feature]
  (if (= feature :immutant) "slim" "bin"))

(defn artifact-spec
  [spec]
  (let [[feature-version type] (split spec #":")
        [feature version]      (split feature-version #"-")]
    [(keyword feature) version type]))

(defn artifact
  "Return the correct artifact based on the arguments passed"
  ([spec]
     (let [s (str spec)]
       (if (re-find #"^http" s)
         (->ArbitraryURL s)
         (apply artifact (artifact-spec s)))))
  ([feature version]
     (artifact feature version nil))
  ([feature version type]
     (if (= feature :hotrod)
       (->HotRodIncremental version)
       ((if (released-version? version) ->Release ->Incremental)
        feature version
        (or type (default-dist-type feature))))))

(defn artifact-exists? [artifact]
  (= 200 (:status (http/head (url artifact) {:throw-exceptions false}))))

(defn download [artifact dest]
  (if (artifact-exists? artifact)
    (do (println "Downloading" (url artifact))
        (.mkdirs (.getParentFile (io/file dest)))
        (let [size (try
                     (filesize artifact)
                     (catch Exception e
                       0))]
          (progress/with-file-progress dest :filesize size
            (with-open [in (io/input-stream (url artifact))]
              (io/copy in dest))))
        true)
    (do (println-err "Error: No artifact found for" (-> artifact :feature name)
                     "version" (:version artifact))
        false)))

(defn verify-sum [uri file]
  (if *verify-sha1-sum*
    (try
      (let [expected (slurp (str uri ".sha1"))
            computed (digest/sha1 file)
            verified (= expected computed)]
        (when-not verified
          (println-err "\nError: sha1 checksum validation failed for" uri "\n"
                       "  Expected:" expected "\n"
                       "    Actual:" computed))
        verified)
      (catch java.io.FileNotFoundException e
        (println-err "\nWarning: no sha1 checksum found for" uri)
        true))
    true))

(defn extract [archive]
  (ex/extract archive (or *extract-dir* *output-dir*)))

(defn download-and-extract
  [artifact]
  (let [url (url artifact)
        file (io/file *output-dir* (.getName (io/file url)))
        new-spec (and (download artifact file)
                      (verify-sum url file)
                      (extract file))]
    (when-not new-spec (System/exit 1))
    new-spec))

(defn overlay-dir
  [target source & [overwrite?]]
  (println "Overlaying" (str target))
  (fs/overlay source target overwrite?))

(defn overlay-config
  [to from]
  (doseq [cfg config-files]
    (let [config (io/file from cfg)
          file (io/file to cfg)]
      (when (and (.exists config) (.exists file))
        (println "Overlaying" (str file))
        (io/copy (xml/stringify
                  (xml/overlay
                   (xml/zip-file config)
                   (xml/zip-file file)))
                 file)))))

(defn overlay-extra
  [to from]
  (when (.exists (io/file from "jboss"))
    (doseq [dir (.listFiles (io/file from))]
      (let [name (.getName dir)]
        (if-not (= name "jboss")
          (overlay-dir (io/file to name) dir))))))

(defn jboss-dir
  [dir]
  (let [sub (io/file dir "jboss")]
    (if (.exists sub) sub dir)))

(defn path
  [spec]
  (let [file (io/file spec)]
    (if (.exists file)
      (if (.isDirectory file)
        file
        (recur (extract file)))
      (recur (download-and-extract (artifact spec))))))

(defn overlay
  [& argv]
  (let [args (opts/parse argv)
        layee (path (:layee args))]
    (when-let [source (:layer args)]
      (let [layer (path source)
            this (jboss-dir layee)
            that (jboss-dir layer)]
        (overlay-dir (io/file this "modules") (io/file that "modules") (:overwrite? args))
        (overlay-config this that)
        (overlay-extra layee layer)))))

(defn usage []
  (if (.exists (io/file "README.md"))
    (println-err (slurp "README.md")))
  (println-err "Valid features: " (map name overlayable-features) "\n"))
