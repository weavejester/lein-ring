(defproject lein-ring "0.8.13"
  :description "Leiningen Ring plugin"
  :url "https://github.com/weavejester/lein-ring"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.xml "0.0.6"]
                 [leinjacker "0.4.1"]
                 [javax.servlet/servlet-api "2.5"]]
  :eval-in-leiningen true
  :java-source-paths ["java"]
  :javac-options ["-target" "1.6" "-source" "1.6"])
