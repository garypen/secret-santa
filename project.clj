(defproject secret-santa "0.2.0"
  :description "choose and email secret santa allocations to participants"
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.clojure/tools.cli "0.2.0"]
                 [org.apache.commons/commons-email "1.2"]]
  :main secret-santa.core)
