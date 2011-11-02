(defproject secret-santa "0.1.0-SNAPSHOT"
  :description "choose and email secret santa allocations to participants"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.cli "0.2.0-SNAPSHOT"]
                 [org.apache.commons/commons-email "1.2"]]
  :main secret-santa.core)