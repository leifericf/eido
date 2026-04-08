(ns eido.ir.effect-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.color :as color]
    [eido.ir :as ir]
    [eido.ir.effect :as effect]
    [eido.ir.fill :as fill]
    [eido.ir.lower :as lower]
    [eido.render :as render]))

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
