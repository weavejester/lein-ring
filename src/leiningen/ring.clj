(ns leiningen.ring
  (:require [leiningen.help :as help]))

(defn run [])

(defn war [])

(defn ring 
  "Manage a Ring-based application."
  {:help-arglists '([run war])
   :subtasks [#'run #'war]}
  ([project]
     (println (help/help-for "ring")))
  ([project subtask]
     (println "Do something")))
