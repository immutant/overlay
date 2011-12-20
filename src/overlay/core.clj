(ns overlay.core
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.data.json :as json]
            [overlay.filesystem :as fs]
            [overlay.xml :as xml]
            [progress.file :as progress])
  (:use [overlay.extract :only [extract]]
        [clojure.string :only [split]])
  (:gen-class))

(def ^{:doc "The output dir used by overlay operations. Root binding ./target/"
       :dynamic true}
  *output-dir* (io/file "target"))
(def ^{:doc "The dir where artifacts will be extracted. If nil, *output-dir* is used as a fallback. Root binding is nil"
       :dynamic true}
  *extract-dir* nil)

(def repository "http://repository-torquebox.forge.cloudbees.com")
(def overlayable-apps #{:immutant :torquebox})
(def ignorable-elements #{:management-interfaces :endpoint-config :virtual-server})

(defn incremental
  "Return the correct URL for app, artifact, and version"
  [app artifact & [version]]
  (let [file (if (keyword? artifact)
               (format "%s-dist-%s.zip" (name app) (name artifact))
               artifact)]
    (format "%s/incremental/%s/%s/%s" repository (name app) (or version "LATEST") file)))

(defn metadata-url
  "Return the metadata url, but only for full binary distributions"
  [url]
  (if (.endsWith url "dist-bin.zip")
    (.replaceFirst url "/[^/]*$" "/build-metadata.json")))

(defn dist-filesize
  "Try to determine artifact size from build-metadata.json."
  [url]
  (if-let [metadata (metadata-url url)]
    (try
      (with-open [r (io/reader metadata)]
        (:dist_size (json/read-json (slurp r))))
      (catch Exception e
        nil))))

(defn filesize
  "Try to determine filesize of the artifact specified by src."
  [src]
  (let [f (io/file src)]
    (if (.exists f)
      (.length f)
      (dist-filesize src))))

(defn download [src dest]
  (println "Downloading" src)
  (.mkdirs (.getParentFile (io/file dest)))
  (progress/with-file-progress dest :filesize (filesize src)
    (with-open [in (io/input-stream src)]
      (io/copy in dest))))

(defn download-and-extract
  [uri]
  (let [name (.getName (io/file uri))
        file (io/file *output-dir* name)]
    (download uri file)
    (extract file (or *extract-dir* *output-dir*))))

(defn overlay-dir
  [target source]
  (println "Overlaying" (str target))
  (fs/overlay source target))

(defn overlay-config
  [file config]
  (println "Overlaying" (str file))
  (io/copy (xml/stringify
            (xml/overlay
             (xml/zip-string (slurp config))
             :onto (xml/zip-file file)
             :ignore #(contains? ignorable-elements (:tag %))))
           file))

(defn overlay-extra
  [to from]
  (when (.exists (io/file from "jboss"))
    (doseq [dir (.listFiles (io/file from))]
      (let [name (.getName dir)]
        (if-not (= name "jboss")
          (overlay-dir (io/file to name) dir))))))

(defn find-modules-and-config
  "Returns a 2-element tuple [modules-path, config-path] to overlay"
  [dir]
  (let [sub (io/file dir "jboss")
        jboss (if (.exists sub) sub dir)]
    [(io/file jboss "modules") (io/file jboss "standalone/configuration/standalone.xml")]))

(defn artifact-spec
  [spec]
  (let [[app version] (split spec #"\W")]
    [(keyword app) version]))

(defn path
  [spec]
  (let [dir (io/file spec)]
    (if (.exists dir)
      dir
      (let [[app version] (artifact-spec spec)
            uri (if (contains? overlayable-apps app)
                  (incremental app :bin version)
                  spec)]
        (recur (download-and-extract uri))))))

(defn overlay
  [target & [source]]
  (let [layee (path target)]
    (when source
      (let [layer (path source)
            [these-modules this-config] (find-modules-and-config layee)
            [those-modules that-config] (find-modules-and-config layer)]
        (overlay-dir these-modules those-modules)
        (overlay-config this-config that-config)
        (overlay-extra layee layer)))))

(defn usage []
  (println (slurp "README.md")))

(defn -main [& args]
  ;; Avoid a 60s delay after this method completes
  (.setKeepAliveTime clojure.lang.Agent/soloExecutor 100 java.util.concurrent.TimeUnit/MILLISECONDS)
  (if (empty? args)
    (usage)
    (overlay (first args) (second args)))
  nil)
