(ns overlay.test.core
  (:require [clojure.zip :as zip])
  (:use [overlay.core])
  (:use [clojure.test]))

(deftest simple-element-overlay
  (let [z1 (zip-string "<root><a/></root>")
        z2 (zip-string "<root><a/><b/></root>")
        ov (overlay z2 z1)]
    (is (= (zip/root ov) (zip/root z2)))))