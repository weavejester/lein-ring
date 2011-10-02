(ns leiningen.ring.war
  (:require [leiningen.compile :as compile]
            [clojure.java.io :as io]
            [clojure.string :as string])
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

(def default-ring-manifest
  {"Created-By"       "Leiningen Ring Plugin"
   "Built-By"         (System/getProperty "user.name")
   "Build-Jdk"        (System/getProperty "java.version")})

(defn make-manifest [user-manifest]
  (Manifest.
   (to-byte-stream
    (reduce
     (fn [accumulated-manifest [k v]]
       (str accumulated-manifest "\n" k ": " v))
     "Manifest-Version: 1.0"
     (merge default-ring-manifest user-manifest)))))

(defn default-servlet-class [project]
  (let [handler-sym (get-in project [:ring :handler])
        ns-parts    (-> (namespace handler-sym)
                        (string/replace "-" "_")
                        (string/split #"\.")
                        (butlast)
                        (vec)
                        (conj "servlet"))]
    (string/join "." ns-parts)))

(defn servlet-class [project]
  (or (get-in project [:ring :servlet-class])
      (default-servlet-class project)))

(defn servlet-ns [project]
  (-> (servlet-class project)
      (string/replace "_" "-")))

(defn servlet-name [project]
  (or (get-in project [:ring :servlet-name])
      (str (get-in project [:ring :handler])
           " servlet")))

(defn has-listener? [project]
  (let [ring-options (:ring project)]
    (or (contains? ring-options :init)
        (contains? ring-options :destroy))))

(defn default-listener-class [project]
  (let [listener-sym (or (get-in project [:ring :init])
                         (get-in project [:ring :destroy]))
        ns-parts     (-> (namespace listener-sym)
                         (string/replace "-" "_")
                         (string/split #"\.")
                         (butlast)
                         (vec)
                         (conj "listener"))]
    (string/join "." ns-parts)))

(defn listener-class [project]
  (or (get-in project [:ring :listener-class])
      (default-listener-class project)))

(defn listener-ns [project]
  (-> (listener-class project)
      (string/replace "_" "-")))

(defn url-pattern [project]
  (or (get-in project [:ring :url-pattern])
      "/*"))

(defn make-web-xml [project]
  (with-out-str
    (prxml
      [:web-app
       (if (has-listener? project)
         [:listener
          [:listener-class (listener-class project)]])
        [:servlet
          [:servlet-name  (servlet-name project)]
          [:servlet-class (servlet-class project)]]
        [:servlet-mapping
          [:servlet-name (servlet-name project)]
          [:url-pattern (url-pattern project)]]])))

(defn source-file [project namespace]
  (io/file (:compile-path project)
           (-> (str namespace)
               (string/replace "-" "_")
               (string/replace "." java.io.File/separator)
               (str ".clj"))))

(defn compile-form [project namespace form]
  (let [out-file (source-file project namespace)]
    (.mkdirs (.getParentFile out-file))
    (with-open [out (io/writer out-file)]
      (binding [*out* out] (prn form))))
  (compile/eval-in-project project
    `(do (clojure.core/compile '~namespace) nil)))

(defn generate-handler [project handler-sym]
  (if (get-in project [:ring :servlet-path-info?] true)
    `(fn [request#]
       (~handler-sym
         (assoc request#
           :path-info (.getPathInfo (:servlet-request request#))
           :context   (.getContextPath (:servlet-request request#)))))
    handler-sym))

(defn compile-servlet [project]
  (let [handler-sym (get-in project [:ring :handler])
        handler-ns  (symbol (namespace handler-sym))
        servlet-ns  (symbol (servlet-ns project))]
    (compile-form project servlet-ns
      `(do (ns ~servlet-ns
             (:require ring.util.servlet ~handler-ns)
             (:gen-class :extends javax.servlet.http.HttpServlet))
           (ring.util.servlet/defservice
             ~(generate-handler project handler-sym))))))

(defn compile-listener [project]
  (let [init-sym    (get-in project [:ring :init])
        destroy-sym (get-in project [:ring :destroy])
        init-ns     (and init-sym    (symbol (namespace init-sym)))
        destroy-ns  (and destroy-sym (symbol (namespace destroy-sym)))
        project-ns  (symbol (listener-ns project))]
    (compile-form project project-ns
      `(do (ns ~project-ns
             (:require ~@(set (remove nil? [init-ns destroy-ns])))
             (:gen-class :implements [javax.servlet.ServletContextListener]))
           ~(let [servlet-context-event (gensym)]
              `(do
                 (defn ~'-contextInitialized [this# ~servlet-context-event]
                   ~(if init-sym
                      `(~init-sym)))
                 (defn ~'-contextDestroyed [this# ~servlet-context-event]
                   ~(if destroy-sym
                      `(~destroy-sym)))))))))

(defn create-war [project file-path]
  (-> (FileOutputStream. file-path)
      (BufferedOutputStream.)
      (JarOutputStream. (make-manifest (:manifest project)))))

(defn write-entry [war war-path entry]
  (.putNextEntry war (JarEntry. war-path))
  (io/copy entry war))

(defn str-entry [war war-path content]
  (write-entry war war-path (to-byte-stream content)))

(defn in-war-path [war-path root file]
  (str war-path
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

(defn war-resources-path [project]
  (:war-resources-path project "war-resources"))

(defn write-war [project war-path]
  (with-open [war-stream (create-war project war-path)]
    (doto war-stream
      (str-entry "WEB-INF/web.xml" (make-web-xml project))
      (dir-entry project "WEB-INF/classes/" (:compile-path project))
      (dir-entry project "WEB-INF/classes/" (:source-path project))
      (dir-entry project "WEB-INF/classes/" (:resources-path project))
      (dir-entry project "" (war-resources-path project)))))

(defn war
  "Create a $PROJECT-$VERSION.war file suitable for use in servlet containers."
  ([project]
     (war project (default-war-name project)))
  ([project war-name]
     (binding [compile/*silently* true]
       (when (zero? (compile/compile project))
         (let [war-path (war-file-path project war-name)]
           (compile-servlet project)
           (if (has-listener? project)
             (compile-listener project))
           (write-war project war-path)
           (println "Created" war-path)
           war-path)))))
