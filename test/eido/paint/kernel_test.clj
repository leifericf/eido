(ns eido.paint.kernel-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.paint.kernel :as kernel]
    [eido.paint.surface :as surface]))

(deftest rasterize-dab-center-test
  (testing "center pixel receives paint"
    (let [s (surface/create-surface 100 100)]
      (kernel/rasterize-dab! s {:dab/cx 50.0 :dab/cy 50.0 :dab/radius 20.0
                                :dab/hardness 0.7 :dab/opacity 1.0
                                :dab/color {:r 255 :g 0 :b 0 :a 1.0}})
      (let [[r _g _b a] (surface/get-pixel s 50 50)]
        (is (> a 0.5) "center alpha should be high")
        (is (> r 0.0) "center red channel should be non-zero")))))

(deftest rasterize-dab-falloff-test
  (testing "opacity decreases from center to edge"
    (let [s (surface/create-surface 100 100)]
      (kernel/rasterize-dab! s {:dab/cx 50.0 :dab/cy 50.0 :dab/radius 20.0
                                :dab/hardness 0.5 :dab/opacity 1.0
                                :dab/color {:r 255 :g 0 :b 0 :a 1.0}})
      (let [[_r1 _g1 _b1 a-center] (surface/get-pixel s 50 50)
            [_r2 _g2 _b2 a-edge]   (surface/get-pixel s 68 50)]
        (is (> a-center a-edge) "center should be more opaque than edge")))))

(deftest rasterize-dab-outside-test
  (testing "pixels outside radius are untouched"
    (let [s (surface/create-surface 100 100)]
      (kernel/rasterize-dab! s {:dab/cx 50.0 :dab/cy 50.0 :dab/radius 10.0
                                :dab/hardness 0.7 :dab/opacity 1.0
                                :dab/color {:r 255 :g 0 :b 0 :a 1.0}})
      (is (= [0.0 0.0 0.0 0.0] (surface/get-pixel s 80 80))))))

(deftest rasterize-dab-accumulation-test
  (testing "overlapping dabs accumulate opacity"
    (let [s (surface/create-surface 100 100)
          dab {:dab/cx 50.0 :dab/cy 50.0 :dab/radius 15.0
               :dab/hardness 0.8 :dab/opacity 0.3
               :dab/color {:r 100 :g 50 :b 25 :a 1.0}}]
      (kernel/rasterize-dab! s dab)
      (let [[_ _ _ a1] (surface/get-pixel s 50 50)]
        (kernel/rasterize-dab! s dab)
        (let [[_ _ _ a2] (surface/get-pixel s 50 50)]
          (is (> a2 a1) "second dab should increase alpha"))))))

(deftest rasterize-dab-clamp-test
  (testing "dab near surface edge does not throw"
    (let [s (surface/create-surface 50 50)]
      (kernel/rasterize-dab! s {:dab/cx 2.0 :dab/cy 2.0 :dab/radius 10.0
                                :dab/hardness 0.7 :dab/opacity 0.5
                                :dab/color {:r 100 :g 100 :b 100 :a 1.0}})
      (kernel/rasterize-dab! s {:dab/cx 48.0 :dab/cy 48.0 :dab/radius 10.0
                                :dab/hardness 0.7 :dab/opacity 0.5
                                :dab/color {:r 100 :g 100 :b 100 :a 1.0}})
      (is true "should not throw"))))

(deftest deform-dab-modes-test
  (testing "all deform modes work without crash"
    (let [s (surface/create-surface 100 100)]
      (kernel/rasterize-dab! s {:dab/cx 50.0 :dab/cy 50.0 :dab/radius 30.0
                                :dab/hardness 0.6 :dab/opacity 0.8
                                :dab/color {:r 200 :g 50 :b 30 :a 1.0}})
      (doseq [mode [:push :swirl :blur :sharpen]]
        (kernel/deform-dab! s {:dab/cx 50.0 :dab/cy 50.0 :dab/radius 20.0
                               :dab/angle 0.5
                               :dab/deform {:deform/mode mode :deform/strength 0.5}})
        (let [[r _g _b a] (surface/get-pixel s 50 50)]
          (is (>= r 0.0) (str mode " red channel non-negative"))
          (is (<= r 1.0) (str mode " red channel <= 1.0"))
          (is (>= a 0.0) (str mode " alpha non-negative")))))))

(deftest deform-dab-sharpen-overflow-test
  (testing "repeated sharpen does not overflow float range"
    (let [s (surface/create-surface 100 100)]
      (kernel/rasterize-dab! s {:dab/cx 50.0 :dab/cy 50.0 :dab/radius 30.0
                                :dab/hardness 0.6 :dab/opacity 0.8
                                :dab/color {:r 200 :g 50 :b 30 :a 1.0}})
      (dotimes [_ 50]
        (kernel/deform-dab! s {:dab/cx 50.0 :dab/cy 50.0 :dab/radius 20.0
                               :dab/deform {:deform/mode :sharpen
                                            :deform/strength 0.8}}))
      (let [[r g b a] (surface/get-pixel s 50 50)]
        (is (every? #(<= 0.0 % 1.0) [r g b a])
            "all channels must stay in [0,1] range")))))
