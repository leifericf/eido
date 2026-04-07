(ns eido.stipple-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.stipple :as stipple]))

;; --- poisson disk sampling ---

(deftest poisson-disk-test
  (testing "generates points within bounds"
    (let [pts (stipple/poisson-disk 0 0 100 100 5 42)]
      (is (pos? (count pts)))
      (is (every? (fn [[x y]] (and (<= 0 x 100) (<= 0 y 100))) pts))))
  (testing "minimum distance between points"
    (let [min-dist 10
          pts (stipple/poisson-disk 0 0 100 100 min-dist 42)]
      (is (every? (fn [[x1 y1]]
                    (every? (fn [[x2 y2]]
                              (or (and (= x1 x2) (= y1 y2))
                                  (>= (Math/sqrt (+ (* (- x2 x1) (- x2 x1))
                                                    (* (- y2 y1) (- y2 y1))))
                                      (* min-dist 0.9))))
                            pts))
                  pts))))
  (testing "deterministic with seed"
    (is (= (stipple/poisson-disk 0 0 100 100 5 42)
           (stipple/poisson-disk 0 0 100 100 5 42)))))

;; --- stipple fill nodes ---

(deftest stipple-fill->nodes-test
  (testing "generates circle nodes"
    (let [nodes (stipple/stipple-fill->nodes 0 0 100 100
                  {:stipple/density 0.5 :stipple/radius 1 :stipple/seed 42})]
      (is (vector? nodes))
      (is (pos? (count nodes)))
      (is (every? #(= :shape/circle (:node/type %)) nodes))))
  (testing "higher density produces more dots"
    (let [sparse (stipple/stipple-fill->nodes 0 0 100 100
                   {:stipple/density 0.2 :stipple/radius 1 :stipple/seed 42})
          dense  (stipple/stipple-fill->nodes 0 0 100 100
                   {:stipple/density 0.8 :stipple/radius 1 :stipple/seed 42})]
      (is (> (count dense) (count sparse))))))
