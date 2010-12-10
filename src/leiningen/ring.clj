(ns leiningen.ring
  (:use [leiningen.help :only (help-for)]
        [leiningen.ring.server :only (server)]
        [leiningen.ring.war :only (war)]))

(defn ring 
  "Manage a Ring-based application."
  {:help-arglists '([server war])
   :subtasks [#'server #'war]}
  ([project]
     (println (help-for "ring")))
  ([project subtask & args]
     (case subtask
       "server" (apply server project args)
       "war"    (apply war project args))))
