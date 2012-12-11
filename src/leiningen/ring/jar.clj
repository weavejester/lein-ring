(ns leiningen.ring.jar
  (:use [leiningen.ring.util :refer (compile-form ensure-handler-set!)])
  (:require [leiningen.jar :as jar]
            [leiningen.ring.server :as server]))

(defn default-main-namespace [project]
  (let [handler-sym (get-in project [:ring :handler])]
    (str (namespace handler-sym) ".main")))

(defn main-namespace [project]
  (or (get-in project [:ring :main])
      (default-main-namespace project)))

(defn compile-main [project]
  (let [main-ns (symbol (main-namespace project))]
    (compile-form project main-ns
      `(do (ns ~main-ns
             (:require ring.server.leiningen)
             (:gen-class))
           (defn ~'-main []
             (ring.server.leiningen/serve
              '~(select-keys project [:ring])))))))

(defn jar [project]
  (ensure-handler-set! project)
  (let [project (server/add-server-dep project)]
    (compile-main project)
    (jar/jar project)))
