(ns leiningen.ring.server
  (:use [leiningen.compile :only (eval-in-project)]))

(defn server-task
  "Shared logic for server and server-headless tasks."
  [project port launch-browser]
    (let [handler-sym (get-in project [:ring :handler])
           handler-ns  (symbol (namespace handler-sym))
           reload-dirs [(:source-path project)]]
       (eval-in-project project
         `(do (require 'leiningen.ring.run-server
                       'ring.middleware.stacktrace
                       'ring.middleware.reload-modified
                       '~handler-ns)
              (leiningen.ring.run-server/run-server
                (-> (var ~handler-sym)
                    (ring.middleware.stacktrace/wrap-stacktrace)
                    (ring.middleware.reload-modified/wrap-reload-modified
                      ~reload-dirs))
                ~(if port (Integer/parseInt port))
		~launch-browser)))))

(defn server
  "Start a Ring server and open a browser."
  ([project]
     (server project nil))
  ([project port]
     (server-task project port true)))
