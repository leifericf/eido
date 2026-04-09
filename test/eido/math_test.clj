(ns eido.math-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.math :as m]))

(def ^:private eps 1e-9)

(defn- approx=
  "Returns true if all elements of two vectors are within eps."
  [a b]
  (and (= (count a) (count b))
       (every? #(< (abs %) eps) (map - a b))))

;; --- vector operations ---

(deftest v+-test
  (is (= [4.0 6.0 8.0] (m/v+ [1 2 3] [3 4 5])))
  (is (= [0.0 0.0 0.0] (m/v+ [1 -1 0] [-1 1 0]))))

(deftest v--test
  (is (= [-2.0 -2.0 -2.0] (m/v- [1 2 3] [3 4 5])))
  (is (= [0.0 0.0 0.0] (m/v- [5 5 5] [5 5 5]))))

(deftest v*-test
  (is (= [2.0 4.0 6.0] (m/v* [1 2 3] 2)))
  (is (= [0.0 0.0 0.0] (m/v* [1 2 3] 0))))

(deftest dot-test
  (is (== 32.0 (m/dot [1 2 3] [4 5 6])))
  (testing "perpendicular vectors"
    (is (== 0.0 (m/dot [1 0 0] [0 1 0])))))

(deftest cross-test
  (is (= [0.0 0.0 1.0] (m/cross [1 0 0] [0 1 0])))
  (is (= [0.0 0.0 -1.0] (m/cross [0 1 0] [1 0 0])))
  (testing "parallel vectors have zero cross product"
    (is (= [0.0 0.0 0.0] (m/cross [1 0 0] [2 0 0])))))

(deftest magnitude-test
  (is (== 1.0 (m/magnitude [1 0 0])))
  (is (== 5.0 (m/magnitude [3 4 0])))
  (is (== 0.0 (m/magnitude [0 0 0]))))

(deftest normalize-test
  (is (approx= [1.0 0.0 0.0] (m/normalize [5 0 0])))
  (is (approx= [0.0 1.0 0.0] (m/normalize [0 3 0])))
  (let [n (m/normalize [1 1 1])
        expected (/ 1.0 (Math/sqrt 3))]
    (is (approx= [expected expected expected] n))))

;; --- rotations ---

(deftest rotate-test
  (testing "X: 90 degrees rotates Y to Z"
    (let [result (m/rotate [0 1 0] :x (/ Math/PI 2))]
      (is (approx= [0.0 0.0 1.0] result))))
  (testing "X: identity rotation"
    (is (approx= [1.0 2.0 3.0] (m/rotate [1 2 3] :x 0))))
  (testing "Y: 90 degrees rotates Z to X"
    (let [result (m/rotate [0 0 1] :y (/ Math/PI 2))]
      (is (approx= [1.0 0.0 0.0] result))))
  (testing "Y: identity rotation"
    (is (approx= [1.0 2.0 3.0] (m/rotate [1 2 3] :y 0))))
  (testing "Z: 90 degrees rotates X to Y"
    (let [result (m/rotate [1 0 0] :z (/ Math/PI 2))]
      (is (approx= [0.0 1.0 0.0] result))))
  (testing "Z: identity rotation"
    (is (approx= [1.0 2.0 3.0] (m/rotate [1 2 3] :z 0)))))

;; --- projections ---

(deftest isometric-project-test
  (let [proj {:projection/type :isometric
              :projection/scale 1.0
              :projection/origin [0.0 0.0]}]
    (testing "origin maps to origin"
      (is (approx= [0.0 0.0] (m/project proj [0 0 0]))))
    (testing "X axis goes right and slightly down"
      (let [[sx sy] (m/project proj [1 0 0])
            cos30 (/ (Math/sqrt 3) 2.0)]
        (is (< (abs (- sx cos30)) eps))
        (is (< (abs (- sy 0.5)) eps))))
    (testing "Y axis goes straight up (negative screen Y)"
      (let [[sx sy] (m/project proj [0 1 0])]
        (is (< (abs sx) eps))
        (is (< (abs (- sy -1.0)) eps))))))

(deftest orthographic-project-test
  (let [proj {:projection/type :orthographic
              :projection/scale 1.0
              :projection/origin [0.0 0.0]
              :projection/yaw 0.0
              :projection/pitch 0.0}]
    (testing "looking straight down Z axis, X goes right, Y goes up"
      (is (approx= [1.0 0.0] (m/project proj [1 0 0])))
      (is (approx= [0.0 -1.0] (m/project proj [0 1 0]))))))

(deftest perspective-project-test
  (let [proj {:projection/type :perspective
              :projection/scale 1.0
              :projection/origin [0.0 0.0]
              :projection/yaw 0.0
              :projection/pitch 0.0
              :projection/distance 5.0}]
    (testing "at z=0, perspective matches orthographic"
      (is (approx= [1.0 0.0] (m/project proj [1 0 0]))))
    (testing "farther objects appear smaller (positive z = farther)"
      (let [[sx1 _] (m/project proj [1 0 1])
            [sx2 _] (m/project proj [1 0 3])]
        (is (> sx1 0))
        (is (> sx2 0))
        (is (< sx2 sx1))))))

(deftest view-transform-test
  (let [proj {:projection/type :orthographic
              :projection/scale 1.0
              :projection/origin [0.0 0.0]
              :projection/yaw 0.0
              :projection/pitch 0.0}]
    (testing "no rotation passes through"
      (is (approx= [1.0 2.0 3.0] (m/view-transform proj [1 2 3]))))))

(deftest view-transform-roll-test
  (testing "roll=0 is identity (no change from before)"
    (let [proj {:projection/type :orthographic
                :projection/yaw 0.5
                :projection/pitch -0.3
                :projection/roll 0.0}]
      (is (approx= (m/view-transform (dissoc proj :projection/roll) [1 2 3])
                    (m/view-transform proj [1 2 3])))))
  (testing "90-degree roll rotates X to Y in view space"
    (let [proj {:projection/type :orthographic
                :projection/yaw 0.0
                :projection/pitch 0.0
                :projection/roll (/ Math/PI 2)}]
      ;; With yaw=0 pitch=0, view space = world space before roll.
      ;; rotate-z(-pi/2) on [1 0 0] -> [0 -1 0]
      (is (approx= [0.0 -1.0 0.0] (m/view-transform proj [1 0 0])))))
  (testing "roll does not affect Z axis"
    (let [proj {:projection/type :orthographic
                :projection/yaw 0.0
                :projection/pitch 0.0
                :projection/roll 1.0}]
      (is (approx= [0.0 0.0 1.0] (m/view-transform proj [0 0 1]))))))

(deftest camera-direction-test
  (testing "isometric camera direction is [1 1 1] normalized"
    (let [dir (m/camera-direction {:projection/type :isometric})
          expected (/ 1.0 (Math/sqrt 3))]
      (is (approx= [expected expected expected] dir))))
  (testing "orthographic default looks along -Z, so camera is at +Z"
    (let [dir (m/camera-direction {:projection/type :orthographic
                                   :projection/yaw 0.0
                                   :projection/pitch 0.0})]
      (is (> (nth dir 2) 0.9)))))

(deftest face-normal-test
  (testing "XY plane triangle has Z normal"
    (let [n (m/face-normal [[0 0 0] [1 0 0] [0 1 0]])]
      (is (approx= [0.0 0.0 1.0] (m/normalize n)))))
  (testing "reversed winding flips normal"
    (let [n (m/face-normal [[0 0 0] [0 1 0] [1 0 0]])]
      (is (approx= [0.0 0.0 -1.0] (m/normalize n))))))

(deftest face-tangent-bitangent-test
  (testing "XZ plane triangle with standard UVs"
    (let [[t b] (m/face-tangent-bitangent
                  [[0 0 0] [1 0 0] [0 0 1]]
                  [[0 0] [1 0] [0 1]])]
      (is (approx= [1.0 0.0 0.0] t))
      (is (approx= [0.0 0.0 1.0] b))))
  (testing "degenerate UV returns nil"
    (is (nil? (m/face-tangent-bitangent
                [[0 0 0] [1 0 0] [0 1 0]]
                [[0 0] [0 0] [0 0]])))))

(deftest face-centroid-test
  (is (approx= [1.0 1.0 1.0]
        (m/face-centroid [[0 0 0] [2 2 2]])))
  (is (approx= [1.0 1.0 0.0]
        (m/face-centroid [[0 0 0] [1 1 0] [2 2 0]]))))

;; --- lerp ---

(deftest lerp-test
  (testing "t=0 returns a"
    (is (approx= [1.0 2.0 3.0] (m/lerp [1 2 3] [4 5 6] 0.0))))
  (testing "t=1 returns b"
    (is (approx= [4.0 5.0 6.0] (m/lerp [1 2 3] [4 5 6] 1.0))))
  (testing "t=0.5 returns midpoint"
    (is (approx= [2.5 3.5 4.5] (m/lerp [1 2 3] [4 5 6] 0.5))))
  (testing "t=0.25"
    (is (approx= [1.75 2.75 3.75] (m/lerp [1 2 3] [4 5 6] 0.25)))))
