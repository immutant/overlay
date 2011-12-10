(ns overlay.core
  (:require [clojure.java.io :as io])
  (:require [clojure.java.shell :as shell])
  (:require [overlay.filesystem :as fs])
  (:require [overlay.xml :as xml])
  (:gen-class))

(def immutant-latest-url "http://repository-torquebox.forge.cloudbees.com/incremental/immutant/LATEST/")
(def torquebox-latest-url "http://repository-torquebox.forge.cloudbees.com/incremental/torquebox/LATEST/")
(def output-dir "target/")

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

(defn extract
  "First attempts to shell out to 'unzip' command"
  [archive dir]
  (println "Extracting" archive)
  (try
    (.mkdirs (io/file dir))
    (shell/sh "unzip" "-q" "-o""-d" (str dir) archive)
    (catch Throwable e
      (extract-java archive dir))))

(defn extract-top-level
  "Assumes the archive has a single top-level directory and returns its name"
  [archive dir]
  (let [tmp (io/file dir ".overlay")]
    (extract archive tmp)
    (let [top (first (.listFiles tmp))
          target (io/file dir (.getName top))]
      (if (.exists target) (fs/delete-file-recursively target))
      (.renameTo top target)
      (.delete tmp)
      target)))

(defn download-and-extract [uri]
  (let [name (.getName (io/file uri))
        local (str output-dir name)]
    (download uri local)
    (extract-top-level local output-dir)))
    
(defn latest []
  (let [torquebox (download-and-extract (str torquebox-latest-url "torquebox-dist-bin.zip"))
        modules (download-and-extract (str immutant-latest-url "immutant-dist-modules.zip"))]
    (let [dir (io/file torquebox "jboss" "modules")]
      (println "Overlaying" (str dir))
      (fs/overlay modules dir))
    (let [file (io/file torquebox "jboss/standalone/configuration/standalone.xml")]
      (println "Overlaying" (str file))
      (io/copy (xml/stringify (xml/overlay
                               (xml/zip-string (slurp (str immutant-latest-url "standalone.xml")))
                               :onto (xml/zip-file file)
                               :ignore #(contains? #{:endpoint-config :virtual-server} (:tag %))))
               file))))
  
;; overlay one directory over another (copy over jruby and share, if present)

(defn -main [& args]
  ;; Avoid a 60s delay after this method completes
  (.setKeepAliveTime clojure.lang.Agent/soloExecutor 100 java.util.concurrent.TimeUnit/MILLISECONDS)

  (println "Be patient! This could take a while...")
  (println "Clearing" output-dir)
  (fs/delete-file-recursively output-dir)
  (latest))
