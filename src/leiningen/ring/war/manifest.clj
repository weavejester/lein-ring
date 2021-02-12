(ns leiningen.ring.war.manifest
  (:require [clojure.string :as string])
  (:import [java.util.jar Manifest]
           [java.io ByteArrayInputStream]))

"Mostly taken from leiningen.jar"

(def ^:private default-manifest
  {"Created-By" "Leiningen Ring Plugin"
   "Built-By" (System/getProperty "user.name")
   "Build-Jdk" (System/getProperty "java.version")})

(declare ^:private manifest-entry)

(defn- manifest-entries [project manifest-seq]
  (map (partial manifest-entry project) manifest-seq))

(defn- manifest-entry [project [k v]]
  (cond (symbol? v) (manifest-entry project [k (resolve v)])
        (fn? v) (manifest-entry project [k (v project)])
        (coll? v) (->> v ;; Sub-manifest = manifest section
                       (manifest-entries project)
                       (cons (str "\nName: " (name k) "\n"))
                       (string/join))
        :else (->> (str (name k) ": " v)
                   (partition-all 70)  ;; Manifest spec says lines <= 72 chars
                   (map (partial apply str))
                   (string/join "\n ")  ;; Manifest spec says join with "\n "
                   (format "%s\n"))))

(defn- manifest-map-to-reordered-seq [mf]
  (sort
    (comparator
      (fn [e1 e2]
        (not (coll? (second e1)))))
    (seq mf)))

(defn make-manifest
  ^Manifest [project]
  (let [project-manifest (into {} (:manifest project))]
    (->> project-manifest
         (merge
           (if (get project-manifest "Main-Class")
             default-manifest
             (if-let [main (:main project)]
               (assoc default-manifest
                 "Main-Class"
                 (munge (str main)))
               default-manifest)))
         manifest-map-to-reordered-seq
         (manifest-entries project)
         (cons "Manifest-Version: 1.0\n")  ;; Manifest-Version line must be first
         (string/join "")
         .getBytes
         ByteArrayInputStream.
         Manifest.)))
