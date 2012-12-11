(ns leiningen.ring.jar
  (:use [leiningen.ring.util :only (compile-form ensure-handler-set!)]
        [leiningen.ring.server :only (add-server-dep)])
  (:require [clojure.string :as str]
            leiningen.jar))

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

(defn update-project [project func & args]
  (vary-meta
   (apply func project args)
   update-in [:without-profiles] #(apply func % args)))

(defn add-main-class [project]
  (update-project project assoc :main (symbol (main-namespace project))))

(defn jar [project]
  (ensure-handler-set! project)
  (let [project (-> project add-server-dep add-main-class)]
    (compile-main project)
    (leiningen.jar/jar project)))
