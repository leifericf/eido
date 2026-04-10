(ns eido.gen.noise-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.noise :as noise])
  (:import
    [java.awt.image BufferedImage]))

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

;; --- preview ---

;; --- simplex2d ---

(deftest simplex2d-range-test
  (testing "simplex2d values are in [-1, 1]"
    (let [values (for [x (range 0 10 0.1)
                       y (range 0 10 0.1)]
                   (noise/simplex2d x y))]
      (is (every? #(<= -1.0 % 1.0) values)))))

(deftest simplex2d-deterministic-test
  (testing "same input produces same output"
    (is (= (noise/simplex2d 1.5 2.3) (noise/simplex2d 1.5 2.3)))))

(deftest simplex2d-variation-test
  (testing "different coordinates produce different values"
    (is (not= (noise/simplex2d 1.0 2.0) (noise/simplex2d 3.0 4.0)))))

(deftest simplex2d-continuity-test
  (testing "nearby points have similar values"
    (let [v1 (noise/simplex2d 1.0 1.0)
          v2 (noise/simplex2d 1.001 1.001)]
      (is (< (Math/abs (- v1 v2)) 0.01)))))

(deftest simplex2d-seeded-test
  (testing "different seeds produce different fields"
    (is (not= (noise/simplex2d 1.5 2.3 {:seed 42})
              (noise/simplex2d 1.5 2.3 {:seed 99}))))
  (testing "same seed is reproducible"
    (is (= (noise/simplex2d 1.5 2.3 {:seed 42})
           (noise/simplex2d 1.5 2.3 {:seed 42})))))

;; --- simplex3d ---

(deftest simplex3d-range-test
  (testing "simplex3d values are in [-1, 1]"
    (let [values (for [x (range 0 5 0.3)
                       y (range 0 5 0.3)
                       z (range 0 5 0.3)]
                   (noise/simplex3d x y z))]
      (is (every? #(<= -1.0 % 1.0) values)))))

(deftest simplex3d-deterministic-test
  (testing "same input produces same output"
    (is (= (noise/simplex3d 1.5 2.3 0.7) (noise/simplex3d 1.5 2.3 0.7)))))

(deftest simplex-fbm-compatibility-test
  (testing "simplex2d works with fbm"
    (let [v (noise/fbm noise/simplex2d 1.5 2.3 {:octaves 4})]
      (is (number? v))
      (is (<= -1.0 v 1.0))))
  (testing "simplex2d works with turbulence"
    (let [v (noise/turbulence noise/simplex2d 1.5 2.3 {:octaves 4})]
      (is (number? v))
      (is (>= v 0.0)))))

;; --- perlin4d ---

(deftest perlin4d-range-test
  (testing "perlin4d values are in [-1, 1]"
    (let [values (for [x (range 0 3 0.5)
                       y (range 0 3 0.5)
                       z (range 0 3 0.5)
                       w (range 0 3 0.5)]
                   (noise/perlin4d x y z w))]
      (is (every? #(<= -1.0 % 1.0) values)))))

(deftest perlin4d-deterministic-test
  (testing "same input produces same output"
    (is (= (noise/perlin4d 1.5 2.3 0.7 0.4)
           (noise/perlin4d 1.5 2.3 0.7 0.4)))))

(deftest perlin4d-seeded-test
  (testing "different seeds produce different values"
    (is (not= (noise/perlin4d 1.5 2.3 0.7 0.4 {:seed 42})
              (noise/perlin4d 1.5 2.3 0.7 0.4 {:seed 99})))))

;; --- simplex4d ---

(deftest simplex4d-range-test
  (testing "simplex4d values are in [-1, 1]"
    (let [values (for [x (range 0 3 0.5)
                       y (range 0 3 0.5)
                       z (range 0 3 0.5)
                       w (range 0 3 0.5)]
                   (noise/simplex4d x y z w))]
      (is (every? #(<= -1.0 % 1.0) values)))))

(deftest simplex4d-deterministic-test
  (testing "same input produces same output"
    (is (= (noise/simplex4d 1.5 2.3 0.7 0.4)
           (noise/simplex4d 1.5 2.3 0.7 0.4)))))

(deftest simplex4d-seeded-test
  (testing "different seeds produce different values"
    (is (not= (noise/simplex4d 1.5 2.3 0.7 0.4 {:seed 42})
              (noise/simplex4d 1.5 2.3 0.7 0.4 {:seed 99})))))

(deftest simplex4d-continuity-test
  (testing "nearby points have similar values"
    (let [v1 (noise/simplex4d 1.0 1.0 1.0 1.0)
          v2 (noise/simplex4d 1.001 1.001 1.001 1.001)]
      (is (< (Math/abs (- v1 v2)) 0.01)))))

(deftest noise-4d-fbm-compatibility-test
  (testing "4D noise works with fbm via 2D wrapper"
    (let [v (noise/fbm (fn [x y _opts] (noise/simplex4d x y 0 0)) 1.5 2.3 {:octaves 4})]
      (is (number? v))
      (is (<= -1.0 v 1.0)))))

;; --- preview ---

(deftest preview-test
  (testing "returns a BufferedImage"
    (let [img (noise/preview noise/perlin2d)]
      (is (instance? BufferedImage img))))
  (testing "default dimensions are 256x256"
    (let [img (noise/preview noise/perlin2d)]
      (is (= 256 (.getWidth ^BufferedImage img)))
      (is (= 256 (.getHeight ^BufferedImage img)))))
  (testing "custom dimensions"
    (let [img (noise/preview noise/perlin2d {:width 100 :height 80})]
      (is (= 100 (.getWidth ^BufferedImage img)))
      (is (= 80 (.getHeight ^BufferedImage img)))))
  (testing "deterministic output"
    (let [img1 (noise/preview noise/perlin2d {:width 32 :height 32 :scale 0.1})
          img2 (noise/preview noise/perlin2d {:width 32 :height 32 :scale 0.1})]
      (is (= (.getRGB ^BufferedImage img1 0 0)
             (.getRGB ^BufferedImage img2 0 0))))))
