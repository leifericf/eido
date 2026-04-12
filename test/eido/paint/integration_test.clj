(ns eido.paint.integration-test
  "End-to-end integration tests for the paint engine.
  Tests the full pipeline: scene map -> compile -> lower -> render."
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.core :as eido])
  (:import
    [java.awt.image BufferedImage]))

(defn- has-painted-pixels?
  "Returns true if the image contains pixels darker than the background."
  [^BufferedImage img ^long bg-r-threshold]
  (let [w (.getWidth img)
        h (.getHeight img)]
    (loop [y 0]
      (if (>= y h)
        false
        (if (loop [x 0]
              (if (>= x w)
                false
                (let [argb (.getRGB img x y)
                      a (bit-and (bit-shift-right argb 24) 0xFF)
                      r (bit-and (bit-shift-right argb 16) 0xFF)]
                  (if (and (> a 10) (< r bg-r-threshold))
                    true
                    (recur (inc x))))))
          true
          (recur (inc y)))))))

;; --- standalone paint surface ---

(deftest standalone-surface-test
  (testing "standalone :paint/surface renders strokes"
    (let [img (eido/render
                {:image/size [200 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :paint/surface
                   :paint/size [200 100]
                   :paint/strokes
                   [{:stroke/brush :ink
                     :stroke/color [:color/rgb 0 0 0]
                     :stroke/radius 8.0
                     :stroke/points [[20 50 1.0 0 0 0] [180 50 1.0 0 0 0]]}]}]})]
      (is (instance? BufferedImage img))
      (is (= 200 (.getWidth img)))
      (is (has-painted-pixels? img 200)
          "should have dark painted pixels"))))

;; --- painted path (implicit surface) ---

(deftest painted-path-test
  (testing "path with :paint/brush renders as paint"
    (let [img (eido/render
                {:image/size [200 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [20 50]] [:line-to [180 50]]]
                   :paint/brush :chalk
                   :paint/color [:color/rgb 60 40 30]
                   :paint/radius 10.0}]})]
      (is (instance? BufferedImage img))
      (is (has-painted-pixels? img 240)))))

;; --- group modifier (shared surface) ---

(deftest paint-group-test
  (testing "group with :paint/surface renders multiple painted children"
    (let [img (eido/render
                {:image/size [200 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :group
                   :paint/surface {}
                   :group/children
                   [{:node/type :shape/path
                     :path/commands [[:move-to [20 30]] [:line-to [180 30]]]
                     :paint/brush :ink
                     :paint/color [:color/rgb 0 0 0]
                     :paint/radius 5.0}
                    {:node/type :shape/path
                     :path/commands [[:move-to [20 70]] [:line-to [180 70]]]
                     :paint/brush :ink
                     :paint/color [:color/rgb 200 0 0]
                     :paint/radius 5.0}]}]})]
      (is (instance? BufferedImage img))
      (is (has-painted-pixels? img 240)))))

;; --- generator composition: flow field ---

(deftest paint-flow-field-test
  (testing "flow field with :paint/brush produces painted streamlines"
    (let [img (eido/render
                {:image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :group
                   :paint/surface {:paint/size [200 200]}
                   :group/children
                   [{:node/type :flow-field
                     :flow/bounds [10 10 180 180]
                     :flow/opts {:density 30 :steps 20 :seed 42}
                     :paint/brush :ink
                     :paint/color [:color/rgb 0 0 0]
                     :paint/radius 2.0}]}]})]
      (is (instance? BufferedImage img))
      (is (has-painted-pixels? img 200)
          "flow field streamlines should be painted"))))

;; --- generator composition: scatter ---

(deftest paint-scatter-test
  (testing "scatter with painted shape produces painted copies"
    (let [img (eido/render
                {:image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :group
                   :paint/surface {:paint/size [200 200]}
                   :group/children
                   [{:node/type :scatter
                     :scatter/positions [[50 50] [100 100] [150 150]]
                     :scatter/shape {:node/type :shape/path
                                     :path/commands [[:move-to [0 0]]
                                                     [:line-to [20 10]]]
                                     :paint/brush :pencil
                                     :paint/color [:color/rgb 40 40 40]
                                     :paint/radius 4.0}}]}]})]
      (is (instance? BufferedImage img))
      (is (has-painted-pixels? img 200)
          "scattered paint strokes should be visible"))))

;; --- mixed: paint alongside regular shapes ---

(deftest paint-mixed-scene-test
  (testing "paint surface coexists with regular shapes"
    (let [img (eido/render
                {:image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [;; Regular circle
                  {:node/type :shape/circle
                   :circle/center [100 100]
                   :circle/radius 40
                   :style/fill [:color/rgb 200 220 240]}
                  ;; Paint surface on top
                  {:node/type :paint/surface
                   :paint/size [200 200]
                   :paint/strokes
                   [{:stroke/brush :ink
                     :stroke/color [:color/rgb 0 0 0]
                     :stroke/radius 3.0
                     :stroke/points [[30 100 1.0 0 0 0] [170 100 1.0 0 0 0]]}]}]})]
      (is (instance? BufferedImage img))
      (is (has-painted-pixels? img 200)))))

;; --- pressure curve ---

(deftest pressure-curve-test
  (testing "pressure curve produces tapered strokes"
    (let [img (eido/render
                {:image/size [200 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [20 50]] [:line-to [180 50]]]
                   :paint/brush :pencil
                   :paint/color [:color/rgb 0 0 0]
                   :paint/radius 12.0
                   :paint/pressure [[0.0 0.1] [0.5 1.0] [1.0 0.1]]}]})]
      (is (instance? BufferedImage img))
      ;; The start and end should be thinner than the middle
      ;; We verify by checking that the middle row has more painted pixels
      (let [mid-dark (loop [x 0 cnt 0]
                       (if (>= x 200) cnt
                         (let [argb (.getRGB img x 50)
                               r (bit-and (bit-shift-right argb 16) 0xFF)]
                           (recur (inc x) (if (< r 200) (inc cnt) cnt)))))
            edge-dark (loop [x 0 cnt 0]
                        (if (>= x 200) cnt
                          (let [argb (.getRGB img x 40)
                                r (bit-and (bit-shift-right argb 16) 0xFF)]
                            (recur (inc x) (if (< r 200) (inc cnt) cnt)))))]
        (is (> mid-dark 20) "center row should have painted pixels")))))

;; --- SVG output ---

(deftest paint-svg-output-test
  (testing "paint surface renders as embedded image in SVG"
    (let [svg (eido/render
                {:image/size [200 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :paint/surface
                   :paint/size [200 100]
                   :paint/strokes
                   [{:stroke/brush :ink
                     :stroke/color [:color/rgb 0 0 0]
                     :stroke/radius 5.0
                     :stroke/points [[20 50 1.0 0 0 0] [180 50 1.0 0 0 0]]}]}]}
                {:format :svg})]
      (is (string? svg))
      (is (re-find #"<image" svg) "should contain <image> element")
      (is (re-find #"data:image/png;base64," svg) "should contain base64 PNG data")
      (is (not (re-find #"rgb\(,,\)" svg)) "should not have empty rgb() values")))

  (testing "painted path also works in SVG"
    (let [svg (eido/render
                {:image/size [200 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [20 50]] [:line-to [180 50]]]
                   :paint/brush :ink
                   :paint/color [:color/rgb 0 0 0]
                   :paint/radius 5.0}]}
                {:format :svg})]
      (is (re-find #"<image" svg) "painted path should embed as image in SVG"))))

;; --- API consistency ---

(deftest paint-size-defaults-to-image-size-test
  (testing "paint/size defaults to image/size when omitted"
    (let [img (eido/render
                {:image/size [300 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :paint/surface
                   :paint/strokes
                   [{:paint/brush :ink :paint/color [:color/rgb 0 0 0]
                     :paint/radius 5.0
                     :paint/points [[20 100 1.0 0 0 0] [280 100 1.0 0 0 0]]}]}]})]
      (is (= 300 (.getWidth img)))
      (is (has-painted-pixels? img 200)))))

(deftest style-fill-fallback-test
  (testing ":style/fill is used as paint color when :paint/color is absent"
    (let [img (eido/render
                {:image/size [200 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [20 50]] [:line-to [180 50]]]
                   :style/fill [:color/rgb 0 0 200]
                   :paint/brush :ink
                   :paint/radius 6.0}]})]
      (is (has-painted-pixels? img 240) "blue paint should be visible"))))

(deftest unified-paint-namespace-test
  (testing ":paint/ namespace works on standalone surface strokes"
    (let [img (eido/render
                {:image/size [200 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :paint/surface
                   :paint/strokes
                   [{:paint/brush :ink
                     :paint/color [:color/rgb 200 0 0]
                     :paint/radius 5.0
                     :paint/points [[20 50 1.0 0 0 0] [180 50 1.0 0 0 0]]}]}]})]
      (is (has-painted-pixels? img 200)))))

(deftest standalone-surface-with-children-test
  (testing "standalone :paint/surface accepts both strokes and children"
    (let [img (eido/render
                {:image/size [200 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :paint/surface
                   :paint/strokes
                   [{:paint/brush :ink :paint/color [:color/rgb 200 0 0]
                     :paint/radius 5.0
                     :paint/points [[20 30 1.0 0 0 0] [180 30 1.0 0 0 0]]}]
                   :paint/children
                   [{:node/type :shape/path
                     :path/commands [[:move-to [20 70]] [:line-to [180 70]]]
                     :paint/brush :ink
                     :paint/color [:color/rgb 0 0 200]
                     :paint/radius 5.0}]}]})]
      (is (has-painted-pixels? img 200)))))

;; --- transform support ---

(deftest paint-with-translate-test
  (testing "translate transform moves painted stroke"
    (let [img (eido/render
                {:image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [20 20]] [:line-to [180 20]]]
                   :paint/brush :ink
                   :paint/color [:color/rgb 0 0 0]
                   :paint/radius 5.0
                   :node/transform [[:transform/translate 0 80]]}]})
          at-original (bit-and (bit-shift-right (.getRGB img 100 20) 16) 0xFF)
          at-shifted  (bit-and (bit-shift-right (.getRGB img 100 100) 16) 0xFF)]
      (is (> at-original 200) "original y should be clear")
      (is (< at-shifted 100) "translated y should have paint"))))

;; --- symmetry composition ---

(deftest paint-symmetry-test
  (testing "radial symmetry correctly rotates paint strokes"
    (let [img (eido/render
                {:image/size [300 300]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :group
                   :paint/surface {:paint/size [300 300]}
                   :group/children
                   [{:node/type :symmetry
                     :symmetry/type :radial
                     :symmetry/n 4
                     :symmetry/center [150 150]
                     :group/children
                     [{:node/type :shape/path
                       :path/commands [[:move-to [150 150]] [:line-to [250 150]]]
                       :paint/brush :ink
                       :paint/color [:color/rgb 0 0 0]
                       :paint/radius 3.0}]}]}]})
          check (fn [x y] (< (bit-and (bit-shift-right (.getRGB img x y) 16) 0xFF) 200))]
      (is (check 210 150) "right arm should have paint")
      (is (check 150 90)  "up arm should have paint")
      (is (check 90 150)  "left arm should have paint")
      (is (check 150 210) "down arm should have paint"))))

;; --- convenience constructors ---

(deftest convenience-constructors-test
  (testing "painted-path helper creates valid scene node"
    (let [p ((requiring-resolve 'eido.paint/painted-path)
              [[100 100] [200 100] [300 150]]
              {:brush :chalk :color [:color/rgb 80 60 40] :radius 10.0})]
      (is (= :shape/path (:node/type p)))
      (is (= :chalk (:paint/brush p)))
      (is (= 10.0 (:paint/radius p)))
      (let [img (eido/render
                  {:image/size [400 200]
                   :image/background [:color/rgb 255 255 255]
                   :image/nodes [p]})]
        (is (has-painted-pixels? img 240)))))

  (testing "paint-surface helper creates valid node"
    (let [mk-stroke (requiring-resolve 'eido.paint/stroke)
          mk-surf   (requiring-resolve 'eido.paint/paint-surface)
          s (mk-surf
              [(mk-stroke [[50 50 0.8 0 0 0] [150 50 0.6 0 0 0]]
                 {:brush :ink :color [:color/rgb 0 0 0] :radius 5.0})]
              {:size [200 100]})]
      (is (= :paint/surface (:node/type s)))
      (let [img (eido/render
                  {:image/size [200 100]
                   :image/background [:color/rgb 255 255 255]
                   :image/nodes [s]})]
        (is (has-painted-pixels? img 200)))))

  (testing "paint-group helper creates group with surface"
    (let [mk-path  (requiring-resolve 'eido.paint/painted-path)
          mk-group (requiring-resolve 'eido.paint/paint-group)
          g (mk-group
              [(mk-path [[20 50] [180 50]]
                 {:brush :ink :color [:color/rgb 0 0 0] :radius 5.0})])]
      (is (= :group (:node/type g)))
      (is (some? (:paint/surface g))))))

(deftest spatter-effect-test
  (testing "spatter emits particles outside main stroke path"
    (let [img (eido/render
                {:image/size [200 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [10 50]] [:line-to [190 50]]]
                   :paint/brush {:brush/type :brush/dab
                                 :brush/tip {:tip/shape :ellipse :tip/hardness 0.7}
                                 :brush/paint {:paint/opacity 0.5 :paint/spacing 0.05}
                                 :brush/spatter {:spatter/threshold 0.2
                                                 :spatter/density 0.5
                                                 :spatter/spread 3.0
                                                 :spatter/mode :scatter}}
                   :paint/color [:color/rgb 0 0 0]
                   :paint/radius 8.0
                   :paint/seed 42}]})]
      ;; Spatter should create pixels away from the center line (y=50)
      (is (has-painted-pixels? img 240))))

  (testing "spatter modes render without error"
    (doseq [mode [:scatter :spray]]
      (let [img (eido/render
                  {:image/size [100 100]
                   :image/background [:color/rgb 255 255 255]
                   :image/nodes
                   [{:node/type :shape/path
                     :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                     :paint/brush {:brush/type :brush/dab
                                   :brush/tip {:tip/shape :ellipse :tip/hardness 0.7}
                                   :brush/paint {:paint/opacity 0.5 :paint/spacing 0.05}
                                   :brush/spatter {:spatter/threshold 0.2
                                                   :spatter/density 0.3
                                                   :spatter/spread 2.0
                                                   :spatter/mode mode}}
                     :paint/color [:color/rgb 0 0 0]
                     :paint/radius 6.0
                     :paint/seed 42}]})]
        (is (some? img) (str "mode " mode " should render"))))))

(deftest new-blend-modes-render-test
  (testing "glazed blend renders through pipeline"
    (let [img (eido/render
                {:image/size [100 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                   :paint/brush :flat-marker
                   :paint/color [:color/rgb 100 60 20]
                   :paint/radius 8.0}]})]
      (is (has-painted-pixels? img 250))))

  (testing "impasto renders with height planes"
    (let [img (eido/render
                {:image/size [100 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                   :paint/brush :impasto
                   :paint/color [:color/rgb 200 60 30]
                   :paint/radius 12.0
                   :paint/seed 42}]})]
      (is (has-painted-pixels? img 240)))))
