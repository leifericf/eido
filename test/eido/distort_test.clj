(ns eido.distort-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.distort :as distort]))

;; --- noise distortion ---

(deftest noise-distort-test
  (testing "noise distortion modifies path points"
    (let [cmds [[:move-to [3.5 7.2]] [:line-to [50.3 12.8]] [:line-to [80.1 95.4]]]
          result (distort/distort-commands cmds {:type :noise :amplitude 5 :frequency 0.1 :seed 42})]
      (is (vector? result))
      (is (= (count cmds) (count result)))
      ;; Points should be different from originals
      (is (not= cmds result))))
  (testing "amplitude 0 leaves points unchanged"
    (let [cmds [[:move-to [50.0 50.0]] [:line-to [100.0 50.0]]]
          result (distort/distort-commands cmds {:type :noise :amplitude 0 :frequency 0.1})]
      (is (= cmds result)))))

;; --- wave distortion ---

(deftest wave-distort-test
  (testing "wave distortion displaces points sinusoidally"
    (let [cmds [[:move-to [0.0 50.0]] [:line-to [50.0 50.0]] [:line-to [100.0 50.0]]]
          result (distort/distort-commands cmds {:type :wave :axis :y :amplitude 10 :wavelength 50})]
      (is (= 3 (count result)))
      ;; Y coords should change
      (let [ys (map (fn [[_ [_ y]]] y) result)]
        (is (not (apply = ys))))))
  (testing "wave on x-axis displaces x coords"
    (let [cmds [[:move-to [50.0 0.0]] [:line-to [50.0 50.0]] [:line-to [50.0 100.0]]]
          result (distort/distort-commands cmds {:type :wave :axis :x :amplitude 10 :wavelength 50})]
      (let [xs (map (fn [[_ [x _]]] x) result)]
        (is (not (apply = xs)))))))

;; --- roughen distortion ---

(deftest roughen-distort-test
  (testing "roughen adds random displacement"
    (let [cmds [[:move-to [0.0 0.0]] [:line-to [50.0 0.0]] [:line-to [100.0 0.0]]]
          result (distort/distort-commands cmds {:type :roughen :amount 5 :seed 42})]
      (is (= 3 (count result)))
      (is (not= cmds result))))
  (testing "deterministic with same seed"
    (let [cmds [[:move-to [0.0 0.0]] [:line-to [50.0 0.0]]]
          r1 (distort/distort-commands cmds {:type :roughen :amount 5 :seed 42})
          r2 (distort/distort-commands cmds {:type :roughen :amount 5 :seed 42})]
      (is (= r1 r2)))))

;; --- jitter distortion ---

(deftest jitter-distort-test
  (testing "jitter displaces points randomly"
    (let [cmds [[:move-to [0.0 0.0]] [:line-to [50.0 50.0]]]
          result (distort/distort-commands cmds {:type :jitter :amount 3 :seed 7})]
      (is (= 2 (count result)))
      (is (not= cmds result)))))

;; --- close commands preserved ---

(deftest close-preserved-test
  (testing "close commands pass through unchanged"
    (let [cmds [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]] [:line-to [50.0 50.0]] [:close]]
          result (distort/distort-commands cmds {:type :noise :amplitude 5 :frequency 0.1 :seed 1})]
      (is (= :close (first (last result)))))))
