(ns leiningen.ring.server
  (:use [leiningen.compile :only (eval-in-project)]))

(defn server
  "Start a Ring server."
  ([project]
     (server project "3000"))
  ([project port]
     (let [handler-sym (get-in project [:ring :handler])
           handler-ns  (symbol (namespace handler-sym))]
       (eval-in-project project
         `(do (require 'ring.adapter.jetty
                       'ring.middleware.stacktrace
                       '~handler-ns)
              (ring.adapter.jetty/run-jetty
                (ring.middleware.stacktrace/wrap-stacktrace ~handler-sym)
                {:port ~(Integer/parseInt port)}))))))
