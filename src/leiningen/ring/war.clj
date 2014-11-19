(ns leiningen.ring.war
  (:require [leiningen.compile :as compile]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [leinjacker.utils :as lju]
            [leinjacker.deps :as deps])
  (:use [clojure.data.xml :only [sexp-as-element indent-str]]
        leiningen.ring.util)
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
  (let [target-dir (or (:target-dir project) (:target-path project))]
    (.mkdirs (io/file target-dir))
    (str target-dir "/" war-name)))

(defn skip-file? [project war-path file]
  (or (re-find #"^\.?#" (.getName file))
      (re-find #"~$" (.getName file))
      (some #(re-find % war-path)
            (get-in project [:ring :war-exclusions] [#"(^|/)\."]))))

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

(def web-app-attrs
  "Attributes for the web-app element, indexed by the servlet version."
  {"2.4" {:xmlns     "http://java.sun.com/xml/ns/j2ee"
          :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
          :xsi:schemaLocation (str "http://java.sun.com/xml/ns/j2ee "
                                   "http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd")
          :version "2.4"}
   "2.5" {:xmlns     "http://java.sun.com/xml/ns/javaee"
          :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
          :xsi:schemaLocation (str "http://java.sun.com/xml/ns/javaee "
                                   "http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd")
          :version "2.5"}
   "3.0" {:xmlns     "http://java.sun.com/xml/ns/javaee"
          :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
          :xsi:schemaLocation (str "http://java.sun.com/xml/ns/javaee "
                                   "http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd")
          :version "3.0"}})

(def default-servlet-version "2.5")

(defn make-web-xml [project]
  (let [ring-options (:ring project)]
    (if (contains? ring-options :web-xml)
      (slurp (:web-xml ring-options))
      (indent-str
        (sexp-as-element
          [:web-app
           (get web-app-attrs
                (get-in project [:ring :servlet-version] default-servlet-version)
                {})
           [:listener
            [:listener-class (listener-class project)]]
           [:servlet
            [:servlet-name  (servlet-name project)]
            [:servlet-class (servlet-class project)]]
           [:servlet-mapping
            [:servlet-name (servlet-name project)]
            [:url-pattern (url-pattern project)]]])))))

(defn generate-handler [project handler-sym]
  (if (get-in project [:ring :servlet-path-info?] true)
    `(let [handler# ~(require-and-resolve handler-sym)]
       (fn [request#]
         (let [context# ^String (.getContextPath (:servlet-request request#))]
           (handler#
            (assoc request#
              :context context#
              :path-info (-> (:uri request#) (subs (.length context#)) not-empty (or "/")))))))
    (require-and-resolve handler-sym)))

(defn write-service
  "Returns code for a service method with an optional prefix suitable for
  being used by genclass to compile a HttpServlet class.

  Twin to ring.util.servlet/write-service, but called as a function to
  generate code, rather than as a macro."
  ([handler]
   (write-service "-" handler))
  ([prefix handler]
   `(def ~(symbol (str prefix "service"))
      (let [method# (atom nil)]
        (fn [servlet# request# response#]
          ((or
             @method#
             (reset! method#
               (~(require-and-resolve
                   `ring.util.servlet/make-service-method)
                 ~handler)))
           servlet# request# response#))))))

(defn compile-servlet [project]
  (let [handler-sym (get-in project [:ring :handler])
        servlet-ns  (symbol (servlet-ns project))]
    (compile-form project servlet-ns
      `(do (ns ~servlet-ns
             (:gen-class :extends javax.servlet.http.HttpServlet))
           ~(write-service
              (generate-handler project handler-sym))))))

(defn compile-listener [project]
  (let [init-sym    (get-in project [:ring :init])
        destroy-sym (get-in project [:ring :destroy])
        handler-ns  (symbol (namespace (get-in project [:ring :handler])))
        project-ns  (symbol (listener-ns project))]
    (compile-form project project-ns
      `(do (ns ~project-ns
             (:gen-class :implements [javax.servlet.ServletContextListener]))
           ~(let [servlet-context-event (gensym)]
              `(do
                 (defn ~'-contextInitialized [this# ~servlet-context-event]
                   ~(if init-sym
                      `(~(require-and-resolve init-sym)))
                   (require '~handler-ns))
                 (defn ~'-contextDestroyed [this# ~servlet-context-event]
                   ~(if destroy-sym
                      `(~(require-and-resolve destroy-sym))))))))))

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

(defn war-resources-paths [project]
  (filter identity
    (distinct (concat [(:war-resources-path project "war-resources")] (:war-resource-paths project)))))

(defn write-war [project war-path]
  (with-open [war-stream (create-war project war-path)]
    (doto war-stream
      (str-entry "WEB-INF/web.xml" (make-web-xml project))
      (dir-entry project "WEB-INF/classes/" (:compile-path project)))
    (doseq [path (source-and-resource-paths project)
            :when path]
      (dir-entry war-stream project "WEB-INF/classes/" path))
    (doseq [path (war-resources-paths project)]
      (dir-entry war-stream project "" path))
    war-stream))

(defn unmerge-profiles [project]
  (if-let [unmerge-fn (and (= 2 (lju/lein-generation))
                           (lju/try-resolve 'leiningen.core.project/unmerge-profiles))]
    (unmerge-fn project [:default])
    project))

(defn add-servlet-dep [project]
  (-> project
      (deps/add-if-missing '[ring/ring-servlet "1.2.1"])
      (deps/add-if-missing '[javax.servlet/servlet-api "2.5"])))

(defn war
  "Create a $PROJECT-$VERSION.war file."
  ([project]
     (war project (default-war-name project)))
  ([project war-name]
     (ensure-handler-set! project)
      (let [project (-> project
                        unmerge-profiles
                        add-servlet-dep)
           result  (compile/compile project)]
       (when-not (and (number? result) (pos? result))
         (let [war-path (war-file-path project war-name)]
           (compile-servlet project)
           (compile-listener project)
           (write-war project war-path)
           (println "Created" war-path)
           war-path)))))
