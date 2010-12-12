(ns leiningen.ring
  (:use [leiningen.help :only (help-for)]
        [leiningen.ring.server :only (server)]
        [leiningen.ring.war :only (war)]
        [leiningen.ring.uberwar :only (uberwar)]))

(defn ring 
  "Manage a Ring-based application."
  {:help-arglists '([server war uberwar])
   :subtasks [#'server #'war #'uberwar]}
  ([project]
     (println (help-for "ring")))
  ([project subtask & args]
     (case subtask
       "server"  (apply server project args)
       "war"     (apply war project args)
       "uberwar" (apply uberwar project args))))
