(ns eido.gen.particle-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.particle :as particle]
    [eido.scene3d :as s3d]))

;; --- determinism ---

(deftest determinism-test
  (testing "same config and seed produces identical output"
    (let [config (particle/with-position particle/fire [200 300])
          run    #(vec (particle/simulate config 10 {:fps 30}))
          r1     (run)
          r2     (run)]
      (is (= r1 r2)))))

(deftest different-seeds-differ-test
  (testing "different seeds produce different output"
    (let [config (particle/with-position particle/sparks [200 200])
          r1 (vec (particle/simulate config 5 {:fps 30}))
          r2 (vec (particle/simulate (particle/with-seed config 99) 5 {:fps 30}))]
      (is (not= r1 r2)))))

;; --- laziness ---

(deftest laziness-test
  (testing "simulate returns a lazy seq"
    (let [config (particle/with-position particle/fire [200 300])
          frames (particle/simulate config 1000 {:fps 30})]
      (is (seq? frames))
      (is (= 3 (count (take 3 frames)))))))

;; --- lifecycle ---

(deftest particles-die-test
  (testing "burst particles eventually die"
    (let [config {:particle/emitter  {:emitter/type :point
                                      :emitter/position [100 100]
                                      :emitter/burst 10
                                      :emitter/direction [0 -1]
                                      :emitter/speed [50 50]}
                  :particle/lifetime [0.1 0.2]
                  :particle/forces   []
                  :particle/seed     42}
          frames (vec (particle/simulate config 30 {:fps 30}))]
      (is (pos? (count (first frames))))
      (is (zero? (count (last frames)))))))

(deftest continuous-emission-test
  (testing "rate-based emitter produces particles over time"
    (let [config {:particle/emitter  {:emitter/type :point
                                      :emitter/position [100 100]
                                      :emitter/rate 60
                                      :emitter/direction [0 -1]
                                      :emitter/speed [50 50]}
                  :particle/lifetime [1.0 1.0]
                  :particle/forces   []
                  :particle/seed     42}
          frames (vec (particle/simulate config 30 {:fps 30}))]
      (is (< (count (first frames))
             (count (nth frames 14)))))))

;; --- forces ---

(deftest gravity-accelerates-test
  (testing "gravity increases downward velocity"
    (let [config {:particle/emitter  {:emitter/type :point
                                      :emitter/position [100 100]
                                      :emitter/burst 1
                                      :emitter/direction [1 0]
                                      :emitter/speed [0 0]}
                  :particle/lifetime [2.0 2.0]
                  :particle/forces   [{:force/type :gravity
                                       :force/acceleration [0 100]}]
                  :particle/seed     42}
          frames (vec (particle/simulate config 10 {:fps 30}))
          y-at   (fn [i] (second (:circle/center (first (nth frames i)))))]
      ;; Particle should move downward (increasing y)
      (is (< (y-at 0) (y-at 4)))
      ;; And accelerate (later frames move more)
      (is (< (- (y-at 2) (y-at 1))
             (- (y-at 5) (y-at 4)))))))

(deftest drag-decelerates-test
  (testing "drag slows fast particles"
    (let [config {:particle/emitter  {:emitter/type :point
                                      :emitter/position [100 100]
                                      :emitter/burst 1
                                      :emitter/direction [1 0]
                                      :emitter/speed [200 200]}
                  :particle/lifetime [2.0 2.0]
                  :particle/forces   [{:force/type :drag
                                       :force/coefficient 2.0}]
                  :particle/seed     42}
          frames (vec (particle/simulate config 10 {:fps 30}))
          x-at   (fn [i] (first (:circle/center (first (nth frames i)))))
          dx1    (- (x-at 1) (x-at 0))
          dx5    (- (x-at 5) (x-at 4))]
      ;; Later movement should be smaller (deceleration)
      (is (< dx5 dx1)))))

;; --- output validity ---

(deftest output-is-valid-nodes-test
  (testing "rendered output contains valid Eido node maps"
    (let [config (particle/with-position particle/fire [200 300])
          frames (vec (particle/simulate config 10 {:fps 30}))]
      (doseq [frame frames
              node frame]
        (is (= :shape/circle (:node/type node)))
        (is (vector? (:circle/center node)))
        (is (number? (:circle/radius node)))
        (is (some? (:style/fill node)))
        (is (number? (:node/opacity node)))))))

(deftest rect-shape-output-test
  (testing "rect shape produces valid rect nodes"
    (let [config (particle/with-position particle/confetti [200 200])
          frame  (first (particle/simulate config 1 {:fps 30}))]
      (is (pos? (count frame)))
      (doseq [node frame]
        (is (= :shape/rect (:node/type node)))
        (is (vector? (:rect/xy node)))
        (is (vector? (:rect/size node)))))))

;; --- presets ---

(deftest presets-are-maps-test
  (testing "all presets are plain maps"
    (doseq [preset [particle/fire particle/confetti particle/snow
                    particle/sparks particle/smoke particle/fountain]]
      (is (map? preset))
      (is (contains? preset :particle/emitter))
      (is (contains? preset :particle/seed)))))

;; --- with-position / with-seed ---

(deftest with-position-test
  (testing "with-position updates emitter position"
    (let [config (particle/with-position particle/fire [500 600])]
      (is (= [500 600] (get-in config [:particle/emitter :emitter/position]))))))

(deftest with-seed-test
  (testing "with-seed updates seed"
    (let [config (particle/with-seed particle/fire 99)]
      (is (= 99 (:particle/seed config))))))

;; --- emitter types ---

(deftest line-emitter-test
  (testing "line emitter spawns particles between two points"
    (let [config {:particle/emitter {:emitter/type :line
                                     :emitter/position [0 100]
                                     :emitter/position-to [400 100]
                                     :emitter/rate 60
                                     :emitter/direction [0 1]
                                     :emitter/speed [10 10]}
                  :particle/lifetime [1.0 1.0]
                  :particle/forces []
                  :particle/seed 42}
          frame (first (particle/simulate config 1 {:fps 30}))]
      (doseq [node frame]
        (let [[x _y] (:circle/center node)]
          (is (<= 0 x 400)))))))

(deftest circle-emitter-test
  (testing "circle emitter spawns particles within radius"
    (let [config {:particle/emitter {:emitter/type :circle
                                     :emitter/position [200 200]
                                     :emitter/radius 50
                                     :emitter/rate 60
                                     :emitter/direction [0 -1]
                                     :emitter/speed [0 0]}
                  :particle/lifetime [1.0 1.0]
                  :particle/forces []
                  :particle/seed 42}
          frame (first (particle/simulate config 1 {:fps 30}))]
      (doseq [node frame]
        (let [[x y] (:circle/center node)
              dx (- x 200) dy (- y 200)
              dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
          (is (<= dist 50.1)))))))

;; --- 3D particles ---

(deftest particle-3d-output-test
  (testing "3D particles produce valid 2D nodes via projection"
    (let [config {:particle/emitter {:emitter/type :point
                                     :emitter/position [0 0 0]
                                     :emitter/burst 5
                                     :emitter/direction [0 1 0]
                                     :emitter/speed [2 5]}
                  :particle/lifetime [1.0 1.0]
                  :particle/forces []
                  :particle/projection (s3d/perspective
                                         {:scale 80 :origin [200 200]
                                          :distance 8})
                  :particle/seed 42}
          frame (first (particle/simulate config 1 {:fps 30}))]
      (is (= 5 (count frame)))
      (doseq [node frame]
        (is (= :shape/circle (:node/type node)))
        (let [[x y] (:circle/center node)]
          (is (number? x))
          (is (number? y)))))))

(deftest particle-3d-determinism-test
  (testing "3D simulation is deterministic"
    (let [config {:particle/emitter {:emitter/type :sphere
                                     :emitter/position [0 0 0]
                                     :emitter/radius 2
                                     :emitter/burst 10
                                     :emitter/direction [0 1 0]
                                     :emitter/speed [1 3]}
                  :particle/lifetime [0.5 1.0]
                  :particle/forces [{:force/type :gravity
                                     :force/acceleration [0 -5 0]}]
                  :particle/projection (s3d/isometric {:scale 50 :origin [200 200]})
                  :particle/seed 77}
          r1 (vec (particle/simulate config 10 {:fps 30}))
          r2 (vec (particle/simulate config 10 {:fps 30}))]
      (is (= r1 r2)))))

(deftest sphere-emitter-test
  (testing "sphere emitter spawns particles within radius"
    (let [config {:particle/emitter {:emitter/type :sphere
                                     :emitter/position [0 0 0]
                                     :emitter/radius 3
                                     :emitter/rate 60
                                     :emitter/direction [0 1 0]
                                     :emitter/speed [0 0]}
                  :particle/lifetime [1.0 1.0]
                  :particle/forces []
                  :particle/projection (s3d/isometric {:scale 50 :origin [200 200]})
                  :particle/seed 42}
          ;; We can't easily check 3D positions from the output (projected to 2D)
          ;; so just verify it produces valid output
          frame (first (particle/simulate config 1 {:fps 30}))]
      (is (pos? (count frame)))
      (doseq [node frame]
        (is (= :shape/circle (:node/type node)))))))
