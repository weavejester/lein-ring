(ns leiningen.ring.uberwar
  (:require [leiningen.ring.war :as war]
            [leiningen.compile :as compile]
            [clojure.java.io :as io]))

(defn default-uberwar-name [project]
  (or (:uberjar-name project)
      (str (:name project) "-" (:version project) "-standalone.war")))

(defn jar-dependencies [project]
  (->> (:library-path project)
       (io/file)
       (.listFiles)
       (filter #(.endsWith (str %) ".jar"))
       ;; Servlet container will have it's own servlet-api implementation
       (remove #(.startsWith (.getName %) "servlet-api-"))))

(defn jar-entries [war project]
  (doseq [jar-file (jar-dependencies project)]
    (let [dir-path (:library-path project)
          war-path (war/in-war-path "WEB-INF/lib/" dir-path jar-file)]
      (war/file-entry war project war-path jar-file))))

(defn write-uberwar [project war-path]
  (with-open [war-stream (war/create-war project war-path)]
    (doto war-stream
      (war/str-entry "WEB-INF/web.xml" (war/make-web-xml project))
      (war/dir-entry project "WEB-INF/classes/" (:compile-path project))
      (war/dir-entry project "WEB-INF/classes/" (:source-path project))
      (war/dir-entry project "" (:resources-path project))
      (jar-entries project))))

(defn uberwar
  ([project]
     (uberwar project (default-uberwar-name project)))
  ([project war-name]
     (binding [compile/*silently* true]
       (when (zero? (compile/compile project))
         (let [war-path (war/war-file-path project war-name)]
           (war/compile-servlet project)
           (write-uberwar project war-path)
           (println "Created" war-path)
           war-path)))))
