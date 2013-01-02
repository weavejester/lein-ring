(ns leiningen.ring.server
  (:require 
    [leinjacker.deps :as deps]
    [leiningen.ring.src-paths :refer [src-paths]])
  (:use [leinjacker.eval :only (eval-in-project)]
        [leiningen.ring.util :only (ensure-handler-set! update-project)]))

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
  (update-project project deps/add-if-missing '[ring-server "0.2.6"]))

(defn server-task
  "Shared logic for server and server-headless tasks."
  [project options]
  (ensure-handler-set! project)
  (let [options (assoc options :reload-paths (src-paths project))
        project (update-in project [:ring] merge options)]
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
