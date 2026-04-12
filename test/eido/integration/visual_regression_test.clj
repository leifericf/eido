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

;; Entries are [name scene-map] or [name scene-map {:png-only true}]
;; when the feature doesn't support SVG output.

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

   ;; text variants
   ["text-glyphs"
    (catalog-scene [{:node/type :shape/text-glyphs
                     :text/content "AB"
                     :text/font {:font/family "SansSerif" :font/size 30}
                     :text/origin [10 60]
                     :style/fill {:color [:color/rgb 0 0 0]}}])]

   ["text-on-path"
    (catalog-scene [{:node/type :shape/text-on-path
                     :text/content "Hello"
                     :text/font {:font/family "SansSerif" :font/size 16}
                     :text/path [[:move-to [10 80]] [:curve-to [30 10] [70 10] [90 80]]]
                     :style/fill {:color [:color/rgb 0 0 0]}}])]

   ;; procedural fills
   ["hatch-fill"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [10 10] :rect/size [80 80]
                     :style/fill {:fill/type :hatch
                                  :hatch/angle 45
                                  :hatch/spacing 6
                                  :hatch/stroke-width 1
                                  :hatch/color [:color/rgb 0 0 0]}}])]

   ["stipple-fill"
    (catalog-scene [{:node/type :shape/circle
                     :circle/center [50 50] :circle/radius 35
                     :style/fill {:fill/type :stipple
                                  :stipple/density 3
                                  :stipple/radius 1.0
                                  :stipple/color [:color/rgb 0 0 0]}}])]

   ;; filters
   ["filter-grayscale"
    (catalog-scene [{:node/type :shape/circle
                     :circle/center [50 50] :circle/radius 35
                     :style/fill {:color [:color/rgb 255 0 0]}
                     :group/filter :grayscale}])]

   ["filter-sepia"
    (catalog-scene [{:node/type :shape/circle
                     :circle/center [50 50] :circle/radius 35
                     :style/fill {:color [:color/rgb 0 100 200]}
                     :group/filter :sepia}])]

   ["filter-invert"
    (catalog-scene [{:node/type :shape/circle
                     :circle/center [50 50] :circle/radius 35
                     :style/fill {:color [:color/rgb 200 0 50]}
                     :group/filter :invert}])]

   ;; composite / blend mode (PNG only — SVG renderer doesn't support :buffer op)
   ["composite-multiply"
    (catalog-scene [{:node/type :group
                     :group/composite :multiply
                     :group/children
                     [{:node/type :shape/rect
                       :rect/xy [10 10] :rect/size [60 60]
                       :style/fill {:color [:color/rgb 255 0 0]}}
                      {:node/type :shape/rect
                       :rect/xy [30 30] :rect/size [60 60]
                       :style/fill {:color [:color/rgb 0 0 255]}}]}])
    {:png-only true}]

   ;; corner radius
   ["corner-radius"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [10 10] :rect/size [80 80]
                     :rect/corner-radius 15
                     :style/fill {:color [:color/rgb 100 150 200]}}])]

   ;; stroke caps and joins
   ["stroke-round-cap"
    (catalog-scene [{:node/type :shape/line
                     :line/from [20 50] :line/to [80 50]
                     :style/stroke {:color [:color/rgb 0 0 0] :width 10 :cap :round}}])]

   ["stroke-round-join"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [20 80]] [:line-to [50 20]] [:line-to [80 80]]]
                     :style/stroke {:color [:color/rgb 0 0 0] :width 8 :join :round}}])]

   ;; generative nodes
   ["scatter"
    (catalog-scene [{:node/type :scatter
                     :scatter/shape {:node/type :shape/circle
                                     :circle/center [0 0] :circle/radius 5
                                     :style/fill {:color [:color/rgb 200 50 50]}}
                     :scatter/positions [[20 20] [50 50] [80 80] [20 80] [80 20]]}])
    {:png-only true}]

   ["flow-field"
    (catalog-scene [{:node/type :flow-field
                     :flow/bounds [5 5 90 90]
                     :style/stroke {:color [:color/rgb 0 0 100] :width 1}}])]

   ["voronoi"
    (catalog-scene [{:node/type :voronoi
                     :voronoi/points [[20 20] [80 30] [50 70] [30 80] [75 75]]
                     :voronoi/bounds [0 0 100 100]
                     :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])]

   ["delaunay"
    (catalog-scene [{:node/type :delaunay
                     :delaunay/points [[20 20] [80 30] [50 70] [30 80] [75 75]]
                     :delaunay/bounds [0 0 100 100]
                     :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])]

   ["contour"
    (catalog-scene [{:node/type :contour
                     :contour/bounds [0 0 100 100]
                     :style/stroke {:color [:color/rgb 0 0 150] :width 1}}])]

   ["lsystem"
    (catalog-scene [{:node/type :lsystem
                     :lsystem/axiom "F"
                     :lsystem/rules {"F" "FF+[+F-F]-[-F+F]"}
                     :lsystem/iterations 3
                     :lsystem/angle 25.0
                     :lsystem/length 4.0
                     :lsystem/origin [50 95]
                     :lsystem/heading -90.0
                     :style/stroke {:color [:color/rgb 60 40 20] :width 1}}])]

   ;; compound nodes
   ["symmetry-radial"
    (catalog-scene [{:node/type :symmetry
                     :symmetry/type :radial
                     :symmetry/n 6
                     :symmetry/center [50 50]
                     :group/children
                     [{:node/type :shape/rect
                       :rect/xy [50 45] :rect/size [30 10]
                       :style/fill {:color [:color/rgb 200 0 0]}}]}])]

   ["path-decorated"
    (catalog-scene [{:node/type :path/decorated
                     :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                     :decorator/shape {:node/type :shape/circle
                                       :circle/center [0 0] :circle/radius 4
                                       :style/fill {:color [:color/rgb 200 0 0]}}
                     :decorator/spacing 15}])
    {:png-only true}]

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

   ;; paint engine
   ["paint-ink-stroke"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                     :paint/brush :ink
                     :paint/color [:color/rgb 0 0 0]
                     :paint/radius 5.0}])
    {:png-only true}]

   ["paint-chalk-pressure"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 50]]
                                    [:curve-to [30 20] [70 80] [90 50]]]
                     :paint/brush :chalk
                     :paint/color [:color/rgb 80 60 40]
                     :paint/radius 8.0
                     :paint/pressure [[0.0 0.2] [0.5 1.0] [1.0 0.1]]}])
    {:png-only true}]

   ["paint-standalone-surface"
    (catalog-scene [{:node/type :paint/surface
                     :paint/strokes
                     [{:paint/brush :marker
                       :paint/color [:color/rgb 200 50 50]
                       :paint/radius 6.0
                       :paint/points [[10 50 0.8 0 0 0] [90 50 0.5 0 0 0]]}]}])
    {:png-only true}]

   ["paint-group-multi-stroke"
    (catalog-scene [{:node/type :group
                     :paint/surface {}
                     :group/children
                     [{:node/type :shape/path
                       :path/commands [[:move-to [10 30]] [:line-to [90 30]]]
                       :paint/brush :ink
                       :paint/color [:color/rgb 0 0 0]
                       :paint/radius 4.0}
                      {:node/type :shape/path
                       :path/commands [[:move-to [10 70]] [:line-to [90 70]]]
                       :paint/brush :chalk
                       :paint/color [:color/rgb 100 50 20]
                       :paint/radius 6.0}]}])
    {:png-only true}]

   ["paint-with-grain"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                     :paint/brush {:brush/type :brush/dab
                                   :brush/tip {:tip/shape :ellipse :tip/hardness 0.5}
                                   :brush/grain {:grain/type :fbm :grain/scale 0.1
                                                 :grain/contrast 0.5}
                                   :brush/paint {:paint/opacity 0.15 :paint/spacing 0.04}}
                     :paint/color [:color/rgb 60 40 25]
                     :paint/radius 10.0}])
    {:png-only true}]

   ["paint-with-substrate"
    (catalog-scene [{:node/type :paint/surface
                     :paint/surface {:substrate/tooth 0.4 :substrate/scale 0.15}
                     :paint/strokes
                     [{:paint/brush :chalk
                       :paint/color [:color/rgb 80 60 40]
                       :paint/radius 12.0
                       :paint/points [[10 50 0.8 0 0 0] [90 50 0.6 0 0 0]]}]}])
    {:png-only true}]

   ["paint-bristle"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                     :paint/brush {:brush/type :brush/dab
                                   :brush/tip {:tip/shape :circle :tip/hardness 0.5}
                                   :brush/bristles {:bristle/count 5 :bristle/spread 0.6}
                                   :brush/paint {:paint/opacity 0.3 :paint/spacing 0.03}}
                     :paint/color [:color/rgb 30 80 40]
                     :paint/radius 12.0}])
    {:png-only true}]

   ["paint-elliptical-tip"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 50]]
                                    [:curve-to [40 20] [60 80] [90 50]]]
                     :paint/brush {:brush/type :brush/dab
                                   :brush/tip {:tip/shape :ellipse
                                               :tip/hardness 0.8
                                               :tip/aspect 3.0}
                                   :brush/paint {:paint/opacity 0.7 :paint/spacing 0.03}}
                     :paint/color [:color/rgb 15 10 5]
                     :paint/radius 6.0}])
    {:png-only true}]

   ["paint-flow-field"
    (catalog-scene [{:node/type :group
                     :paint/surface {}
                     :group/children
                     [{:node/type :flow-field
                       :flow/bounds [5 5 90 90]
                       :flow/opts {:density 30 :steps 15 :seed 42}
                       :paint/brush :ink
                       :paint/color [:color/rgb 0 0 0]
                       :paint/radius 1.5}]}])
    {:png-only true}]

   ["paint-symmetry-radial"
    (catalog-scene [{:node/type :group
                     :paint/surface {}
                     :group/children
                     [{:node/type :symmetry
                       :symmetry/type :radial
                       :symmetry/n 4
                       :symmetry/center [50 50]
                       :group/children
                       [{:node/type :shape/path
                         :path/commands [[:move-to [50 50]] [:line-to [90 50]]]
                         :paint/brush :ink
                         :paint/color [:color/rgb 0 0 0]
                         :paint/radius 2.0}]}]}])
    {:png-only true}]

   ["paint-style-fill-fallback"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                     :style/fill [:color/rgb 0 0 200]
                     :paint/brush :ink
                     :paint/radius 5.0}])
    {:png-only true}]

   ["paint-with-translate"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 10]] [:line-to [90 10]]]
                     :paint/brush :ink
                     :paint/color [:color/rgb 0 0 0]
                     :paint/radius 4.0
                     :node/transform [[:transform/translate 0 40]]}])
    {:png-only true}]

   ["paint-jittered-chalk"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                     :paint/brush :chalk
                     :paint/color [:color/rgb 60 40 30]
                     :paint/radius 10.0
                     :paint/seed 42}])
    {:png-only true}]

   ["paint-glazed-blend"
    (catalog-scene [{:node/type :group
                     :paint/surface {}
                     :group/children
                     [{:node/type :shape/path
                       :path/commands [[:move-to [10 40]] [:line-to [90 40]]]
                       :paint/brush {:brush/type :brush/dab
                                     :brush/tip {:tip/shape :rect :tip/hardness 0.92 :tip/aspect 2.0}
                                     :brush/paint {:paint/opacity 0.35 :paint/spacing 0.03
                                                   :paint/blend :glazed}}
                       :paint/color [:color/rgb 220 180 50]
                       :paint/radius 8.0}
                      {:node/type :shape/path
                       :path/commands [[:move-to [10 60]] [:line-to [90 60]]]
                       :paint/brush {:brush/type :brush/dab
                                     :brush/tip {:tip/shape :rect :tip/hardness 0.92 :tip/aspect 2.0}
                                     :brush/paint {:paint/opacity 0.35 :paint/spacing 0.03
                                                   :paint/blend :glazed}}
                       :paint/color [:color/rgb 50 180 220]
                       :paint/radius 8.0}]}])
    {:png-only true}]

   ["paint-spatter"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                     :paint/brush {:brush/type :brush/dab
                                   :brush/tip {:tip/shape :ellipse :tip/hardness 0.7}
                                   :brush/paint {:paint/opacity 0.5 :paint/spacing 0.05}
                                   :brush/spatter {:spatter/threshold 0.2
                                                   :spatter/density 0.4
                                                   :spatter/spread 3.0
                                                   :spatter/mode :scatter}}
                     :paint/color [:color/rgb 180 30 30]
                     :paint/radius 8.0
                     :paint/seed 42}])
    {:png-only true}]

   ["paint-impasto"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                     :paint/brush {:brush/type :brush/dab
                                   :brush/tip {:tip/shape :ellipse :tip/hardness 0.6}
                                   :brush/paint {:paint/opacity 0.7 :paint/spacing 0.06}
                                   :brush/impasto {:impasto/height 0.6}
                                   :brush/jitter {:jitter/position 0.08 :jitter/opacity 0.15}}
                     :paint/color [:color/rgb 200 60 30]
                     :paint/radius 15.0
                     :paint/seed 42}])
    {:png-only true}]

   ["paint-local-grain"
    (catalog-scene [{:node/type :shape/path
                     :path/commands [[:move-to [10 50]] [:line-to [90 50]]]
                     :paint/brush {:brush/type :brush/dab
                                   :brush/tip {:tip/shape :ellipse :tip/hardness 0.5}
                                   :brush/paint {:paint/opacity 0.15 :paint/spacing 0.05}
                                   :brush/grain {:grain/type :fiber :grain/scale 0.15
                                                 :grain/mode :local :grain/stretch 3.0}
                                   :brush/jitter {:jitter/position 0.1 :jitter/opacity 0.2}}
                     :paint/color [:color/rgb 80 50 30]
                     :paint/radius 12.0
                     :paint/seed 42}])
    {:png-only true}]

   ["gradient-with-opacity"
    (catalog-scene [{:node/type :shape/rect
                     :rect/xy [0 0] :rect/size [100 100]
                     :style/fill {:gradient/type :radial
                                  :gradient/center [50 50] :gradient/radius 50
                                  :gradient/stops [[0.0 [:color/rgb 0 0 0]]
                                                   [1.0 [:color/rgb 255 255 255]]]}
                     :node/opacity 0.6}])]])

;; --- determinism test ---

(defn- entry-name  [entry] (nth entry 0))
(defn- entry-scene [entry] (nth entry 1))
(defn- entry-opts  [entry] (nth entry 2 {}))

(deftest visual-catalog-determinism-test
  (testing "every catalog scene renders identically on consecutive runs"
    (doseq [entry feature-catalog]
      (testing (entry-name entry)
        (let [img1 (eido/render (entry-scene entry))
              img2 (eido/render (entry-scene entry))]
          (is (instance? BufferedImage img1)
              (str (entry-name entry) " should produce an image"))
          (is (images-identical? img1 img2)
              (str (entry-name entry) " should be deterministic")))))))

;; --- smoke test: all features render ---

(deftest visual-catalog-smoke-test
  (testing "every catalog scene renders to expected dimensions"
    (doseq [entry feature-catalog]
      (testing (entry-name entry)
        (let [img (eido/render (entry-scene entry))]
          (is (= 100 (.getWidth img)) (str (entry-name entry) " width"))
          (is (= 100 (.getHeight img)) (str (entry-name entry) " height")))))))

;; --- SVG catalog determinism ---

(deftest visual-catalog-svg-determinism-test
  (testing "every catalog scene produces identical SVG on consecutive runs"
    (doseq [entry feature-catalog
            :when (not (:png-only (entry-opts entry)))]
      (testing (entry-name entry)
        (let [svg1 (str (eido/render (entry-scene entry) {:format :svg}))
              svg2 (str (eido/render (entry-scene entry) {:format :svg}))]
          (is (= svg1 svg2)
              (str (entry-name entry) " SVG should be deterministic")))))))
