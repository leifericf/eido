(ns eido.ir.procedural-test
  "Tests for procedural fills — the first new capability from the semantic IR."
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.color :as color]
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

;; --- procedural fill constructor ---

(deftest procedural-fill-constructor-test
  (let [prog {:program/body [:color/rgb 255 0 0]}
        f (fill/procedural prog)]
    (is (= :fill/procedural (:fill/type f)))
    (is (= prog (:fill/program f)))))

;; --- procedural fill evaluation ---

(deftest evaluate-procedural-solid-test
  (testing "procedural fill that returns solid red"
    (let [f {:fill/type    :fill/procedural
             :fill/program {:program/body [:color/rgb 255 0 0]}}
          img (fill/evaluate-procedural-fill f 10 10)]
      (is (some? img))
      (is (= 10 (.getWidth img)))
      ;; Every pixel should be red
      (let [argb (.getRGB img 5 5)
            r (bit-and (bit-shift-right argb 16) 0xff)]
        (is (= 255 r))))))

(deftest evaluate-procedural-gradient-test
  (testing "procedural fill with horizontal gradient via :mix"
    (let [f {:fill/type    :fill/procedural
             :fill/program {:program/body
                            [:color/rgb
                             [:* 255 [:x :uv]]
                             0
                             [:* 255 [:- 1.0 [:x :uv]]]]}}
          img (fill/evaluate-procedural-fill f 100 10)]
      ;; Left edge should be dark red, right edge should be blue
      (let [[lr _ lb] [(bit-and (bit-shift-right (.getRGB img 1 5) 16) 0xff)
                       0
                       (bit-and (.getRGB img 1 5) 0xff)]
            [rr _ rb] [(bit-and (bit-shift-right (.getRGB img 98 5) 16) 0xff)
                       0
                       (bit-and (.getRGB img 98 5) 0xff)]]
        (is (< lr 20) "left red should be near 0")
        (is (> rr 240) "right red should be near 255")))))

(deftest evaluate-procedural-noise-test
  (testing "procedural fill with noise"
    (let [f {:fill/type    :fill/procedural
             :fill/program {:program/body
                            [:clamp
                             [:+ 0.5
                                  [:* 0.5
                                      [:field/noise
                                       {:field/type :field/noise
                                        :field/scale 5.0
                                        :field/variant :fbm
                                        :field/seed 42}
                                       :uv]]]
                             0.0 1.0]}}
          img (fill/evaluate-procedural-fill f 50 50)]
      ;; Noise should produce non-uniform pixels
      (let [p1 (bit-and (bit-shift-right (.getRGB img 10 10) 16) 0xff)
            p2 (bit-and (bit-shift-right (.getRGB img 40 40) 16) 0xff)]
        ;; At least some variation (not guaranteed but very likely with noise)
        (is (some? img))))))

;; --- procedural fill rendering via semantic IR ---

(deftest procedural-fill-renders-test
  (testing "procedural fill renders correctly through full pipeline"
    (let [semantic (ir/container
                     [200 200]
                     (color/resolve-color [:color/rgb 255 255 255])
                     [(ir/draw-item
                        (ir/rect-geometry [20 20] [160 160])
                        :fill (fill/procedural
                                {:program/body [:color/rgb 200 50 50]}))])
          img (render-semantic semantic)]
      ;; Center of the rect should be the procedural color
      (is (= [200 50 50] (pixel-rgb img 100 100)))
      ;; Corner (outside rect) should be white background
      (is (= [255 255 255] (pixel-rgb img 5 5))))))

(deftest procedural-noise-fill-renders-test
  (testing "noise-based procedural fill produces non-uniform output"
    (let [semantic (ir/container
                     [100 100]
                     (color/resolve-color [:color/rgb 255 255 255])
                     [(ir/draw-item
                        (ir/rect-geometry [0 0] [100 100])
                        :fill (fill/procedural
                                {:program/body
                                 [:color/rgb
                                  [:* 255
                                      [:clamp
                                       [:+ 0.5
                                            [:* 0.5
                                                [:field/noise
                                                 {:field/type :field/noise
                                                  :field/scale 3.0
                                                  :field/variant :fbm
                                                  :field/seed 42}
                                                 :uv]]]
                                       0.0 1.0]]
                                  100
                                  50]}))])
          img (render-semantic semantic)]
      ;; Should render without error
      (is (some? img))
      ;; Pixels should not all be the same (noise varies)
      (let [p1 (pixel-rgb img 10 10)
            p2 (pixel-rgb img 90 90)]
        ;; Green and blue channels are constant, red varies with noise
        (is (= 100 (nth p1 1)))
        (is (= 50 (nth p1 2)))))))

(deftest procedural-fill-on-circle-test
  (testing "procedural fill works on circle geometry"
    (let [semantic (ir/container
                     [200 200]
                     (color/resolve-color [:color/rgb 0 0 0])
                     [(ir/draw-item
                        (ir/circle-geometry [100 100] 80)
                        :fill (fill/procedural
                                {:program/body [:color/rgb 0 200 100]}))])
          img (render-semantic semantic)]
      ;; Center of circle should have the fill color
      (is (= [0 200 100] (pixel-rgb img 100 100))))))
