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
  (with-out-str (lazy-xml/emit (zip/root zipper) :indent 4)))

(defn xml-node-equal
  "Ignore the content attribute of XML nodes"
  [n p]
  (and (= (:tag n) (:tag p))
       (let [src (:attrs n) tgt (:attrs p)]
         (every? (fn [[k v]] (= v (k src))) tgt))))

(defn subsystem-node-equal
  "Ignore the version of the xmlns"
  [n p]
  (and (= :subsystem (:tag n) (:tag p))
       (let [f (fn [m] (second (re-find #"(.*):[\d.]+$" (get-in m [:attrs :xmlns]))))
             x (f n)
             y (f p)]
         (and x (= x y)))))

(defn node-equal
  [n p]
  (or (subsystem-node-equal n p) (xml-node-equal n p)))

(defn xml-node-replace
  [node loc]
  (if (map? node)
    (assoc node :attrs (merge (:attrs node) (:attrs (zip/node loc))))
    node))

(defn find-child
  "Find a matching child among the children of the overlay-ee"
  [child {:keys [onto pred] :or {pred node-equal}}]
  (let [target (zip/node child)]
    (loop [cur (zip/down onto)]
      (cond
       (nil? cur) nil
       (pred target (zip/node cur)) cur
       :else (recur (zip/right cur))))))

(defn insert-child
  "The child must be inserted relative to its source siblings"
  [child {:keys [onto] :as args}]
  (loop [sibling (zip/left child)]
    (if (nil? sibling)
      (zip/insert-child onto (zip/node child))
      (if-let [found (find-child sibling args)]
        (zip/up (zip/insert-right found (zip/node child)))
        (recur (zip/left sibling))))))  

(defn overlay-child
  "If matching child found, recursively overlay its
   children. Otherwise, add the child onto the overlay-ee.
   TODO: or REPLACE node, e.g. newer version subsystem!"
  [child {:keys [onto] :as args}]
  (if-let [found (find-child child args)]
    (zip/up (overlay-siblings (zip/down child) (assoc args :onto (zip/edit found xml-node-replace child))))
    (insert-child child args)))

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


