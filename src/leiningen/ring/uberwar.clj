(ns leiningen.ring.uberwar
  (:use leiningen.ring.util)
  (:require [leiningen.ring.war :as war]
            [leiningen.jar :as jar]
            [leinjacker.deps :as deps]
            [leiningen.compile :as compile]
            [clojure.java.io :as io]
            [leinjacker.utils :as lju])
  (:import [java.util.jar JarFile JarEntry]
           (java.io File)))

(defn default-uberwar-name [project]
  (or (get-in project [:ring :uberwar-name])
      (:uberjar-name project)
      (str (:name project) "-" (:version project) "-standalone.war")))

(defn get-classpath [project]
  (if-let [get-cp (resolve 'leiningen.core.classpath/get-classpath)]
    (get-cp project)
    (->> (:library-path project) io/file .listFiles (map str))))

(defn contains-entry? [^java.io.File file ^String entry]
  (with-open [jar-file (JarFile. file)]
    (some (partial = entry)
          (map #(.getName ^JarEntry %)
               (enumeration-seq (.entries jar-file))))))

(defn jar-dependencies [project]
  (for [pathname (get-classpath project)
        :let [file (io/file pathname)
              fname (.getName file)]
        :when (and (.endsWith fname ".jar")
                   ;; Servlet container will have it's own servlet-api impl
                   (not (contains-entry? file "javax/servlet/Servlet.class")))]
    file))

(defn jar-entry [war project jar-file]
  (let [dir-path (.getParent jar-file)
            war-path (war/in-war-path "WEB-INF/lib/" dir-path jar-file)]
        (war/file-entry war project war-path jar-file)))

(defn jar-entries [war project]
  (doseq [jar-file (jar-dependencies project)]
    (jar-entry war project jar-file)))

(defn write-uberwar [project war-path]
  (with-open [war-stream (war/create-war project war-path)]
    (war/str-entry war-stream "WEB-INF/web.xml" (war/make-web-xml project))
    ;; if they want the AOT-compiled classes jar'd up first, then invoke lein's jar and include that.
    ;; otherwise, include the classes directly in WEB-INF/classes
    (if (get-in project [:ring :jar-classes?])
      (let [jar-results (jar/jar project)]                  ; invoke the normal lein "jar" task and capture the results
          (jar-entry war-stream project (File. (jar-results [:extension "jar"]))))
      (war/dir-entry war-stream project "WEB-INF/classes/" (:compile-path project)))
    (doseq [path (source-and-resource-paths project)
            :when path]
      (war/dir-entry war-stream project "WEB-INF/classes/" path))
    (doseq [path (war/war-resources-paths project)]
      (war/dir-entry war-stream project "" path))
    (jar-entries war-stream project)))

(defn unmerge-profiles [project]
  (if-let [unmerge-fn (and (= 2 (lju/lein-generation))
                           (lju/try-resolve 'leiningen.core.project/unmerge-profiles))]
    (unmerge-fn project [:default])
    project))

(defn uberwar
  "Create a $PROJECT-$VERSION.war with dependencies."
  ([project]
     (uberwar project (default-uberwar-name project)))
  ([project war-name]
     (ensure-handler-set! project)
     (let [project (-> project
                       unmerge-profiles
                       war/add-servlet-dep)
           result  (compile/compile project)]
       (when-not (and (number? result) (pos? result))
         (let [war-path (war/war-file-path project war-name)]
           (war/compile-servlet project)
           (war/compile-listener project)
           (write-uberwar project war-path)
           (println "Created" war-path)
           war-path)))))
