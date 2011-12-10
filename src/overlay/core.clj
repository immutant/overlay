(ns overlay.core
  (:require [clojure.java.io :as io])
  (:require [clojure.java.shell :as shell])
  (:require [overlay.filesystem :as fs])
  (:require [overlay.xml :as xml])
  (:gen-class))

(def repository "http://repository-torquebox.forge.cloudbees.com")
(def output-dir "target/")

(defn incremental
  "Return the correct URL for app, artifact, and version"
  [app artifact & [version]]
  (let [file (if (keyword? artifact)
               (format "%s-dist-%s.zip" (name app) (name artifact))
               artifact)]
    (format "%s/incremental/%s/%s/%s" repository (name app) (or version "LATEST") file)))

(defn download [src dest]
  (println "Downloading" src)
  (.mkdirs (.getParentFile (io/file dest)))
  (with-open [input (io/input-stream src)
              output (io/output-stream dest)]
    (io/copy input output)))

(defn extract-java
  "Multi-platform, but won't preserve unix file permissions"
  [archive dir]
  (with-open [zip (java.util.zip.ZipFile. archive)] 
    (doseq [entry (enumeration-seq (.entries zip))]
      (let [file (io/file dir (.getName entry))]
        (if (.isDirectory entry)
          (.mkdirs file)
          (io/copy (.getInputStream zip entry) file))))))

(defn extract-shell
  "Attempts to shell out to 'unzip' command"
  [archive dir]
  (shell/sh "unzip" "-q" "-o""-d" (str dir) archive))

(defn extract
  "Assumes the archive has a single top-level directory and returns its File"
  [archive dir]
  (println "Extracting" archive)
  (let [tmp (io/file dir ".vanilla-extract")]
    (fs/delete-file-recursively tmp :quietly)
    (.mkdirs tmp)
    (try
      (extract-shell archive tmp)
      (catch Throwable e
        (extract-java archive tmp)))
    (let [top (first (.listFiles tmp))
          target (io/file dir (.getName top))]
      (if (.exists target) (fs/delete-file-recursively target))
      (.renameTo top target)
      (.delete tmp)
      (println "Extracted" (str target))
      target)))

(defn download-and-extract [uri]
  (let [name (.getName (io/file uri))
        local (str output-dir name)]
    (download uri local)
    (extract local output-dir)))
    
(defn latest []
  (let [torquebox (download-and-extract (incremental :torquebox :bin))
        modules (download-and-extract (incremental :immutant :modules))]
    (let [dir (io/file torquebox "jboss" "modules")]
      (println "Overlaying" (str dir))
      (fs/overlay modules dir))
    (let [file (io/file torquebox "jboss/standalone/configuration/standalone.xml")]
      (println "Overlaying" (str file))
      (io/copy (xml/stringify (xml/overlay
                               (xml/zip-string (slurp (incremental :immutant "standalone.xml")))
                               :onto (xml/zip-file file)
                               :ignore #(contains? #{:endpoint-config :virtual-server} (:tag %))))
               file))))
  
(defn -main [& args]
  ;; Avoid a 60s delay after this method completes
  (.setKeepAliveTime clojure.lang.Agent/soloExecutor 100 java.util.concurrent.TimeUnit/MILLISECONDS)

  (println "This might take a while...")
  (println "Clearing" output-dir)
  (fs/delete-file-recursively output-dir :quietly)
  (latest))
