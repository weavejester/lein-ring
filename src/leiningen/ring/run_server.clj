(ns leiningen.ring.run-server
  (:use ring.adapter.jetty
        ring.middleware.stacktrace
        ring.middleware.reload
        [clojure.java.browse :only (browse-url)]))

(defn try-ports
  "Try running a function with different ports until it succeeds"
  [func ports]
  (try (func (first ports))
       (catch java.net.BindException ex
         (if-let [ports (next ports)]
           (try-ports func ports)
           (throw ex)))))

(defn- add-destroy-callback [{destroy-fn :destroy}]
  (when destroy-fn
    (. (Runtime/getRuntime)
       (addShutdownHook (Thread. destroy-fn)))))

(defn- run-init [{init-fn :init}]
  (when init-fn
    (init-fn)))

(defn- get-handler [{handler :handler}]
  (-> handler
      wrap-stacktrace
      wrap-reload))

(defn- jetty-options [{port :port :as options}]
  (-> options
      (assoc :join? false)
      (assoc :port (Integer. port))))

(def suitable-ports (range 3000 3011))

(defn- start-jetty [options]
  (let [handler (get-handler options)]
    (if (:port options)
      (run-jetty handler (jetty-options options))
      (try-ports #(start-jetty (assoc options :port %))
                 suitable-ports))))

(defn- open-browser [server {headless? :headless?}]
  (let [connector (first (.getConnectors server))
        host      (or (.getHost connector) "0.0.0.0")
        port      (.getPort connector)
        url-host  (if (= host "0.0.0.0") "127.0.0.1" host)]
    (println "Started server on port" port)
    (when-not headless?
      (browse-url (str "http://" url-host ":" port)))))

(defn run-server [options]
  (add-destroy-callback options)
  (run-init options)
  (doto (start-jetty options)
    (open-browser options)
    (.join)))
