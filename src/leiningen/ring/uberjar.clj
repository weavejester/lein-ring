(ns leiningen.ring.uberjar
  (:use [leiningen.ring.util :only (ensure-handler-set!)]
        [leiningen.ring.server :only (add-server-dep)])
  (:require [leiningen.ring.jar :as jar]
            [leiningen.clean :as clean]
            [leiningen.uberjar]))

(defn- no-uberjar-clean [project]
  "Modifies the uberjar profile so that no auto-clean is performed
  when leiningen takes control, thus avoiding the autogenerated main files
  from being wiped."
  (vary-meta project assoc-in [:profiles :uberjar :auto-clean] false))

(defn uberjar
  "Create an executable $PROJECT-$VERSION.jar file with dependencies."
  [project]
  (ensure-handler-set! project)
  (when (:auto-clean project true)
    (clean/clean project))
  (let [project (-> project add-server-dep no-uberjar-clean)]
    (leiningen.uberjar/uberjar project)))
