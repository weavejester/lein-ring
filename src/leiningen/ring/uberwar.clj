(ns leiningen.ring.uberwar
  (:require [leiningen.ring.war :as war]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util.jar JarFile JarEntry]
           [java.util Properties]
           [java.net URL]
           (java.io File)))

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

(def ^:private javax-servlet? #{"javax/servlet/Servlet.class"})

(defn- pom-properties? [file-name]
  (str/ends-with? file-name "pom.properties"))

(defn- read-jar-pom-properties [^String full-path]
  (with-open [in (.openStream (URL. full-path))]
    (->> (doto (Properties.) (.load in))
         (into {}))))

(defn- from-pom-properties
  "Returns a jar name which includes the groupId, artifactId, & version,
   as read by the pom.properties file inside the jar file found at <jar-path>."
  [jar-path pom-properties-jar-path]
  (let [pom-properties-full-path (str "jar:file:" jar-path "!/" pom-properties-jar-path)
        {:strs [groupId artifactId version]} (read-jar-pom-properties pom-properties-full-path)]
    (when (and groupId artifactId version)
      ;; found pom.properties - reconstruct the jar name to avoid collisions (issue #216)
      (str groupId \- artifactId \- version ".jar"))))

(defn- war-path-for-jar [^File jar]
  (with-open [jar-file (JarFile. jar)]
    (let [javax&pom (->> (.entries jar-file)
                         enumeration-seq
                         (map (fn [^JarEntry je] (.getName je)))
                         (filter (some-fn javax-servlet? pom-properties?)))] ;; 2 elements maximum
      ;; Servlet container will have it's own servlet-api impl
      (when-not (some javax-servlet? javax&pom)
        ;; we've confirmed that one of the two possible files is missing,
        ;; so if we find one (via `first`), it will be the pom.properties
        (str "WEB-INF/lib/"
             (or (some->> (first javax&pom)
                          (from-pom-properties (.getAbsolutePath jar)))
                 ;; fallback to using the original jar name (best-effort approach)
                 (.getName jar)))))))

(defn- jar-entries
  "Populates the 'WEB-INF/lib/' directory of the given <war> with this project's dependencies (jars).
   If these contain a 'pom.properties' file, they will be copied into the war named as
   $GROUP-$ARTIFACT-$VERSION.jar (see `war-path-for-jar`), otherwise using their original filename."
  [war project]
  (doseq [jar-file (jar-dependencies project)]
    (when-let [war-path (war-path-for-jar jar-file)]
      (war/file-entry war project war-path jar-file))))

(defn uberwar
  "Create a $PROJECT-$VERSION.war with dependencies."
  ([project]
     (uberwar project (default-uberwar-name project)))
  ([project war-name]
     (war/war
       project war-name
       :profiles-to-merge [:uberjar]
       :additional-writes jar-entries)))

(comment
  ;; will only work under *NIX
  (let [clojure-jar (-> (System/getProperty "user.home")
                        (io/file ".m2/repository/org/clojure/clojure/1.10.1/clojure-1.10.1.jar"))]
    (= "WEB-INF/lib/org.clojure-clojure-1.10.1.jar" ;; <group-id>-<artifact-id>-<version>.jar
       (war-path-for-jar clojure-jar))
    )
  )
