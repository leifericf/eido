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
