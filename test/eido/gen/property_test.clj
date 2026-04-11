(ns eido.gen.property-test
  "Property-based tests for generative algorithms.
  Uses test.check to verify invariants over many random inputs."
  (:require
    [clojure.test :refer [deftest is]]
    [clojure.test.check.clojure-test :refer [defspec]]
    [clojure.test.check.generators :as tc-gen]
    [clojure.test.check.properties :as prop]
    [eido.gen :as gen]))

;; --- generators ---

(def gen-seed
  "Integer seed in a reasonable range."
  (tc-gen/choose 0 10000))

(def gen-coord
  "Coordinate value for noise input."
  (tc-gen/double* {:min -500.0 :max 500.0 :NaN? false :infinite? false}))

(def gen-bounds
  "Valid [x y w h] bounds with positive w and h."
  (tc-gen/let [x (tc-gen/double* {:min -100.0 :max 100.0 :NaN? false :infinite? false})
               y (tc-gen/double* {:min -100.0 :max 100.0 :NaN? false :infinite? false})
               w (tc-gen/double* {:min 10.0 :max 300.0 :NaN? false :infinite? false})
               h (tc-gen/double* {:min 10.0 :max 300.0 :NaN? false :infinite? false})]
    [x y w h]))

(def gen-min-dist
  "Minimum distance for Poisson disk (must be positive)."
  (tc-gen/double* {:min 5.0 :max 30.0 :NaN? false :infinite? false}))

(def gen-points
  "3-20 random [x y] points within [0 0 100 100]."
  (tc-gen/let [n (tc-gen/choose 3 20)]
    (tc-gen/vector
      (tc-gen/let [x (tc-gen/double* {:min 1.0 :max 99.0 :NaN? false :infinite? false})
                   y (tc-gen/double* {:min 1.0 :max 99.0 :NaN? false :infinite? false})]
        [x y])
      n)))

;; --- noise properties ---

(defspec perlin2d-output-in-range 100
  (prop/for-all [x gen-coord
                 y gen-coord
                 seed gen-seed]
    (let [v (gen/perlin2d x y {:seed seed})]
      (and (>= v -1.0) (<= v 1.0)))))

(defspec simplex2d-output-in-range 100
  (prop/for-all [x gen-coord
                 y gen-coord
                 seed gen-seed]
    (let [v (gen/simplex2d x y {:seed seed})]
      (and (>= v -1.0) (<= v 1.0)))))

(defspec turbulence-non-negative 100
  (prop/for-all [x gen-coord
                 y gen-coord
                 seed gen-seed]
    (let [v (gen/turbulence gen/perlin2d x y {:seed seed})]
      (>= v 0.0))))

;; --- circle packing properties ---

(defspec circle-pack-within-bounds 50
  (prop/for-all [bounds gen-bounds
                 seed   gen-seed]
    (let [[bx by bw bh] bounds
          circles (gen/circle-pack bounds {:seed seed :max-circles 50})]
      (every? (fn [{:keys [center radius]}]
                (let [[cx cy] center]
                  (and (>= (- cx radius) (- bx 0.01))
                       (<= (+ cx radius) (+ bx bw 0.01))
                       (>= (- cy radius) (- by 0.01))
                       (<= (+ cy radius) (+ by bh 0.01)))))
              circles))))

(defspec circle-pack-no-overlap 50
  (prop/for-all [bounds gen-bounds
                 seed   gen-seed]
    (let [padding  1.0
          circles  (gen/circle-pack bounds {:seed seed :padding padding :max-circles 50})
          n        (count circles)]
      (every? true?
        (for [i (range n)
              j (range (inc i) n)
              :let [{[x1 y1] :center r1 :radius} (nth circles i)
                    {[x2 y2] :center r2 :radius} (nth circles j)
                    dx (- x1 x2) dy (- y1 y2)
                    dist (Math/sqrt (+ (* dx dx) (* dy dy)))]]
          (>= dist (- (+ r1 r2 padding) 0.01)))))))

;; --- Poisson disk properties ---

(defspec poisson-disk-respects-min-distance 50
  (prop/for-all [bounds   gen-bounds
                 min-dist gen-min-dist
                 seed     gen-seed]
    (let [pts (gen/poisson-disk bounds {:min-dist min-dist :seed seed})
          n   (count pts)]
      (every? true?
        (for [i (range n)
              j (range (inc i) n)
              :let [[x1 y1] (nth pts i)
                    [x2 y2] (nth pts j)
                    dx (- x1 x2) dy (- y1 y2)
                    dist (Math/sqrt (+ (* dx dx) (* dy dy)))]]
          (>= dist (- min-dist 0.01)))))))

(defspec poisson-disk-within-bounds 50
  (prop/for-all [bounds   gen-bounds
                 min-dist gen-min-dist
                 seed     gen-seed]
    (let [[bx by bw bh] bounds
          pts (gen/poisson-disk bounds {:min-dist min-dist :seed seed})]
      (every? (fn [[x y]]
                (and (>= x (- bx 0.01)) (<= x (+ bx bw 0.01))
                     (>= y (- by 0.01)) (<= y (+ by bh 0.01))))
              pts))))

;; --- Voronoi properties ---

(defspec voronoi-cell-count-matches-points 50
  (prop/for-all [pts gen-points]
    (let [cells (gen/voronoi-cells pts [0 0 100 100])]
      (= (count pts) (count cells)))))

;; --- probability distribution properties ---

(defspec uniform-values-in-range 100
  (prop/for-all [n    (tc-gen/choose 1 100)
                 lo   (tc-gen/double* {:min -100.0 :max 0.0 :NaN? false :infinite? false})
                 span (tc-gen/double* {:min 0.1 :max 200.0 :NaN? false :infinite? false})
                 seed gen-seed]
    (let [hi  (+ lo span)
          vs  (gen/uniform n lo hi seed)]
      (and (= n (count vs))
           (every? #(and (>= % lo) (< % hi)) vs)))))

(defspec shuffle-preserves-elements 100
  (prop/for-all [coll (tc-gen/not-empty (tc-gen/vector tc-gen/small-integer))
                 seed gen-seed]
    (let [shuffled (gen/shuffle-seeded coll seed)]
      (= (sort coll) (sort shuffled)))))
