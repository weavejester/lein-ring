(ns leiningen.ring.server-headless
  (:use leiningen.ring.server))

(defn server-headless
  "Start a Ring server without opening a browser."
  ([project]
     (server-task project {:open-browser? false}))
  ([project ^String port]
     (server-task project {:port (Integer. port), :open-browser? false})))
