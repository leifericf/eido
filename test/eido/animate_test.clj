(ns eido.animate-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.animate :as anim]))

;; --- progress ---

(deftest progress-test
  (testing "first frame is 0.0"
    (is (== 0.0 (anim/progress 0 60))))

  (testing "last frame is 1.0"
    (is (== 1.0 (anim/progress 59 60))))

  (testing "midpoint"
    (is (== 0.5 (anim/progress 30 61))))

  (testing "single frame returns 0.0"
    (is (== 0.0 (anim/progress 0 1)))))

;; --- ping-pong ---

(deftest ping-pong-test
  (testing "starts at 0"
    (is (== 0.0 (anim/ping-pong 0.0))))

  (testing "peaks at midpoint"
    (is (== 1.0 (anim/ping-pong 0.5))))

  (testing "returns to 0"
    (is (== 0.0 (anim/ping-pong 1.0))))

  (testing "symmetric"
    (is (== (anim/ping-pong 0.25)
            (anim/ping-pong 0.75))))

  (testing "quarter is 0.5"
    (is (== 0.5 (anim/ping-pong 0.25)))))

;; --- cycle-n ---

(deftest cycle-n-test
  (testing "single cycle matches input"
    (is (== 0.5 (anim/cycle-n 1 0.5))))

  (testing "two cycles at midpoint wraps to 0"
    (is (< (Math/abs (- 0.0 (anim/cycle-n 2 0.5))) 1e-9)))

  (testing "two cycles at quarter is 0.5"
    (is (== 0.5 (anim/cycle-n 2 0.25))))

  (testing "start is always 0"
    (is (== 0.0 (anim/cycle-n 3 0.0)))))

;; --- lerp ---

(deftest lerp-test
  (testing "t=0 returns a"
    (is (== 10.0 (anim/lerp 10 20 0.0))))

  (testing "t=1 returns b"
    (is (== 20.0 (anim/lerp 10 20 1.0))))

  (testing "t=0.5 returns midpoint"
    (is (== 15.0 (anim/lerp 10 20 0.5))))

  (testing "works with negative values"
    (is (== 0.0 (anim/lerp -10 10 0.5)))))

;; --- easing ---

(deftest ease-in-test
  (testing "boundary: f(0) = 0"
    (is (== 0.0 (anim/ease-in 0.0))))

  (testing "boundary: f(1) = 1"
    (is (== 1.0 (anim/ease-in 1.0))))

  (testing "slower at start than linear"
    (is (< (anim/ease-in 0.25) 0.25))))

(deftest ease-out-test
  (testing "boundary: f(0) = 0"
    (is (== 0.0 (anim/ease-out 0.0))))

  (testing "boundary: f(1) = 1"
    (is (== 1.0 (anim/ease-out 1.0))))

  (testing "faster at start than linear"
    (is (> (anim/ease-out 0.25) 0.25))))

(deftest ease-in-out-test
  (testing "boundary: f(0) = 0"
    (is (== 0.0 (anim/ease-in-out 0.0))))

  (testing "boundary: f(1) = 1"
    (is (== 1.0 (anim/ease-in-out 1.0))))

  (testing "midpoint: f(0.5) = 0.5"
    (is (== 0.5 (anim/ease-in-out 0.5))))

  (testing "slow at start"
    (is (< (anim/ease-in-out 0.25) 0.25)))

  (testing "fast at end"
    (is (> (anim/ease-in-out 0.75) 0.75))))

;; --- stagger ---

(deftest stagger-test
  (testing "no overlap — elements are sequential"
    (is (== 0.0 (anim/stagger 0 3 0.0 0.0))
        "first element starts at 0")
    (is (== 1.0 (anim/stagger 0 3 (/ 1.0 3) 0.0))
        "first element done at 1/3")
    (is (== 0.0 (anim/stagger 2 3 0.0 0.0))
        "last element hasn't started at 0"))

  (testing "full overlap — all elements simultaneous"
    (is (== 0.5 (anim/stagger 0 3 0.5 1.0)))
    (is (== 0.5 (anim/stagger 1 3 0.5 1.0)))
    (is (== 0.5 (anim/stagger 2 3 0.5 1.0))))

  (testing "clamped to [0, 1]"
    (is (== 0.0 (anim/stagger 2 3 0.1 0.0)))
    (is (== 1.0 (anim/stagger 0 3 0.5 0.0)))))

;; --- composition ---

(deftest composition-test
  (testing "easing composed with progress"
    (let [t (anim/ease-in (anim/progress 0 60))]
      (is (== 0.0 t)))
    (let [t (anim/ease-in (anim/progress 59 60))]
      (is (== 1.0 t))))

  (testing "lerp with eased progress"
    (let [t (anim/lerp 100 200 (anim/ease-in-out 0.5))]
      (is (== 150.0 t))))

  (testing "ping-pong with lerp"
    (let [t (anim/lerp 0 100 (anim/ping-pong 0.5))]
      (is (== 100.0 t)))
    (let [t (anim/lerp 0 100 (anim/ping-pong 1.0))]
      (is (== 0.0 t)))))
