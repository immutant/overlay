(ns overlay.main
  (:use overlay.core))

(defn -main [& args]
  (if (empty? args)
    (usage)
    (binding [*verify-sha1-sum* true]
      (overlay (first args) (second args))))
  (shutdown-agents)
  nil)
