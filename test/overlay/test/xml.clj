(ns overlay.test.xml
  (:require [clojure.zip :as zip])
  (:require [clojure.string :as str])
  (:use [overlay.xml])
  (:use [clojure.test]))

(defn pretty [z]
  (stringify z))

(defn subsystem [xmlns]
  {:tag :subsystem
   :attrs {:xmlns xmlns}})

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
    (is (= z2 ov))))

(deftest standalone-overlay-torquebox-onto-immutant
  (let [i (zip-file "test-resources/standalone-immutant.xml")
        t (zip-file "test-resources/standalone-torquebox.xml")
        expect (zip-file "test-resources/standalone-overlaid-immutant.xml")
        ov (overlay t :onto i)]
    (is (= (zip/root expect) (zip/root ov)) (stringify ov))))

(deftest standalone-ha-overlay-torquebox-onto-immutant
  (let [i (zip-file "test-resources/standalone-ha-immutant.xml")
        t (zip-file "test-resources/standalone-ha-torquebox.xml")
        expect (zip-file "test-resources/standalone-ha-overlaid-immutant.xml")
        ov (overlay t :onto i)]
    (is (= (zip/root expect) (zip/root ov)) (stringify ov))))

(deftest standalone-overlay-immutant-onto-torquebox
  (let [i (zip-file "test-resources/standalone-immutant.xml")
        t (zip-file "test-resources/standalone-torquebox.xml")
        expect (zip-file "test-resources/standalone-overlaid-torquebox.xml")
        ov (overlay i :onto t)]
    (is (= (zip/root expect) (zip/root ov)) (stringify ov))))

(deftest standalone-ha-overlay-immutant-onto-torquebox
  (let [i (zip-file "test-resources/standalone-ha-immutant.xml")
        t (zip-file "test-resources/standalone-ha-torquebox.xml")
        expect (zip-file "test-resources/standalone-ha-overlaid-torquebox.xml")
        ov (overlay i :onto t)]
    (is (= (zip/root expect) (zip/root ov)) (stringify ov))))

(deftest subsystem-equality
  (is (false? (subsystem-node-equal
               (subsystem "urn:jboss:domain:jaxr:1.0")
               (subsystem "urn:jboss:domain:jaxrs:1.0"))))
  (is (false? (subsystem-node-equal
               (subsystem "urn:jboss:domain:jaxrs:1.0")
               (subsystem "urn:jboss:domain:jaxr:1.0"))))
  (is (true? (subsystem-node-equal
               (subsystem "urn:jboss:domain:logging:1.1")
               (subsystem "urn:jboss:domain:logging:1.2")))))
