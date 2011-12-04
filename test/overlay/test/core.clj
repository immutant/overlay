(ns overlay.test.core
  (:require [clojure.zip :as zip])
  (:use [overlay.core])
  (:use [clojure.test]))

(deftest simple-element-overlay
  (let [z1 (zip-string "<root><a/></root>")
        z2 (zip-string "<root><a/><b/></root>")
        ov (overlay z2 :onto z1)]
    (is (= (zip/root ov) (zip/root z2)))))

(deftest element-overlay-with-predicate
  (let [z1 (zip-string "<root><a/></root>")
        z2 (zip-string "<root><a/><b/></root>")
        ov (overlay z2 :onto z1 :pred =)]
    (is (= (zip/root ov) (zip/root z2)))))

(deftest file-overlay
  (let [i (zip-file "test-resources/standalone-immutant.xml")
        t (zip-file "test-resources/standalone-torquebox.xml")
        expect (zip-file "test-resources/standalone-overlaid.xml")
        ov (overlay i :onto t :ignore #(= (:tag %) :endpoint-config))]
    (is (= (zip/root expect) (zip/root ov)))))