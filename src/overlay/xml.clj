(ns overlay.xml
  (:require [clojure.java.io :as io]
            [clojure.xml     :as xml]
            [clojure.zip     :as zip]
            [clojure.string  :as str]))

(declare overlay)

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

(defn match-attr-subset?
  "One node has a subset of the attrs of the other"
  [n p]
  (and (= (:tag n) (:tag p))
       (let [src (:attrs n) tgt (:attrs p)]
         (or (every? (fn [[k v]] (= v (k src))) tgt)
             (every? (fn [[k v]] (= v (k tgt))) src)))))

(defn match-subsystem-name?
  "Ignores the version of the xmlns"
  [n p]
  (and (= :subsystem (:tag n) (:tag p))
       (let [f (fn [m] (second (re-find #"(.*):[\d.]+$" (get-in m [:attrs :xmlns]))))
             x (f n)
             y (f p)]
         (and x (= x y)))))

(defn match-name-attr?
  "Each node has the same name"
  [n p]
  (and (= (:tag n) (:tag p))
       (let [x (get-in n [:attrs :name])
             y (get-in p [:attrs :name])]
         (and x (= x y)))))

(defn match?
  [n p]
  (or (match-name-attr? n p)
      (match-subsystem-name? n p)
      (match-attr-subset? n p)))

(defn merge-attributes
  [node loc]
  (if (map? node)
    (assoc node :attrs (merge (:attrs node) (:attrs (zip/node loc))))
    node))

(defn find-child
  "Find a matching node among the children of the overlay-ee"
  [node tgt]
  (loop [cur (zip/down tgt)]
    (cond
     (nil? cur) nil
     (match? node (zip/node cur)) cur
     :else (recur (zip/right cur)))))

(defn insert-child
  "The child must be inserted relative to its source siblings"
  [src tgt]
  (loop [sibling (zip/left src)]
    (if (nil? sibling)
      (zip/insert-child tgt (zip/node src))
      (if-let [found (find-child (zip/node sibling) tgt)]
        (zip/up (zip/insert-right found (zip/node src)))
        (recur (zip/left sibling))))))  

(defn replace-child
  "Overwrite the target child with the source"
  [src tgt]
  (insert-child src (zip/remove (find-child (zip/node src) tgt))))

(defmulti overlay-child (fn [s t] (:tag (zip/node s))))

(defmethod overlay-child :default [src tgt]
  "If matching child found, recursively overlay its
   children. Otherwise, add the child onto the overlay-ee."
  (if-let [found (find-child (zip/node src) tgt)]
    (zip/up (overlay src (zip/edit found merge-attributes src)))
    (insert-child src tgt)))

(defn overlay
  "Overlay one zipper onto another"
  [src tgt]
  (loop [sibling (zip/down src) target tgt]
    (cond
     (nil? sibling) target
     :else (recur (zip/right sibling) (overlay-child sibling target)))))
