(ns eido.integration.feature-combo-test
  "End-to-end tests for feature combinations that generative artists
  commonly use together. Each test renders a complete scene and verifies
  the output is a valid image of the expected dimensions."
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.core :as eido]
    [eido.gen :as gen])
  (:import
    [java.awt.image BufferedImage]))

;; --- helpers ---

(defn- render-ok?
  "Renders scene, returns true if result is a BufferedImage of expected size."
  ([scene] (render-ok? scene 200 200))
  ([scene w h]
   (let [img (eido/render scene)]
     (and (instance? BufferedImage img)
          (= w (.getWidth img))
          (= h (.getHeight img))))))

(defn- pixel-rgb
  "Returns {:r :g :b} for the pixel at (x, y)."
  [^BufferedImage img x y]
  (let [argb (.getRGB img (int x) (int y))]
    {:r (bit-and (bit-shift-right argb 16) 0xFF)
     :g (bit-and (bit-shift-right argb 8) 0xFF)
     :b (bit-and argb 0xFF)}))

(def ^:private base-scene
  {:image/size [200 200]
   :image/background [:color/rgb 255 255 255]})

(defn- scene [nodes]
  (assoc base-scene :image/nodes nodes))

;; --- gradient + clip ---

(deftest gradient-fill-with-clip-test
  (testing "linear gradient clipped to circle renders without error"
    (is (render-ok?
          (scene [{:node/type :group
                   :group/clip {:node/type :shape/circle
                                :circle/center [100 100]
                                :circle/radius 60}
                   :group/children
                   [{:node/type :shape/rect
                     :rect/xy [0 0] :rect/size [200 200]
                     :style/fill {:gradient/type :linear
                                  :gradient/from [0 100] :gradient/to [200 100]
                                  :gradient/stops [[0.0 [:color/rgb 255 0 0]]
                                                   [1.0 [:color/rgb 0 0 255]]]}}]}]))))
  (testing "pixel outside clip is background"
    (let [img (eido/render
                (scene [{:node/type :group
                         :group/clip {:node/type :shape/circle
                                      :circle/center [100 100]
                                      :circle/radius 30}
                         :group/children
                         [{:node/type :shape/rect
                           :rect/xy [0 0] :rect/size [200 200]
                           :style/fill {:color [:color/rgb 255 0 0]}}]}]))
          outside (pixel-rgb img 5 5)
          inside  (pixel-rgb img 100 100)]
      (is (= 255 (:r outside) (:g outside) (:b outside))
          "outside clip should be white background")
      (is (= 255 (:r inside))
          "inside clip should be red"))))

;; --- gradient + opacity ---

(deftest gradient-fill-with-opacity-test
  (testing "semi-transparent gradient over white background"
    (let [img (eido/render
                (scene [{:node/type :shape/rect
                         :rect/xy [0 0] :rect/size [200 200]
                         :style/fill {:gradient/type :linear
                                      :gradient/from [0 100] :gradient/to [200 100]
                                      :gradient/stops [[0.0 [:color/rgb 255 0 0]]
                                                       [1.0 [:color/rgb 0 0 255]]]}
                         :node/opacity 0.5}]))
          left (pixel-rgb img 5 100)]
      (is (instance? BufferedImage img))
      ;; 50% red on white => r~255, g~128, b~128
      (is (> (:r left) 200) "left should have high red component")
      (is (> (:g left) 100) "white bleed-through raises green"))))

;; --- scatter + effect ---

(deftest scatter-with-shadow-effect-test
  (testing "scattered shapes with shadow render without error"
    (is (render-ok?
          (scene [{:node/type :scatter
                   :scatter/shape {:node/type :shape/circle
                                   :circle/center [0 0]
                                   :circle/radius 10
                                   :style/fill {:color [:color/rgb 200 0 0]}
                                   :node/effects [{:effect/type :shadow
                                                   :shadow/dx 3 :shadow/dy 3
                                                   :shadow/blur 3
                                                   :shadow/color [:color/rgb 0 0 0 128]}]}
                   :scatter/positions [[50 50] [100 100] [150 150]]}])))))

;; --- flow-field + styling ---

(deftest flow-field-with-opacity-test
  (testing "flow-field paths with opacity render without error"
    (is (render-ok?
          (scene [{:node/type :flow-field
                   :flow/bounds [10 10 180 180]
                   :style/stroke {:color [:color/rgb 0 0 128] :width 1.5}
                   :node/opacity 0.6}])))))

;; --- voronoi + gradient fills ---

(deftest voronoi-with-styling-test
  (testing "voronoi cells with stroke render without error"
    (is (render-ok?
          (scene [{:node/type :voronoi
                   :voronoi/points [[30 30] [100 50] [170 80]
                                    [50 150] [130 170]]
                   :voronoi/bounds [0 0 200 200]
                   :style/fill {:color [:color/rgb 220 220 240]}
                   :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])))))

;; --- l-system + stroke styling ---

(deftest lsystem-with-stroke-test
  (testing "L-system tree with stroke renders without error"
    (is (render-ok?
          (scene [{:node/type :lsystem
                   :lsystem/axiom "F"
                   :lsystem/rules {"F" "FF+[+F-F-F]-[-F+F+F]"}
                   :lsystem/iterations 3
                   :lsystem/angle 25.0
                   :lsystem/length 5.0
                   :lsystem/origin [100 190]
                   :lsystem/heading -90.0
                   :style/stroke {:color [:color/rgb 60 40 20] :width 1}}])))))

;; --- text + transform ---

(deftest text-with-transform-test
  (testing "rotated text renders without error"
    (is (render-ok?
          (scene [{:node/type :shape/text
                   :text/content "Hello"
                   :text/font {:font/family "SansSerif" :font/size 24}
                   :text/origin [100 100]
                   :style/fill {:color [:color/rgb 0 0 0]}
                   :node/transform [[:transform/rotate 45]]}])))))

;; --- stacked transforms ---

(deftest stacked-transforms-test
  (testing "translate + rotate + scale composition renders correctly"
    (let [img (eido/render
                (scene [{:node/type :shape/rect
                         :rect/xy [0 0] :rect/size [40 40]
                         :style/fill {:color [:color/rgb 255 0 0]}
                         :node/transform [[:transform/translate 100 100]
                                          [:transform/rotate 45]
                                          [:transform/scale 0.5 0.5]]}]))]
      (is (instance? BufferedImage img))
      ;; origin should be background (shape translated away)
      (is (= {:r 255 :g 255 :b 255} (pixel-rgb img 0 0))))))

;; --- group with clip + nested opacity ---

(deftest clip-with-nested-opacity-test
  (testing "clipped group with child opacity"
    (let [img (eido/render
                (scene [{:node/type :group
                         :group/clip {:node/type :shape/circle
                                      :circle/center [100 100]
                                      :circle/radius 50}
                         :group/children
                         [{:node/type :shape/rect
                           :rect/xy [0 0] :rect/size [200 200]
                           :style/fill {:color [:color/rgb 0 0 255]}
                           :node/opacity 0.5}]}]))]
      (is (instance? BufferedImage img))
      ;; outside clip should be white
      (is (= {:r 255 :g 255 :b 255} (pixel-rgb img 5 5)))
      ;; inside clip: 50% blue on white => b~128
      (let [px (pixel-rgb img 100 100)]
        (is (> (:b px) 100))
        (is (> (:g px) 100))))))

;; --- hatch fill + effect ---

(deftest hatch-fill-with-effect-test
  (testing "hatch-filled shape with glow renders without error"
    (is (render-ok?
          (scene [{:node/type :shape/rect
                   :rect/xy [30 30] :rect/size [140 140]
                   :style/fill {:fill/type :hatch
                                :hatch/angle 45
                                :hatch/spacing 6
                                :hatch/stroke-width 1
                                :hatch/color [:color/rgb 0 0 0]}
                   :node/effects [{:effect/type :glow
                                   :glow/radius 5
                                   :glow/color [:color/rgb 255 200 0]}]}])))))

;; --- circle-pack + group clip ---

(deftest circle-pack-nodes-with-clip-test
  (testing "circle-pack results clipped to shape"
    (let [circles (gen/circle-pack [10 10 180 180] {:seed 42 :max-circles 20})
          nodes   (gen/pack->nodes circles {:fill {:color [:color/rgb 200 50 50]}})
          img     (eido/render
                    (scene [{:node/type :group
                             :group/clip {:node/type :shape/circle
                                          :circle/center [100 100]
                                          :circle/radius 70}
                             :group/children nodes}]))]
      (is (instance? BufferedImage img))
      ;; corner should be white (outside clip)
      (is (= {:r 255 :g 255 :b 255} (pixel-rgb img 2 2))))))

;; --- multiple effects stacked ---

(deftest multiple-effects-test
  (testing "shadow + blur on same shape renders without error"
    (is (render-ok?
          (scene [{:node/type :shape/circle
                   :circle/center [100 100]
                   :circle/radius 40
                   :style/fill {:color [:color/rgb 255 0 0]}
                   :node/effects [{:effect/type :shadow
                                   :shadow/dx 5 :shadow/dy 5
                                   :shadow/blur 3
                                   :shadow/color [:color/rgb 0 0 0 128]}
                                  {:effect/type :blur
                                   :blur/radius 2}]}])))))

;; --- SVG output for combos ---

(deftest gradient-to-svg-test
  (testing "linear gradient renders to valid SVG"
    (let [svg (str (eido/render
                     (scene [{:node/type :shape/rect
                              :rect/xy [0 0] :rect/size [200 200]
                              :style/fill {:gradient/type :linear
                                           :gradient/from [0 0] :gradient/to [200 0]
                                           :gradient/stops [[0.0 [:color/rgb 255 0 0]]
                                                            [1.0 [:color/rgb 0 0 255]]]}}])
                     {:format :svg}))]
      (is (clojure.string/includes? svg "linearGradient"))
      (is (clojure.string/includes? svg "<stop")))))

(deftest clip-to-svg-test
  (testing "clipped group renders to SVG with clipPath"
    (let [svg (str (eido/render
                     (scene [{:node/type :group
                              :group/clip {:node/type :shape/circle
                                           :circle/center [100 100]
                                           :circle/radius 50}
                              :group/children
                              [{:node/type :shape/rect
                                :rect/xy [0 0] :rect/size [200 200]
                                :style/fill {:color [:color/rgb 255 0 0]}}]}])
                     {:format :svg}))]
      (is (clojure.string/includes? svg "clipPath")))))
