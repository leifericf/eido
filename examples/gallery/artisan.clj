(ns gallery.artisan
  "Artisan gallery — handcrafted techniques and underrepresented features."
  {:category "Artisan"}
  (:require
    [eido.animate :as anim]
    [eido.gen.contour :as contour]
    [eido.gen.flow :as flow]
    [eido.gen.lsystem :as lsystem]
    [eido.path.morph :as morph]
    [eido.gen.noise :as noise]
    [eido.color.palette :as palette]
    [eido.path :as path]
    [eido.gen.scatter :as scatter]
    [eido.scene :as scene]
    [eido.scene3d :as s3d]
    [eido.vary :as vary]
    [eido.gen.voronoi :as voronoi]))

;; --- 1. Woodcut Landscape ---

(defn ^{:example {:output "artisan-woodcut-landscape.png"
                  :title  "Woodcut Landscape"
                  :desc   "Cross-hatched mountains with brush strokes on cream."
                  :tags   ["hatching" "distortion" "brush-strokes"]}}
  woodcut-landscape []
  (let [cream [:color/rgb 245 235 215]
        ink   [:color/rgb 25 20 15]
        sky-hatches
        (for [i (range 20)]
          (let [y (+ 20 (* i 12))]
            {:node/type :shape/path
             :path/commands [[:move-to [0.0 (double y)]]
                             [:line-to [600.0 (double y)]]]
             :style/stroke {:color ink :width 0.4}
             :node/opacity 0.3}))
        mountain
        {:node/type :shape/path
         :path/commands [[:move-to [0.0 280.0]]
                         [:line-to [80.0 200.0]]
                         [:line-to [150.0 130.0]]
                         [:line-to [220.0 100.0]]
                         [:line-to [280.0 140.0]]
                         [:line-to [340.0 90.0]]
                         [:line-to [400.0 120.0]]
                         [:line-to [460.0 160.0]]
                         [:line-to [520.0 200.0]]
                         [:line-to [600.0 250.0]]
                         [:line-to [600.0 400.0]]
                         [:line-to [0.0 400.0]]
                         [:close]]
         :style/fill {:fill/type :hatch
                      :hatch/layers [{:angle 45 :spacing 4}
                                     {:angle -30 :spacing 6}
                                     {:angle 0 :spacing 10}]
                      :hatch/stroke-width 0.8
                      :hatch/color ink}
         :style/stroke {:color ink :width 2.5}
         :node/transform [[:transform/distort {:type :roughen :amount 3 :seed 42}]]}
        foreground
        {:node/type :shape/path
         :path/commands [[:move-to [0.0 340.0]]
                         [:line-to [200.0 320.0]]
                         [:line-to [400.0 330.0]]
                         [:line-to [600.0 310.0]]
                         [:line-to [600.0 400.0]]
                         [:line-to [0.0 400.0]]
                         [:close]]
         :style/fill {:fill/type :hatch
                      :hatch/layers [{:angle 0 :spacing 3}
                                     {:angle 90 :spacing 3}]
                      :hatch/stroke-width 1.0
                      :hatch/color ink}
         :style/stroke {:color ink :width 3}
         :node/transform [[:transform/distort {:type :roughen :amount 2 :seed 99}]]}
        river
        {:node/type :shape/path
         :path/commands [[:move-to [0.0 350.0]]
                         [:curve-to [150.0 330.0] [300.0 360.0] [450.0 340.0]]
                         [:curve-to [520.0 330.0] [570.0 345.0] [600.0 335.0]]]
         :stroke/profile :brush
         :style/stroke {:color ink :width 6}}]
    {:image/size [600 400]
     :image/background cream
     :image/nodes (vec (concat sky-hatches [mountain foreground river]))}))

;; --- 2. Art Deco Sunburst ---

(defn ^{:example {:output "artisan-art-deco-sunburst.png"
                  :title  "Art Deco Sunburst"
                  :desc   "Gold-on-black geometric rays with decorated border."
                  :tags   ["symmetry" "path-decorators" "gradients"]}}
  art-deco-sunburst []
  (let [gold [:color/rgb 218 175 80]
        dark [:color/rgb 15 12 8]
        cx 300.0 cy 300.0
        rays (vec
               (for [i (range 36)]
                 (let [angle (* i (/ Math/PI 18))
                       r1 60.0 r2 260.0
                       w (if (even? i) 0.12 0.06)
                       a1 (- angle w) a2 (+ angle w)]
                   {:node/type :shape/path
                    :path/commands [[:move-to [(+ cx (* r1 (Math/cos a1)))
                                               (+ cy (* r1 (Math/sin a1)))]]
                                    [:line-to [(+ cx (* r2 (Math/cos a1)))
                                               (+ cy (* r2 (Math/sin a1)))]]
                                    [:line-to [(+ cx (* r2 (Math/cos a2)))
                                               (+ cy (* r2 (Math/sin a2)))]]
                                    [:line-to [(+ cx (* r1 (Math/cos a2)))
                                               (+ cy (* r1 (Math/sin a2)))]]
                                    [:close]]
                    :style/fill {:gradient/type :radial
                                 :gradient/center [cx cy]
                                 :gradient/radius 260.0
                                 :gradient/stops [[0.0 [:color/rgb 255 220 120]]
                                                  [1.0 gold]]}})))
        center-circle {:node/type :shape/circle
                       :circle/center [cx cy]
                       :circle/radius 55.0
                       :style/fill {:gradient/type :radial
                                    :gradient/center [cx cy]
                                    :gradient/radius 55.0
                                    :gradient/stops [[0.0 [:color/rgb 255 240 180]]
                                                     [1.0 gold]]}
                       :style/stroke {:color gold :width 2}}
        border-frame {:node/type :path/decorated
                      :path/commands [[:move-to [30.0 30.0]]
                                      [:line-to [570.0 30.0]]
                                      [:line-to [570.0 570.0]]
                                      [:line-to [30.0 570.0]]
                                      [:line-to [30.0 30.0]]]
                      :decorator/shape (assoc (scene/regular-polygon [0.0 0.0] 6.0 4)
                                              :style/fill gold)
                      :decorator/spacing 20
                      :decorator/rotate? true}
        outer-rect {:node/type :shape/rect
                    :rect/xy [20.0 20.0]
                    :rect/size [560.0 560.0]
                    :style/stroke {:color gold :width 3}}]
    {:image/size [600 600]
     :image/background dark
     :image/nodes (conj rays center-circle border-frame outer-rect)}))

;; --- 3. Sumi-e Bamboo ---

(defn ^{:example {:output "artisan-sumi-e-bamboo.png"
                  :title  "Sumi-e Bamboo"
                  :desc   "Minimalist ink wash with warped brush strokes and stippled leaves."
                  :tags   ["brush-strokes" "warp" "stipple"]}}
  sumi-e-bamboo []
  (let [ink [:color/rgb 30 30 30]
        bg  [:color/rgb 245 240 228]
        stems
        (for [[sx sy h] [[150 380 250] [200 390 220] [170 395 180]]]
          {:node/type :shape/path
           :path/commands [[:move-to [(double sx) (double sy)]]
                           [:line-to [(double sx) (double (- sy h))]]]
           :stroke/profile :brush
           :style/stroke {:color ink :width 8}})
        nodes-on-stems
        (for [[sx sy h] [[150 380 250] [200 390 220] [170 395 180]]
              frac [0.3 0.5 0.7]]
          (let [ny (- sy (* h frac))]
            {:node/type :shape/path
             :path/commands [[:move-to [(- (double sx) 6) (double ny)]]
                             [:line-to [(+ (double sx) 6) (double ny)]]]
             :style/stroke {:color ink :width 3}}))
        leaves
        (for [[lx ly angle] [[130 200 -40] [180 160 30] [215 230 -20]
                              [140 280 35] [195 140 -50] [160 310 25]]]
          {:node/type :shape/path
           :path/commands [[:move-to [(double lx) (double ly)]]
                           [:curve-to [(+ lx (* 30 (Math/cos (Math/toRadians angle))))
                                       (+ ly (* 30 (Math/sin (Math/toRadians angle))))]
                                      [(+ lx (* 50 (Math/cos (Math/toRadians angle))))
                                       (+ ly (* 15 (Math/sin (Math/toRadians angle))))]
                                      [(+ lx (* 60 (Math/cos (Math/toRadians angle))))
                                       (+ ly (* 5 (Math/sin (Math/toRadians angle))))]]]
           :stroke/profile :pointed
           :style/stroke {:color [:color/rgba 30 30 30 0.7] :width 6}})
        stippled-accent
        {:node/type :shape/circle
         :circle/center [350.0 150.0]
         :circle/radius 60.0
         :style/fill {:fill/type :stipple
                      :stipple/density 0.15
                      :stipple/radius 1.0
                      :stipple/seed 42
                      :stipple/color [:color/rgba 30 30 30 0.4]
                      :stipple/background bg}
         :node/opacity 0.5}]
    {:image/size [500 450]
     :image/background bg
     :image/nodes
     [{:node/type :group
       :group/warp {:type :wave :amplitude 3 :frequency 0.02 :seed 42}
       :group/children (vec (concat stems nodes-on-stems leaves [stippled-accent]))}]}))

;; --- 4. Memphis Pattern ---

(defn ^{:example {:output "artisan-memphis-pattern.png"
                  :title  "Memphis Pattern"
                  :desc   "Bold 80s geometric pattern with scatter and pattern fills."
                  :tags   ["pattern-fills" "scatter"]}}
  memphis-pattern []
  (let [pink   [:color/rgb 255 100 150]
        yellow [:color/rgb 255 220 50]
        cyan   [:color/rgb 50 210 230]
        black  [:color/rgb 20 20 20]
        bg     [:color/rgb 245 235 220]
        circles
        (for [[x y r c] [[80 80 35 pink] [320 100 45 cyan] [180 300 30 yellow]
                          [420 280 40 pink] [500 120 25 cyan]]]
          {:node/type :shape/circle
           :circle/center [(double x) (double y)]
           :circle/radius (double r)
           :style/fill c
           :style/stroke {:color black :width 3}})
        triangles
        (for [[x y c] [[250 60 yellow] [100 220 cyan] [400 180 pink] [350 350 yellow]]]
          (assoc (scene/triangle [(double x) (double y)]
                                 [(+ (double x) 40.0) (+ (double y) 60.0)]
                                 [(- (double x) 40.0) (+ (double y) 60.0)])
                 :style/fill c
                 :style/stroke {:color black :width 3}))
        squiggles
        (for [[sx sy] [[50 160] [280 220] [450 50] [150 380]]]
          (let [pts (for [i (range 8)]
                      [(+ sx (* i 15)) (+ sy (* 10 (Math/sin (* i 1.5))))])]
            {:node/type :shape/path
             :path/commands (into [[:move-to (first pts)]]
                                  (mapv #(vector :line-to %) (rest pts)))
             :style/stroke {:color black :width 3}}))
        pattern-rect
        {:node/type :shape/rect
         :rect/xy [380.0 320.0]
         :rect/size [100.0 80.0]
         :style/fill {:fill/type :pattern
                      :pattern/size [16 16]
                      :pattern/nodes
                      [{:node/type :shape/rect
                        :rect/xy [0.0 0.0] :rect/size [16.0 16.0]
                        :style/fill cyan}
                       {:node/type :shape/circle
                        :circle/center [8.0 8.0] :circle/radius 3.0
                        :style/fill black}]}
         :style/stroke {:color black :width 3}}
        dots (scatter/poisson-disk 10 10 530 390 40 42)]
    {:image/size [550 420]
     :image/background bg
     :image/nodes
     (vec (concat circles triangles squiggles [pattern-rect]
                  [{:node/type :scatter
                    :scatter/shape {:node/type :shape/circle
                                    :circle/center [0.0 0.0]
                                    :circle/radius 3.0
                                    :style/fill black}
                    :scatter/positions dots
                    :scatter/jitter {:x 5 :y 5 :seed 77}}]))}))

;; --- 5. Celtic Interlace ---

(defn ^{:example {:output "artisan-celtic-interlace.png"
                  :title  "Celtic Interlace"
                  :desc   "Knotwork paths with grid symmetry and path decorators."
                  :tags   ["symmetry" "path-decorators"]}}
  celtic-interlace []
  (let [gold  [:color/rgb 180 140 60]
        dark  [:color/rgb 30 25 15]
        bg    [:color/rgb 40 35 25]
        knot-unit
        [{:node/type :shape/path
          :path/commands [[:move-to [0.0 25.0]]
                          [:curve-to [25.0 0.0] [75.0 50.0] [100.0 25.0]]
                          [:curve-to [75.0 0.0] [25.0 50.0] [0.0 25.0]]]
          :style/stroke {:color gold :width 5}
          :style/fill {:fill/type :hatch
                       :hatch/angle 45
                       :hatch/spacing 6
                       :hatch/stroke-width 0.5
                       :hatch/color [:color/rgb 140 110 40]}}
         {:node/type :shape/path
          :path/commands [[:move-to [0.0 25.0]]
                          [:curve-to [25.0 50.0] [75.0 0.0] [100.0 25.0]]]
          :style/stroke {:color [:color/rgb 220 180 80] :width 3}}]
        border-path [[:move-to [40.0 40.0]]
                     [:line-to [460.0 40.0]]
                     [:line-to [460.0 460.0]]
                     [:line-to [40.0 460.0]]
                     [:line-to [40.0 40.0]]]
        decorated-border
        {:node/type :path/decorated
         :path/commands border-path
         :decorator/shape {:node/type :shape/circle
                           :circle/center [0.0 0.0]
                           :circle/radius 4.0
                           :style/fill gold}
         :decorator/spacing 15
         :decorator/rotate? false}
        inner-border
        {:node/type :shape/rect
         :rect/xy [50.0 50.0]
         :rect/size [400.0 400.0]
         :style/stroke {:color gold :width 2}}]
    {:image/size [500 500]
     :image/background bg
     :image/nodes
     [{:node/type :symmetry
       :symmetry/type :grid
       :symmetry/cols 4
       :symmetry/rows 4
       :symmetry/spacing [100 100]
       :symmetry/origin [60 60]
       :group/children knot-unit}
      decorated-border
      inner-border]}))

;; --- 6. Halftone Layers ---

(defn ^{:example {:output "artisan-halftone-layers.png"
                  :title  "Halftone Layers"
                  :desc   "CMYK-style overlapping shapes with halftone filter at different angles."
                  :tags   ["filters" "compositing"]}}
  halftone-layers []
  {:image/size [500 500]
   :image/background [:color/rgb 245 240 235]
   :image/nodes
   [{:node/type :group
     :group/composite :multiply
     :group/filter [:halftone 6 0]
     :group/children
     [{:node/type :shape/circle
       :circle/center [200.0 200.0]
       :circle/radius 150.0
       :style/fill [:color/rgb 0 180 220]}]}
    {:node/type :group
     :group/composite :multiply
     :group/filter [:halftone 6 30]
     :group/children
     [{:node/type :shape/circle
       :circle/center [300.0 200.0]
       :circle/radius 150.0
       :style/fill [:color/rgb 220 30 80]}]}
    {:node/type :group
     :group/composite :multiply
     :group/filter [:halftone 6 60]
     :group/children
     [{:node/type :shape/circle
       :circle/center [250.0 300.0]
       :circle/radius 150.0
       :style/fill [:color/rgb 255 220 40]}]}]})

;; --- 7. Paper Collage ---

(defn ^{:example {:output "artisan-paper-collage.png"
                  :title  "Paper Collage"
                  :desc   "Torn-edge shapes with grain, hatching, stippling, and shadows."
                  :tags   ["grain" "distortion" "hatching" "stipple" "shadows"]}}
  paper-collage []
  (let [bg [:color/rgb 230 220 200]]
    {:image/size [500 450]
     :image/background bg
     :image/nodes
     [;; Bottom layer: hatched rectangle with shadow and grain
      {:node/type :group
       :group/filter [:grain 0.12 42]
       :group/children
       [{:node/type :shape/rect
         :rect/xy [60.0 120.0]
         :rect/size [200.0 250.0]
         :style/fill {:fill/type :hatch
                      :hatch/layers [{:angle 45 :spacing 5}
                                     {:angle -45 :spacing 5}]
                      :hatch/stroke-width 0.6
                      :hatch/color [:color/rgb 40 60 100]
                      :hatch/background [:color/rgb 200 210 230]}
         :style/stroke {:color [:color/rgb 40 60 100] :width 1}
         :effect/shadow {:dx 5 :dy 5 :blur 8
                         :color [:color/rgb 0 0 0] :opacity 0.25}
         :node/transform [[:transform/distort {:type :roughen :amount 4 :seed 42}]]}]}
      ;; Middle layer: solid warm shape with grain
      {:node/type :group
       :group/filter [:grain 0.1 99]
       :group/children
       [{:node/type :shape/path
         :path/commands [[:move-to [180.0 80.0]]
                         [:line-to [380.0 90.0]]
                         [:line-to [370.0 280.0]]
                         [:line-to [190.0 270.0]]
                         [:close]]
         :style/fill [:color/rgb 220 160 100]
         :style/stroke {:color [:color/rgb 160 110 60] :width 1}
         :effect/shadow {:dx 4 :dy 6 :blur 10
                         :color [:color/rgb 0 0 0] :opacity 0.2}
         :node/transform [[:transform/distort {:type :roughen :amount 5 :seed 77}]]}]}
      ;; Top layer: stippled circle
      {:node/type :shape/circle
       :circle/center [320.0 300.0]
       :circle/radius 80.0
       :style/fill {:fill/type :stipple
                    :stipple/density 0.5
                    :stipple/radius 1.2
                    :stipple/seed 33
                    :stipple/color [:color/rgb 140 40 40]
                    :stipple/background [:color/rgb 240 220 210]}
       :style/stroke {:color [:color/rgb 140 40 40] :width 1.5}
       :effect/shadow {:dx 3 :dy 4 :blur 6
                       :color [:color/rgb 0 0 0] :opacity 0.2}
       :node/transform [[:transform/distort {:type :roughen :amount 3 :seed 55}]]}
      ;; Decorative strip
      {:node/type :shape/rect
       :rect/xy [100.0 350.0]
       :rect/size [300.0 30.0]
       :style/fill {:fill/type :hatch
                    :hatch/angle 0
                    :hatch/spacing 4
                    :hatch/stroke-width 0.5
                    :hatch/color [:color/rgb 60 80 60]
                    :hatch/background [:color/rgb 210 220 200]}
       :node/transform [[:transform/distort {:type :roughen :amount 3 :seed 88}]]}]}))

;; --- 8. Botanical L-system ---

(defn ^{:example {:output "artisan-botanical-lsystem.png"
                  :title  "Botanical L-system"
                  :desc   "Delicate plant with L-system branches, stippled flowers, hatched leaves."
                  :tags   ["l-systems" "stipple" "hatching"]}}
  botanical-lsystem []
  (let [bg    [:color/rgb 250 245 235]
        brown [:color/rgb 80 55 30]
        green [:color/rgb 50 90 30]
        flowers
        (for [[fx fy r] [[160 80 15] [130 120 12] [200 100 10]
                          [140 160 8] [180 140 11]]]
          {:node/type :shape/circle
           :circle/center [(double fx) (double fy)]
           :circle/radius (double r)
           :style/fill {:fill/type :stipple
                        :stipple/density 0.6
                        :stipple/radius 0.8
                        :stipple/seed (+ fx fy)
                        :stipple/color [:color/rgb 180 50 80]
                        :stipple/background [:color/rgb 250 200 210]}
           :style/stroke {:color [:color/rgb 180 50 80] :width 0.8}})
        leaves
        (for [[lx ly] [[120 200] [190 180] [100 260] [200 240]]]
          {:node/type :shape/path
           :path/commands [[:move-to [(double lx) (double ly)]]
                           [:curve-to [(+ lx 15.0) (- ly 20.0)]
                                      [(+ lx 25.0) (- ly 15.0)]
                                      [(+ lx 30.0) (double ly)]]
                           [:curve-to [(+ lx 25.0) (+ ly 15.0)]
                                      [(+ lx 15.0) (+ ly 20.0)]
                                      [(double lx) (double ly)]]]
           :style/fill {:fill/type :hatch
                        :hatch/angle 30
                        :hatch/spacing 3
                        :hatch/stroke-width 0.5
                        :hatch/color green}
           :style/stroke {:color green :width 0.8}})]
    {:image/size [400 450]
     :image/background bg
     :image/nodes
     (vec (concat
            [{:node/type :shape/rect
              :rect/xy [0.0 380.0]
              :rect/size [400.0 70.0]
              :style/fill {:fill/type :hatch
                           :hatch/angle 0
                           :hatch/spacing 4
                           :hatch/stroke-width 0.4
                           :hatch/color [:color/rgb 140 120 80]
                           :hatch/background bg}}
             {:node/type :lsystem
              :lsystem/axiom "F"
              :lsystem/rules {"F" "FF-[-F+F+F]+[+F-F-F]"}
              :lsystem/iterations 4
              :lsystem/angle 22.5
              :lsystem/length 4.0
              :lsystem/origin [170 380]
              :lsystem/heading -90.0
              :style/stroke {:color brown :width 1.2}
              :node/transform [[:transform/distort {:type :roughen :amount 1 :seed 42}]]}]
            leaves
            flowers))}))

;; --- 9. Type Poster ---

(defn ^{:example {:output "artisan-type-poster.png"
                  :title  "Type Poster"
                  :desc   "Bold text-clip with gradient fill and drop shadow."
                  :tags   ["text-clip" "gradients" "shadows"]}}
  type-poster []
  (let [gradient-bg
        {:node/type :shape/rect
         :rect/xy [-10.0 -250.0]
         :rect/size [620.0 300.0]
         :style/fill {:gradient/type :linear
                      :gradient/from [0.0 -250.0]
                      :gradient/to   [0.0 50.0]
                      :gradient/stops [[0.0 [:color/rgb 255 80 60]]
                                       [0.5 [:color/rgb 255 180 40]]
                                       [1.0 [:color/rgb 60 200 255]]]}}
        pattern-overlay
        {:node/type :shape/rect
         :rect/xy [-10.0 -250.0]
         :rect/size [620.0 300.0]
         :style/fill {:fill/type :pattern
                      :pattern/size [20 20]
                      :pattern/nodes
                      [{:node/type :shape/circle
                        :circle/center [10.0 10.0]
                        :circle/radius 3.0
                        :style/fill [:color/rgba 255 255 255 0.15]}]}
         :node/opacity 0.5}
        shadow-text
        (-> (scene/text-outline "BOLD"
              {:font/family "SansSerif" :font/size 180 :font/weight :bold}
              [42 232])
            (assoc :style/fill [:color/rgba 0 0 0 0.3]))]
    {:image/size [600 300]
     :image/background [:color/rgb 20 15 30]
     :image/nodes
     [shadow-text
      (scene/text-clip "BOLD"
        {:font/family "SansSerif" :font/size 180 :font/weight :bold}
        [38 228]
        [gradient-bg pattern-overlay])]}))

;; --- 10. Contour Terrain ---

(defn ^{:example {:output "artisan-contour-terrain.png"
                  :title  "Contour Terrain"
                  :desc   "Topographic map with contour layers, hatched water, and compass rose."
                  :tags   ["contours" "hatching" "path-decorators"]}}
  contour-terrain []
  (let [thresholds (mapv #(- (* % 0.12) 0.5) (range 10))
        land-pal (palette/gradient-palette [:color/rgb 60 100 50] [:color/rgb 200 180 140] 10)
        water
        {:node/type :shape/rect
         :rect/xy [0.0 0.0]
         :rect/size [600.0 450.0]
         :style/fill {:fill/type :hatch
                      :hatch/angle 0
                      :hatch/spacing 6
                      :hatch/stroke-width 0.4
                      :hatch/color [:color/rgb 60 80 140]
                      :hatch/background [:color/rgb 200 215 240]}}
        contours
        (vec (map-indexed
               (fn [i threshold]
                 {:node/type :contour
                  :contour/bounds [0 0 600 450]
                  :contour/opts {:thresholds [threshold]
                                 :resolution 3
                                 :noise-scale 0.01
                                 :seed 55}
                  :style/stroke {:color (nth land-pal i)
                                 :width (if (zero? (mod i 3)) 1.8 0.7)}})
               thresholds))
        ;; Compass rose using path decorators
        rose-cx 530.0 rose-cy 60.0
        compass-ring
        {:node/type :path/decorated
         :path/commands (into [[:move-to [(+ rose-cx 30.0) rose-cy]]]
                              (for [a (range 10 370 10)]
                                (let [rad (Math/toRadians (double a))]
                                  [:line-to [(+ rose-cx (* 30.0 (Math/cos rad)))
                                             (+ rose-cy (* 30.0 (Math/sin rad)))]])))
         :decorator/shape {:node/type :shape/circle
                           :circle/center [0.0 0.0]
                           :circle/radius 2.0
                           :style/fill [:color/rgb 120 80 40]}
         :decorator/spacing 12
         :decorator/rotate? false}
        compass-needle
        (assoc (scene/triangle [rose-cx (- rose-cy 25.0)]
                               [(- rose-cx 6.0) (+ rose-cy 8.0)]
                               [(+ rose-cx 6.0) (+ rose-cy 8.0)])
               :style/fill [:color/rgb 180 40 30]
               :style/stroke {:color [:color/rgb 80 20 10] :width 1})]
    {:image/size [600 450]
     :image/background [:color/rgb 200 215 240]
     :image/nodes (vec (concat [water] contours [compass-ring compass-needle]))}))

;; --- 11. Watercolor Blooms ---

(defn ^{:example {:output "artisan-watercolor-blooms.png"
                  :title  "Watercolor Blooms"
                  :desc   "Soft overlapping translucent circles with multiply blending."
                  :tags   ["compositing" "transparency"]}}
  watercolor-blooms []
  (let [pal (:pastel palette/palettes)
        blooms
        (for [[x y r ci] [[150 180 100 0] [280 160 90 1] [200 280 110 2]
                           [350 250 85 3] [120 320 75 4] [400 150 95 0]
                           [320 340 80 1] [180 120 70 2] [450 300 65 3]]]
          {:node/type :shape/circle
           :circle/center [(double x) (double y)]
           :circle/radius (double r)
           :style/fill (nth pal ci)
           :node/opacity 0.35})]
    {:image/size [550 450]
     :image/background [:color/rgb 250 248 242]
     :image/nodes
     [{:node/type :group
       :group/composite :multiply
       :group/children (vec blooms)}]}))

;; --- 12. Geometric Tiling ---

(defn ^{:example {:output "artisan-geometric-tiling.png"
                  :title  "Geometric Tiling"
                  :desc   "Islamic-inspired star pattern with grid symmetry and hatching."
                  :tags   ["symmetry" "hatching"]}}
  geometric-tiling []
  (let [teal  [:color/rgb 20 100 120]
        gold  [:color/rgb 180 150 60]
        cream [:color/rgb 245 238 220]
        tile-star (scene/star [0.0 0.0] 35.0 15.0 8)
        tile-inner (scene/regular-polygon [0.0 0.0] 14.0 8)]
    {:image/size [500 500]
     :image/background cream
     :image/nodes
     [{:node/type :symmetry
       :symmetry/type :grid
       :symmetry/cols 5
       :symmetry/rows 5
       :symmetry/spacing [95 95]
       :symmetry/origin [55 55]
       :group/children
       [(assoc tile-star
               :style/fill {:fill/type :hatch
                            :hatch/layers [{:angle 0 :spacing 4}
                                           {:angle 60 :spacing 4}
                                           {:angle -60 :spacing 4}]
                            :hatch/stroke-width 0.5
                            :hatch/color teal}
               :style/stroke {:color teal :width 1.5})
        (assoc tile-inner
               :style/fill gold
               :style/stroke {:color [:color/rgb 120 100 30] :width 1})]}
      {:node/type :shape/rect
       :rect/xy [10.0 10.0]
       :rect/size [480.0 480.0]
       :style/stroke {:color teal :width 3}}]}))

;; --- 13. Pointillist Landscape ---

(defn ^{:example {:output "artisan-pointillist-landscape.png"
                  :title  "Pointillist Landscape"
                  :desc   "Dense scatter of colored dots forming gradient sky and ground."
                  :tags   ["scatter" "noise" "variation"]}}
  pointillist-landscape []
  (let [pts (scatter/poisson-disk 5 5 595 395 8 42)
        sky-stops [[0.0 [:color/rgb 40 60 150]]
                   [0.4 [:color/rgb 120 160 220]]
                   [0.6 [:color/rgb 220 180 100]]
                   [1.0 [:color/rgb 60 130 50]]]
        overrides (vary/by-noise pts 0.008 42
                    (fn [v]
                      (let [[_x y] nil]
                        {:node/opacity (+ 0.6 (* 0.4 (Math/abs v)))})))
        ;; Color by y-position via manual override
        colored-overrides
        (vec (map-indexed
               (fn [i [_x y]]
                 (let [t (/ y 400.0)
                       color (palette/gradient-map sky-stops t)]
                   (merge (nth overrides i) {:style/fill color})))
               pts))]
    {:image/size [600 400]
     :image/background [:color/rgb 245 240 230]
     :image/nodes
     [{:node/type :scatter
       :scatter/shape {:node/type :shape/circle
                       :circle/center [0.0 0.0]
                       :circle/radius 3.5}
       :scatter/positions pts
       :scatter/overrides colored-overrides}]}))

;; --- 14. Woven Ribbons ---

(defn ^{:example {:output "artisan-woven-ribbons.gif"
                  :title  "Woven Ribbons"
                  :desc   "Animated interlocking sine waves with clipping for over/under illusion."
                  :tags   ["clipping" "animation"]}}
  woven-ribbons []
  {:frames
   (anim/frames 40
     (fn [t]
       (let [w 500 h 300
             phase (* t 2 Math/PI)
             ribbon-w 20.0
             make-ribbon
             (fn [idx color amp freq y-off]
               (let [pts (for [x (range 0 (inc w) 4)]
                           [(double x)
                            (+ y-off (* amp (Math/sin (+ (* x freq) phase (* idx 1.2)))))])
                     upper-pts pts
                     lower-pts (map (fn [[x y]] [x (+ y ribbon-w)]) pts)
                     cmds (into [[:move-to (first upper-pts)]]
                                (concat
                                  (mapv #(vector :line-to %) (rest upper-pts))
                                  (mapv #(vector :line-to %) (reverse lower-pts))
                                  [[:close]]))]
                 {:node/type :shape/path
                  :path/commands cmds
                  :style/fill color
                  :style/stroke {:color [:color/rgba 0 0 0 0.3] :width 0.5}}))
             ribbons [(make-ribbon 0 [:color/rgb 220 60 60]  40 0.025 100.0)
                      (make-ribbon 1 [:color/rgb 60 120 220] 40 0.025 160.0)
                      (make-ribbon 2 [:color/rgb 60 180 80]  40 0.025 220.0)]
             ;; Clip alternating sections to create weave
             clip-sections
             (fn [ribbon-idx ribbon]
               (let [even-clips
                     (for [seg (range 0 (/ w 50))]
                       (let [x-start (* seg 50)
                             show? (even? (+ seg ribbon-idx))]
                         (when show?
                           {:node/type :shape/rect
                            :rect/xy [(double x-start) 0.0]
                            :rect/size [50.0 (double h)]})))
                     clip-nodes (vec (remove nil? even-clips))]
                 [{:node/type :group
                   :group/clip {:node/type :shape/rect
                                :rect/xy [0.0 0.0]
                                :rect/size [(double w) (double h)]}
                   :group/children [ribbon]}]))]
         {:image/size [w h]
          :image/background [:color/rgb 240 235 225]
          :image/nodes
          (vec (concat
                 ;; Back layers (full ribbons at lower opacity)
                 (map #(assoc % :node/opacity 0.3) ribbons)
                 ;; Front layers with clipping
                 (mapcat (fn [i] (clip-sections i (nth ribbons i)))
                         (range 3))))})))
   :fps 20})

;; --- 15. Vintage Map ---

(defn ^{:example {:output "artisan-vintage-map.png"
                  :title  "Vintage Map"
                  :desc   "Contour lines, hatched ocean, stippled land, decorative frame, and text."
                  :tags   ["contours" "hatching" "stipple" "path-decorators" "text"]}}
  vintage-map []
  (let [parchment [:color/rgb 235 220 190]
        ink       [:color/rgb 50 40 30]
        ocean
        {:node/type :shape/rect
         :rect/xy [30.0 30.0]
         :rect/size [540.0 390.0]
         :style/fill {:fill/type :hatch
                      :hatch/angle 0
                      :hatch/spacing 5
                      :hatch/stroke-width 0.3
                      :hatch/color [:color/rgb 80 100 140]
                      :hatch/background [:color/rgb 200 210 225]}}
        island
        {:node/type :shape/path
         :path/commands [[:move-to [200.0 180.0]]
                         [:curve-to [250.0 120.0] [350.0 130.0] [380.0 180.0]]
                         [:curve-to [400.0 220.0] [390.0 280.0] [340.0 300.0]]
                         [:curve-to [290.0 320.0] [220.0 310.0] [190.0 260.0]]
                         [:curve-to [170.0 220.0] [175.0 200.0] [200.0 180.0]]]
         :style/fill {:fill/type :stipple
                      :stipple/density 0.3
                      :stipple/radius 0.8
                      :stipple/seed 42
                      :stipple/color [:color/rgb 120 100 60]
                      :stipple/background parchment}
         :style/stroke {:color ink :width 1.5}
         :node/transform [[:transform/distort {:type :roughen :amount 3 :seed 42}]]}
        contours
        (vec (for [i (range 5)]
               {:node/type :contour
                :contour/bounds [150 100 300 250]
                :contour/opts {:thresholds [(* i 0.15)]
                               :resolution 3
                               :noise-scale 0.015
                               :seed 42}
                :style/stroke {:color [:color/rgba 100 80 40 0.5] :width 0.6}}))
        frame
        {:node/type :path/decorated
         :path/commands [[:move-to [20.0 20.0]]
                         [:line-to [580.0 20.0]]
                         [:line-to [580.0 430.0]]
                         [:line-to [20.0 430.0]]
                         [:line-to [20.0 20.0]]]
         :decorator/shape (assoc (scene/regular-polygon [0.0 0.0] 5.0 4)
                                 :style/fill ink)
         :decorator/spacing 18
         :decorator/rotate? true}
        title-text
        (-> (scene/text-outline "TERRA INCOGNITA"
              {:font/family "Serif" :font/size 28 :font/weight :bold}
              [140 440])
            (assoc :style/fill ink))]
    {:image/size [600 460]
     :image/background parchment
     :image/nodes (vec (concat [ocean island] contours [frame title-text]))}))

;; --- 16. Glitch Art ---

(defn ^{:example {:output "artisan-glitch-art.png"
                  :title  "Glitch Art"
                  :desc   "Posterized and duotoned shapes with offset color channels."
                  :tags   ["filters" "posterize" "duotone"]}}
  glitch-art []
  {:image/size [500 400]
   :image/background [:color/rgb 10 10 15]
   :image/nodes
   [;; Duotone layer — shifted left
    {:node/type :group
     :group/filter [:duotone [:color/rgb 0 20 60] [:color/rgb 0 255 200]]
     :group/children
     [{:node/type :shape/rect
       :rect/xy [30.0 60.0]
       :rect/size [220.0 280.0]
       :style/fill [:color/rgb 200 200 200]}
      {:node/type :shape/circle
       :circle/center [140.0 200.0]
       :circle/radius 80.0
       :style/fill [:color/rgb 100 100 100]}]}
    ;; Posterize layer — shifted right
    {:node/type :group
     :group/filter [:posterize 3]
     :group/children
     [{:node/type :shape/rect
       :rect/xy [250.0 60.0]
       :rect/size [220.0 280.0]
       :style/fill [:color/rgb 255 80 120]}
      {:node/type :shape/circle
       :circle/center [360.0 200.0]
       :circle/radius 80.0
       :style/fill [:color/rgb 255 200 50]}]}
    ;; Offset "red channel" strip
    {:node/type :group
     :group/composite :screen
     :group/children
     [{:node/type :shape/rect
       :rect/xy [0.0 150.0]
       :rect/size [500.0 40.0]
       :style/fill [:color/rgba 255 0 50 0.4]}]}
    ;; Offset "cyan channel" strip
    {:node/type :group
     :group/composite :screen
     :group/children
     [{:node/type :shape/rect
       :rect/xy [0.0 190.0]
       :rect/size [500.0 30.0]
       :style/fill [:color/rgba 0 255 200 0.3]}]}
    ;; Scan lines
    {:node/type :group
     :group/children
     (vec (for [i (range 0 400 4)]
            {:node/type :shape/line
             :line/from [0.0 (double i)]
             :line/to   [500.0 (double i)]
             :node/opacity 0.08
             :style/stroke {:color [:color/rgb 255 255 255] :width 1}}))}]})

;; --- 17. Spiral Text ---

(defn ^{:example {:output "artisan-spiral-text.png"
                  :title  "Spiral Text"
                  :desc   "Text following an Archimedean spiral with gradient color."
                  :tags   ["text-on-path" "gradients"]}}
  spiral-text []
  (let [cx 250.0 cy 250.0
        ;; Build Archimedean spiral as path commands
        spiral-pts (for [i (range 0 720 5)]
                     (let [angle (Math/toRadians (double i))
                           r (+ 30.0 (* 0.15 i))]
                       [(+ cx (* r (Math/cos angle)))
                        (+ cy (* r (Math/sin angle)))]))
        spiral-cmds (into [[:move-to (first spiral-pts)]]
                          (mapv #(vector :line-to %) (rest spiral-pts)))
        text-node (scene/text-on-path
                    "In the beginning was the word and the word was data and the data was good -- "
                    {:font/family "Serif" :font/size 14}
                    spiral-cmds)
        ;; Spiral path as faint guide
        guide {:node/type :shape/path
               :path/commands spiral-cmds
               :style/stroke {:color [:color/rgba 100 80 160 0.15] :width 0.5}}]
    {:image/size [500 500]
     :image/background [:color/rgb 245 240 230]
     :image/nodes
     [guide
      (assoc text-node
             :style/fill {:gradient/type :linear
                          :gradient/from [0.0 0.0]
                          :gradient/to [500.0 500.0]
                          :gradient/stops [[0.0 [:color/rgb 40 20 100]]
                                           [0.5 [:color/rgb 180 40 80]]
                                           [1.0 [:color/rgb 220 160 30]]]})]}))

;; --- 18. Morphing Geometry ---

(defn ^{:example {:output "artisan-morphing-geometry.gif"
                  :title  "Morphing Geometry"
                  :desc   "Animated loop: triangle to square to pentagon to hexagon to circle."
                  :tags   ["morphing" "animation"]}}
  morphing-geometry []
  (let [cx 200.0 cy 200.0 r 100.0
        tri-cmds  (:path/commands (scene/regular-polygon [cx cy] r 3))
        sq-cmds   (:path/commands (scene/regular-polygon [cx cy] r 4))
        pent-cmds (:path/commands (scene/regular-polygon [cx cy] r 5))
        hex-cmds  (:path/commands (scene/regular-polygon [cx cy] r 6))
        circ-cmds (:path/commands (scene/regular-polygon [cx cy] r 48))
        shapes [tri-cmds sq-cmds pent-cmds hex-cmds circ-cmds]
        n-shapes (count shapes)
        colors [[:color/rgb 230 70 70]
                [:color/rgb 70 180 230]
                [:color/rgb 80 200 120]
                [:color/rgb 230 180 50]
                [:color/rgb 180 80 220]]]
    {:frames
     (anim/frames 50
       (fn [t]
         (let [progress (* t n-shapes)
               idx (min (dec n-shapes) (int progress))
               next-idx (mod (inc idx) n-shapes)
               local-t (- progress idx)
               eased (anim/ease-in-out-cubic local-t)
               cmds (morph/morph-auto (nth shapes idx)
                                       (nth shapes next-idx)
                                       eased)
               color (palette/gradient-map
                       [[0.0 (nth colors idx)]
                        [1.0 (nth colors next-idx)]]
                       eased)]
           {:image/size [400 400]
            :image/background [:color/rgb 20 18 30]
            :image/nodes
            [{:node/type :shape/path
              :path/commands cmds
              :style/fill color
              :style/stroke {:color [:color/rgba 255 255 255 0.4] :width 2}}]})))
     :fps 20}))
