(ns leiningen.ring.war
  (:require [leiningen.compile :as compile]
            [clojure.java.io :as io])
  (:import [java.util.jar Manifest
                          JarEntry
                          JarOutputStream]
           [java.io BufferedOutputStream 
                    FileOutputStream 
                    ByteArrayInputStream]))

(defn default-war-name [project]
  (or (get-in project [:ring :war-name])
      (str (:name project) "-" (:version project) ".war")))

(defn war-file-path [project war-name]
  (let [target-dir (:target-dir project)]
    (.mkdirs (io/file target-dir))
    (str target-dir "/" war-name)))

(defn skip-file? [project war-path file]
  (or (re-find #"^\.?#" (.getName file))
      (re-find #"~$" (.getName file))
      (some #(re-find % war-path)
            (get-in project [:ring :war-exclusions]))))

(defn- to-byte-stream [^String s]
  (ByteArrayInputStream. (.getBytes s)))

(defn make-manifest []
  (Manifest.
   (to-byte-stream
    (str 
     "Manifest-Version: 1.0\n"
     "Created-By: Leiningen Ring Plugin\n"
     "Built-By: " (System/getProperty "user.name") "\n"
     "Build-Jdk: " (System/getProperty "java.version") "\n\n"))))

(defn create-war [project file-path]
  (-> (FileOutputStream. file-path)
      (BufferedOutputStream.)
      (JarOutputStream. (make-manifest))))

(defn make-web-xml [project] "<xml/>")

(defn write-entry [war war-path entry]
  (.putNextEntry war (JarEntry. war-path))
  (io/copy entry war))

(defn str-entry [war war-path content]
  (write-entry war war-path (to-byte-stream content)))

(defn- in-war-path [war-path root file]
  (str war-path "/"
       (-> (.toURI (io/file root)) 
           (.relativize (.toURI file))
           (.getPath))))

(defn dir-entry [war project war-root dir-path]
  (doseq [file (file-seq (io/file dir-path))]
    (if (and (.exists file) (.isFile file))
      (let [war-path (in-war-path war-root dir-path file)]
        (if-not (skip-file? project war-path file)
          (write-entry war war-path file))))))

(defn write-war [project war-path]
  (with-open [war-stream (create-war project war-path)]
    (doto war-stream
      (str-entry "/WEB-INF/web.xml" (make-web-xml project))
      (dir-entry project "/WEB-INF/classes" (:compile-path project))
      (dir-entry project "/WEB-INF/classes" (:source-path project))
      (dir-entry project "/" (:resources-path project)))))

(defn war
  "Create a $PROJECT-$VERSION.war file suitable for use in servlet containers."
  ([project]
     (war project (default-war-name project)))
  ([project war-name]
     (binding [compile/*silently* true]
       (when (zero? (compile/compile project))
         (let [war-path (war-file-path project war-name)]
           (write-war project war-path)
           (println "Created" war-path)
           war-path)))))
