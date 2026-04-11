(ns eido.integration.visual-regression-test
  "Visual regression tests: a catalog of small scenes covering every
  visual feature, rendered twice and compared pixel-for-pixel. Any
  non-determinism in the rendering pipeline causes a test failure.

  Scenes are defined in code — no reference images on disk."
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.core :as eido])
  (:import
    [java.awt.image BufferedImage]))

;; --- pixel extraction ---

(defn- pixel-array
  "Returns an int array of all ARGB pixels for the image."
  ^ints [^BufferedImage img]
  (let [w (.getWidth img) h (.getHeight img)
        buf (int-array (* w h))]
    (.getRGB img 0 0 w h buf 0 w)
    buf))

(defn- images-identical?
  "Returns true if two BufferedImages have identical pixel data."
  [^BufferedImage a ^BufferedImage b]
  (and (= (.getWidth a) (.getWidth b))
       (= (.getHeight a) (.getHeight b))
       (java.util.Arrays/equals (pixel-array a) (pixel-array b))))

;; --- feature catalog ---
;; Each entry: [name scene-map]
;; Scenes are small (100x100) for speed.

(def ^:private sz [100 100])

(defn- catalog-scene [nodes]
  {:image/size sz
   :image/background [:color/rgb 255 255 255]
   :image/nodes nodes})

(def ^:private feature-catalog
  [;; shapes
   ["rect"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [10 10] :rect/size [80 80]
                     :style/fill {:color [:color/rgb 200 50 50]}}])]

   ["circle"
    (catalog-scene [{:node/type :shape/circle
                     :circle/center [50 50] :circle/radius 35
                     :style/fill {:color [:color/rgb 50 50 200]}}])]

   ["ellipse"
    (catalog-scene [{:node/type :shape/ellipse
                     :ellipse/center [50 50] :ellipse/rx 40 :ellipse/ry 25
                     :style/fill {:color [:color/rgb 50 180 50]}}])]

   ["arc"
    (catalog-scene [{:node/type :shape/arc
                     :arc/center [50 50] :arc/rx 35 :arc/ry 35
                     :arc/start 0 :arc/extent 270
                     :style/fill {:color [:color/rgb 200 150 50]}}])]

   ["line"
    (catalog-scene [{:node/type :shape/line
                     :line/from [10 10] :line/to [90 90]
                     :style/stroke {:color [:color/rgb 0 0 0] :width 3}}])]

   ["path"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 80]]
                                    [:line-to [50 10]]
                                    [:line-to [90 80]]
                                    [:close]]
                     :style/fill {:color [:color/rgb 180 50 180]}}])]

   ;; fills
   ["linear-gradient"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [0 0] :rect/size [100 100]
                     :style/fill {:gradient/type :linear
                                  :gradient/from [0 50] :gradient/to [100 50]
                                  :gradient/stops [[0.0 [:color/rgb 255 0 0]]
                                                   [1.0 [:color/rgb 0 0 255]]]}}])]

   ["radial-gradient"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [0 0] :rect/size [100 100]
                     :style/fill {:gradient/type :radial
                                  :gradient/center [50 50] :gradient/radius 50
                                  :gradient/stops [[0.0 [:color/rgb 255 255 255]]
                                                   [1.0 [:color/rgb 0 0 0]]]}}])]

   ;; strokes
   ["solid-stroke"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [20 20] :rect/size [60 60]
                     :style/stroke {:color [:color/rgb 0 0 0] :width 3}}])]

   ["dashed-stroke"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [20 20] :rect/size [60 60]
                     :style/stroke {:color [:color/rgb 0 0 0] :width 2
                                    :dash [8 4]}}])]

   ;; effects
   ["shadow"
    (catalog-scene [{:node/type :shape/circle
                     :circle/center [45 45] :circle/radius 25
                     :style/fill {:color [:color/rgb 200 0 0]}
                     :node/effects [{:effect/type :shadow
                                     :shadow/dx 5 :shadow/dy 5
                                     :shadow/blur 4
                                     :shadow/color [:color/rgb 0 0 0 128]}]}])]

   ["blur"
    (catalog-scene [{:node/type :shape/circle
                     :circle/center [50 50] :circle/radius 25
                     :style/fill {:color [:color/rgb 0 0 200]}
                     :node/effects [{:effect/type :blur :blur/radius 4}]}])]

   ["glow"
    (catalog-scene [{:node/type :shape/circle
                     :circle/center [50 50] :circle/radius 20
                     :style/fill {:color [:color/rgb 200 200 0]}
                     :node/effects [{:effect/type :glow :glow/radius 6
                                     :glow/color [:color/rgb 255 200 0]}]}])]

   ;; transforms
   ["translate"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [0 0] :rect/size [30 30]
                     :style/fill {:color [:color/rgb 0 150 0]}
                     :node/transform [[:transform/translate 35 35]]}])]

   ["rotate"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [25 25] :rect/size [50 50]
                     :style/fill {:color [:color/rgb 150 0 150]}
                     :node/transform [[:transform/rotate 30]]}])]

   ["scale"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [25 25] :rect/size [50 50]
                     :style/fill {:color [:color/rgb 0 0 150]}
                     :node/transform [[:transform/scale 0.5 0.5]]}])]

   ["combined-transform"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [0 0] :rect/size [40 40]
                     :style/fill {:color [:color/rgb 100 50 0]}
                     :node/transform [[:transform/translate 50 50]
                                      [:transform/rotate 45]
                                      [:transform/scale 0.7 0.7]]}])]

   ;; opacity
   ["opacity-25"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [10 10] :rect/size [80 80]
                     :style/fill {:color [:color/rgb 255 0 0]}
                     :node/opacity 0.25}])]

   ["opacity-50"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [10 10] :rect/size [80 80]
                     :style/fill {:color [:color/rgb 255 0 0]}
                     :node/opacity 0.50}])]

   ["opacity-75"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [10 10] :rect/size [80 80]
                     :style/fill {:color [:color/rgb 255 0 0]}
                     :node/opacity 0.75}])]

   ["nested-group-opacity"
    (catalog-scene [{:node/type :group
                     :node/opacity 0.5
                     :group/children
                     [{:node/type :shape/rect
                       :rect/xy [10 10] :rect/size [80 80]
                       :style/fill {:color [:color/rgb 0 0 255]}
                       :node/opacity 0.5}]}])]

   ;; clips
   ["circle-clip"
    (catalog-scene [{:node/type :group
                     :group/clip {:node/type :shape/circle
                                  :circle/center [50 50] :circle/radius 35}
                     :group/children
                     [{:node/type :shape/rect
                       :rect/xy [0 0] :rect/size [100 100]
                       :style/fill {:color [:color/rgb 255 0 0]}}]}])]

   ["rect-clip"
    (catalog-scene [{:node/type :group
                     :group/clip {:node/type :shape/rect
                                  :rect/xy [20 20] :rect/size [60 60]}
                     :group/children
                     [{:node/type :shape/circle
                       :circle/center [50 50] :circle/radius 45
                       :style/fill {:color [:color/rgb 0 0 255]}}]}])]

   ;; text
   ["text"
    (catalog-scene [{:node/type :shape/text
                     :text/content "Eido"
                     :text/font {:font/family "SansSerif" :font/size 24}
                     :text/origin [10 60]
                     :style/fill {:color [:color/rgb 0 0 0]}}])]

   ;; feature interactions
   ["gradient-with-clip"
    (catalog-scene [{:node/type :group
                     :group/clip {:node/type :shape/circle
                                  :circle/center [50 50] :circle/radius 40}
                     :group/children
                     [{:node/type :shape/rect
                       :rect/xy [0 0] :rect/size [100 100]
                       :style/fill {:gradient/type :linear
                                    :gradient/from [0 50] :gradient/to [100 50]
                                    :gradient/stops [[0.0 [:color/rgb 255 0 0]]
                                                     [1.0 [:color/rgb 0 0 255]]]}}]}])]

   ["gradient-with-opacity"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [0 0] :rect/size [100 100]
                     :style/fill {:gradient/type :radial
                                  :gradient/center [50 50] :gradient/radius 50
                                  :gradient/stops [[0.0 [:color/rgb 0 0 0]]
                                                   [1.0 [:color/rgb 255 255 255]]]}
                     :node/opacity 0.6}])]])

;; --- determinism test ---

(deftest visual-catalog-determinism-test
  (testing "every catalog scene renders identically on consecutive runs"
    (doseq [[feature-name scene] feature-catalog]
      (testing feature-name
        (let [img1 (eido/render scene)
              img2 (eido/render scene)]
          (is (instance? BufferedImage img1)
              (str feature-name " should produce an image"))
          (is (images-identical? img1 img2)
              (str feature-name " should be deterministic")))))))

;; --- smoke test: all features render ---

(deftest visual-catalog-smoke-test
  (testing "every catalog scene renders to expected dimensions"
    (doseq [[feature-name scene] feature-catalog]
      (testing feature-name
        (let [img (eido/render scene)]
          (is (= 100 (.getWidth img)) (str feature-name " width"))
          (is (= 100 (.getHeight img)) (str feature-name " height")))))))

;; --- SVG catalog determinism ---

(deftest visual-catalog-svg-determinism-test
  (testing "every catalog scene produces identical SVG on consecutive runs"
    (doseq [[feature-name scene] feature-catalog]
      (testing feature-name
        (let [svg1 (str (eido/render scene {:format :svg}))
              svg2 (str (eido/render scene {:format :svg}))]
          (is (= svg1 svg2)
              (str feature-name " SVG should be deterministic")))))))
