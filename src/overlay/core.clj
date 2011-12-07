(ns overlay.core
  (:require [overlay.filesystem :as fs])
  (:require [overlay.xml :as xml])
  (:gen-class))

(defn -main [& args]
  (println "Welcome to my project! These are your args:" args))
             