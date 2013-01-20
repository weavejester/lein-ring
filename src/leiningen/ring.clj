(ns leiningen.ring
  (:use [leiningen.help :only (help-for subtask-help-for)]
        [leiningen.ring.server :only (server)]
        [leiningen.ring.server-headless :only (server-headless)]
        [leiningen.ring.jar :only (jar)]
        [leiningen.ring.uberjar :only (uberjar)]
        [leiningen.ring.war :only (war)]
        [leiningen.ring.uberwar :only (uberwar)]))

(defn- nary? [v n]
  (some #{n} (map count (:arglists (meta v)))))

(defn ring
  "Manage a Ring-based application."
  {:help-arglists '([server server-headless war uberwar jar uberjar])
   :subtasks [#'server #'server-headless #'war #'uberwar #'jar #'uberjar]}
  ([project]
     (println (if (nary? #'help-for 2)
                (help-for project "ring")
                (help-for "ring"))))
  ([project subtask & args]
     (case subtask
       "server"          (apply server project args)
       "server-headless" (apply server-headless project args)
       "jar"             (apply jar project args)
       "uberjar"         (apply uberjar project args)
       "war"             (apply war project args)
       "uberwar"         (apply uberwar project args)
                         (println "Subtask" (str \" subtask \") "not found."
                                  (subtask-help-for *ns* #'ring)))))
