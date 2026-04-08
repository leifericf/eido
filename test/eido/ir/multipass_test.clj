(ns eido.ir.multipass-test
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

(defn- render-pipeline [ir-container]
  (render/render (lower/lower ir-container) {}))

;; --- tests ---

(deftest single-draw-pass-test
  (testing "pipeline with just a draw pass works like container"
    (let [pipe (ir/pipeline
                 [200 200]
                 (color/resolve-color [:color/rgb 255 255 255])
                 [{:pass/id    :draw
                   :pass/type  :draw-geometry
                   :pass/items [(ir/draw-item
                                  (ir/rect-geometry [20 20] [160 160])
                                  :fill (fill/solid [:color/rgb 200 0 0]))]}])
          img (render-pipeline pipe)]
      (is (= [200 0 0] (pixel-rgb img 100 100)))
      (is (= [255 255 255] (pixel-rgb img 5 5))))))

(deftest draw-then-grain-test
  (testing "draw pass followed by grain effect pass"
    (let [pipe (ir/pipeline
                 [100 100]
                 (color/resolve-color [:color/rgb 128 128 128])
                 [{:pass/id    :draw
                   :pass/type  :draw-geometry
                   :pass/items [(ir/draw-item
                                  (ir/rect-geometry [0 0] [100 100])
                                  :fill (fill/solid [:color/rgb 128 128 128]))]}
                  (ir/effect-pass :grain-pass
                    (effect/grain :amount 50 :seed 42))])
          img (render-pipeline pipe)]
      (is (some? img))
      ;; Grain should modify pixels from uniform 128
      (let [[r1 _ _] (pixel-rgb img 10 10)
            [r2 _ _] (pixel-rgb img 50 50)]
        (is (or (not= 128 r1) (not= 128 r2)))))))

(deftest draw-then-posterize-test
  (testing "draw pass followed by posterize effect pass"
    (let [pipe (ir/pipeline
                 [100 100]
                 (color/resolve-color [:color/rgb 255 255 255])
                 [{:pass/id    :draw
                   :pass/type  :draw-geometry
                   :pass/items [(ir/draw-item
                                  (ir/rect-geometry [0 0] [100 100])
                                  :fill (fill/solid [:color/rgb 100 150 200]))]}
                  (ir/effect-pass :posterize-pass
                    (effect/posterize :levels 2))])
          img (render-pipeline pipe)]
      (is (some? img)))))

(deftest effect-pass-constructor-test
  (let [pass (ir/effect-pass :my-blur (effect/blur :radius 5))]
    (is (= :my-blur (:pass/id pass)))
    (is (= :effect-pass (:pass/type pass)))
    (is (= :framebuffer (:pass/input pass)))
    (is (= :effect/blur (get-in pass [:pass/effect :effect/type])))))

(deftest pipeline-constructor-test
  (let [pipe (ir/pipeline [800 600]
               {:r 0 :g 0 :b 0 :a 1.0}
               [{:pass/id :draw :pass/type :draw-geometry :pass/items []}
                (ir/effect-pass :blur (effect/blur :radius 3))])]
    (is (= 1 (:ir/version pipe)))
    (is (= [800 600] (:ir/size pipe)))
    (is (= 2 (count (:ir/passes pipe))))))
