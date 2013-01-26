(defproject org.immutant/overlay "1.3.0"
  :description "Overlays features from one JBoss AS7 installation onto another"
  :url "http://github.com/immutant/overlay"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojure/data.json "0.2.0"]
                 [digest "1.4.0"]
                 [progress "1.0.1"]
                 [clj-http-lite "0.2.0"]]
  :aliases {"overlay" ["run" "-m" "overlay.main"]})
