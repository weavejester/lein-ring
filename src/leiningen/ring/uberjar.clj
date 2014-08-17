(ns leiningen.ring.uberjar
  (:use [leiningen.ring.util :only (ensure-handler-set!)]
        [leiningen.ring.server :only (add-server-dep)])
  (:require [leiningen.core.project :as project]
            [leiningen.ring.jar :as jar]
            leiningen.uberjar))

(defn uberjar
  "Create an executable $PROJECT-$VERSION.jar file with dependencies."
  [project]
  (ensure-handler-set! project)
  (let [project (-> project add-server-dep jar/add-main-class)]
    (let [project (project/merge-profiles project [:uberjar])]
      (jar/compile-main project))
    (leiningen.uberjar/uberjar project)))
