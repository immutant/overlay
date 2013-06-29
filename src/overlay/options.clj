(ns overlay.options)

(def option? (comp (partial = \-) first str))

(defn layee [m]
  (assoc m :layee (->> m :argv (remove option?) first)))

(defn layer [m]
  (assoc m :layer (->> m :argv (remove option?) second)))

(defn overwrite? [m]
  (assoc m :overwrite? (->> m :argv (some #{"-o" "--overwrite"}))))

(def parse* (comp layee layer overwrite?))

(defn parse
  [argv]
  (parse* {:argv argv}))
