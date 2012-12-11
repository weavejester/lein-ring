(ns leiningen.ring.jar
  (:use [leiningen.ring.util :refer (compile-form ensure-handler-set!)])
  (:require [leiningen.jar :as jar]))

(defn jar [project]
  (ensure-handler-set! project)
  (jar/jar project))