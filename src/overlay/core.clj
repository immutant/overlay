(ns overlay.core
  (:require [clojure.java.io :as io])
  (:require [clojure.java.shell :as shell])
  (:require [overlay.filesystem :as fs])
  (:require [overlay.xml :as xml])
  (:use [overlay.extract :only [extract]])
  (:use [clojure.string :only [split]])
  (:gen-class))

(def repository "http://repository-torquebox.forge.cloudbees.com")
(def output-dir "target/")
(def overlayable-apps #{:immutant :torquebox})
(def ignorable-elements #{:management-interfaces :endpoint-config :virtual-server})

(defn incremental
  "Return the correct URL for app, artifact, and version"
  [app artifact & [version]]
  (let [file (if (keyword? artifact)
               (format "%s-dist-%s.zip" (name app) (name artifact))
               artifact)]
    (format "%s/incremental/%s/%s/%s" repository (name app) (or version "LATEST") file)))

(defn download [src dest]
  (println "Downloading" src)
  (.mkdirs (.getParentFile (io/file dest)))
  (with-open [input (io/input-stream src)
              output (io/output-stream dest)]
    (io/copy input output)))

(defn download-and-extract [uri & [dir]]
  (let [name (.getName (io/file uri))
        file (io/file (or dir output-dir) name)]
    (download uri file)
    (extract file (.getParentFile file))))

(defn overlay-modules
  [dir modules]
  (println "Overlaying" (str dir))
  (fs/overlay modules dir))
  
(defn overlay-config
  [file config]
  (println "Overlaying" (str file))
  (io/copy (xml/stringify
            (xml/overlay
             (xml/zip-string (slurp config))
             :onto (xml/zip-file file)
             :ignore #(contains? ignorable-elements (:tag %))))
           file))

(defn find-modules-and-config
  [dir]
  (let [sub (io/file dir "jboss")
        jboss (if (.exists sub) sub dir)]
    [(io/file jboss "modules") (io/file jboss "standalone/configuration/standalone.xml")]))

(defn artifact-spec
  [spec]
  (let [[app version] (split spec #"\W")]
    [(keyword app) version]))
  
(defn overlay
  [dir modules config]
  (let [[these-modules this-config] (find-modules-and-config dir)]
    (overlay-modules these-modules modules)
    (overlay-config this-config config)))

(defn layer
  [spec]
  (let [dir (io/file spec)]
    (if (.exists dir)
      (find-modules-and-config dir)
      (let [[app version] (artifact-spec spec)]
        (if (contains? overlayable-apps app)
          [(download-and-extract (incremental app :modules version)),
           (incremental app "standalone.xml")]
          (layer (download-and-extract spec)))))))

(defn layee
  [spec]
  (let [dir (io/file spec)]
    (if (.exists dir)
      dir
      (let [[app version] (artifact-spec spec)]
        (if (contains? overlayable-apps app)
          (download-and-extract (incremental app :bin version))
          (download-and-extract spec))))))
  
(defn latest []
  (apply overlay (layee "torquebox") (layer "immutant")))

(defn usage []
  (println (slurp "README.md")))

(defn -main [& args]
  ;; Avoid a 60s delay after this method completes
  (.setKeepAliveTime clojure.lang.Agent/soloExecutor 100 java.util.concurrent.TimeUnit/MILLISECONDS)
  (if (empty? args)
    (usage)
    (let [target (layee (first args))
          source (second args)]
      (and source (apply overlay target (layer source))))))

