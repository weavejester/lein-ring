(ns leiningen.ring.war
  (:require [leiningen.compile :as compile]
            [leiningen.jar :as jar]
            [clojure.java.io :as io]))

(defn default-war-name [project]
  (or (get-in project [:ring :war-name])
      (str (:name project) "-" (:version project) ".war")))

(defn war-file-path [project war-name]
  (let [target-dir (:target-dir project)]
    (.mkdirs (io/file target-dir))
    (str target-dir "/" war-name)))

(defn- file-exists? [filepath]
  (if filepath
    (.exists (io/file filepath))))

(defn- filespecs [project]
  (concat
    (if-not (:omit-source project)
      [{:type :path :path (:source-path project)}])
    (if (file-exists? (:resources-path project))
      [{:type :path :path (:resources-path project)}])
    [{:type :path :path (:compile-path project)}
     {:type :path :path (str (:root project) "/project.clj")}]))

(defn war
  "Create a $PROJECT-$VERSION.war file suitable for use in servlet containers."
  ([project]
     (war project (default-war-name project)))
  ([project war-name]
     (binding [compile/*silently* true]
       (when (zero? (compile/compile project))
         (let [war-path (war-file-path project war-name)]
           (jar/write-jar project war-path (filespecs project))
           (println "Created" war-path)
           war-path)))))
