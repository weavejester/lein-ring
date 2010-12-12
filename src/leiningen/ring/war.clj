(ns leiningen.ring.war
  (:require [leiningen.compile :as compile]
            [clojure.java.io :as io])
  (:use [clojure.contrib.prxml :only (prxml)])
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

(defn make-web-xml [project]
  (with-out-str
    (prxml
      [:web-app
        [:servlet
          [:servlet-name "war-servlet"]
          [:servlet-class "deploy.servlet"]]
        [:servlet-mapping
          [:servlet-name "war-servlet"]
          [:url-pattern "/*"]]])))


(defn source-file [project namespace]
  (io/file (:compile-path project)
           (-> (str namespace)
               (.replace "-" "_")
               (.replace "." java.io.File/separator)
               (str ".clj"))))

(defn compile-form [project namespace form]
  (let [out-file (source-file project namespace)]
    (.mkdirs (.getParentFile out-file))
    (with-open [out (io/writer out-file)]
      (binding [*out* out] (prn form))))
  (compile/eval-in-project project
    `(clojure.core/compile '~namespace)))

(defn compile-servlet [project]
  (let [handler-sym (get-in project [:ring :handler])
        handler-ns  (symbol (namespace handler-sym))]
    (compile-form project 'deploy.servlet
      `(do (ns deploy.servlet
             (:require ring.util.servlet ~handler-ns)
             (:gen-class :extends javax.servlet.http.HttpServlet))
           (ring.util.servlet/defservice ~handler-sym)))))

(defn create-war [project file-path]
  (-> (FileOutputStream. file-path)
      (BufferedOutputStream.)
      (JarOutputStream. (make-manifest))))

(defn write-entry [war war-path entry]
  (.putNextEntry war (JarEntry. war-path))
  (io/copy entry war))

(defn str-entry [war war-path content]
  (write-entry war war-path (to-byte-stream content)))

(defn in-war-path [war-path root file]
  (str war-path "/"
       (-> (.toURI (io/file root)) 
           (.relativize (.toURI file))
           (.getPath))))

(defn file-entry [war project war-path file]
  (when (and (.exists file)
             (.isFile file)
             (not (skip-file? project war-path file)))
    (write-entry war war-path file)))

(defn dir-entry [war project war-root dir-path]
  (doseq [file (file-seq (io/file dir-path))]
    (let [war-path (in-war-path war-root dir-path file)]
      (file-entry war project war-path file))))

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
           (compile-servlet project)
           (write-war project war-path)
           (println "Created" war-path)
           war-path)))))
