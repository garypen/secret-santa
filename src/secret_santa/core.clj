(ns secret-santa.core
  (:require [clojure.java.io :as io])
  (:require [clojure.set :as set])
  (:require [clojure.string :as string])
  (:use [clojure.tools.cli])
  (:import org.apache.commons.mail.SimpleEmail)
  (:gen-class))

(defn- randomize
  "Randomize the sequence of a supplied vector."
  [users]
  (loop [participants [] remaining users]
    (if (empty? remaining)
      participants
      (let [chosen (rand-nth (seq remaining))]
        (recur (conj participants chosen)
               (set/difference remaining #{chosen}))))))

(defn- pick-pairs
  "Pick pairs consisting of a giver and a receiver"
  [users]
  (let [first (first users) last (last users)]
    (loop [pairs [[last first]] users users]
      (let [[giver receiver] (take 2 users)]
        (if (or (= giver last) (= receiver last))
          (conj pairs [giver receiver])
          (recur (conj pairs [giver receiver]) (rest users)))))))

(defn- email-giver
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

(defn- email-examiner
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

(defn- read-data
  "Read a list of people to choose between. Return a set of users (which has the beneficial side-effect of eliminating duplicates in the input)."
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
                                   ["-o" "--originator" "The originator" :default "" :required true]
                                   ["-s" "--server" "Email server" :default "" :required true]
                                   ["-t" "--tester" "Testing address"])]
    (when (:help options)
      (println banner)
      (System/exit 0))
    (cond
     (nil? args) (println banner)
     :default (let [pairs (pick-pairs (randomize (read-data (first args))))]
                (println "Dispatching emails...")
                (when (:examiner options)
                  (email-examiner (:tester options) (:server options)
                                  (:originator options) (:examiner options)
                                  pairs))
                (doseq [pair pairs]
                  (email-giver (:tester options) (:server options)
                               (:originator options) pair))))))
