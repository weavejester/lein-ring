(ns leiningen.ring
  (:use [leiningen.help :only (help-for)]
        [leiningen.ring.server :only (server)]
        [leiningen.ring.server-headless :only (server-headless)]
        [leiningen.ring.war :only (war)]
        [leiningen.ring.uberwar :only (uberwar)]))

(defn ring 
  "Manage a Ring-based application."
  {:help-arglists '([server server-headless war uberwar])
   :subtasks [#'server #'server-headless #'war #'uberwar]}
  ([project]
     (println (help-for "ring")))
  ([project subtask & args]
     (case subtask
       "server"          (apply server project args)
       "server-headless" (apply server-headless project args)
       "war"             (apply war project args)
       "uberwar"         (apply uberwar project args))))
