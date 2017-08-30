(ns leiningen.test.war
  (:require [clojure.test :refer :all]
            [leiningen.ring.war :refer :all]))

(deftest test-generate-war
  (testing "Generation of context params elements"
    (is (empty? (make-context-params {}))
        "If no context params are specified, none should be generated")
    (is (empty? (make-context-params "froboz"))
        "If the argument to make-context-params is not a map, no context params should be generated")
    (is (= 1 (count (make-context-params {:foo "bar"})))
        "If one context param is specified, one should be generated")
    (is (= '([:context-param [:param-name "foo"][:param-value "bar"]])
           (make-context-params {:foo "bar"})))
    (is (= '([:context-param [:param-name "foo"][:param-value 123]])
           (make-context-params {:foo 123})))
    (is (= 2 (count (make-context-params {:foo "bar" :froboz "baz"})))
        "If two context params are specified, two should be generated"))
  (testing "Generation of war file"
    (is (= "" (make-web-xml {:ring {:web-xml "/dev/null"}}))
        "If a file path is specified as :web-xml, the content of that file should be returned")))
