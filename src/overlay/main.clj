(ns overlay.main
  (:use overlay.core))

(defn -main [& args]
  (if (empty? args)
    (usage)
    (overlay (first args) (second args)))
  (shutdown-agents)
  nil)
