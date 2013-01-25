(ns overlay.core
  (:require [clojure.java.io      :as io]
            [clojure.java.shell   :as shell]
            [clojure.data.json    :as json]
            [digest               :as digest]
            [overlay.filesystem   :as fs]
            [overlay.extract      :as ex]
            [overlay.xml          :as xml]
            [progress.file        :as progress]
            [clj-http.lite.client :as http])
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

(def repository "http://repository-projectodd.forge.cloudbees.com")
(def overlayable-apps #{:immutant :torquebox})
(def ignorable-elements #{:management-interfaces :endpoint-config :virtual-server :cache-container :mod-cluster-config})
(def config-files ["standalone/configuration/standalone.xml"
                   "standalone/configuration/standalone-ha.xml"
                   "standalone/configuration/standalone-full.xml"
                   "domain/configuration/domain.xml"])

(defn println-err [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn metadata-url
  "Return the metadata url, but only for full binary distributions"
  [url]
  (if (.endsWith url "dist-bin.zip")
    (.replaceFirst url "/[^/]*$" "/build-metadata.json")))

(defprotocol BinArtifact
  "Collects relevant information for a particular binary artifact to be overlayed."
  (url [_] "The URL from which the artifact may be downloaded.")
  (filesize [_] "The size of the downloaded artifact."))

(defrecord Incremental [app version]
  BinArtifact
  (url [_]
       (let [file (format "%s-dist-bin.zip" (name app))]
         (format "%s/incremental/%s/%s/%s" repository (name app) (or version "LATEST") file)))
  (filesize [this]
   (let [metadata (metadata-url (url this))]
     (with-open [r (io/reader metadata)]
       (:dist_size (json/read-str (slurp r) :key-fn keyword))))))

(defrecord Release [app version]
  BinArtifact
  (url [_]
       (let [app-name (name app)
             file (format "%s-dist-%s-bin.zip" app-name version)]
         (format "%s/release/org/%s/%s-dist/%s/%s" repository app-name app-name version file)))
  (filesize [this]
    (-> (http/head (url this))
        :headers
        (get "content-length")
        Integer.)))

(defn released-version? [version]
  (and version (.contains version ".")))

(defn artifact-spec
  [spec]
  (let [[app version] (split spec #"-")]
    [(keyword app) version]))

(defn artifact
  "Return the correct artifact based on the arguments passed"
  ([spec]
   (apply artifact (artifact-spec (str spec))))
  ([app version]
  ((if (released-version? version) ->Release ->Incremental) app version)))

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
    (do (println-err "Error: No artifact found for" (-> artifact :app name)
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
  [target source]
  (println "Overlaying" (str target))
  (fs/overlay source target))

(defn overlay-config
  [to from]
  (doseq [cfg config-files]
    (let [config (io/file from cfg)
          file (io/file to cfg)]
      (println "Overlaying" (str file))
      (io/copy (xml/stringify
                (xml/overlay
                 (xml/zip-file config)
                 :onto (xml/zip-file file)
                 :ignore #(contains? ignorable-elements (:tag %))))
               file))))

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
      (let [[app version] (artifact-spec spec)]
        (if (contains? overlayable-apps app)
          (recur (download-and-extract (artifact spec)))
          ((println-err "Don't know how to overlay" (str app))
           (System/exit 1)))))))

(defn overlay
  [target & [source]]
  (let [layee (path target)]
    (when source
      (let [layer (path source)
            this-jboss (jboss-dir layee)
            that-jboss (jboss-dir layer)]
        (overlay-dir (io/file this-jboss "modules") (io/file that-jboss "modules"))
        (overlay-config this-jboss that-jboss)
        (overlay-extra layee layer)))))

(defn usage []
  (println-err (slurp "README.md")))
