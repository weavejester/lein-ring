(ns leiningen.ring.war
  (:require [leiningen.compile :as compile]
            [leiningen.ring.war.manifest
             :refer [make-manifest]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [leinjacker.deps :as deps])
  (:use [clojure.data.xml :only [sexp-as-element indent-str]]
        leiningen.ring.util)
  (:import [java.util.jar JarEntry
                          JarOutputStream]
           [java.io BufferedOutputStream
                    FileOutputStream
                    ByteArrayInputStream
                    File]))

(defn default-war-name [project]
  (or (get-in project [:ring :war-name])
      (str (:name project) "-" (:version project) ".war")))

(defn war-file-path [project war-name]
  (let [target-dir (or (:target-dir project) (:target-path project))]
    (.mkdirs (io/file target-dir))
    (str target-dir "/" war-name)))

(defn skip-file? [project war-path ^File file]
  (or (re-find #"^\.?#" (.getName file))
      (re-find #"~$" (.getName file))
      (some #(re-find % war-path)
            (get-in project [:ring :war-exclusions] [#"(^|/)\."]))))

(defn- to-byte-stream [^String s]
  (ByteArrayInputStream. (.getBytes s)))

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
                         (get-in project [:ring :destroy])
                         (get-in project [:ring :handler]))
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
    `(let [handler# ~(generate-resolve handler-sym)]
       (fn [request#]
         (let [context# (.getContextPath
                          ^javax.servlet.http.HttpServletRequest
                          (:servlet-request request#))]
           (handler#
            (assoc request#
              :context context#
              :path-info (-> (:uri request#) (subs (.length context#)) not-empty (or "/")))))))
    (generate-resolve handler-sym)))

(defn compile-servlet [project]
  (let [servlet-ns  (symbol (servlet-ns project))
        init-sym    (get-in project [:ring :servlet-init])
        destroy-sym (get-in project [:ring :servlet-destroy])]
    (compile-form project servlet-ns
      `(do (ns ~servlet-ns
             (:gen-class :extends javax.servlet.http.HttpServlet
               :exposes-methods {~'init    ~'superInit
                                 ~'destroy ~'superDestroy})
             (:import javax.servlet.http.HttpServlet))
           (def ~'service-method)
           (defn ~'-service [servlet# request# response#]
             (~'service-method servlet# request# response#))

           ~(if init-sym
              `(defn ~'-init [this# cfg#]
                 (. this# (~'superInit cfg#)) ;; super.init(cfg) first
                 (~(generate-resolve init-sym))))

           ~(if destroy-sym
              `(defn ~'-destroy [this#]
                 (~(generate-resolve destroy-sym))
                 (. this# ~'superDestroy))) ;; super.destroy() second
           )
      :print-meta true)))

(defn compile-listener [project]
  (let [init-sym    (get-in project [:ring :init])
        destroy-sym (get-in project [:ring :destroy])
        handler-sym (get-in project [:ring :handler])
        async?      (get-in project [:ring :async?])
        servlet-ns  (servlet-ns project)
        project-ns  (symbol (listener-ns project))]
    (assert-vars-exist project init-sym destroy-sym handler-sym)
    (compile-form project project-ns
      `(do (ns ~project-ns
             (:gen-class :implements [javax.servlet.ServletContextListener]))
           ~(let [servlet-context-event (gensym)]
              `(do
                 (defn ~'-contextInitialized [this# ~servlet-context-event]
                   ~(if init-sym
                      `(~(generate-resolve init-sym)))
                   (let [handler# ~(generate-handler project handler-sym)
                         make-service-method# ~(generate-resolve
                                                 'ring.util.servlet/make-service-method)
                         method# (make-service-method# handler# ~@(when async? [{:async? true}]))]
                     (alter-var-root
                       ~(generate-resolve (symbol servlet-ns "service-method"))
                       (constantly method#))))
                 (defn ~'-contextDestroyed [this# ~servlet-context-event]
                   ~(if destroy-sym
                      `(~(generate-resolve destroy-sym)))))))
      :print-meta true)))

(defn create-war ^JarOutputStream [project ^String file-path]
  (-> (FileOutputStream. file-path)
      (BufferedOutputStream.)
      (JarOutputStream. (make-manifest project))))

(defmulti write-entry (fn [war _ _] (class war)))
(defmethod write-entry JarOutputStream [war war-path entry]
  (.putNextEntry ^JarOutputStream war (JarEntry. ^String war-path))
  (io/copy entry war))
(defmethod write-entry File [war-dir war-path file-or-data]
  (let [to-file (io/file war-dir war-path)]
    (io/make-parents to-file)
    (io/copy file-or-data to-file)))

(defn str-entry [war war-path content]
  (write-entry war war-path (to-byte-stream content)))

(defn in-war-path [war-path root ^File file]
  (str war-path
       (-> (.toURI (io/file root))
           (.relativize (.toURI file))
           (.getPath))))

(defn file-entry [war project ^String war-path ^File file]
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
          (distinct (concat [(:war-resources-path project "war-resources")]
                            (:war-resource-paths project)))))

(defn write-war [war project & [additional-writes]]
  (doto war
    (str-entry "WEB-INF/web.xml" (make-web-xml project))
    (dir-entry project "WEB-INF/classes/" (:compile-path project)))
  (doseq [path (source-and-resource-paths project)
          :when path]
    (dir-entry war project "WEB-INF/classes/" path))
  (doseq [path (war-resources-paths project)]
    (dir-entry war project "" path))
  (when additional-writes
    (additional-writes war project))
  war)

(defn prepare-war [project war-path & [additional-writes]]
  (if (get-in project [:ring :exploded])
    (let [war-dir (io/as-file war-path)]
      (.mkdirs (io/file war-dir "META-INF"))
      (with-open [manifest-stream (io/output-stream (io/file war-dir "META-INF/MANIFEST.MF"))]
        (.write (make-manifest project) manifest-stream))
      (write-war war-dir project additional-writes))
    (with-open [war-stream (create-war project war-path)]
      (write-war war-stream project additional-writes))))

(defn add-servlet-dep [project]
  (-> project
      (deps/add-if-missing ['ring/ring-servlet ring-version])
      (deps/add-if-missing '[javax.servlet/javax.servlet-api "3.1.0"])))

(defn war
  "Create a $PROJECT-$VERSION.war file."
  ([project]
     (war project (default-war-name project)))
  ([project war-name & {:keys [profiles-to-merge additional-writes]}]
     (ensure-handler-set! project)
      (let [project (-> project
                        (unmerge-profiles [:default])
                        (merge-profiles profiles-to-merge)
                        add-servlet-dep)
           result  (compile/compile project)]
       (when-not (and (number? result) (pos? result))
         (let [war-path (war-file-path project war-name)]
           (compile-servlet project)
           (compile-listener project)
           (prepare-war project war-path additional-writes)
           (println "Created" war-path)
           war-path)))))
