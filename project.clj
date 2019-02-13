(defproject lein-ring "0.12.5"
  :description "Leiningen Ring plugin"
  :url "https://github.com/weavejester/lein-ring"
  :scm {:name "git"
        :url "https://github.com/weavejester/lein-ring"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/core.unify "0.5.7"]
                 [org.clojure/data.xml "0.0.8"]
                 [leinjacker "0.4.2"
                  :exclusions [org.clojure/core.unify]]]
  :eval-in-leiningen true)
