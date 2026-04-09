(ns eido.gen.boids-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.boids :as boids]))

;; --- init-flock ---

(deftest init-flock-count-test
  (testing "creates the requested number of boids"
    (let [flock (boids/init-flock {:count 30 :bounds [0 0 400 400] :seed 42})]
      (is (= 30 (count (:boids flock)))))))

(deftest init-flock-determinism-test
  (testing "same seed produces same flock"
    (is (= (boids/init-flock {:count 10 :bounds [0 0 400 400] :seed 42})
           (boids/init-flock {:count 10 :bounds [0 0 400 400] :seed 42})))))

(deftest init-flock-different-seeds-test
  (testing "different seeds produce different flocks"
    (is (not= (boids/init-flock {:count 10 :bounds [0 0 400 400] :seed 42})
              (boids/init-flock {:count 10 :bounds [0 0 400 400] :seed 99})))))

(deftest init-flock-within-bounds-test
  (testing "boids start within bounds"
    (let [flock (boids/init-flock {:count 50 :bounds [10 20 200 150] :seed 42})]
      (doseq [{[x y] :pos} (:boids flock)]
        (is (>= x 10))
        (is (<= x 210))
        (is (>= y 20))
        (is (<= y 170))))))

;; --- step-flock ---

(deftest step-flock-preserves-count-test
  (testing "stepping preserves boid count"
    (let [flock (boids/init-flock {:count 20 :bounds [0 0 400 400] :seed 42})
          next  (boids/step-flock flock boids/classic)]
      (is (= 20 (count (:boids next)))))))

(deftest step-flock-advances-tick-test
  (testing "tick increments"
    (let [flock (boids/init-flock {:count 5 :bounds [0 0 400 400] :seed 42})
          next  (boids/step-flock flock boids/classic)]
      (is (= 1 (:tick next))))))

(deftest step-flock-determinism-test
  (testing "same input produces same output"
    (let [flock (boids/init-flock {:count 10 :bounds [0 0 400 400] :seed 42})]
      (is (= (boids/step-flock flock boids/classic)
             (boids/step-flock flock boids/classic))))))

;; --- separation ---

(deftest separation-pushes-apart-test
  (testing "close boids are pushed apart"
    (let [boid {:pos [100.0 100.0] :vel [1.0 0.0]}
          neighbors [{:pos [102.0 100.0] :vel [1.0 0.0]}]
          [fx _fy] (boids/separation boid neighbors 50.0 1.0)]
      ;; Force should push left (away from neighbor to the right)
      (is (< fx 0)))))

;; --- cohesion ---

(deftest cohesion-pulls-toward-center-test
  (testing "boid is pulled toward neighbor centroid"
    (let [boid {:pos [0.0 0.0] :vel [0.0 0.0]}
          neighbors [{:pos [100.0 0.0] :vel [0.0 0.0]}
                     {:pos [0.0 100.0] :vel [0.0 0.0]}]
          [fx fy] (boids/cohesion boid neighbors 200.0 1.0)]
      (is (> fx 0))
      (is (> fy 0)))))

;; --- alignment ---

(deftest alignment-matches-heading-test
  (testing "boid steers toward average neighbor velocity"
    (let [boid {:pos [0.0 0.0] :vel [0.0 0.0]}
          neighbors [{:pos [10.0 0.0] :vel [3.0 0.0]}
                     {:pos [0.0 10.0] :vel [3.0 0.0]}]
          [fx _fy] (boids/alignment boid neighbors 50.0 1.0)]
      (is (> fx 0)))))

;; --- bounds ---

(deftest boids-stay-in-bounds-test
  (testing "boids stay roughly within bounds over many steps"
    (let [config (assoc boids/classic :count 20 :seed 42)
          flock  (boids/init-flock config)
          final  (nth (iterate #(boids/step-flock % config) flock) 100)
          [bx by bw bh] (:bounds config)
          margin 100]
      (doseq [{[x y] :pos} (:boids final)]
        (is (> x (- bx margin)))
        (is (< x (+ bx bw margin)))
        (is (> y (- by margin)))
        (is (< y (+ by bh margin)))))))

;; --- simulate-flock ---

(deftest simulate-flock-count-test
  (testing "produces requested number of frames"
    (let [frames (boids/simulate-flock boids/classic 10 {})]
      (is (= 10 (count frames))))))

;; --- flock->nodes ---

(deftest flock->nodes-test
  (testing "produces scene nodes for each boid"
    (let [flock (boids/init-flock {:count 5 :bounds [0 0 400 400] :seed 42})
          nodes (boids/flock->nodes flock {:shape :triangle :size 8})]
      (is (= 5 (count nodes)))
      (is (every? #(= :shape/path (:node/type %)) nodes)))))
