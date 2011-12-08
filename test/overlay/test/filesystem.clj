(ns overlay.test.filesystem
  (:use [overlay.filesystem])
  (:require [clojure.java.io :as io])
  (:use [clojure.test]))

(def output-dir "test-resources/output/")

(defn files
  "A recursive listing of the relative filenames beneath dir"
  [dir]
  (letfn [(contents [d]
            (reduce decide [] (.listFiles d)))
          (decide [c x]
            (if (.isDirectory x)
              (concat c [x] (contents x))
              (cons x c)))]
    (sort (map #(str (relative % dir)) (contents (io/file dir))))))

(use-fixtures :each (fn [f] (delete-file-recursively output-dir :quietly) (f)))

;; (deftest no-output-dir
;;   (println "no-output-dir")
;;   (is (not (.exists (io/file output-dir)))))

(deftest overlay-directory
  (println "overlay-directory")
  (let [from "test-resources/modules"
        to (str output-dir "copy-of-modules")]
    (overlay from to)
    (is (= (files from) (files to)))))