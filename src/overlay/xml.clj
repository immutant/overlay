(ns overlay.xml
  (:require [clojure.java.io :as io])
  (:require [clojure.xml :as xml])
  (:require [clojure.contrib.lazy-xml :as lazy-xml])
  (:require [clojure.zip :as zip]))

(declare overlay-siblings)

(defn zip-file
  "Create a zipper from a filename"
  [name]
  (zip/xml-zip (xml/parse (io/file name))))

(defn zip-string
  "Create a zipper from a string containing xml"
  [s]
  (zip/xml-zip (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))))

(defn stringify
  "Pretty print an xml zipper"
  [zipper]
  (with-out-str (lazy-xml/emit (zip/root zipper) :indent 2)))

(defn xml-node-equal
  "Ignore the content attribute of XML nodes"
  [n p]
  (and (= (:tag n) (:tag p))
       (= (:attrs n) (:attrs p))))

(defn find-child
  "Find a matching child among the children of the overlay-ee"
  [child {:keys [onto pred] :or {pred
  xml-node-equal}}]
  (let [target (zip/node child)]
    (loop [cur (zip/down onto)]
      (cond
       (nil? cur) nil
       (pred target (zip/node cur)) cur
       :else (recur (zip/right cur))))))

(defn overlay-child
  "If matching child found, recursively overlay its
   children. Otherwise append the child onto the overlay-ee."
  [child {:keys [onto] :as args}]
  (let [found (find-child child args)]
    (if found
      (zip/up (overlay-siblings (zip/down child) (assoc args :onto found)))
      (zip/append-child onto (zip/node child)))))

(defn overlay-siblings
  "Overlay each sibling of the child onto the target"
  [child {:keys [onto ignore] :or {ignore #{}} :as args}]
  (cond
   (nil? child) onto
   (ignore (zip/node child)) (recur (zip/right child) args)
   :else (recur (zip/right child) (assoc args :onto (overlay-child child args)))))

(defn overlay
  "Overlay one zipper onto another"
  [src & {:keys [onto] :as args}]
  (overlay-siblings (zip/down src) args))


