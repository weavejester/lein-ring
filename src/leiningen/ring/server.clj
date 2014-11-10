(ns leiningen.ring.server
  (:require [leinjacker.deps :as deps]
            [leiningen.core.classpath :as classpath]
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
  (add-dep project '[ring-server/ring-server "0.3.1"]))

(defn start-server-expr [project]
  `(ring.server.leiningen/serve '~(select-keys project [:ring])))

(defn nrepl? [project]
  (-> project :ring :nrepl :start?))

(defn add-optional-nrepl-dep [project]
  (if (nrepl? project)
    (add-dep project '[org.clojure/tools.nrepl "0.2.3"])
    project))

(defn start-nrepl-expr [project]
  (let [port (-> project :ring :nrepl (:port 0))
        middleware (-> project :ring :nrepl :nrepl-middleware)]
    `(let [{port# :port} (clojure.tools.nrepl.server/start-server :port ~port :handler (apply clojure.tools.nrepl.server/default-handler middleware))]
       (doseq [port-file# ["target/repl-port" ".nrepl-port"]]
         (-> port-file#
             java.io.File.
             (doto .deleteOnExit)
             (spit port#)))
       (println "Started nREPL server on port" port#))))

(defn server-task
  "Shared logic for server and server-headless tasks."
  [project options]
  (ensure-handler-set! project)
  (let [project (-> project
                    (assoc-in [:ring :reload-paths] (reload-paths project))
                    (update-in [:ring] merge options))]
    (eval-in-project
     (-> project add-server-dep add-optional-nrepl-dep)
     (if (nrepl? project)
       `(do ~(start-nrepl-expr project) ~(start-server-expr project))
       (start-server-expr project))
     (load-namespaces
      'ring.server.leiningen
      (if (nrepl? project) 'clojure.tools.nrepl.server)
      (-> project :ring :handler)
      (-> project :ring :init)
      (-> project :ring :destroy)))))

(defn server
  "Start a Ring server and open a browser."
  ([project]
     (server-task project {}))
  ([project port]
     (server-task project {:port (Integer. port)})))
