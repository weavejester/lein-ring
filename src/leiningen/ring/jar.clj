(ns leiningen.ring.jar
  (:require [leiningen.jar :as lein-jar]
            [leiningen.ring.server :refer (add-server-dep)]
            [leiningen.ring.util :refer (ensure-handler-set!)]))

(defn jar
  "Create an executable $PROJECT-$VERSION.jar file."
  [project]
  (ensure-handler-set! project)
  (let [project (add-server-dep project)]
    (lein-jar/jar project)))
