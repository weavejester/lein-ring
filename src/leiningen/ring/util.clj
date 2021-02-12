(ns leiningen.ring.util
  (:use [leinjacker.eval :only (eval-in-project)])
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [leiningen.deps]
            [leiningen.core.main :as lein-core]
            [leinjacker.utils :as lju]))

(defn find-namespaces [var-syms]
  (->> (remove nil? var-syms)
       (map (comp symbol namespace))
       (distinct)))

(defn assert-vars-exist [project & var-syms]
  (eval-in-project
   project
   (into [] var-syms)
   `(require ~@(for [n (find-namespaces var-syms)] `(quote ~n)))))

(defn generate-resolve [qual-sym]
  `(do (require '~(symbol (namespace qual-sym)))
       (resolve '~qual-sym)))

(defn ensure-handler-set!
  "Ensure the :handler option is set in the project map."
  [project]
  (when-not (-> project :ring :handler)
    (println
     (str "Missing Ring :handler option in project map.\n\n"
          "You need to have a line in your project.clj file that looks like:\n"
          "  :ring {:handler your.app/handler}"))
    (lein-core/exit 1)))

(defn source-file
  ^java.io.File [project namespace]
  (io/file (:compile-path project)
           (-> (str namespace)
               (str/replace "-" "_")
               (str/replace "." java.io.File/separator)
               (str ".clj"))))

(defn compile-form
  "Compile the supplied form into the target directory."
  [project namespace form & {:keys [print-meta]}]
  ;; We need to ensure that deps has already run before we write anything
  ;; to :target-dir, which is otherwise cleaned by deps if it runs for
  ;; the first time as a side effect of eval-in-project Ideally,
  ;; generated sources would be going into a dedicated directory and thus
  ;; be immune from the lifecycle around :target-dir; that would be
  ;; straightforward using lein 2.x middlewares, but not so easy with 1.x.
  (leiningen.deps/deps project)
  (let [out-file (source-file project namespace)]
    (.mkdirs (.getParentFile out-file))
    (with-open [out (io/writer out-file)]
      (binding [*out* out
                *print-meta* print-meta]
        (prn form))))
  (eval-in-project project
    `(do (clojure.core/compile '~namespace) nil)
    nil))

(defn update-project
  "Update the project map using a function."
  [project func & args]
  (vary-meta
   (apply func project args)
   update-in [:without-profiles] #(apply func % args)))

(defn source-and-resource-paths
  "Return a distinct sequence of the project's source and resource paths,
  unless :omit-source is true, in which case return only resource paths."
  [project]
  (let [resource-paths (concat [(:resources-path project)] (:resource-paths project))
        source-paths (if (:omit-source project)
                       '()
                       (concat [(:source-path project)] (:source-paths project)))]
    (distinct (concat source-paths resource-paths))))

(defn unmerge-profiles [project profiles]
  (if-let [unmerge-fn (and (= 2 (lju/lein-generation))
                           (lju/try-resolve 'leiningen.core.project/unmerge-profiles))]
    (unmerge-fn project profiles)
    project))

(defn merge-profiles [project profiles]
  (if-let [merge-fn (and (= 2 (lju/lein-generation))
                         (lju/try-resolve 'leiningen.core.project/merge-profiles))]
    (merge-fn project profiles)
    project))

(def ring-version "1.6.1")
