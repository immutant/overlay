(ns overlay.core
  (:require [clojure.java.io :as io])
  (:require [clojure.xml :as xml])
  (:require [clojure.contrib.lazy-xml :as lazy-xml])
  (:require [clojure.zip :as zip]))

(declare overlay-siblings)

(defn zip-file [name]
  (zip/xml-zip (xml/parse (io/file name))))

(defn zip-string [s]
  (zip/xml-zip (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))))

(defn xml-node-equal [n p]
  (and (= (:tag n) (:tag p))
       (= (:attrs n) (:attrs p))))

(defn find-child [child {:keys [onto pred] :or {pred xml-node-equal}}]
  (let [target (zip/node child)]
    (loop [cur (zip/down onto)]
      (cond
       (nil? cur) nil
       (pred target (zip/node cur)) cur
       :else (recur (zip/right cur))))))

(defn overlay-child [child {:keys [onto] :as args}]
  (let [found (find-child child args)]
    (if found
      (zip/up (overlay-siblings (zip/down child) (assoc args :onto found)))
      (zip/append-child onto (zip/node child)))))

(defn overlay-siblings [child {:keys [onto] :as args}]
  (if (nil? child)
    onto
    (let [parent (overlay-child child args)]
      (recur (zip/right child) (assoc args :onto parent)))))

(defn overlay [src & {:keys [onto] :as args}]
  "Recursively overlay on one zipper with the nodes of another"
  (overlay-siblings (zip/down src) args))

(defn stringify [zipper]
  (with-out-str (lazy-xml/emit (zip/root zipper) :indent 2)))

