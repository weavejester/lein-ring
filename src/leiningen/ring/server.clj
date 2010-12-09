(ns leiningen.ring.server
  (:use [leiningen.compile :only (eval-in-project)]))

(defn server
  "Start a Ring server."
  ([project]
     (server project "3000"))
  ([project port]
     (let [handler-sym (get-in project [:ring :handler])]
       (eval-in-project project
         `(do (require 'ring.adapter.jetty
                       '~(symbol (namespace handler-sym)))
              (ring.adapter.jetty/run-jetty
                 ~handler-sym
                 {:port ~(Integer/parseInt port)}))))))
