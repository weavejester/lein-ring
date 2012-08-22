(ns leiningen.ring.util)

(defn ensure-handler-set!
  "Ensure the :handler option is set in the project map."
  [project]
  (when-not (-> project :ring :handler)
    (println
     (str "Missing Ring :handler option in project map.\n\n"
          "You need to have a line in your project.clj file that looks like:\n"
          "  :ring {:handler your.app/handler}"))
    (System/exit 1)))