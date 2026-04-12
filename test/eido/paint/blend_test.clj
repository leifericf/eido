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
