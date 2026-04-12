(ns eido.paint.tip-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.paint.tip :as tip]))

(deftest hardness-falloff-test
  (testing "center is full coverage"
    (is (= 1.0 (tip/hardness-falloff 0.0 0.7))))
  (testing "beyond edge is zero"
    (is (= 0.0 (tip/hardness-falloff 1.0 0.7)))
    (is (= 0.0 (tip/hardness-falloff 1.5 0.7))))
  (testing "hard brush: full coverage up to hardness threshold"
    (is (= 1.0 (tip/hardness-falloff 0.5 0.7))))
  (testing "soft brush: gradual falloff"
    (let [v (tip/hardness-falloff 0.5 0.0)]
      (is (> v 0.0))
      (is (< v 1.0)))))

(deftest circle-tip-test
  (testing "center of circle is full"
    (is (= 1.0 (tip/evaluate-tip {:tip/shape :circle :tip/hardness 0.7}
                                  0.0 0.0 0.0))))
  (testing "outside circle is zero"
    (is (= 0.0 (tip/evaluate-tip {:tip/shape :circle :tip/hardness 0.7}
                                  1.5 0.0 0.0))))
  (testing "edge has some coverage"
    (let [v (tip/evaluate-tip {:tip/shape :circle :tip/hardness 0.5}
                              0.8 0.0 0.0)]
      (is (> v 0.0))
      (is (< v 1.0)))))

(deftest ellipse-tip-test
  (testing "wide ellipse: point at 1.5x along major axis is inside"
    (let [v (tip/evaluate-tip {:tip/shape :ellipse :tip/hardness 0.7 :tip/aspect 2.0}
                              1.5 0.0 0.0)]
      (is (> v 0.0) "should be inside wide ellipse")))
  (testing "wide ellipse: point at 1.5x along minor axis is outside"
    (let [v (tip/evaluate-tip {:tip/shape :ellipse :tip/hardness 0.7 :tip/aspect 2.0}
                              0.0 1.5 0.0)]
      (is (= 0.0 v) "should be outside along minor axis"))))

(deftest rotated-tip-test
  (testing "rotation transforms coordinate system"
    (let [;; Point at (1, 0) with no rotation should be at edge of unit circle
          v-no-rot  (tip/evaluate-tip {:tip/shape :ellipse :tip/hardness 0.7 :tip/aspect 2.0}
                                      0.9 0.0 0.0)
          ;; Same point with 90 degree rotation — now along minor axis
          v-rotated (tip/evaluate-tip {:tip/shape :ellipse :tip/hardness 0.7 :tip/aspect 2.0}
                                      0.9 0.0 (/ Math/PI 2.0))]
      (is (> v-no-rot 0.0) "no rotation: inside major axis")
      ;; With rotation, the major axis is now vertical, so (0.9, 0) is along minor axis
      (is (< v-rotated v-no-rot) "rotated: less coverage along what is now minor axis"))))

(deftest bristle-offsets-test
  (testing "produces correct number of bristles"
    (let [offsets (tip/bristle-offsets {:bristle/count 7} 0.0 42)]
      (is (= 7 (count offsets)))))

  (testing "each bristle has offset, opacity-scale, size-scale"
    (let [offsets (tip/bristle-offsets {:bristle/count 3 :bristle/spread 0.5} 0.0 0)]
      (doseq [o offsets]
        (is (vector? (:offset o)))
        (is (number? (:opacity-scale o)))
        (is (number? (:size-scale o))))))

  (testing "center bristle is near zero offset"
    (let [offsets (tip/bristle-offsets {:bristle/count 3 :bristle/spread 0.5
                                       :bristle/randomize 0.0}
                                      0.0 0)
          center (nth offsets 1)]
      (let [[dx dy] (:offset center)]
        (is (< (Math/abs dx) 0.1) "center x should be near zero")))))

(deftest line-tip-test
  (testing "center of line has full coverage"
    (is (= 1.0 (tip/evaluate-tip {:tip/shape :line :tip/hardness 0.7 :tip/aspect 2.0}
                                  0.0 0.0 0.0))))
  (testing "far from line is zero"
    (is (= 0.0 (tip/evaluate-tip {:tip/shape :line :tip/hardness 0.7 :tip/aspect 2.0}
                                  0.0 2.0 0.0)))))
