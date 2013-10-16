(ns overlay.extract
  (:use [overlay.filesystem :only [delete-file-recursively +x-sh-scripts]])
  (:require [clojure.java.io :as io]))

(defn extract-java
  "Multi-platform, but won't preserve unix file permissions"
  [archive dir]
  (with-open [zip (java.util.zip.ZipFile. archive)] 
    (doseq [entry (enumeration-seq (.entries zip))]
      (let [file (io/file dir (.getName entry))]
        (if (.isDirectory entry)
          (.mkdirs file)
          (io/copy (.getInputStream zip entry) file))))))

(defn move-extract [from to]
  (let [top (first (.listFiles from))
        target (io/file to (.getName top))]
      (if (.exists target) (delete-file-recursively target))
      (.renameTo top target)
      (.delete from)
      target))

(defn extract
  "Assumes the archive has a single top-level directory and returns its File"
  [archive dir]
  (println "Extracting" (str archive))
  (let [tmp (io/file dir ".vanilla-extract")]
    (delete-file-recursively tmp :quietly)
    (.mkdirs tmp)
    (extract-java archive tmp)
    (+x-sh-scripts tmp)
    (let [final (move-extract tmp dir)]
      (println "Extracted" (str final))
      final)))
