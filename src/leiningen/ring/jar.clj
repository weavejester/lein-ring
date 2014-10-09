(ns leiningen.ring.jar
  (:use [leiningen.ring.util :only (compile-form ensure-handler-set! update-project)]
        [leiningen.ring.server :only (add-server-dep)])
  (:require [clojure.string :as str]
            leiningen.jar))

(defn default-main-namespace [project]
  (let [handler-sym (get-in project [:ring :handler])]
    (str (namespace handler-sym) ".main")))

(defn main-namespace [project]
  (or (get-in project [:ring :main])
      (default-main-namespace project)))

(defn jar
  "Create an executable $PROJECT-$VERSION.jar file."
  [project]
  (ensure-handler-set! project)
  (let [project (add-server-dep project)]
    (leiningen.jar/jar project)))
