(ns overlay.filesystem
  (:require [clojure.java.io :as io]))

(defn delete-file-recursively
  "rm -rf"
  [f & [silently]]
  (let [f (io/file f)]
    (if (.isDirectory f)
      (doseq [child (.listFiles f)]
        (delete-file-recursively child silently)))
    (io/delete-file f silently)))

(defn relative
  "Return a File constructed with its path relative to a base path"
  [file base]
  (io/file (.substring (.getPath file) (+ 1 (count (str base))))))

(defn overlay
  "Overlay the contents of one directory onto another"
  [src tgt]
  (letfn [(visit [dir]
            (doseq [here (.listFiles dir)]
              (let [there (io/file tgt (relative here src))]
                (if (.isDirectory here)
                  (do (.mkdir there) (visit here))
                  (io/copy here there)))))]
    (.mkdirs (io/file tgt))
    (visit (io/file src))))
