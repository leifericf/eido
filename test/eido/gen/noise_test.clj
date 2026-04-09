(ns eido.gen.noise-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.noise :as noise]))

;; --- perlin2d ---

(deftest perlin2d-range-test
  (testing "perlin2d values are in [-1, 1]"
    (let [values (for [x (range 0 10 0.1)
                       y (range 0 10 0.1)]
                   (noise/perlin2d x y))]
      (is (every? #(<= -1.0 % 1.0) values)))))

(deftest perlin2d-deterministic-test
  (testing "perlin2d returns same value for same input"
    (is (= (noise/perlin2d 1.5 2.3)
           (noise/perlin2d 1.5 2.3)))))

(deftest perlin2d-varies-test
  (testing "perlin2d produces different values at different points"
    (let [v1 (noise/perlin2d 0.0 0.0)
          v2 (noise/perlin2d 1.5 2.3)
          v3 (noise/perlin2d 5.7 8.1)]
      (is (not= v1 v2))
      (is (not= v2 v3)))))

(deftest perlin2d-continuity-test
  (testing "nearby points produce similar values (smooth)"
    (let [v1 (noise/perlin2d 1.0 1.0)
          v2 (noise/perlin2d 1.01 1.0)]
      (is (< (Math/abs (- v1 v2)) 0.1)))))

(deftest perlin2d-integer-grid-test
  (testing "perlin2d at integer coordinates returns 0 (gradient dot product)"
    (is (= 0.0 (noise/perlin2d 0 0)))
    (is (= 0.0 (noise/perlin2d 1 0)))
    (is (= 0.0 (noise/perlin2d 0 1)))))

;; --- seeded noise ---

(deftest perlin2d-seeded-test
  (testing "different seeds produce different noise fields"
    (let [v1 (noise/perlin2d 1.5 2.3 {:seed 0})
          v2 (noise/perlin2d 1.5 2.3 {:seed 42})]
      (is (not= v1 v2))))
  (testing "same seed produces same result"
    (is (= (noise/perlin2d 1.5 2.3 {:seed 42})
           (noise/perlin2d 1.5 2.3 {:seed 42})))))

;; --- perlin3d ---

(deftest perlin3d-range-test
  (testing "perlin3d values are in [-1, 1]"
    (let [values (for [x (range 0 5 0.5)
                       y (range 0 5 0.5)
                       z (range 0 5 0.5)]
                   (noise/perlin3d x y z))]
      (is (every? #(<= -1.0 % 1.0) values)))))

(deftest perlin3d-seeded-test
  (testing "3D noise supports seeds"
    (is (not= (noise/perlin3d 1.5 2.3 0.7 {:seed 0})
              (noise/perlin3d 1.5 2.3 0.7 {:seed 42})))))

;; --- fbm ---

(deftest fbm-test
  (testing "fbm produces values with more detail than raw noise"
    (let [raw  (noise/perlin2d 1.5 2.3)
          fbm1 (noise/fbm noise/perlin2d 1.5 2.3 {:octaves 4})]
      (is (number? fbm1))
      (is (not= raw fbm1)))))

(deftest fbm-defaults-test
  (testing "fbm works with default options"
    (is (number? (noise/fbm noise/perlin2d 1.5 2.3)))))

(deftest fbm-octaves-test
  (testing "more octaves add detail (different result)"
    (let [v1 (noise/fbm noise/perlin2d 1.5 2.3 {:octaves 1})
          v4 (noise/fbm noise/perlin2d 1.5 2.3 {:octaves 4})]
      (is (not= v1 v4)))))

;; --- turbulence ---

(deftest turbulence-non-negative-test
  (testing "turbulence produces non-negative values"
    (let [values (for [x (range 0 10 0.3)
                       y (range 0 10 0.3)]
                   (noise/turbulence noise/perlin2d x y {:octaves 4}))]
      (is (every? #(>= % 0.0) values)))))

;; --- ridge ---

(deftest ridge-test
  (testing "ridge noise produces values"
    (let [v (noise/ridge noise/perlin2d 1.5 2.3 {:octaves 4})]
      (is (number? v)))))
