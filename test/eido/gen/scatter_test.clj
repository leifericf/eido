(ns eido.gen.scatter-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.scatter :as scatter]))

;; --- distribution generators ---

(deftest grid-positions-test
  (testing "grid generates correct number of positions"
    (let [pts (scatter/grid 0 0 100 100 5 5)]
      (is (= 25 (count pts)))
      (is (every? (fn [[x y]] (and (<= 0 x 100) (<= 0 y 100))) pts)))))

(deftest poisson-disk-positions-test
  (testing "poisson-disk generates positions within bounds"
    (let [pts (scatter/poisson-disk 0 0 100 100 10 42)]
      (is (pos? (count pts)))
      (is (every? (fn [[x y]] (and (<= 0 x 100) (<= 0 y 100))) pts)))))

(deftest noise-field-positions-test
  (testing "noise-field generates positions biased by noise"
    (let [pts (scatter/noise-field 0 0 100 100 200 42)]
      (is (pos? (count pts)))
      (is (<= (count pts) 200))
      (is (every? (fn [[x y]] (and (<= 0 x 100) (<= 0 y 100))) pts)))))

;; --- scatter node expansion ---

(deftest scatter->nodes-test
  (testing "scatter produces group nodes for each position"
    (let [shape {:node/type :shape/circle
                 :circle/center [0.0 0.0]
                 :circle/radius 3.0
                 :style/fill [:color/rgb 255 0 0]}
          positions [[10.0 20.0] [30.0 40.0] [50.0 60.0]]
          nodes (scatter/scatter->nodes shape positions nil)]
      (is (= 3 (count nodes)))
      (is (every? #(= :group (:node/type %)) nodes)))))

(deftest scatter-with-jitter-test
  (testing "jitter modifies positions"
    (let [shape {:node/type :shape/circle
                 :circle/center [0.0 0.0]
                 :circle/radius 3.0
                 :style/fill [:color/rgb 255 0 0]}
          positions [[50.0 50.0]]
          nodes-no-jitter (scatter/scatter->nodes shape positions nil)
          nodes-jitter    (scatter/scatter->nodes shape positions {:x 10 :y 10 :seed 42})]
      (is (= 1 (count nodes-no-jitter)))
      (is (= 1 (count nodes-jitter)))
      ;; Jittered position should differ
      (is (not= (:node/transform (first nodes-no-jitter))
                (:node/transform (first nodes-jitter)))))))
