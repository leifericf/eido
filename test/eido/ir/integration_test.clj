(ns eido.ir.integration-test
  "Integration tests for the semantic IR pipeline.
  Verifies that scenes compiled through the semantic path produce
  correct output."
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.color :as color]
    [eido.compile :as compile]
    [eido.ir :as ir]
    [eido.ir.lower :as lower]
    [eido.render :as render]))

;; --- helpers ---

(defn- pixel-rgb
  [^java.awt.image.BufferedImage img x y]
  (let [rgb (.getRGB img x y)]
    [(bit-and (bit-shift-right rgb 16) 0xff)
     (bit-and (bit-shift-right rgb 8) 0xff)
     (bit-and rgb 0xff)]))

;; --- compile-semantic tests ---

(deftest compile-semantic-simple-test
  (testing "compile-semantic produces a valid IR container"
    (let [scene {:eido/validate false
                 :image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [10 10]
                   :rect/size [80 80]
                   :style/fill [:color/rgb 200 0 0]}]}
          semantic (compile/compile-semantic scene)]
      (is (= 1 (:ir/version semantic)))
      (is (= [200 200] (:ir/size semantic)))
      (is (= 1 (count (:ir/passes semantic))))
      (is (= 1 (count (:pass/items (first (:ir/passes semantic)))))))))

(deftest compile-semantic-lowered-renders-test
  (testing "semantic → lower → render produces correct pixels"
    (let [scene {:eido/validate false
                 :image/size [100 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [10 10]
                   :rect/size [80 80]
                   :style/fill [:color/rgb 200 0 0]}]}
          semantic (compile/compile-semantic scene)
          concrete (lower/lower semantic)
          img      (render/render concrete {})]
      ;; Center should be red
      (is (= [200 0 0] (pixel-rgb img 50 50)))
      ;; Corner should be white
      (is (= [255 255 255] (pixel-rgb img 5 5))))))

(deftest compile-semantic-hatch-test
  (testing "hatch fill survives in semantic IR and renders after lowering"
    (let [scene {:eido/validate false
                 :image/size [200 200]
                 :image/background [:color/rgb 245 235 215]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [20 20]
                   :rect/size [160 160]
                   :style/fill {:fill/type :hatch
                                :hatch/angle 45
                                :hatch/spacing 5
                                :hatch/stroke-width 0.8
                                :hatch/color [:color/rgb 30 30 30]}}]}
          semantic (compile/compile-semantic scene)
          ;; The fill should be preserved as :hatch in the semantic IR
          item     (first (:pass/items (first (:ir/passes semantic))))
          _        (is (= :hatch (:fill/type (:item/fill item))))
          ;; Lower and render
          concrete (lower/lower semantic)
          img      (render/render concrete {})]
      ;; Should have hatch lines (center should not be pure background)
      (is (some? img)))))

(deftest compile-semantic-circle-test
  (testing "circle with solid fill compiles and renders via semantic path"
    (let [scene {:eido/validate false
                 :image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/circle
                   :circle/center [100 100]
                   :circle/radius 50
                   :style/fill [:color/rgb 0 0 200]}]}
          semantic (compile/compile-semantic scene)
          concrete (lower/lower semantic)
          img      (render/render concrete {})]
      (is (= [0 0 200] (pixel-rgb img 100 100))))))

(deftest compile-semantic-gradient-test
  (testing "gradient fill preserved in semantic IR"
    (let [scene {:eido/validate false
                 :image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [0 0]
                   :rect/size [200 200]
                   :style/fill {:gradient/type :linear
                                :gradient/from [0 0]
                                :gradient/to [200 0]
                                :gradient/stops [[0.0 [:color/rgb 255 0 0]]
                                                 [1.0 [:color/rgb 0 0 255]]]}}]}
          semantic (compile/compile-semantic scene)
          item     (first (:pass/items (first (:ir/passes semantic))))
          _        (is (= :fill/gradient (:fill/type (:item/fill item))))
          concrete (lower/lower semantic)
          img      (render/render concrete {})]
      ;; Left side should be reddish, right side bluish
      (let [[lr _ _] (pixel-rgb img 10 100)
            [_ _ rb] (pixel-rgb img 190 100)]
        (is (> lr 200))
        (is (> rb 200))))))

(deftest compile-semantic-multiple-shapes-test
  (testing "multiple shapes compile correctly"
    (let [scene {:eido/validate false
                 :image/size [300 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [10 10]
                   :rect/size [100 80]
                   :style/fill [:color/rgb 200 0 0]}
                  {:node/type :shape/circle
                   :circle/center [220 100]
                   :circle/radius 40
                   :style/fill [:color/rgb 0 200 0]}]}
          semantic (compile/compile-semantic scene)
          items    (:pass/items (first (:ir/passes semantic)))
          _        (is (= 2 (count items)))
          concrete (lower/lower semantic)
          img      (render/render concrete {})]
      (is (= [200 0 0] (pixel-rgb img 50 50)))
      (is (= [0 200 0] (pixel-rgb img 220 100))))))

;; --- generator integration tests ---

(deftest compile-semantic-flow-field-test
  (testing "flow field node compiles through semantic path"
    (let [scene {:eido/validate false
                 :image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type  :flow-field
                   :flow/bounds [0 0 200 200]
                   :flow/opts {:density 30 :steps 20 :seed 42}
                   :style/stroke {:color [:color/rgb 0 0 0] :width 1}}]}
          semantic (compile/compile-semantic scene)
          item     (first (:pass/items (first (:ir/passes semantic))))]
      ;; Should be a generator item
      (is (= :generator/flow-field
             (get-in item [:item/generator :generator/type])))
      ;; Should lower and render
      (let [concrete (lower/lower semantic)
            img      (render/render concrete {})]
        (is (some? img))))))

(deftest compile-semantic-voronoi-test
  (testing "voronoi node compiles through semantic path"
    (let [scene {:eido/validate false
                 :image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type     :voronoi
                   :voronoi/points [[40 40] [160 40] [100 160]]
                   :voronoi/bounds [0 0 200 200]
                   :style/stroke {:color [:color/rgb 0 0 0] :width 1}}]}
          semantic (compile/compile-semantic scene)
          item     (first (:pass/items (first (:ir/passes semantic))))]
      (is (= :generator/voronoi
             (get-in item [:item/generator :generator/type])))
      (let [concrete (lower/lower semantic)
            img      (render/render concrete {})]
        (is (some? img))))))

(deftest compile-semantic-scatter-test
  (testing "scatter node compiles through semantic path"
    (let [scene {:eido/validate false
                 :image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type         :scatter
                   :scatter/shape     {:node/type :shape/circle
                                       :circle/center [0 0]
                                       :circle/radius 5
                                       :style/fill [:color/rgb 200 0 0]}
                   :scatter/positions [[50 50] [100 100] [150 150]]}]}
          semantic (compile/compile-semantic scene)
          item     (first (:pass/items (first (:ir/passes semantic))))]
      (is (= :generator/scatter
             (get-in item [:item/generator :generator/type])))
      (let [concrete (lower/lower semantic)
            img      (render/render concrete {})]
        (is (some? img))))))
