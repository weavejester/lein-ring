(ns leiningen.ring.server
  (:use [leiningen.compile :only (eval-in-project)]))

(defn server
  "Start a Ring server."
  ([project]
     (server project nil))
  ([project port]
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
                ~(if port (Integer/parseInt port))))))))
