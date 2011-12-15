(ns overlay.core
  (:require [clojure.java.io :as io])
  (:require [clojure.java.shell :as shell])
  (:require [overlay.filesystem :as fs])
  (:require [overlay.xml :as xml])
  (:require [dl.download :as dl])
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
  (dl/download src dest))

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
  
(defn layer
  "Returns a 2-element tuple of modules path and config path to overlay"
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
  "Returns the path to the distro being overlaid"
  [spec]
  (let [dir (io/file spec)]
    (if (.exists dir)
      dir
      (let [[app version] (artifact-spec spec)]
        (if (contains? overlayable-apps app)
          (download-and-extract (incremental app :bin version))
          (download-and-extract spec))))))

(defn overlay
  ([target source]
     (if source
       (apply overlay (layee target) (layer source))
       (layee target)))  
  ([dir modules config]
     (let [[these-modules this-config] (find-modules-and-config dir)]
       (overlay-modules these-modules modules)
       (overlay-config this-config config))))
  
(defn usage []
  (println (slurp "README.md")))

(defn -main [& args]
  ;; Avoid a 60s delay after this method completes
  (.setKeepAliveTime clojure.lang.Agent/soloExecutor 100 java.util.concurrent.TimeUnit/MILLISECONDS)
  (if (empty? args)
    (usage)
    (overlay (first args) (second args)))
  nil)

