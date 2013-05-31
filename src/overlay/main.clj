(ns overlay.main
  (:use overlay.core))

(defn -main [& args]
  (if (empty? args)
    (usage)
    (binding [*verify-sha1-sum* true]
      (apply overlay args)))
  (shutdown-agents)
  nil)
