(ns leiningen.ring.uberwar
  (:require [leiningen.ring.war :as war]
            [leiningen.compile :as compile]
            [clojure.java.io :as io]))

(def default-uberwar-exclusions [#"servlet-api-.+\.jar"])

(defn default-uberwar-name [project]
  (or (:uberjar-name project)
      (str (:name project) "-" (:version project) "-standalone.war")))

(defn get-classpath [project]
  (if-let [get-cp (resolve 'leiningen.core.classpath/get-classpath)]
    (get-cp project)
    (->> (:library-path project) io/file .listFiles (map str))))

(defn jar-dependencies [project]
  (for [pathname (get-classpath project)
        :let [file (io/file pathname)
              fname (.getName file)]
        :when (not-any?
                #(re-matches % fname)
                (or
                  (get-in project [:ring :uberwar-exclusions])
                  default-uberwar-exclusions))]
    file))

(defn jar-entries [war project]
  (doseq [jar-file (jar-dependencies project)]
    (let [dir-path (.getParent jar-file)
          war-path (war/in-war-path "WEB-INF/lib/" dir-path jar-file)]
      (war/file-entry war project war-path jar-file))))

(defn write-uberwar [project war-path]
  (with-open [war-stream (war/create-war project war-path)]
    (doto war-stream
      (war/str-entry "WEB-INF/web.xml" (war/make-web-xml project))
      (war/dir-entry project "WEB-INF/classes/" (:compile-path project)))
    (doseq [path (concat [(:source-path project)] (:source-paths project)
                         [(:resources-path project)] (:resource-paths project))
            :when path]
      (war/dir-entry war-stream project "WEB-INF/classes/" path))
    (war/dir-entry war-stream project "" (war/war-resources-path project))
    (jar-entries war-stream project)))

(defn uberwar
  "Create a $PROJECT-$VERSION.war with dependencies."
  ([project]
     (uberwar project (default-uberwar-name project)))
  ([project war-name]
     (let [project (war/add-servlet-dep project)
           result  (compile/compile project)]
       (when-not (and (number? result) (pos? result))
         (let [war-path (war/war-file-path project war-name)]
           (war/compile-servlet project)
           (if (war/has-listener? project)
             (war/compile-listener project))
           (write-uberwar project war-path)
           (println "Created" war-path)
           war-path)))))
