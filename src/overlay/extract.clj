(ns overlay.extract
  (:use [overlay.filesystem :only [delete-file-recursively]])
  (:require [clojure.java.io    :as io]
            [clojure.java.shell :as shell]))

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
  (shell/sh "unzip" "-q" "-o" "-d" (str dir) (str archive)))

(defn extract
  "Assumes the archive has a single top-level directory and returns its File"
  [archive dir]
  (println "Extracting" (str archive))
  (let [tmp (io/file dir ".vanilla-extract")]
    (delete-file-recursively tmp :quietly)
    (.mkdirs tmp)
    (try
      (extract-shell archive tmp)
      (catch Throwable e
        (println "WARNING: failed to find 'unzip' on your path - falling back to extracting via java. Any executables extracted won't be +x")
        (extract-java archive tmp)))
    (let [top (first (.listFiles tmp))
          target (io/file dir (.getName top))]
      (if (.exists target) (delete-file-recursively target))
      (.renameTo top target)
      (.delete tmp)
      (println "Extracted" (str target))
      target)))

