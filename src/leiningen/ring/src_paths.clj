(ns leiningen.ring.src-paths
  (:require [clojure.java.io :as io]
            [leiningen.core.project :as lp]))

;; code is modified from leiningen.core.classpath

(defn read-dependency-project [root dep]
  (let [project-file (io/file root "checkouts" dep "project.clj")]
    (if (.exists project-file)
      (let [project (.getCanonicalPath project-file)]
        (try (lp/read project [])
          (catch Exception e
            (throw (Exception. (format "Problem loading %s" project) e)))))
      (println
        "WARN ignoring checkouts directory" dep
        "as it does not contain a project.clj file."))))

(defn project-src-paths 
  "extract source paths for a single project. Merges lein1 and lein2 paths"
  [project]
  (cons
    (:source-path project)   ; lein1
    (:source-paths project)  ; lein2
    ))

(defn checkout-src-paths 
  "extract source paths for all checkouts of a project
   this does not recurse through projects to find all checkouts
   (same as leiningen's classpath)"
  [project]
  (apply concat
         (for [dep (.list (io/file (:root project) "checkouts"))
               :let [dep-project (read-dependency-project
                                   (:root project) dep)]
               :when dep-project]
           (project-src-paths dep-project))))

;; this function should be used if leiningen changes to include 
;; recursive checkouts on it's classpath. Currently, it does not.
#_(defn recursive-checkout-src-paths 
  "extract source paths for all checkouts of a project
   this does recurse through projects to find all checkouts"
  [project]
  (apply concat
         (for [dep (.list (io/file (:root project) "checkouts"))
               :let [dep-project (read-dependency-project
                                   (:root project) dep)]
               :when dep-project]
           (concat
             (project-src-paths dep-project)
             (recursive-checkout-src-paths dep-project)
             ))))

(defn src-paths
  "extract source paths from project and it's checkouts projects"
  [project]
  ;; remove duplicates and possible nil
  (-> #{}
    (into (project-src-paths project))
    (into (checkout-src-paths project))
    (disj nil)
    seq))
