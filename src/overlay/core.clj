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

(defn overlay-dir
  [target source]
  (println "Overlaying" (str target))
  (fs/overlay source target))
  
(defn overlay-config
  [file config]
  (println "Overlaying" (str file))
  (io/copy (xml/stringify
            (xml/overlay
             (xml/zip-string (slurp config))
             :onto (xml/zip-file file)
             :ignore #(contains? ignorable-elements (:tag %))))
           file))

(defn overlay-extra
  [to from]
  (when (.exists (io/file from "jboss"))
    (doseq [dir (.listFiles (io/file from))]
      (let [name (.getName dir)]
        (if-not (= name "jboss")
          (overlay-dir (io/file to name) dir))))))

(defn find-modules-and-config
  "Returns a 2-element tuple [modules-path, config-path] to overlay"
  [dir]
  (let [sub (io/file dir "jboss")
        jboss (if (.exists sub) sub dir)]
    [(io/file jboss "modules") (io/file jboss "standalone/configuration/standalone.xml")]))

(defn artifact-spec
  [spec]
  (let [[app version] (split spec #"\W")]
    [(keyword app) version]))

(defn path
  [spec]
  (let [dir (io/file spec)]
    (if (.exists dir)
      dir
      (let [[app version] (artifact-spec spec)
            uri (if (contains? overlayable-apps app)
                  (incremental app :bin version)
                  spec)]
        (recur (download-and-extract uri))))))

(defn overlay
  [target & [source]]
  (let [layee (path target)]
    (when source
      (let [layer (path source)
            [these-modules this-config] (find-modules-and-config layee)
            [those-modules that-config] (find-modules-and-config layer)]
        (overlay-dir these-modules those-modules)
        (overlay-config this-config that-config)
        (overlay-extra layee layer)))))
  
(defn usage []
  (println (slurp "README.md")))

(defn -main [& args]
  ;; Avoid a 60s delay after this method completes
  (.setKeepAliveTime clojure.lang.Agent/soloExecutor 100 java.util.concurrent.TimeUnit/MILLISECONDS)
  (if (empty? args)
    (usage)
    (overlay (first args) (second args)))
  nil)

