(ns leiningen.ring
  (:use [leiningen.help :only (help-for)]
        [leiningen.ring.server :only (server)]))

(defn ring 
  "Manage a Ring-based application."
  {:help-arglists '([server])
   :subtasks [#'server]}
  ([project]
     (println (help-for "ring")))
  ([project subtask & args]
     (case subtask
       "server" (apply server project args))))
