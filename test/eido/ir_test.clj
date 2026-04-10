(ns eido.ir-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.color :as color]
    [eido.engine.compile :as compile]
    [eido.ir :as ir]
    [eido.ir.lower :as lower]
    [eido.engine.render :as render]))

;; --- helpers ---

(defn- pixel-rgb
  "Extracts [r g b] from a BufferedImage at (x, y)."
  [^java.awt.image.BufferedImage img x y]
  (let [rgb (.getRGB img x y)]
    [(bit-and (bit-shift-right rgb 16) 0xff)
     (bit-and (bit-shift-right rgb 8) 0xff)
     (bit-and rgb 0xff)]))

;; --- semantic IR container tests ---

(deftest container-structure-test
  (testing "container produces expected top-level keys"
    (let [c (ir/container [800 600]
                          {:r 255 :g 255 :b 255 :a 1.0}
                          [])]
      (is (= 1 (:ir/version c)))
      (is (= [800 600] (:ir/size c)))
      (is (= {:r 255 :g 255 :b 255 :a 1.0} (:ir/background c)))
      (is (vector? (:ir/passes c)))
      (is (= 1 (count (:ir/passes c))))
      (is (= :draw-geometry (:pass/type (first (:ir/passes c))))))))

(deftest lower-empty-scene-test
  (testing "lowering an empty container produces valid concrete IR"
    (let [semantic (ir/container [400 300]
                                 {:r 128 :g 128 :b 128 :a 1.0}
                                 [])
          concrete (lower/lower semantic)]
      (is (= [400 300] (:ir/size concrete)))
      (is (= {:r 128 :g 128 :b 128 :a 1.0} (:ir/background concrete)))
      (is (= [] (:ir/ops concrete))))))

(deftest lower-rect-test
  (testing "lowered rect matches legacy compile output"
    (let [scene {:eido/validate false
                 :image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [10 20]
                   :rect/size [80 60]
                   :style/fill [:color/rgb 200 0 0]}]}
          legacy-ir (compile/compile scene)
          semantic  (ir/container
                      [200 200]
                      (color/resolve-color [:color/rgb 255 255 255])
                      [(ir/draw-item
                         (ir/rect-geometry [10 20] [80 60])
                         :fill {:fill/type :fill/solid
                                :color [:color/rgb 200 0 0]}
                         :opacity 1.0)])
          concrete (lower/lower semantic)]
      (is (= (:ir/size legacy-ir) (:ir/size concrete)))
      (is (= (count (:ir/ops legacy-ir)) (count (:ir/ops concrete))))
      (let [legacy-op (first (:ir/ops legacy-ir))
            new-op    (first (:ir/ops concrete))]
        (is (= (:op legacy-op) (:op new-op)))
        (is (= (:x legacy-op) (:x new-op)))
        (is (= (:y legacy-op) (:y new-op)))
        (is (= (:w legacy-op) (:w new-op)))
        (is (= (:h legacy-op) (:h new-op)))
        (is (= (:fill legacy-op) (:fill new-op)))))))

(deftest lower-circle-test
  (testing "lowered circle produces correct CircleOp"
    (let [semantic (ir/container
                     [200 200]
                     {:r 255 :g 255 :b 255 :a 1.0}
                     [(ir/draw-item
                        (ir/circle-geometry [100 100] 50)
                        :fill {:fill/type :fill/solid
                               :color [:color/rgb 0 0 255]}
                        :opacity 0.5)])
          concrete (lower/lower semantic)
          op       (first (:ir/ops concrete))]
      (is (= :circle (:op op)))
      (is (= 100 (:cx op)))
      (is (= 100 (:cy op)))
      (is (= 50 (:r op)))
      (is (= 0.5 (:opacity op)))
      (is (= {:r 0 :g 0 :b 255 :a 1.0} (:fill op))))))

(deftest lower-path-test
  (testing "lowered path compiles commands correctly"
    (let [commands [[:move-to [0.0 0.0]]
                    [:line-to [100.0 0.0]]
                    [:line-to [100.0 100.0]]
                    [:close]]
          semantic (ir/container
                     [200 200]
                     {:r 255 :g 255 :b 255 :a 1.0}
                     [(ir/draw-item
                        (ir/path-geometry commands)
                        :fill {:fill/type :fill/solid
                               :color [:color/rgb 0 200 0]})])
          concrete (lower/lower semantic)
          op       (first (:ir/ops concrete))]
      (is (= :path (:op op)))
      (is (= [[:move-to 0.0 0.0]
              [:line-to 100.0 0.0]
              [:line-to 100.0 100.0]
              [:close]]
             (:commands op))))))

(deftest lower-stroke-test
  (testing "stroke properties are lowered correctly"
    (let [semantic (ir/container
                     [200 200]
                     {:r 255 :g 255 :b 255 :a 1.0}
                     [(ir/draw-item
                        (ir/rect-geometry [10 10] [100 50])
                        :stroke {:color [:color/rgb 255 0 0]
                                 :width 3
                                 :cap :round
                                 :join :bevel
                                 :dash [5 3]})])
          concrete (lower/lower semantic)
          op       (first (:ir/ops concrete))]
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (:stroke-color op)))
      (is (= 3 (:stroke-width op)))
      (is (= :round (:stroke-cap op)))
      (is (= :bevel (:stroke-join op)))
      (is (= [5 3] (:stroke-dash op))))))

(deftest lower-gradient-test
  (testing "gradient fill is lowered with resolved colors"
    (let [semantic (ir/container
                     [200 200]
                     {:r 255 :g 255 :b 255 :a 1.0}
                     [(ir/draw-item
                        (ir/rect-geometry [0 0] [200 200])
                        :fill {:fill/type :fill/gradient
                               :gradient/type :linear
                               :gradient/from [0 0]
                               :gradient/to [200 0]
                               :gradient/stops
                               [[0.0 [:color/rgb 255 0 0]]
                                [1.0 [:color/rgb 0 0 255]]]})])
          concrete (lower/lower semantic)
          op       (first (:ir/ops concrete))
          fill     (:fill op)]
      (is (= :linear (:gradient/type fill)))
      (is (= [0 0] (:gradient/from fill)))
      (is (= [200 0] (:gradient/to fill)))
      (is (= 2 (count (:gradient/stops fill))))
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (second (first (:gradient/stops fill))))))))

(deftest render-round-trip-test
  (testing "semantic IR renders identically to legacy compile"
    (let [scene {:eido/validate false
                 :image/size [100 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [10 10]
                   :rect/size [80 80]
                   :style/fill [:color/rgb 200 0 0]}]}
          legacy-ir   (compile/compile scene)
          semantic    (ir/container
                        [100 100]
                        (color/resolve-color [:color/rgb 255 255 255])
                        [(ir/draw-item
                           (ir/rect-geometry [10 10] [80 80])
                           :fill {:fill/type :fill/solid
                                  :color [:color/rgb 200 0 0]}
                           :opacity 1.0)])
          concrete    (lower/lower semantic)
          legacy-img  (render/render legacy-ir {})
          new-img     (render/render concrete {})]
      ;; Center pixel of the red rect
      (is (= (pixel-rgb legacy-img 50 50)
             (pixel-rgb new-img 50 50)))
      ;; Corner pixel (white background)
      (is (= (pixel-rgb legacy-img 5 5)
             (pixel-rgb new-img 5 5))))))

;; --- geometry-bounds tests ---

(deftest geometry-bounds-arc-test
  (testing "arc geometry returns bounding box from center and radii"
    (let [bounds (ir/geometry-bounds {:geometry/type :arc
                                      :arc/center [100 100]
                                      :arc/rx 50 :arc/ry 30
                                      :arc/start 0 :arc/extent 90})]
      (is (= [50 70 100 60] bounds)))))

(deftest geometry-bounds-line-test
  (testing "line geometry returns bounding box from endpoints"
    (let [bounds (ir/geometry-bounds {:geometry/type :line
                                      :line/from [10 80]
                                      :line/to [100 20]})]
      (is (= [10 20 90.0 60.0] bounds)))))

(deftest geometry-bounds-path-curves-test
  (testing "path bounds include curve-to endpoints"
    (let [bounds (ir/geometry-bounds
                   {:geometry/type :path
                    :path/commands [[:move-to [50 50]]
                                   [:curve-to [60 0] [90 0] [100 50]]
                                   [:line-to [100 100]]
                                   [:close]]})]
      (is (= [50 50 50 50] bounds))))
  (testing "path bounds include quad-to endpoints"
    (let [bounds (ir/geometry-bounds
                   {:geometry/type :path
                    :path/commands [[:move-to [0 0]]
                                   [:quad-to [50 -10] [100 0]]
                                   [:quad-to [110 50] [100 100]]
                                   [:close]]})]
      (is (= [0 0 100 100] bounds))))
  (testing "path with only curves still computes bounds"
    (let [bounds (ir/geometry-bounds
                   {:geometry/type :path
                    :path/commands [[:move-to [50 0]]
                                   [:curve-to [100 0] [100 50] [100 100]]
                                   [:curve-to [100 150] [50 200] [0 200]]
                                   [:close]]})]
      (is (= [0 0 100 200] bounds)))))
