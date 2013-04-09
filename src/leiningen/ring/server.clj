(ns leiningen.ring.server
  (:require [leinjacker.deps :as deps]
            [leiningen.core.classpath :as classpath]
            [clojure.tools.nrepl.server :as nrepl]
            [clojure.java.io :as io])
  (:use [leinjacker.eval :only (eval-in-project)]
        [leiningen.ring.util :only (ensure-handler-set! update-project)]))

(defn classpath-dirs 
  "list of all dirs on the leiningen classpath"
  [project]
  (filter
   #(.isDirectory (io/file %))
   (classpath/get-classpath project)))

(defn load-namespaces
  "Create require forms for each of the supplied symbols. This exists because
  Clojure cannot load and use a new namespace in the same eval form."
  [& syms]
  `(require
    ~@(for [s syms :when s]
        `'~(if-let [ns (namespace s)]
             (symbol ns)
             s))))

(defn reload-paths [project]
  (or (get-in project [:ring :reload-paths])
      (classpath-dirs project)))

(defn add-dep [project dep]
  (update-project project deps/add-if-missing dep))

(defn add-server-dep [project]
  (add-dep project '[ring-server/ring-server "0.2.8"]))

(defn add-nrepl-dep [project start-repl?]
  (if start-repl?
    (add-dep project '[org.clojure/tools.nrepl "0.2.2"])
    project))

(defn server-task
  "Shared logic for server and server-headless tasks."
  [project options]
  (ensure-handler-set! project)
  (let [project (-> project
                    (assoc-in [:ring :reload-paths] (reload-paths project))
                    (update-in [:ring] merge options))
        {nrepl-port :port
         start-nrepl? :start?
         :or {nrepl-port 0}} (get-in project [:ring :nrepl])]
    (eval-in-project
     (-> project add-server-dep (add-nrepl-dep start-nrepl?))
     `(do
        ~(when start-nrepl?
           `(let [{actual-port# :port} (nrepl/start-server :port ~nrepl-port)]
              (println "Started nREPL server on port" actual-port#)))
        (ring.server.leiningen/serve
         '~(select-keys project [:ring])))
     (load-namespaces
      'ring.server.leiningen
      (when start-nrepl? 'clojure.tools.nrepl.server)
      (-> project :ring :handler)
      (-> project :ring :init)
      (-> project :ring :destroy)))))

(defn server
  "Start a Ring server and open a browser."
  ([project]
     (server-task project {}))
  ([project port]
     (server-task project {:port (Integer. port)})))
