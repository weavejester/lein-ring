(ns leiningen.ring.server
  (:use [leiningen.compile :only (eval-in-project)]))

(defn server
  "Start a Ring server."
  ([project]
     (server project "3000"))
  ([project port]
     (let [handler-sym (get-in project [:ring :handler])
           handler-ns  (symbol (namespace handler-sym))
           reload-dirs [(:source-path project)]]
       (eval-in-project project
         `(do (require 'ring.adapter.jetty
                       'ring.middleware.stacktrace
                       'ring.middleware.reload-modified
                       '~handler-ns)
              (ring.adapter.jetty/run-jetty
               (-> (var ~handler-sym)
                   (ring.middleware.stacktrace/wrap-stacktrace)
                   (ring.middleware.reload-modified/wrap-reload-modified
                     ~reload-dirs))
                {:port ~(Integer/parseInt port)}))))))
