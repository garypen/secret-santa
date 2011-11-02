(ns secret-santa.core
  (:require [clojure.java.io :as io])
  (:require [clojure.set :as set])
  (:require [clojure.string :as string])
  (:use [clojure.tools.cli])
  (:import org.apache.commons.mail.SimpleEmail)
  (:gen-class))

(def valid-data (atom true))

(defn pick-receiver
  "Pick a random receiver from a sequence whilst ensuring duplication
does not occur"
  [giver receivers]
  (rand-nth (seq (set/difference receivers [giver]))))

(defn pick-giver
  "Pick a random giver from a sequence"
  [givers]
  (rand-nth (seq givers)))

(defn pick-pairs
  "Pick a set of pairs consisting of a giver and a receiver"
  [users]
  (loop [pairs #{} givers users receivers users]
    (let [giver (pick-giver givers)
          receiver (pick-receiver giver receivers)]
      (if (empty? givers)
        pairs
        (recur (into pairs #{[giver receiver]})
               (set/difference givers [giver])
               (set/difference receivers [receiver]))))))

(defn email-giver
  "Print a pair consisting of a giver and a receiver"
  [tester server originator pair]
  (let [[giver email] (string/split (nth pair 0) #":")
        [receiver] (string/split (nth pair 1) #":")
        email (if (nil? tester) email tester)]
    (doto (SimpleEmail.)
      (.setHostName server)
      (.addTo email)
      (.setFrom originator "Secret Santa")
      (.setSubject "Hello from secret santa")
      (.setMsg (str "Your Secret Santa Details\nYou (" giver ":" email
                    ") should buy a present for (" receiver
                    ")\nThanks for using Secret Santa."))
      (.send))))

(defn email-examiner
  "Email the pairings to a designated examiner"
  [tester server originator examiner pairs]
  (let [text (ref "Hi examiner,\n\nSecret Santa has chosen the following pairings, please ensure they are correct and let the sender of this message know if you think there is a mistake.\n\n") examiner (if (nil? tester) examiner tester)]
    (dosync
     (doseq [pair pairs]
       (ref-set text (str @text (apply str (interpose " buys a present for " [(nth pair 0) (nth pair 1)])) "\n")))
     (ref-set text (str @text "Thanks for helping out,\n\nSecret Santa\n\n")))
    (doto (SimpleEmail.)
      (.setHostName server)
      (.addTo examiner)
      (.setFrom originator "Secret Santa")
      (.setSubject "Supervise request from secret santa")
      (.setMsg @text)
      (.send))))

(defn validate-pairings
  "Take a pair and a set of pairs, reverse the pair and ensure it
  isn't present in the set of pairs"
  [pair pairs]
  (if (nil? (nth pair 1))
    (reset! valid-data false)
    (if (set/subset? #{(reverse pair)} pairs)
      (reset! valid-data false))))


(defn read-data
  "Read a list of people to choose between. Return a sequence of folks."
  [file-name]
  (println (str "Processing: " file-name))
  (with-open [in-file (io/reader file-name)]
    (doall
     (set (line-seq in-file)))))

(defn -main
  [& args]
  (let [[options args banner] (cli args
                                   ["-e" "--examiner" "Email validator"]
                                   ["-h" "--help" "Display help" :default false :flag true]
                                   ["-o" "--originator" "The originator" :default ""]
                                   ["-s" "--server" "Email server" :default ""]
                                   ["-t" "--tester" "Testing address"])]
    (when (:help options)
      (println banner)
      (System/exit 0))
    (when (.equals (:originator options) "")
      (println "An originator and a server are required (supercedes information below).")
      (println banner)
      (System/exit 2))
    (when (.equals (:server options) "")
      (println "An originator and a server are required (supercedes information below).")
      (println banner)
      (System/exit 2))
    (cond
     (nil? args) (println banner)
     :default (let [[one] args data (read-data one)]
                (loop [pairs (pick-pairs data)]
                  (println "Searching for good combinations...")
                  (reset! valid-data true)
                  (doseq [pair pairs]
                    (validate-pairings pair pairs))
                  (if (true? @valid-data)
                    (do
                      (println "Found a good solution. Dispatching emails.")
                      (when (:examiner options)
                        (email-examiner (:tester options) (:server options)
                          (:originator options) (:examiner options) pairs))
                      (doseq [pair pairs]
                        (email-giver (:tester options) (:server options)
                          (:originator options) pair)))
                    (do
                      (println "Discarding invalid solution: " (str pairs))
                      (recur (pick-pairs data)))))))))
