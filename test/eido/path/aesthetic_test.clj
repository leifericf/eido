(ns eido.path.aesthetic-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.path.aesthetic :as aes]))

(def square-cmds
  [[:move-to [0.0 0.0]]
   [:line-to [100.0 0.0]]
   [:line-to [100.0 100.0]]
   [:line-to [0.0 100.0]]
   [:close]])

(def line-cmds
  [[:move-to [0.0 0.0]]
   [:line-to [50.0 0.0]]
   [:line-to [100.0 0.0]]])

;; --- smooth-commands ---

(deftest smooth-commands-basic-test
  (testing "produces :curve-to commands"
    (let [result (aes/smooth-commands square-cmds {})]
      (is (vector? result))
      (is (pos? (count result)))
      (is (some #(= :curve-to (first %)) result)))))

(deftest smooth-commands-starts-with-move-to-test
  (testing "result starts with :move-to"
    (let [result (aes/smooth-commands square-cmds {})]
      (is (= :move-to (ffirst result))))))

(deftest smooth-commands-deterministic-test
  (testing "same input produces same output"
    (is (= (aes/smooth-commands square-cmds {:samples 32})
           (aes/smooth-commands square-cmds {:samples 32})))))

(deftest smooth-commands-short-path-test
  (testing "handles 2-point path"
    (let [cmds [[:move-to [0.0 0.0]] [:line-to [100.0 100.0]]]
          result (aes/smooth-commands cmds {})]
      (is (vector? result))
      (is (pos? (count result))))))

;; --- jittered-commands ---

(deftest jittered-commands-determinism-test
  (testing "same seed produces same output"
    (is (= (aes/jittered-commands line-cmds {:amount 5.0 :seed 42})
           (aes/jittered-commands line-cmds {:amount 5.0 :seed 42})))))

(deftest jittered-commands-different-seeds-test
  (testing "different seeds produce different output"
    (is (not= (aes/jittered-commands line-cmds {:amount 5.0 :seed 42})
              (aes/jittered-commands line-cmds {:amount 5.0 :seed 99})))))

(deftest jittered-commands-preserves-structure-test
  (testing "output is a valid path command sequence"
    (let [result (aes/jittered-commands square-cmds {:amount 3.0 :seed 42})]
      (is (vector? result))
      (is (= :move-to (ffirst result))))))

;; --- dash-commands ---

(deftest dash-commands-basic-test
  (testing "produces multiple dash segments"
    (let [dashes (aes/dash-commands line-cmds {:dash [20.0 10.0]})]
      (is (vector? dashes))
      (is (> (count dashes) 1)))))

(deftest dash-commands-each-dash-valid-test
  (testing "each dash starts with :move-to"
    (let [dashes (aes/dash-commands line-cmds {:dash [20.0 10.0]})]
      (is (every? #(= :move-to (ffirst %)) dashes)))))

(deftest dash-commands-deterministic-test
  (testing "same input produces same output"
    (is (= (aes/dash-commands line-cmds {:dash [15.0 5.0]})
           (aes/dash-commands line-cmds {:dash [15.0 5.0]})))))

(deftest dash-commands-respects-offset-test
  (testing "offset shifts the dash pattern"
    (let [d1 (aes/dash-commands line-cmds {:dash [20.0 10.0] :offset 0.0})
          d2 (aes/dash-commands line-cmds {:dash [20.0 10.0] :offset 5.0})]
      (is (not= d1 d2)))))

;; --- convenience helper tests ---

(deftest stylize-test
  (testing "chains smooth then jitter"
    (let [result (aes/stylize line-cmds [{:op :smooth :samples 20}
                                          {:op :jitter :amount 2.0 :seed 42}])]
      (is (vector? result))
      (is (= :move-to (ffirst result)))))
  (testing "dash as last step returns vector of segments"
    (let [result (aes/stylize line-cmds [{:op :dash :dash [20.0 10.0]}])]
      (is (vector? result))
      (is (> (count result) 0)))))
