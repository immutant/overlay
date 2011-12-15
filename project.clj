(defproject org.immutant/overlay "1.0.0-SNAPSHOT"
  :description "Overlays modules from one JBoss AS7 installation onto another"
  :url "http://github.com/immutant/overlay"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [dl "1.0.0"]]
  :dev-dependencies [[lein-clojars "0.7.0"]]
  :main overlay.core)
