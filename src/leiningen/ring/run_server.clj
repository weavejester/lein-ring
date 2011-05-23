(ns leiningen.ring.run-server
  (:use ring.adapter.jetty
        ring.middleware.stacktrace
        [clojure.java.browse :only (browse-url)]))

(defn- try-ports [func ports]
  (try (func (first ports))
       (catch java.net.BindException ex
         (if-let [ports (next ports)]
           (try-ports func ports)
           (throw ex)))))

(def suitable-ports (range 3000 3011))

(defn- jetty-server [handler port]
  (if port
    (run-jetty (wrap-stacktrace handler)
               {:port port, :join? false})
    (try-ports #(jetty-server handler %)
               suitable-ports)))

(defn run-server [handler port launch-browser? init-fn destroy-fn]
  (when destroy-fn
    (. (Runtime/getRuntime)
       (addShutdownHook (Thread. destroy-fn))))
  (when init-fn (init-fn))
  (let [server    (jetty-server handler port)
        connector (first (.getConnectors server))
        host      (or (.getHost connector) "0.0.0.0")
        port      (.getPort connector)
        url-host  (if (= host "0.0.0.0") "127.0.0.1" host)]
    (println "Started server on port" port)
    (when launch-browser?
      (browse-url (str "http://" url-host ":" port)))
    (.join server)))
