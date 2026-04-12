(ns eido.gen.ca-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.ca :as ca]))

;; --- Cellular Automata ---

(deftest ca-grid-dimensions-test
  (testing "grid has correct dimensions"
    (let [g (ca/ca-grid 20 15 :random 42)]
      (is (= 20 (:w g)))
      (is (= 15 (:h g)))
      (is (= 300 (alength ^booleans (:grid g)))))))

(deftest ca-grid-determinism-test
  (testing "same seed produces same grid"
    (let [g1 (ca/ca-grid 10 10 :random 42)
          g2 (ca/ca-grid 10 10 :random 42)]
      (is (java.util.Arrays/equals ^booleans (:grid g1) ^booleans (:grid g2))))))

(deftest ca-grid-unknown-init-test
  (testing "nil or unknown init type throws ExceptionInfo"
    (is (thrown? clojure.lang.ExceptionInfo (ca/ca-grid 5 5 nil 42)))
    (is (thrown? clojure.lang.ExceptionInfo (ca/ca-grid 5 5 :invalid 42)))))

(deftest ca-step-preserves-dimensions-test
  (testing "step preserves grid dimensions"
    (let [g  (ca/ca-grid 10 10 :random 42)
          g2 (ca/ca-step g :life)]
      (is (= 10 (:w g2)))
      (is (= 10 (:h g2)))
      (is (= 100 (alength ^booleans (:grid g2)))))))

(deftest ca-block-still-life-test
  (testing "2x2 block is a still life in Game of Life"
    (let [g {:grid (boolean-array (map boolean
                     [false false false false
                      false true  true  false
                      false true  true  false
                      false false false false]))
             :w 4 :h 4}
          g2 (ca/ca-step g :life)]
      ;; Center 2x2 should remain alive
      (is (aget ^booleans (:grid g2) 5))
      (is (aget ^booleans (:grid g2) 6))
      (is (aget ^booleans (:grid g2) 9))
      (is (aget ^booleans (:grid g2) 10))
      ;; Corners should remain dead
      (is (not (aget ^booleans (:grid g2) 0)))
      (is (not (aget ^booleans (:grid g2) 15))))))

(deftest ca-blinker-oscillator-test
  (testing "blinker oscillates with period 2"
    (let [g {:grid (boolean-array (map boolean
                     [false false false false false
                      false false true  false false
                      false false true  false false
                      false false true  false false
                      false false false false false]))
             :w 5 :h 5}
          g1 (ca/ca-step g :life)
          g2 (ca/ca-step g1 :life)]
      ;; After one step: horizontal blinker
      (is (aget ^booleans (:grid g1) 11))  ;; (1,2)
      (is (aget ^booleans (:grid g1) 12))  ;; (2,2)
      (is (aget ^booleans (:grid g1) 13))  ;; (3,2)
      ;; After two steps: back to vertical
      (is (java.util.Arrays/equals ^booleans (:grid g) ^booleans (:grid g2))))))

(deftest ca-run-test
  (testing "ca-run advances multiple steps"
    (let [g  (ca/ca-grid 10 10 :random 42)
          g5 (ca/ca-run g :life 5)]
      (is (= 10 (:w g5))))))

(deftest ca->nodes-test
  (testing "produces rect scene nodes for alive cells"
    (let [g     (ca/ca-grid 5 5 :random 42)
          nodes (ca/ca->nodes g 10 {:style/fill [:color/rgb 0 0 0]})
          ^booleans arr (:grid g)
          alive (loop [i 0 cnt 0]
                  (if (>= i (alength arr)) cnt
                    (recur (inc i) (if (aget arr i) (inc cnt) cnt))))]
      ;; Should have one node per alive cell
      (is (= alive (count nodes)))
      (is (every? #(= :shape/rect (:node/type %)) nodes)))))

;; --- Reaction-Diffusion ---

(deftest rd-grid-dimensions-test
  (testing "RD grid has correct dimensions"
    (let [g (ca/rd-grid 20 15 :center 42)]
      (is (= 20 (:w g)))
      (is (= 15 (:h g)))
      (is (= 300 (alength ^doubles (:a g))))
      (is (= 300 (alength ^doubles (:b g)))))))

(deftest rd-grid-initial-concentrations-test
  (testing "A starts at 1.0, B starts near 0.0 (except seed area)"
    (let [g (ca/rd-grid 20 20 :center 42)
          ^doubles a (:a g)]
      ;; Corner should be A=1.0
      (is (> (aget a 0) 0.9)))))

(deftest rd-step-preserves-dimensions-test
  (testing "RD step preserves grid dimensions"
    (let [g  (ca/rd-grid 20 20 :center 42)
          g2 (ca/rd-step g (:coral ca/rd-presets))]
      (is (= 20 (:w g2)))
      (is (= 20 (:h g2))))))

(deftest rd-run-produces-patterns-test
  (testing "after many steps B concentration is nonzero somewhere"
    (let [g  (ca/rd-grid 30 30 :center 42)
          g' (ca/rd-run g (:coral ca/rd-presets) 100)
          ^doubles b (:b g')]
      (is (some #(> % 0.01) (seq b))))))

(deftest rd-determinism-test
  (testing "same seed and params produce same result"
    (let [g1 (ca/rd-run (ca/rd-grid 20 20 :center 42) (:coral ca/rd-presets) 50)
          g2 (ca/rd-run (ca/rd-grid 20 20 :center 42) (:coral ca/rd-presets) 50)]
      (is (java.util.Arrays/equals ^doubles (:a g1) ^doubles (:a g2)))
      (is (java.util.Arrays/equals ^doubles (:b g1) ^doubles (:b g2))))))

(deftest rd->nodes-test
  (testing "produces rect scene nodes"
    (let [g     (ca/rd-grid 10 10 :center 42)
          nodes (ca/rd->nodes g 5 (fn [_a b] (if (> b 0.1)
                                                [:color/rgb 0 0 0]
                                                [:color/rgb 255 255 255])))]
      (is (= 100 (count nodes)))
      (is (every? #(= :shape/rect (:node/type %)) nodes)))))
