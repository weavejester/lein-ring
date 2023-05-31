(ns leiningen.ring.server
  (:require [leinjacker.deps :as deps]
            [leiningen.core.classpath :as classpath]
            [clojure.java.io :as io])
  (:use [leinjacker.eval :only (eval-in-project)]
        [leiningen.ring.util :only (ensure-handler-set! update-project ring-version)]))

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
  (-> project
      (add-dep ['ring ring-version])
      (add-dep '[ring-server/ring-server "0.5.0"])))

(defn start-server-expr [project]
  `(ring.server.leiningen/serve '~(select-keys project [:ring])))

(defn nrepl? [project]
  (-> project :ring :nrepl :start?))

(defn add-optional-nrepl-dep [project]
  (if (nrepl? project)
    (add-dep project '[nrepl "1.0.0"])
    project))

(defn nrepl-middleware [project]
  (or (-> project :ring :nrepl :nrepl-middleware)
      (-> project :repl-options :nrepl-middleware)))

(defn nrepl-handler [middleware]
  `(nrepl.server/default-handler
     ~@(map #(if (symbol? %) (list 'var %) %) middleware)))

(defn start-nrepl-expr [project]
  (let [nrepl-opts (-> project :ring :nrepl)
        port (:port nrepl-opts 0)
        bind (:host nrepl-opts)
        handler (nrepl-handler (nrepl-middleware project))]
    `(let [{port# :port} (nrepl.server/start-server
                          :port ~port
                          :bind ~bind
                          :handler ~handler)]
       (doseq [port-file# ["target/repl-port" ".nrepl-port"]]
         (-> port-file#
             io/file
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
     (apply load-namespaces
            (conj (into
                   ['ring.server.leiningen
                    (if (nrepl? project) 'nrepl.server)]
                   (if (nrepl? project) (nrepl-middleware project)))
                  (-> project :ring :handler)
                  (-> project :ring :init)
                  (-> project :ring :destroy))))))

(defn server
  "Start a Ring server and open a browser."
  ([project]
     (server-task project {}))
  ([project ^String port]
     (server-task project {:port (Integer. port)})))
