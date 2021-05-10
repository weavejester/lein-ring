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

(defn- jar-name-from-pom-properties
  [jar-path pom-properties-jar-path]
  (let [pom-properties-full-path (str "jar:file:"
                                      jar-path "!/"
                                      pom-properties-jar-path)
        {:strs [groupId artifactId version]} (read-jar-pom-properties
                                               pom-properties-full-path)]
    (when (and groupId artifactId version)
      (str groupId \- artifactId \- version ".jar"))))

(defn- war-path-for-jar [^File jar]
  (with-open [jar-file (JarFile. jar)]
    (let [javax&pom (->> (.entries jar-file)
                         enumeration-seq
                         (map (fn [^JarEntry je] (.getName je)))
                         (filter (some-fn javax-servlet? pom-properties?)))] ;; 2 elements maximum
      ;; Servlet container will have it's own servlet-api impl
      (when-not (some javax-servlet? javax&pom)
        (str "WEB-INF/lib/"
             (or (some->> (first javax&pom)
                          (jar-name-from-pom-properties (.getAbsolutePath jar)))
                 ;; fallback to using the original jar name (best-effort approach)
                 (.getName jar)))))))

(defn- populate-war-with-dependent-jars
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
       :additional-writes populate-war-with-dependent-jars)))

