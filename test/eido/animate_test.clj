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

;; --- cubic easing ---

(deftest ease-in-cubic-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-in-cubic 0.0)))
    (is (== 1.0 (anim/ease-in-cubic 1.0))))
  (testing "slower than linear at start"
    (is (< (anim/ease-in-cubic 0.25) 0.25))))

(deftest ease-out-cubic-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-out-cubic 0.0)))
    (is (== 1.0 (anim/ease-out-cubic 1.0))))
  (testing "faster than linear at start"
    (is (> (anim/ease-out-cubic 0.25) 0.25))))

(deftest ease-in-out-cubic-test
  (testing "boundaries and midpoint"
    (is (== 0.0 (anim/ease-in-out-cubic 0.0)))
    (is (== 1.0 (anim/ease-in-out-cubic 1.0)))
    (is (== 0.5 (anim/ease-in-out-cubic 0.5)))))

;; --- quart easing ---

(deftest ease-in-quart-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-in-quart 0.0)))
    (is (== 1.0 (anim/ease-in-quart 1.0))))
  (testing "even slower than cubic at start"
    (is (< (anim/ease-in-quart 0.25) (anim/ease-in-cubic 0.25)))))

(deftest ease-out-quart-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-out-quart 0.0)))
    (is (== 1.0 (anim/ease-out-quart 1.0)))))

(deftest ease-in-out-quart-test
  (testing "boundaries and midpoint"
    (is (== 0.0 (anim/ease-in-out-quart 0.0)))
    (is (== 1.0 (anim/ease-in-out-quart 1.0)))
    (is (== 0.5 (anim/ease-in-out-quart 0.5)))))

;; --- expo easing ---

(deftest ease-in-expo-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-in-expo 0.0)))
    (is (== 1.0 (anim/ease-in-expo 1.0))))
  (testing "very slow at start"
    (is (< (anim/ease-in-expo 0.1) 0.01))))

(deftest ease-out-expo-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-out-expo 0.0)))
    (is (== 1.0 (anim/ease-out-expo 1.0)))))

(deftest ease-in-out-expo-test
  (testing "boundaries and midpoint"
    (is (== 0.0 (anim/ease-in-out-expo 0.0)))
    (is (== 1.0 (anim/ease-in-out-expo 1.0)))
    (is (== 0.5 (anim/ease-in-out-expo 0.5)))))

;; --- circ easing ---

(deftest ease-in-circ-test
  (testing "boundaries"
    (is (< (Math/abs (- 0.0 (anim/ease-in-circ 0.0))) 1e-9))
    (is (== 1.0 (anim/ease-in-circ 1.0)))))

(deftest ease-out-circ-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-out-circ 0.0)))
    (is (< (Math/abs (- 1.0 (anim/ease-out-circ 1.0))) 1e-9))))

(deftest ease-in-out-circ-test
  (testing "boundaries"
    (is (< (Math/abs (- 0.0 (anim/ease-in-out-circ 0.0))) 1e-9))
    (is (< (Math/abs (- 1.0 (anim/ease-in-out-circ 1.0))) 1e-9))))

;; --- back easing ---

(deftest ease-in-back-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-in-back 0.0)))
    (is (< (Math/abs (- 1.0 (anim/ease-in-back 1.0))) 1e-9)))
  (testing "goes negative (overshoots backward)"
    (is (< (anim/ease-in-back 0.1) 0.0))))

(deftest ease-out-back-test
  (testing "boundaries"
    (is (< (Math/abs (- 0.0 (anim/ease-out-back 0.0))) 1e-9))
    (is (< (Math/abs (- 1.0 (anim/ease-out-back 1.0))) 1e-9)))
  (testing "overshoots past 1.0"
    (is (> (anim/ease-out-back 0.9) 1.0))))

(deftest ease-in-out-back-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-in-out-back 0.0)))
    (is (< (Math/abs (- 1.0 (anim/ease-in-out-back 1.0))) 1e-9))))

;; --- elastic easing ---

(deftest ease-in-elastic-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-in-elastic 0.0)))
    (is (== 1.0 (anim/ease-in-elastic 1.0)))))

(deftest ease-out-elastic-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-out-elastic 0.0)))
    (is (== 1.0 (anim/ease-out-elastic 1.0)))))

(deftest ease-in-out-elastic-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-in-out-elastic 0.0)))
    (is (== 1.0 (anim/ease-in-out-elastic 1.0)))))

;; --- bounce easing ---

(deftest ease-out-bounce-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-out-bounce 0.0)))
    (is (== 1.0 (anim/ease-out-bounce 1.0))))
  (testing "monotonically reaches 1.0 at end"
    (is (> (anim/ease-out-bounce 0.99) 0.95))))

(deftest ease-in-bounce-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-in-bounce 0.0)))
    (is (== 1.0 (anim/ease-in-bounce 1.0)))))

(deftest ease-in-out-bounce-test
  (testing "boundaries"
    (is (== 0.0 (anim/ease-in-out-bounce 0.0)))
    (is (== 1.0 (anim/ease-in-out-bounce 1.0)))
    (is (== 0.5 (anim/ease-in-out-bounce 0.5)))))

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

;; --- frames ---

(deftest frames-test
  (testing "produces correct progress values"
    (is (= [0.0 0.25 0.5 0.75 1.0]
           (anim/frames 5 identity))))

  (testing "single frame returns [0.0]"
    (is (= [0.0]
           (anim/frames 1 identity))))

  (testing "zero frames returns []"
    (is (= []
           (anim/frames 0 identity))))

  (testing "applies f to each progress value"
    (is (= [0.0 50.0 100.0]
           (anim/frames 3 (fn [t] (anim/lerp 0 100 t))))))

  (testing "builds scene maps"
    (let [scenes (anim/frames 3 (fn [t] {:radius (anim/lerp 10 50 t)}))]
      (is (= 3 (count scenes)))
      (is (= {:radius 10.0} (first scenes)))
      (is (= {:radius 50.0} (last scenes))))))

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

;; --- convenience helper tests ---

(deftest pulse-test
  (testing "pulse at t=0 is 0.5"
    (is (< (Math/abs (- (anim/pulse 0.0) 0.5)) 0.01)))
  (testing "pulse at t=0.25 is 1.0"
    (is (< (Math/abs (- (anim/pulse 0.25) 1.0)) 0.01)))
  (testing "pulse with frequency 2 oscillates twice as fast"
    (is (< (Math/abs (- (anim/pulse 0.125 2.0) 1.0)) 0.01))))

(deftest fade-linear-test
  (testing "1.0 at t=0, 0.0 at t=1"
    (is (== 1.0 (anim/fade-linear 0.0)))
    (is (== 0.0 (anim/fade-linear 1.0)))
    (is (== 0.5 (anim/fade-linear 0.5)))))

(deftest fade-out-test
  (testing "starts at 1.0 and ends at 0.0"
    (is (== 1.0 (anim/fade-out 0.0)))
    (is (== 0.0 (anim/fade-out 1.0))))
  (testing "quadratic: midpoint is 0.25 not 0.5"
    (is (== 0.25 (anim/fade-out 0.5)))))

(deftest fade-in-test
  (testing "starts at 0.0 and ends at 1.0"
    (is (== 0.0 (anim/fade-in 0.0)))
    (is (== 1.0 (anim/fade-in 1.0))))
  (testing "quadratic: midpoint is 0.25"
    (is (== 0.25 (anim/fade-in 0.5)))))
