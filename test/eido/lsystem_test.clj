(ns eido.lsystem-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.lsystem :as lsystem]))

(deftest expand-string-test
  (testing "applies rules to axiom"
    (is (= "F+F" (lsystem/expand-string "F" {"F" "F+F"} 1)))
    (is (= "F+F+F+F" (lsystem/expand-string "F" {"F" "F+F"} 2))))
  (testing "characters without rules pass through"
    (is (= "F+G" (lsystem/expand-string "F+G" {"F" "F"} 1)))))

(deftest interpret-test
  (testing "F produces a line segment"
    (let [cmds (lsystem/interpret "F" 90.0 10.0 [0 0] -90.0)]
      (is (= 2 (count cmds)))
      (is (= :move-to (first (first cmds))))
      (is (= :line-to (first (second cmds)))))))

(deftest interpret-turn-test
  (testing "F+F produces an angled path"
    (let [cmds (lsystem/interpret "F+F" 90.0 10.0 [0 0] 0.0)]
      ;; move-to, line-to, line-to (3 commands)
      (is (= 3 (count cmds))))))

(deftest interpret-branch-test
  (testing "branching with [] creates separate subpaths"
    (let [cmds (lsystem/interpret "F[+F]F" 45.0 10.0 [0 0] -90.0)]
      ;; Should have a move-to for the branch return
      (is (some #(= :move-to (first %)) (rest cmds))))))

(deftest lsystem->path-cmds-test
  (testing "full pipeline: axiom -> rules -> path"
    (let [cmds (lsystem/lsystem->path-cmds
                 "F" {"F" "F[+F]F[-F]F"} 2 25.0 5.0 [100 200] -90.0)]
      (is (vector? cmds))
      (is (pos? (count cmds)))
      (is (= :move-to (first (first cmds)))))))
