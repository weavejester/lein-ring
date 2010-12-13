(ns leiningen.ring.run-server
  (:use ring.adapter.jetty
        ring.middleware.stacktrace
        ring.middleware.reload-modified))

(defn- try-ports [func ports]
  (try (func (first ports))
       (catch java.net.BindException ex
         (if-let [ports (next ports)]
           (try-ports func ports)
           (throw ex)))))

(def suitable-ports (range 3000 3011))

(defn run-server [handler port]
  (if port
    (do (println "Starting web server on port" port)
        (run-jetty handler {:port port}))
    (try-ports #(run-server handler %) suitable-ports)))
