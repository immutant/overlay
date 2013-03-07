(ns overlay.xml
  (:require [clojure.java.io :as io]
            [clojure.xml     :as xml]
            [clojure.zip     :as zip]
            [clojure.string  :as str]))

(declare overlay-siblings)

(defn zip-file
  "Create a zipper from a filename"
  [name]
  (zip/xml-zip (xml/parse (io/file name))))

(defn zip-string
  "Create a zipper from a string containing xml"
  [s]
  (zip/xml-zip (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))))

(def ^:dynamic *indent* 4)

(defn- print-indent [level]
  (print (apply str (repeat (* level *indent*) \ ))))

;; borrowed from clojure.xml and modified to indent
(defn- indenting-emit-element
  ([e]
     (indenting-emit-element e 0))
  ([e level]
     (if (instance? String e)
       (println e)
       (do
         (print-indent level)
         (print (str "<" (name (:tag e))))
         (when (:attrs e)
           (doseq [attr (:attrs e)]
             (print (str " " (name (key attr)) "='" (val attr)"'"))))
         (if (:content e)
           (do
             (let [content (:content e)]
               (if (instance? String (first content))
                 (do
                   (print ">")
                   (print (str/trim (first content))))
                 (do 
                   (println ">")
                   (doseq [c content]
                     (indenting-emit-element c (inc level)))
                   (print-indent level))))
             (println (str "</" (name (:tag e)) ">")))
           (println "/>"))))))

(defn stringify
  "Pretty print an xml zipper"
  [zipper]
  (with-out-str
    (with-redefs [xml/emit-element indenting-emit-element]
      (xml/emit (zip/root zipper)))))

(defn same-attr-subset
  "One node has a subset of the attrs of the other"
  [n p]
  (and (= (:tag n) (:tag p))
       (let [src (:attrs n) tgt (:attrs p)]
         (or (every? (fn [[k v]] (= v (k src))) tgt)
             (every? (fn [[k v]] (= v (k tgt))) src)))))

(defn same-subsystem-name
  "Ignores the version of the xmlns"
  [n p]
  (and (= :subsystem (:tag n) (:tag p))
       (let [f (fn [m] (second (re-find #"(.*):[\d.]+$" (get-in m [:attrs :xmlns]))))
             x (f n)
             y (f p)]
         (and x (= x y)))))

(defn same-name-attr
  "Each node has the same name"
  [n p]
  (and (= (:tag n) (:tag p))
       (let [x (get-in n [:attrs :name])
             y (get-in p [:attrs :name])]
         (and x (= x y)))))

(defn node-equal
  [n p]
  (or (same-name-attr n p)
      (same-subsystem-name n p)
      (same-attr-subset n p)))

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


