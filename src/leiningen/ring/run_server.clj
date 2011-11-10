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

(defn- jetty-server [handler jetty-params]
  (if (:port jetty-params)
    (run-jetty (wrap-stacktrace handler)
               (assoc jetty-params :join? false))
    (letfn [(try-port
              [port] (jetty-server handler (assoc jetty-params :port port)))]
      (try-ports try-port suitable-ports))))

(defn run-server [handler jetty-params launch-browser?
                  init-fn destroy-fn report-ports]
  (when destroy-fn
    (. (Runtime/getRuntime)
       (addShutdownHook (Thread. destroy-fn))))
  (when init-fn (init-fn))
  (let [server     (jetty-server handler jetty-params)
        [http ssl] (.getConnectors server)
        host       (or (.getHost http) "0.0.0.0")
        port       (.getPort http)
        ssl-port    (if ssl (.getPort ssl))
        url-host   (if (= host "0.0.0.0") "localhost" host)]
    (println "Started server on port" port)
    (when ssl-port
      (println "Started SSL server on port" ssl-port))
    (when report-ports
      (report-ports port ssl-port))
    (when launch-browser?
      (browse-url (str "http://" url-host ":" port)))
    (.join server)))
