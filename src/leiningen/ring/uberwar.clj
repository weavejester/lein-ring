(ns leiningen.ring.uberwar
  (:require [leiningen.ring.war :as war]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util.jar JarFile JarEntry]
           [java.util Properties]
           [java.io File]))

(defn default-uberwar-name [project]
  (or (get-in project [:ring :uberwar-name])
      (:uberjar-name project)
      (str (:name project) "-" (:version project) "-standalone.war")))

(defn get-classpath [project]
  (if-let [get-cp (resolve 'leiningen.core.classpath/get-classpath)]
    (get-cp project)
    (->> (:library-path project) io/file .listFiles (map str))))

(defn jar-dependencies [project]
  (for [pathname (get-classpath project)
        :when (str/ends-with? pathname ".jar")]
    (io/file pathname)))

(defn- contains-javax-servlet-class? [^JarFile jar-file]
  (some? (.getEntry jar-file "javax/servlet/Servlet.class")))

(defn- pom-properties? [^JarEntry entry]
  (when (str/ends-with? (.getName entry) "pom.properties")
    entry))

(defn- find-pom-properties [^JarFile jar-file]
  (->> jar-file .entries enumeration-seq (some pom-properties?)))

(defn- read-jar-pom-properties
  [^JarFile jar-file ^JarEntry pom-properties]
  (with-open [in (.getInputStream jar-file pom-properties)]
    (into {} (doto (Properties.) (.load in)))))

(defn- jar-name-from-pom-properties
  [^JarFile jar-file ^JarEntry pom-properties]
  (let [{:strs [groupId artifactId version]}
        (read-jar-pom-properties jar-file pom-properties)]
    (when (and groupId artifactId version)
      (str groupId \- artifactId \- version ".jar"))))

(defn- war-path-for-jar [^File jar]
  (with-open [jar-file (JarFile. jar)]
    ;; Servlet container will have its own servlet-api implementation
    (when-not (contains-javax-servlet-class? jar-file)
      (let [name-from-pom (some->> (find-pom-properties jar-file)
                                   (jar-name-from-pom-properties jar-file))]
        (->> (or name-from-pom (.getName jar))
             (str "WEB-INF/lib/"))))))

(defn- populate-war-with-dependent-jars [war project]
  (doseq [jar (jar-dependencies project)]
    (when-let [war-path (war-path-for-jar jar)]
      (war/file-entry war project war-path jar))))

(defn uberwar
  "Create a $PROJECT-$VERSION.war with dependencies."
  ([project]
     (uberwar project (default-uberwar-name project)))
  ([project war-name]
     (war/war
       project war-name
       :profiles-to-merge [:uberjar]
       :additional-writes populate-war-with-dependent-jars)))
