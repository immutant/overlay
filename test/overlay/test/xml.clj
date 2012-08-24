(ns overlay.test.xml
  (:require [clojure.zip :as zip])
  (:require [clojure.string :as str])
  (:use [overlay.xml])
  (:use [clojure.test]))

(defn pretty [z]
  (stringify z))

(deftest simple-element-overlay
  (let [z1 (zip-string "<root><a/></root>")
        z2 (zip-string "<root><a/><b/></root>")
        ov (overlay z2 :onto z1)]
    (is (= (pretty z2) (pretty ov)))))

(deftest element-overlay-with-predicate
  (let [z1 (zip-string "<root><a/></root>")
        z2 (zip-string "<root><a/><b/></root>")
        ov (overlay z2 :onto z1 :pred =)]
    (is (= (pretty z2) (pretty ov)))))

(deftest element-insert-before
  (let [z1 (zip-string "<root><b/></root>")
        z2 (zip-string "<root><a/><b/></root>")
        ov (overlay z2 :onto z1)]
    (is (= (pretty z2) (pretty ov)))))

(deftest element-insert-middle
  (let [z1 (zip-string "<root><a/><c/></root>")
        z2 (zip-string "<root><a/><b/><c/></root>")
        ov (overlay z2 :onto z1)]
    (is (= (pretty z2) (pretty ov)))))

(deftest source-element-with-attribute-superset
  (let [z1 (zip-string "<root><a name='fred'/></root>")
        z2 (zip-string "<root><a name='fred' sex='m'/></root>")
        ov (overlay z2 :onto z1)]
    (is (= (pretty z2) (pretty ov)))))

(deftest file-overlay
  (let [i (zip-file "test-resources/standalone-immutant.xml")
        t (zip-file "test-resources/standalone-torquebox.xml")
        expect (zip-file "test-resources/standalone-overlaid.xml")
        ov (overlay i :onto t :ignore #(= (:tag %) :endpoint-config))]
    (is (= (zip/root expect) (zip/root ov)) (stringify ov))))
