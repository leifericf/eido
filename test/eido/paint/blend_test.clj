(ns eido.paint.blend-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.paint.blend :as blend]))

(deftest source-over-test
  (testing "opaque source replaces destination"
    (let [[r g b a] (blend/blend :source-over
                      [1.0 0.0 0.0 1.0]    ;; opaque red
                      [0.0 1.0 0.0 1.0])]  ;; opaque green
      (is (< (Math/abs (- r 1.0)) 0.001))
      (is (< (Math/abs (- a 1.0)) 0.001))))

  (testing "transparent source preserves destination"
    (let [[r g b a] (blend/blend :source-over
                      [0.0 0.0 0.0 0.0]    ;; fully transparent
                      [0.0 0.5 0.0 0.5])]  ;; semi-green
      (is (< (Math/abs (- g 0.5)) 0.001))
      (is (< (Math/abs (- a 0.5)) 0.001)))))

(deftest erase-test
  (testing "erase reduces destination alpha"
    (let [[_r _g _b a] (blend/blend :erase
                         [0.0 0.0 0.0 0.5]  ;; erase with 50%
                         [0.5 0.0 0.0 1.0])] ;; opaque red
      (is (< a 1.0) "alpha should decrease")
      (is (>= a 0.0)))))

(deftest multiply-test
  (testing "multiply darkens fully opaque sources"
    (let [[r _g _b _a] (blend/blend :multiply
                         [0.8 0.0 0.0 1.0]
                         [0.5 0.0 0.0 1.0])]
      (is (< r 0.5) "multiply should produce darker result"))))

(deftest glazed-test
  (testing "glazed uses max of source and destination per channel"
    (let [[r g b a] (blend/blend :glazed
                      [0.3 0.0 0.0 0.3]   ;; dim red
                      [0.0 0.5 0.0 0.5])] ;; brighter green
      (is (< (Math/abs (- r 0.3)) 0.001) "red from source")
      (is (< (Math/abs (- g 0.5)) 0.001) "green from destination")
      (is (< (Math/abs (- a 0.5)) 0.001) "alpha is max")))

  (testing "glazed never exceeds max of inputs"
    (let [[r g b a] (blend/blend :glazed
                      [0.4 0.3 0.2 0.5]
                      [0.2 0.5 0.1 0.3])]
      (is (<= r (max 0.4 0.2)))
      (is (<= g (max 0.3 0.5)))
      (is (<= b (max 0.2 0.1)))
      (is (<= a (max 0.5 0.3))))))

(deftest opaque-test
  (testing "opaque source replaces destination"
    (let [[r _g _b a] (blend/blend :opaque
                        [0.8 0.0 0.0 1.0]   ;; fully opaque red
                        [0.0 0.5 0.0 0.5])] ;; semi green
      (is (< (Math/abs (- r 0.8)) 0.001) "opaque source replaces")
      (is (< (Math/abs (- a 1.0)) 0.001))))

  (testing "semi-opaque blends with stronger weight"
    (let [[r _g _b _a] (blend/blend :opaque
                         [0.3 0.0 0.0 0.5]
                         [0.0 0.5 0.0 0.5])]
      ;; Result should lean toward source due to 1.5x weight
      (is (> r 0.0) "source red contributes"))))
