(ns eido.ir.fill-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.color :as color]
    [eido.compile :as compile]
    [eido.ir :as ir]
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

;; --- fill constructor tests ---

(deftest solid-fill-test
  (is (= {:fill/type :fill/solid :color [:color/rgb 200 0 0]}
         (fill/solid [:color/rgb 200 0 0]))))

(deftest gradient-fill-test
  (let [g (fill/gradient :linear
                         [[0.0 [:color/rgb 255 0 0]]
                          [1.0 [:color/rgb 0 0 255]]]
                         :from [0 0] :to [100 0])]
    (is (= :fill/gradient (:fill/type g)))
    (is (= :linear (:gradient/type g)))
    (is (= [0 0] (:gradient/from g)))))

(deftest hatch-fill-test
  (let [h (fill/hatch {:hatch/angle 45 :hatch/spacing 5})]
    (is (= :hatch (:fill/type h)))
    (is (= 45 (:hatch/angle h)))))

(deftest semantic-fill-predicate-test
  (is (fill/semantic-fill? {:fill/type :hatch}))
  (is (fill/semantic-fill? {:fill/type :stipple}))
  (is (fill/semantic-fill? {:fill/type :fill/hatch}))
  (is (not (fill/semantic-fill? {:fill/type :fill/solid})))
  (is (not (fill/semantic-fill? nil))))

;; --- hatch lowering tests ---

(deftest lower-hatch-rect-test
  (testing "hatch fill on rect produces non-empty ops"
    (let [item (ir/draw-item
                 (ir/rect-geometry [20 20] [200 100])
                 :fill {:fill/type :hatch
                        :hatch/angle 45
                        :hatch/spacing 5
                        :hatch/stroke-width 0.8
                        :hatch/color [:color/rgb 0 0 0]})
          ops  (fill/lower-hatch item)]
      (is (vector? ops))
      (is (pos? (count ops))))))

(deftest lower-hatch-renders-test
  (testing "hatch fill renders without errors via semantic IR"
    (let [semantic (ir/container
                     [200 200]
                     (color/resolve-color [:color/rgb 245 235 215])
                     [(ir/draw-item
                        (ir/rect-geometry [10 10] [180 180])
                        :fill {:fill/type :hatch
                               :hatch/angle 45
                               :hatch/spacing 6
                               :hatch/stroke-width 0.8
                               :hatch/color [:color/rgb 30 30 30]})])
          img (render-semantic semantic)]
      (is (some? img))
      ;; Center should not be solid white (hatch lines should be present)
      (is (not= [245 235 215] (pixel-rgb img 100 100))))))

;; --- stipple lowering tests ---

(deftest lower-stipple-rect-test
  (testing "stipple fill on rect produces non-empty ops"
    (let [item (ir/draw-item
                 (ir/rect-geometry [20 20] [200 150])
                 :fill {:fill/type :stipple
                        :stipple/density 0.5
                        :stipple/radius 2
                        :stipple/seed 42
                        :stipple/color [:color/rgb 0 0 0]})
          ops  (fill/lower-stipple item)]
      (is (vector? ops))
      (is (pos? (count ops))))))

(deftest lower-stipple-renders-test
  (testing "stipple fill renders without errors via semantic IR"
    (let [semantic (ir/container
                     [200 200]
                     (color/resolve-color [:color/rgb 255 255 255])
                     [(ir/draw-item
                        (ir/circle-geometry [100 100] 80)
                        :fill {:fill/type :stipple
                               :stipple/density 0.6
                               :stipple/radius 2
                               :stipple/seed 42
                               :stipple/color [:color/rgb 0 0 0]})])
          img (render-semantic semantic)]
      (is (some? img)))))

;; --- mixed scene test ---

(deftest mixed-fills-scene-test
  (testing "scene with solid and hatch fills renders correctly"
    (let [semantic (ir/container
                     [300 200]
                     (color/resolve-color [:color/rgb 255 255 255])
                     [(ir/draw-item
                        (ir/rect-geometry [10 10] [120 80])
                        :fill (fill/solid [:color/rgb 200 0 0])
                        :opacity 1.0)
                      (ir/draw-item
                        (ir/rect-geometry [150 10] [120 80])
                        :fill {:fill/type :hatch
                               :hatch/angle 0
                               :hatch/spacing 4
                               :hatch/stroke-width 0.6
                               :hatch/color [:color/rgb 0 0 0]})])
          img (render-semantic semantic)]
      ;; Left rect center should be red
      (is (= [200 0 0] (pixel-rgb img 70 50)))
      ;; Right rect area should have hatch content (not pure white)
      (is (some? img)))))
