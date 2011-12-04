(ns overlay.filesystem
  (:require [clojure.java.io :as io]))

(defn overlay
  "Overlay the contents of one directory onto another"
  [src tgt]
  (letfn [(relative [f]
            (io/file (.substring (.getPath f) (+ 1 (count src)))))
          (visit [dir]
            (doseq [here (.listFiles dir)]
              (let [there (io/file tgt (relative here))]
                (if (.isDirectory here)
                  (do (.mkdir there) (visit here))
                  (io/copy here there)))))]
    (.mkdirs (io/file tgt))
    (visit (io/file src))))
