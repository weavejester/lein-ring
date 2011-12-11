(ns leiningen.ring.server
  (:use [leiningen.compile :only (eval-in-project)]))

(defn- sym-and-ns [project key]
  (if-let [sym (get-in project [:ring key])]
    [sym (symbol (namespace sym))]))

(defn- require-sym [sym]
  (if sym
    (list `(require '~sym))))

(defn server-task
  "Shared logic for server and server-headless tasks."
  [project port launch-browser]
  (let [reload-dirs [(:source-path project)]
        [handler-sym handler-ns] (sym-and-ns project :handler)
        [init-sym init-ns]       (sym-and-ns project :init)
        [destroy-sym destroy-ns] (sym-and-ns project :destroy)]
       (eval-in-project project
         `(do (leiningen.ring.run-server/run-server
                (-> (var ~handler-sym)
                    (ring.middleware.reload/wrap-reload ~reload-dirs)
                    (ring.middleware.stacktrace/wrap-stacktrace))
                ~(if port (Integer/parseInt port))
                ~launch-browser
                ~init-sym
                ~destroy-sym))
         nil nil `(do (require 'leiningen.ring.run-server
                               'ring.middleware.stacktrace
                               'ring.middleware.reload
                               '~handler-ns)
                      ~@(require-sym init-ns)
                      ~@(require-sym destroy-ns)))))

(defn server
  "Start a Ring server and open a browser."
  ([project]
     (server project nil))
  ([project port]
     (server-task project port true)))
