(ns eido.gen.lsystem-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.lsystem :as lsystem]))

(deftest expand-string-test
  (testing "applies rules to axiom"
    (is (= "F+F" (lsystem/expand-string "F" {"F" "F+F"} 1)))
    (is (= "F+F+F+F" (lsystem/expand-string "F" {"F" "F+F"} 2))))
  (testing "characters without rules pass through"
    (is (= "F+G" (lsystem/expand-string "F+G" {"F" "F"} 1)))))

(deftest interpret-test
  (testing "F produces a line segment"
    (let [cmds (lsystem/interpret "F" {:angle 90.0 :length 10.0 :origin [0 0] :heading -90.0})]
      (is (= 2 (count cmds)))
      (is (= :move-to (first (first cmds))))
      (is (= :line-to (first (second cmds)))))))

(deftest interpret-turn-test
  (testing "F+F produces an angled path"
    (let [cmds (lsystem/interpret "F+F" {:angle 90.0 :length 10.0 :origin [0 0] :heading 0.0})]
      ;; move-to, line-to, line-to (3 commands)
      (is (= 3 (count cmds))))))

(deftest interpret-branch-test
  (testing "branching with [] creates separate subpaths"
    (let [cmds (lsystem/interpret "F[+F]F" {:angle 45.0 :length 10.0 :origin [0 0] :heading -90.0})]
      ;; Should have a move-to for the branch return
      (is (some #(= :move-to (first %)) (rest cmds))))))

(deftest lsystem->path-cmds-test
  (testing "full pipeline: axiom -> rules -> path"
    (let [cmds (lsystem/lsystem->path-cmds
                 "F" {"F" "F[+F]F[-F]F"}
                 {:iterations 2 :angle 25.0 :length 5.0 :origin [100 200] :heading -90.0})]
      (is (vector? cmds))
      (is (pos? (count cmds)))
      (is (= :move-to (first (first cmds)))))))

;; --- constrained expansion tests ---

(deftest constrained-expansion-test
  (testing "vector rule alternatives with :bounds produce valid output"
    (let [cmds (lsystem/lsystem->path-cmds "F" lsystem/bush
                 {:iterations 4 :angle 22.5 :length 5.0
                  :origin [200 380] :heading -90
                  :bounds [0 0 400 400] :seed 42})]
      (is (vector? cmds))
      (is (pos? (count cmds)))
      (is (= :move-to (first (first cmds)))))))

(deftest constrained-stays-in-bounds-test
  (testing "bounded result stays within specified bounds"
    (let [bounds [20 20 360 360]
          cmds (lsystem/lsystem->path-cmds "F" lsystem/tree
                 {:iterations 4 :angle 25 :length 5
                  :origin [200 380] :heading -90
                  :bounds bounds :seed 42})
          points (keep (fn [[cmd & args]]
                         (when (#{:move-to :line-to} cmd) (first args)))
                       cmds)
          xs (map first points) ys (map second points)]
      (is (>= (reduce min xs) 20))
      (is (>= (reduce min ys) 20))
      (is (<= (reduce max xs) 380))
      (is (<= (reduce max ys) 380)))))

(deftest constrained-seed-variety-test
  (testing "different seeds produce different bounded expansions"
    (let [opts {:iterations 3 :angle 25 :length 8
                :origin [80 140] :heading -90
                :bounds [0 0 160 160]}
          r1 (lsystem/lsystem->path-cmds "F" lsystem/tree (assoc opts :seed 1))
          r2 (lsystem/lsystem->path-cmds "F" lsystem/tree (assoc opts :seed 99))]
      (is (not= r1 r2)))))

(deftest constrained-deterministic-test
  (testing "same seed produces same result"
    (let [opts {:iterations 4 :angle 22.5 :length 5
                :origin [200 380] :heading -90
                :bounds [0 0 400 400] :seed 42}]
      (is (= (lsystem/lsystem->path-cmds "F" lsystem/bush opts)
             (lsystem/lsystem->path-cmds "F" lsystem/bush opts))))))

(deftest presets-exist-test
  (testing "all presets are maps with vector alternatives"
    (doseq [preset [lsystem/bush lsystem/fern lsystem/coral
                    lsystem/lightning lsystem/seaweed lsystem/tree]]
      (is (map? preset))
      (is (every? vector? (vals preset))))))
