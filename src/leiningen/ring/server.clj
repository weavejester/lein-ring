(ns leiningen.ring.server
  (:require 
    [leinjacker.deps :as deps]
    [leiningen.core.classpath :as classpath])
  (:use [leinjacker.eval :only (eval-in-project)]
        [leiningen.ring.util :only (ensure-handler-set! update-project)])
  (:import  java.io.File))

(defn classpath-dirs 
  "list of all dirs on the leiningen classpath"
  [project]
  (for [item (classpath/get-classpath project)
        :when (.isDirectory (File. item))]
    item))

(defn load-namespaces
  "Create require forms for each of the supplied symbols. This exists because
  Clojure cannot load and use a new namespace in the same eval form."
  [& syms]
  `(require
    ~@(for [s syms :when s]
        `'~(if-let [ns (namespace s)]
             (symbol ns)
             s))))

(defn add-server-dep [project]
  (update-project project deps/add-if-missing '[ring-server "0.2.7"]))

(defn server-task
  "Shared logic for server and server-headless tasks."
  [project options]
  (ensure-handler-set! project)
  (let [project (update-in project [:ring] merge options)
        project (if (get-in project [:ring :reload-paths])
                  project
                  (assoc-in project [:ring :reload-paths] (classpath-dirs project)))
        ]
    (eval-in-project
     (add-server-dep project)
     `(ring.server.leiningen/serve
       '~(select-keys project [:ring]))
     (load-namespaces
      'ring.server.leiningen
      (-> project :ring :handler)
      (-> project :ring :init)
      (-> project :ring :destroy)))))

(defn server
  "Start a Ring server and open a browser."
  ([project]
     (server-task project {}))
  ([project port]
     (server-task project {:port (Integer. port)})))
