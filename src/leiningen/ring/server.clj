(ns leiningen.ring.server
  (:use [leiningen.compile :only (eval-in-project)]))

(defn load-namespaces
  "Create require forms for each of the supplied symbols. This exists because
  Clojure cannot load and use a new namespace in the same eval form."
  [& syms]
  `(require
    ~@(for [s syms :when s]
        `'~(if-let [ns (namespace s)]
             (symbol ns)
             s))))

(defn server-task
  "Shared logic for server and server-headless tasks."
  [project options]
  (let [options (merge (:ring project) options)]
    (eval-in-project
     project
     `(leiningen.ring.run-server/run-server ~options)
     nil nil
     (load-namespaces
      'leiningen.ring.run-server
      'ring.middleware.stacktrace
      'ring.middleware.reload
      (:handler options)
      (:init options)
      (:destroy options)))))

(defn server
  "Start a Ring server and open a browser."
  ([project]
     (server project nil))
  ([project port]
     (server-task project {:port port, :headless? false})))
