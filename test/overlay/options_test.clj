(ns overlay.options-test
  (:use overlay.options
        clojure.test))

(deftest no-args
  (let [m {:argv []}]
    (is (nil? (:layee (layee m))))
    (is (nil? (:layer (layer m))))
    (is (not (:overwrite? (overwrite? m))))))

(deftest layee-only
  (let [m {:argv ["/some/path"]}]
    (is (= "/some/path" (:layee (layee m))))))

(deftest layee-only-with-options
  (let [m {:argv ["-p" "/some/path" "--help"]}]
    (is (= "/some/path" (:layee (layee m))))))

(deftest layee-with-layer
  (let [m {:argv ["layee" "layer"]}
        v ((comp layee layer) m)]
    (is (= "layee" (:layee v)))
    (is (= "layer" (:layer v)))))

(deftest layee-with-layer-and-options
  (let [m {:argv ["-1" "layee" "--two" "layer" "-h"]}
        v ((comp layee layer) m)]
    (is (= "layee" (:layee v)))
    (is (= "layer" (:layer v)))))

(deftest overwrite-option
  (let [m {:argv ["layee" "layer" "-o"]}]
    (is (:overwrite? (overwrite? m))))
  (let [m {:argv ["--overwrite" "layee" "layer"]}]
    (is (:overwrite? (overwrite? m)))))

(deftest parse-options
  (let [v (parse ["-o" "layee" "layer"])]
    (is (:overwrite? v))
    (is (= "layee" (:layee v)))
    (is (= "layer" (:layer v)))))
