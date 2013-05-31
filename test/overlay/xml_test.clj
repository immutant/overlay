(ns overlay.xml-test
  (:require [clojure.zip :as zip]
            [clojure.string :as str]
            overlay.jboss)
  (:use overlay.xml
        clojure.test))

(defn pretty [z]
  (stringify z))

(defn subsystem [xmlns]
  {:tag :subsystem
   :attrs {:xmlns xmlns}})

(deftest simple-element-overlay
  (let [z1 (zip-string "<root><a/></root>")
        z2 (zip-string "<root><a/><b/></root>")
        ov (overlay z2 z1)]
    (is (= (pretty z2) (pretty ov)))))

(deftest element-insert-before
  (let [z1 (zip-string "<root><b/></root>")
        z2 (zip-string "<root><a/><b/></root>")
        ov (overlay z2 z1)]
    (is (= (pretty z2) (pretty ov)))))

(deftest element-insert-middle
  (let [z1 (zip-string "<root><a/><c/></root>")
        z2 (zip-string "<root><a/><b/><c/></root>")
        ov (overlay z2 z1)]
    (is (= (pretty z2) (pretty ov)))))

(deftest source-element-with-attribute-subset
  (let [z1 (zip-string "<root><a foo='x'/></root>")
        z2 (zip-string "<root><a foo='x' bar='y'/></root>")
        o1 (overlay z2 z1)
        o2 (overlay z1 z2)]
    (is (= z2 o1))
    (is (= z2 o2))))

(deftest source-element-with-name-attribute
  (let [z1 (zip-string "<root><a name='fred'/></root>")
        z2 (zip-string "<root><a name='fred' sex='m'/></root>")
        o1 (overlay z2 z1)
        o2 (overlay z1 z2)]
    (is (= z2 o1))
    (is (= z2 o2))))

(deftest standalone-overlay-torquebox-onto-immutant
  (let [i (zip-file "test-resources/standalone-immutant.xml")
        t (zip-file "test-resources/standalone-torquebox.xml")
        expect (zip-file "test-resources/standalone-overlaid-immutant.xml")
        ov (overlay t i)]
    (is (= (stringify expect) (stringify ov)))))

(deftest standalone-ha-overlay-torquebox-onto-immutant
  (let [i (zip-file "test-resources/standalone-ha-immutant.xml")
        t (zip-file "test-resources/standalone-ha-torquebox.xml")
        expect (zip-file "test-resources/standalone-ha-overlaid-immutant.xml")
        ov (overlay t i)]
    (is (= (stringify expect) (stringify ov)))))

(deftest standalone-overlay-immutant-onto-torquebox
  (let [i (zip-file "test-resources/standalone-immutant.xml")
        t (zip-file "test-resources/standalone-torquebox.xml")
        expect (zip-file "test-resources/standalone-overlaid-torquebox.xml")
        ov (overlay i t)]
    (is (= (stringify expect) (stringify ov)))))

(deftest standalone-ha-overlay-immutant-onto-torquebox
  (let [i (zip-file "test-resources/standalone-ha-immutant.xml")
        t (zip-file "test-resources/standalone-ha-torquebox.xml")
        expect (zip-file "test-resources/standalone-ha-overlaid-torquebox.xml")
        ov (overlay i t)]
    (is (= (stringify expect) (stringify ov)))))

(deftest subsystem-equality
  (is (false? (match-subsystem-name?
               (subsystem "urn:jboss:domain:jaxr:1.0")
               (subsystem "urn:jboss:domain:jaxrs:1.0"))))
  (is (false? (match-subsystem-name?
               (subsystem "urn:jboss:domain:jaxrs:1.0")
               (subsystem "urn:jboss:domain:jaxr:1.0"))))
  (is (true? (match-subsystem-name?
               (subsystem "urn:jboss:domain:logging:1.1")
               (subsystem "urn:jboss:domain:logging:1.2")))))

(deftest elements-differ-by-version
  (let [src (zip-string "<root><subsystem xmlns='urn:jboss:domain:messaging:1.3'/></root>")
        tgt (zip-string "<root><subsystem xmlns='urn:jboss:domain:messaging:1.1'/></root>")
        ov (overlay src tgt)]
    (is (= (pretty src) (pretty ov)))))

