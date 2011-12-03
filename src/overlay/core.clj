(ns overlay.core
  (:require [clojure.java.io :as io])
  (:require [clojure.xml :as xml])
  (:require [clojure.contrib.lazy-xml :as lazy-xml])
  (:require [clojure.zip :as zip]))

(declare overlay)

(defn zip-file [name]
  (zip/xml-zip (xml/parse (io/file name))))

(defn zip-string [s]
  (zip/xml-zip (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))))

(defn find-child [child parent pred]
  (let [target (zip/node child)]
    (loop [cur (zip/down parent)]
      (cond
       (nil? cur) nil
       (pred target (zip/node cur)) cur
       :else (recur (zip/right cur))))))

(defn overlay-child [child parent pred]
  (let [found (find-child child parent pred)]
    (if found
      (zip/up (overlay child found pred))
      (zip/append-child parent (zip/node child)))))

(defn overlay-siblings [child parent pred]
  (if (nil? child)
    parent
    (let [new-parent (overlay-child child parent pred)]
      (recur (zip/right child) new-parent pred))))

(defn eq [n p]
  (and (= (:tag n) (:tag p)) (= (:attrs n) (:attrs p))))

(defn overlay [src tgt & [pred]]
  "Recursively overlay each child of src onto tgt"
  (overlay-siblings (zip/down src) tgt (or pred eq)))

(defn stringify [zipper]
  (with-out-str (lazy-xml/emit (zip/root zipper) :indent 2)))

