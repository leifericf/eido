(ns eido.ir.effect-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.color :as color]
    [eido.ir :as ir]
    [eido.ir.effect :as effect]
    [eido.ir.fill :as fill]
    [eido.ir.lower :as lower]
    [eido.engine.render :as render]))

;; --- helpers ---

(defn- pixel-rgb
  [^java.awt.image.BufferedImage img x y]
  (let [rgb (.getRGB img x y)]
    [(bit-and (bit-shift-right rgb 16) 0xff)
     (bit-and (bit-shift-right rgb 8) 0xff)
     (bit-and rgb 0xff)]))

(defn- render-semantic [ir-container]
  (render/render (lower/lower ir-container) {}))

;; --- effect constructor tests ---

(deftest shadow-constructor-test
  (let [s (effect/shadow :dx 5 :dy 5 :blur 10
                         :color [:color/rgb 0 0 0] :opacity 0.6)]
    (is (= :effect/shadow (:effect/type s)))
    (is (= 5 (:effect/dx s)))
    (is (= 10 (:effect/blur s)))))

(deftest glow-constructor-test
  (let [g (effect/glow :blur 12 :color [:color/rgb 255 150 0] :opacity 0.8)]
    (is (= :effect/glow (:effect/type g)))
    (is (= 12 (:effect/blur g)))))

;; --- effect lowering tests ---

(deftest lower-shadow-test
  (testing "shadow effect produces buffer ops"
    (let [item (ir/draw-item
                 (ir/rect-geometry [50 50] [100 80])
                 :fill (fill/solid [:color/rgb 60 120 200])
                 :effects [(effect/shadow :dx 3 :dy 3 :blur 5
                                          :color [:color/rgb 0 0 0]
                                          :opacity 0.5)])
          ops  (effect/lower-effects item)]
      (is (vector? ops))
      (is (pos? (count ops)))
      ;; Should contain at least one buffer op (for the blur)
      (is (some #(= :buffer (:op %)) ops)))))

(deftest lower-glow-test
  (testing "glow effect produces buffer ops"
    (let [item (ir/draw-item
                 (ir/circle-geometry [100 100] 40)
                 :fill (fill/solid [:color/rgb 255 100 50])
                 :effects [(effect/glow :blur 10
                                        :color [:color/rgb 255 150 50]
                                        :opacity 0.7)])
          ops  (effect/lower-effects item)]
      (is (vector? ops))
      (is (some #(= :buffer (:op %)) ops)))))

(deftest shadow-renders-test
  (testing "shadow effect renders without errors"
    (let [semantic (ir/container
                     [200 200]
                     (color/resolve-color [:color/rgb 240 240 240])
                     [(ir/draw-item
                        (ir/rect-geometry [40 40] [120 120])
                        :fill (fill/solid [:color/rgb 60 120 200])
                        :effects [(effect/shadow :dx 5 :dy 5 :blur 8
                                                  :color [:color/rgb 0 0 0]
                                                  :opacity 0.4)])])
          img (render-semantic semantic)]
      (is (some? img))
      ;; Shadow area (offset from rect) should be darker than background
      (let [[r g b] (pixel-rgb img 170 170)]
        (is (< r 240))))))

(deftest glow-renders-test
  (testing "glow effect renders without errors"
    (let [semantic (ir/container
                     [200 200]
                     (color/resolve-color [:color/rgb 20 20 30])
                     [(ir/draw-item
                        (ir/circle-geometry [100 100] 40)
                        :fill (fill/solid [:color/rgb 255 100 50])
                        :effects [(effect/glow :blur 15
                                               :color [:color/rgb 255 150 50]
                                               :opacity 0.8)])])
          img (render-semantic semantic)]
      (is (some? img)))))

;; --- filter effect constructor tests ---

(deftest blur-constructor-test
  (let [b (effect/blur :radius 8)]
    (is (= :effect/blur (:effect/type b)))
    (is (= 8 (:effect/radius b)))))

(deftest grain-constructor-test
  (let [g (effect/grain :amount 50 :seed 42)]
    (is (= :effect/grain (:effect/type g)))
    (is (= 50 (:effect/amount g)))
    (is (= 42 (:effect/seed g)))))

(deftest posterize-constructor-test
  (let [p (effect/posterize :levels 4)]
    (is (= :effect/posterize (:effect/type p)))
    (is (= 4 (:effect/levels p)))))

(deftest duotone-constructor-test
  (let [d (effect/duotone :color-a [:color/rgb 20 20 60]
                          :color-b [:color/rgb 255 230 180])]
    (is (= :effect/duotone (:effect/type d)))
    (is (= [:color/rgb 20 20 60] (:effect/color-a d)))))

(deftest halftone-constructor-test
  (let [h (effect/halftone :dot-size 8 :angle 30)]
    (is (= :effect/halftone (:effect/type h)))
    (is (= 8 (:effect/dot-size h)))
    (is (= 30 (:effect/angle h)))))

;; --- filter effect lowering tests ---

(deftest grain-renders-test
  (testing "grain effect renders without errors"
    (let [semantic (ir/container
                     [100 100]
                     (color/resolve-color [:color/rgb 128 128 128])
                     [(ir/draw-item
                        (ir/rect-geometry [0 0] [100 100])
                        :fill (fill/solid [:color/rgb 128 128 128])
                        :effects [(effect/grain :amount 50 :seed 42)])])
          img (render-semantic semantic)]
      (is (some? img))
      ;; Grain should make pixels vary from the base color
      (let [[r1 _ _] (pixel-rgb img 10 10)
            [r2 _ _] (pixel-rgb img 50 50)]
        ;; At least one pixel should differ from 128 (grain noise)
        (is (or (not= 128 r1) (not= 128 r2)))))))

(deftest posterize-renders-test
  (testing "posterize effect renders"
    (let [semantic (ir/container
                     [100 100]
                     (color/resolve-color [:color/rgb 255 255 255])
                     [(ir/draw-item
                        (ir/rect-geometry [0 0] [100 100])
                        :fill (fill/solid [:color/rgb 100 150 200])
                        :effects [(effect/posterize :levels 2)])])
          img (render-semantic semantic)]
      (is (some? img)))))

(deftest duotone-renders-test
  (testing "duotone effect renders"
    (let [semantic (ir/container
                     [100 100]
                     (color/resolve-color [:color/rgb 255 255 255])
                     [(ir/draw-item
                        (ir/rect-geometry [0 0] [100 100])
                        :fill (fill/solid [:color/rgb 100 150 200])
                        :effects [(effect/duotone
                                    :color-a [:color/rgb 20 20 60]
                                    :color-b [:color/rgb 255 230 180])])])
          img (render-semantic semantic)]
      (is (some? img)))))

(deftest halftone-renders-test
  (testing "halftone effect renders"
    (let [semantic (ir/container
                     [100 100]
                     (color/resolve-color [:color/rgb 255 255 255])
                     [(ir/draw-item
                        (ir/rect-geometry [0 0] [100 100])
                        :fill (fill/solid [:color/rgb 50 50 50])
                        :effects [(effect/halftone :dot-size 8 :angle 45)])])
          img (render-semantic semantic)]
      (is (some? img)))))

(deftest combined-effects-test
  (testing "shadow + grain on same item"
    (let [semantic (ir/container
                     [200 200]
                     (color/resolve-color [:color/rgb 240 240 240])
                     [(ir/draw-item
                        (ir/rect-geometry [40 40] [120 120])
                        :fill (fill/solid [:color/rgb 60 120 200])
                        :effects [(effect/shadow :dx 4 :dy 4 :blur 6
                                                  :color [:color/rgb 0 0 0]
                                                  :opacity 0.4)
                                  (effect/grain :amount 30 :seed 99)])])
          img (render-semantic semantic)]
      (is (some? img)))))
