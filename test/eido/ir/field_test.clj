(ns eido.ir.field-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.ir.field :as field]
    [eido.noise :as noise]))

(deftest noise-field-constructor-test
  (let [f (field/noise-field :scale 0.02 :variant :fbm :seed 42 :octaves 6)]
    (is (= :field/noise (:field/type f)))
    (is (= 0.02 (:field/scale f)))
    (is (= :fbm (:field/variant f)))
    (is (= 42 (:field/seed f)))
    (is (= 6 (:field/octaves f)))))

(deftest constant-field-test
  (let [f (field/constant-field 0.5)]
    (is (= :field/constant (:field/type f)))
    (is (= 0.5 (field/evaluate f 0.0 0.0)))
    (is (= 0.5 (field/evaluate f 100.0 200.0)))))

(deftest distance-field-test
  (let [f (field/distance-field [0.0 0.0])]
    (is (= 0.0 (field/evaluate f 0.0 0.0)))
    (is (< (Math/abs (- 5.0 (field/evaluate f 3.0 4.0))) 0.001))
    (is (< (Math/abs (- 10.0 (field/evaluate f 6.0 8.0))) 0.001))))

(deftest noise-field-raw-test
  (testing "raw perlin noise evaluation"
    (let [f (field/noise-field :scale 1.0 :variant :raw :seed 42)]
      (is (double? (field/evaluate f 1.5 2.3)))
      (is (<= -1.0 (field/evaluate f 1.5 2.3) 1.0)))))

(deftest noise-field-fbm-test
  (testing "fbm noise evaluation matches direct call"
    (let [f (field/noise-field :scale 0.1 :variant :fbm :seed 42 :octaves 4)
          direct (noise/fbm noise/perlin2d 1.5 2.3
                   {:seed 42 :octaves 4})]
      ;; Field scales the input
      (is (double? (field/evaluate f 15.0 23.0)))
      ;; At scaled coords, should match direct fbm call
      (is (< (Math/abs (- direct (field/evaluate f 15.0 23.0))) 0.001)))))

(deftest noise-field-deterministic-test
  (testing "same seed produces same values"
    (let [f (field/noise-field :scale 0.05 :seed 123)]
      (is (= (field/evaluate f 10.0 20.0)
             (field/evaluate f 10.0 20.0))))))

(deftest noise-field-variants-test
  (testing "all noise variants evaluate without error"
    (doseq [variant [:raw :fbm :turbulence :ridge]]
      (let [f (field/noise-field :scale 0.1 :variant variant :seed 42)]
        (is (double? (field/evaluate f 5.0 5.0))
            (str "variant " variant " should return double"))))))

;; --- 3D evaluation ---

(deftest evaluate-3d-test
  (testing "noise field evaluates at 3D coordinates"
    (let [f (field/noise-field :scale 1.0 :variant :raw :seed 42)]
      (is (double? (field/evaluate-3d f 1.5 2.3 0.7)))
      (is (<= -1.0 (field/evaluate-3d f 1.5 2.3 0.7) 1.0))))
  (testing "constant field ignores z"
    (let [f (field/constant-field 0.5)]
      (is (= 0.5 (field/evaluate-3d f 10.0 20.0 30.0)))))
  (testing "distance field uses x and y only"
    (let [f (field/distance-field [0.0 0.0])]
      (is (< (Math/abs (- 5.0 (field/evaluate-3d f 3.0 4.0 99.0))) 0.001))))
  (testing "all noise variants work in 3D"
    (doseq [variant [:raw :fbm :turbulence :ridge]]
      (let [f (field/noise-field :scale 0.1 :variant variant :seed 42)]
        (is (double? (field/evaluate-3d f 5.0 5.0 5.0))
            (str "3D variant " variant " should return double")))))
  (testing "3D evaluation is deterministic"
    (let [f (field/noise-field :scale 0.05 :seed 123)]
      (is (= (field/evaluate-3d f 10.0 20.0 30.0)
             (field/evaluate-3d f 10.0 20.0 30.0))))))
