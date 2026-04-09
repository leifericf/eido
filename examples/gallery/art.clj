(ns gallery.art
  "Artistic expression gallery — showcasing eido's expressive toolkit."
  {:category "Artistic Expression"}
  (:require
    [eido.animate :as anim]
    [eido.gen.flow :as flow]
    [eido.gen.lsystem :as lsystem]
    [eido.path.morph :as morph]
    [eido.gen.noise :as noise]
    [eido.color.palette :as palette]
    [eido.path :as path]
    [eido.gen.scatter :as scatter]
    [eido.scene :as scene]
    [eido.gen.vary :as vary]
    [eido.gen.voronoi :as voronoi]))

;; --- 1. Ink Landscape ---

(defn ^{:example {:output "art-ink-landscape.png"
                  :title  "Ink Landscape"
                  :desc   "Hatching, path distortion, and calligraphic strokes."}}
  ink-landscape []
  (let [mountain-path [[:move-to [0.0 300.0]]
                       [:line-to [50.0 250.0]]
                       [:line-to [120.0 180.0]]
                       [:line-to [180.0 120.0]]
                       [:line-to [220.0 90.0]]
                       [:line-to [260.0 110.0]]
                       [:line-to [300.0 140.0]]
                       [:line-to [340.0 100.0]]
                       [:line-to [380.0 80.0]]
                       [:line-to [420.0 95.0]]
                       [:line-to [480.0 150.0]]
                       [:line-to [530.0 200.0]]
                       [:line-to [580.0 240.0]]
                       [:line-to [600.0 260.0]]
                       [:line-to [600.0 400.0]]
                       [:line-to [0.0 400.0]]
                       [:close]]
        sun-rays (for [i (range 16)]
                   (let [angle (* i (/ Math/PI 8))
                         cx 480.0 cy 80.0
                         r1 35.0 r2 60.0]
                     {:node/type :shape/path
                      :path/commands [[:move-to [(+ cx (* r1 (Math/cos angle)))
                                                 (+ cy (* r1 (Math/sin angle)))]]
                                      [:line-to [(+ cx (* r2 (Math/cos angle)))
                                                  (+ cy (* r2 (Math/sin angle)))]]]
                      :style/stroke {:color [:color/rgb 40 30 20] :width 1.5}}))]
    {:image/size [600 400]
     :image/background [:color/rgb 245 235 220]
     :image/nodes
     (into
       [{:node/type :shape/circle
         :circle/center [480.0 80.0]
         :circle/radius 30.0
         :style/fill {:fill/type :hatch
                      :hatch/angle 0
                      :hatch/spacing 3
                      :hatch/stroke-width 0.8
                      :hatch/color [:color/rgb 40 30 20]
                      :hatch/background [:color/rgb 245 235 220]}
         :style/stroke {:color [:color/rgb 40 30 20] :width 1.5}}]
       (concat
         sun-rays
         [{:node/type :shape/path
           :path/commands mountain-path
           :style/fill {:fill/type :hatch
                        :hatch/layers [{:angle 45 :spacing 6}
                                       {:angle -30 :spacing 8}]
                        :hatch/stroke-width 0.7
                        :hatch/color [:color/rgb 40 30 20]}
           :style/stroke {:color [:color/rgb 30 20 10] :width 2}
           :node/transform [[:transform/distort {:type :roughen :amount 2 :seed 42}]]}
          {:node/type :shape/path
           :path/commands [[:move-to [0.0 350.0]]
                           [:curve-to [100.0 330.0] [200.0 310.0] [300.0 320.0]]
                           [:curve-to [400.0 330.0] [500.0 340.0] [600.0 330.0]]]
           :stroke/profile :brush
           :style/stroke {:color [:color/rgb 30 20 10] :width 8}}]))}))

;; --- 2. Starfield ---

(defn ^{:example {:output "art-starfield.png"
                  :title  "Starfield"
                  :desc   "Scatter, noise, glow, and nebula."}}
  starfield []
  (let [pal (:midnight palette/palettes)
        star-positions (scatter/poisson-disk 0 0 600 400 15 42)
        bright-positions (scatter/noise-field 0 0 600 400 30 99)]
    {:image/size [600 400]
     :image/background (nth pal 0)
     :image/nodes
     [{:node/type :shape/circle
       :circle/center [200.0 180.0]
       :circle/radius 120.0
       :style/fill [:color/rgba 120 60 160 0.15]}
      {:node/type :shape/circle
       :circle/center [400.0 250.0]
       :circle/radius 100.0
       :style/fill [:color/rgba 60 80 180 0.12]}
      {:node/type :scatter
       :scatter/shape {:node/type :shape/circle
                       :circle/center [0.0 0.0]
                       :circle/radius 1.0
                       :style/fill [:color/rgba 200 200 220 0.6]}
       :scatter/positions star-positions
       :scatter/jitter {:x 2 :y 2 :seed 11}}
      {:node/type :scatter
       :scatter/shape {:node/type :shape/circle
                       :circle/center [0.0 0.0]
                       :circle/radius 2.5
                       :style/fill [:color/rgb 255 250 230]
                       :effect/glow {:blur 6
                                     :color [:color/rgb 200 200 255]
                                     :opacity 0.4}}
       :scatter/positions bright-positions
       :scatter/jitter {:x 1 :y 1 :seed 22}}]}))

;; --- 3. Stipple Spheres ---

(defn ^{:example {:output "art-stipple-spheres.png"
                  :title  "Stipple Spheres"
                  :desc   "Stippled spheres with shadows and varying density."}}
  stipple-spheres []
  {:image/size [400 400]
   :image/background [:color/rgb 245 240 230]
   :image/nodes
   [{:node/type :shape/circle
     :circle/center [200.0 200.0]
     :circle/radius 120.0
     :style/fill {:fill/type :stipple
                  :stipple/density 0.7
                  :stipple/radius 1.2
                  :stipple/seed 42
                  :stipple/color [:color/rgb 30 30 30]
                  :stipple/background [:color/rgb 245 240 230]}
     :style/stroke {:color [:color/rgb 30 30 30] :width 1.5}
     :effect/shadow {:dx 6 :dy 6 :blur 12
                     :color [:color/rgb 0 0 0]
                     :opacity 0.2}}
    {:node/type :shape/circle
     :circle/center [320.0 120.0]
     :circle/radius 50.0
     :style/fill {:fill/type :stipple
                  :stipple/density 0.3
                  :stipple/radius 1.0
                  :stipple/seed 99
                  :stipple/color [:color/rgb 30 30 30]
                  :stipple/background [:color/rgb 245 240 230]}
     :style/stroke {:color [:color/rgb 30 30 30] :width 1}}
    {:node/type :shape/circle
     :circle/center [130.0 320.0]
     :circle/radius 35.0
     :style/fill {:fill/type :stipple
                  :stipple/density 0.5
                  :stipple/radius 0.8
                  :stipple/seed 77
                  :stipple/color [:color/rgb 30 30 30]
                  :stipple/background [:color/rgb 245 240 230]}
     :style/stroke {:color [:color/rgb 30 30 30] :width 1}}]})

;; --- 4. Polka Pop ---

(defn ^{:example {:output "art-polka-pop.png"
                  :title  "Polka Pop"
                  :desc   "Pop art polka dot circles with pattern fills."}}
  polka-pop []
  (let [pal (:neon palette/palettes)]
    {:image/size [400 400]
     :image/background [:color/rgb 20 20 20]
     :image/nodes
     (vec (map-indexed
            (fn [i [x y r]]
              {:node/type :shape/circle
               :circle/center [x y]
               :circle/radius r
               :style/fill {:fill/type :pattern
                            :pattern/size [14 14]
                            :pattern/nodes
                            [{:node/type :shape/rect
                              :rect/xy [0.0 0.0]
                              :rect/size [14.0 14.0]
                              :style/fill (nth pal (mod i 5))}
                             {:node/type :shape/circle
                              :circle/center [7.0 7.0]
                              :circle/radius 3.0
                              :style/fill [:color/rgb 20 20 20]}]}
               :style/stroke {:color [:color/rgb 255 255 255] :width 3}
               :effect/shadow {:dx 5 :dy 5 :blur 10
                               :color [:color/rgb 0 0 0]
                               :opacity 0.5}})
            [[120.0 150.0 80.0]
             [280.0 120.0 60.0]
             [200.0 280.0 90.0]
             [330.0 300.0 45.0]
             [80.0 320.0 40.0]]))}))

;; --- 5. Calligraphy Flow ---

(defn ^{:example {:output "art-calligraphy-flow.gif"
                  :title  "Calligraphy Flow"
                  :desc   "Animated variable-width calligraphic strokes."}}
  calligraphy-flow []
  {:frames
   (anim/frames 60
     (fn [t]
       (let [wave-y (fn [x] (* 40 (Math/sin (+ (* x 0.015) (* t 2 Math/PI)))))
             paths (for [i (range 5)]
                     (let [base-y (+ 80 (* i 70))
                           pts (for [x (range 20 581 10)]
                                 [x (+ base-y (wave-y (+ x (* i 50))))])]
                       {:node/type :shape/path
                        :path/commands (into [[:move-to (first pts)]]
                                             (mapv (fn [p] [:line-to p]) (rest pts)))
                        :stroke/profile :pointed
                        :style/stroke {:color (nth (:sunset palette/palettes) (mod i 5))
                                       :width (+ 6 (* 2 i))}}))]
         {:image/size [600 450]
          :image/background [:color/rgb 25 20 30]
          :image/nodes (vec paths)})))
   :fps 30})

;; --- 6. Decorative Frame ---

(defn ^{:example {:output "art-decorative-frame.png"
                  :title  "Decorative Frame"
                  :desc   "Path decorators, hatching, and stipple accents."}}
  decorative-frame []
  {:image/size [400 400]
   :image/background [:color/rgb 250 245 235]
   :image/nodes
   [{:node/type :shape/rect
     :rect/xy [80.0 80.0]
     :rect/size [240.0 240.0]
     :style/fill {:fill/type :hatch
                  :hatch/layers [{:angle 30 :spacing 12}
                                 {:angle -30 :spacing 12}]
                  :hatch/stroke-width 0.5
                  :hatch/color [:color/rgb 180 160 130]
                  :hatch/background [:color/rgb 250 245 235]}}
    {:node/type :path/decorated
     :path/commands [[:move-to [50.0 50.0]]
                     [:line-to [350.0 50.0]]
                     [:line-to [350.0 350.0]]
                     [:line-to [50.0 350.0]]
                     [:line-to [50.0 50.0]]]
     :decorator/shape (assoc (scene/regular-polygon [0.0 0.0] 8 4)
                              :style/fill [:color/rgb 120 80 40])
     :decorator/spacing 25
     :decorator/rotate? true}
    {:node/type :shape/circle
     :circle/center [50.0 50.0]
     :circle/radius 15.0
     :style/fill {:fill/type :stipple
                  :stipple/density 0.8 :stipple/radius 0.8 :stipple/seed 42
                  :stipple/color [:color/rgb 120 80 40]
                  :stipple/background [:color/rgb 250 245 235]}
     :style/stroke {:color [:color/rgb 120 80 40] :width 1.5}}
    {:node/type :shape/circle
     :circle/center [350.0 50.0]
     :circle/radius 15.0
     :style/fill {:fill/type :stipple
                  :stipple/density 0.8 :stipple/radius 0.8 :stipple/seed 43
                  :stipple/color [:color/rgb 120 80 40]
                  :stipple/background [:color/rgb 250 245 235]}
     :style/stroke {:color [:color/rgb 120 80 40] :width 1.5}}
    {:node/type :shape/circle
     :circle/center [50.0 350.0]
     :circle/radius 15.0
     :style/fill {:fill/type :stipple
                  :stipple/density 0.8 :stipple/radius 0.8 :stipple/seed 44
                  :stipple/color [:color/rgb 120 80 40]
                  :stipple/background [:color/rgb 250 245 235]}
     :style/stroke {:color [:color/rgb 120 80 40] :width 1.5}}
    {:node/type :shape/circle
     :circle/center [350.0 350.0]
     :circle/radius 15.0
     :style/fill {:fill/type :stipple
                  :stipple/density 0.8 :stipple/radius 0.8 :stipple/seed 45
                  :stipple/color [:color/rgb 120 80 40]
                  :stipple/background [:color/rgb 250 245 235]}
     :style/stroke {:color [:color/rgb 120 80 40] :width 1.5}}]})

;; --- 7. Noise Garden ---

(defn ^{:example {:output "art-noise-garden.gif"
                  :title  "Noise Garden"
                  :desc   "Animated swaying flowers driven by noise."}}
  noise-garden []
  (let [pal (:forest palette/palettes)]
    {:frames
     (anim/frames 60
       (fn [t]
         (let [flowers
               (for [i (range 40)]
                 (let [bx (+ 40 (mod (* i 137.508 1.1) 520))
                       by (+ 60 (mod (* i 97.3 1.3) 250))
                       sway (* 8 (Math/sin (+ (* t 2 Math/PI) (* i 0.7))))
                       rng (java.util.Random. (long (+ i 100)))
                       r (+ 5 (* (.nextDouble rng) 15))
                       color-idx (mod i 5)]
                   {:node/type :shape/circle
                    :circle/center [(+ bx sway) by]
                    :circle/radius r
                    :style/fill (nth pal color-idx)
                    :node/opacity (+ 0.6 (* 0.4 (Math/sin (+ (* t 4 Math/PI) i))))}))
               stems
               (for [i (range 40)]
                 (let [bx (+ 40 (mod (* i 137.508 1.1) 520))
                       by (+ 60 (mod (* i 97.3 1.3) 250))
                       sway (* 8 (Math/sin (+ (* t 2 Math/PI) (* i 0.7))))
                       rng (java.util.Random. (long (+ i 100)))]
                   {:node/type :shape/path
                    :path/commands [[:move-to [(+ bx sway) by]]
                                    [:line-to [bx (+ by 40 (* (.nextDouble rng) 60))]]]
                    :style/stroke {:color (nth pal 0) :width 1.5}}))]
           {:image/size [600 400]
            :image/background [:color/rgb 250 248 240]
            :image/nodes (vec (concat stems flowers))})))
     :fps 30}))

;; --- 8. Mandala ---

(defn ^{:example {:output "art-mandala.png"
                  :title  "Mandala"
                  :desc   "Radial symmetry with hatching and stippling."}}
  mandala []
  {:image/size [600 600]
   :image/background [:color/rgb 245 235 215]
   :image/nodes
   [{:node/type :symmetry
     :symmetry/type :radial
     :symmetry/n 12
     :symmetry/center [300 300]
     :group/children
     [{:node/type :shape/path
       :path/commands [[:move-to [300.0 290.0]]
                       [:curve-to [330.0 200.0] [360.0 120.0] [300.0 50.0]]
                       [:curve-to [240.0 120.0] [270.0 200.0] [300.0 290.0]]]
       :style/fill {:fill/type :hatch
                    :hatch/angle 60
                    :hatch/spacing 4
                    :hatch/stroke-width 0.6
                    :hatch/color [:color/rgb 140 60 30]}
       :style/stroke {:color [:color/rgb 140 60 30] :width 0.8}}
      {:node/type :shape/path
       :path/commands [[:move-to [300.0 270.0]]
                       [:curve-to [315.0 200.0] [325.0 140.0] [300.0 80.0]]]
       :stroke/profile :pointed
       :style/stroke {:color [:color/rgb 180 80 30] :width 6}}]}
    {:node/type :shape/circle
     :circle/center [300.0 300.0]
     :circle/radius 40.0
     :style/fill {:fill/type :stipple
                  :stipple/density 0.6
                  :stipple/radius 1.0
                  :stipple/seed 42
                  :stipple/color [:color/rgb 140 60 30]
                  :stipple/background [:color/rgb 245 235 215]}
     :style/stroke {:color [:color/rgb 140 60 30] :width 2}}]})

;; --- 9. Van Gogh Swirls ---

(defn ^{:example {:output "art-van-gogh-swirls.png"
                  :title  "Van Gogh Swirls"
                  :desc   "Flow fields with variable brush strokes and warm palette."}}
  van-gogh-swirls []
  (let [pal (:fire palette/palettes)
        paths (flow/flow-field 0 0 600 400
                {:density 6 :steps 80 :step-length 1.5
                 :noise-scale 0.004 :seed 77})]
    {:image/size [600 400]
     :image/background [:color/rgb 15 15 40]
     :image/nodes
     (vec (map-indexed
            (fn [i path]
              (-> path
                  (assoc :stroke/profile :brush)
                  (assoc :style/stroke
                         {:color (nth pal (mod i 5))
                          :width (+ 3 (* 2 (noise/perlin2d (* i 0.1) 0.5)))})))
            paths))}))

;; --- 10. Topographic Map ---

(defn ^{:example {:output "art-topo-map.png"
                  :title  "Topographic Map"
                  :desc   "Contour lines colored by elevation."}}
  topo-map []
  (let [thresholds (mapv #(- (* % 0.15) 0.6) (range 9))
        pal (palette/gradient-palette [:color/rgb 30 80 30] [:color/rgb 200 170 120] 9)]
    {:image/size [500 400]
     :image/background [:color/rgb 210 225 210]
     :image/nodes
     (vec (map-indexed
            (fn [i threshold]
              {:node/type :contour
               :contour/bounds [0 0 500 400]
               :contour/opts {:thresholds [threshold]
                              :resolution 3
                              :noise-scale 0.012
                              :seed 42}
               :style/stroke {:color (nth pal i) :width (if (zero? (mod i 3)) 1.5 0.7)}})
            thresholds))}))

;; --- 11. Stained Glass ---

(defn ^{:example {:output "art-stained-glass.png"
                  :title  "Stained Glass"
                  :desc   "Voronoi cells with colored fills and dark outlines."}}
  stained-glass []
  (let [pts (scatter/poisson-disk 20 20 460 360 50 42)
        cells (voronoi/voronoi-cells pts 0 0 500 400)
        rng (java.util.Random. 42)
        warm-colors [[:color/rgb 200 50 50]
                     [:color/rgb 50 80 180]
                     [:color/rgb 220 180 40]
                     [:color/rgb 60 160 80]
                     [:color/rgb 180 60 160]
                     [:color/rgb 200 120 40]
                     [:color/rgb 80 180 200]]]
    {:image/size [500 400]
     :image/background [:color/rgb 30 20 15]
     :image/nodes
     (vec (map-indexed
            (fn [_i cell]
              (-> cell
                  (assoc :style/fill (nth warm-colors (mod (.nextInt rng 100) (count warm-colors))))
                  (assoc :style/stroke {:color [:color/rgb 20 15 10] :width 4})
                  (assoc :node/opacity 0.85)))
            cells))}))

;; --- 12. Risograph ---

(defn ^{:example {:output "art-risograph.png"
                  :title  "Risograph"
                  :desc   "Posterize filter, grain, and bold overlapping shapes."}}
  risograph []
  {:image/size [400 400]
   :image/background [:color/rgb 240 235 220]
   :image/nodes
   [{:node/type :group
     :group/composite :src-over
     :group/filter [:grain 0.15 42]
     :group/children
     [{:node/type :group
       :group/composite :src-over
       :group/filter [:posterize 4]
       :group/children
       [{:node/type :shape/circle
         :circle/center [200.0 200.0]
         :circle/radius 160.0
         :style/fill [:color/rgb 230 80 60]}
        {:node/type :shape/rect
         :rect/xy [120.0 100.0]
         :rect/size [180.0 200.0]
         :style/fill [:color/rgba 40 80 180 0.7]}
        {:node/type :shape/circle
         :circle/center [250.0 250.0]
         :circle/radius 100.0
         :style/fill [:color/rgba 255 200 40 0.6]}]}]}]})

;; --- 13. Thermal ---

(defn ^{:example {:output "art-thermal.png"
                  :title  "Thermal Imaging"
                  :desc   "Gradient-mapped noise field in thermal colors."}}
  thermal []
  (let [thermal-stops [[0.0 [:color/rgb 0 0 30]]
                       [0.2 [:color/rgb 20 0 100]]
                       [0.4 [:color/rgb 180 0 80]]
                       [0.6 [:color/rgb 255 100 0]]
                       [0.8 [:color/rgb 255 220 50]]
                       [1.0 [:color/rgb 255 255 200]]]
        cols 60 rows 40
        cell-w (/ 600.0 cols)
        cell-h (/ 400.0 rows)]
    {:image/size [600 400]
     :image/background [:color/rgb 0 0 0]
     :image/nodes
     (vec (for [row (range rows)
                col (range cols)]
            (let [x (* col cell-w)
                  y (* row cell-h)
                  v (noise/fbm noise/perlin2d (* x 0.008) (* y 0.008)
                      {:octaves 4 :seed 42})
                  t (+ 0.5 (* 0.5 v))
                  color (palette/gradient-map thermal-stops t)]
              {:node/type :shape/rect
               :rect/xy [x y]
               :rect/size [cell-w cell-h]
               :style/fill color})))}))

;; --- 14. Flow Mandala ---

(defn ^{:example {:output "art-flow-mandala.gif"
                  :title  "Flow Mandala"
                  :desc   "Animated flow field with radial symmetry."}}
  flow-mandala []
  {:frames
   (anim/frames 40
     (fn [t]
       (let [paths (flow/flow-field 0 0 400 400
                     {:density 14 :steps 30 :step-length 2
                      :noise-scale (+ 0.004 (* 0.002 (Math/sin (* t 2 Math/PI))))
                      :seed 42})]
         {:image/size [400 400]
          :image/background [:color/rgb 10 10 20]
          :image/nodes
          [{:node/type :symmetry
            :symmetry/type :radial
            :symmetry/n 6
            :symmetry/center [200 200]
            :group/children
            (vec (map-indexed
                   (fn [i path]
                     (-> path
                         (assoc :style/stroke
                                {:color (palette/gradient-map
                                          [[0.0 [:color/rgb 80 40 200]]
                                           [0.5 [:color/rgb 200 50 150]]
                                           [1.0 [:color/rgb 255 200 80]]]
                                          (mod (* i 0.03) 1.0))
                                 :width 0.6})
                         (assoc :node/opacity
                                (+ 0.4 (* 0.4 (Math/sin (+ (* t 4 Math/PI) (* i 0.2))))))))
                   paths))}]})))
   :fps 30})

;; --- 15. Chromatic Scatter ---

(defn ^{:example {:output "art-chromatic-scatter.png"
                  :title  "Chromatic Scatter"
                  :desc   "Per-instance color variation driven by noise."}}
  chromatic-scatter []
  (let [pts (scatter/poisson-disk 30 30 540 340 18 42)
        stops [[0.0 [:color/rgb 20 0 80]]
               [0.3 [:color/rgb 180 0 100]]
               [0.6 [:color/rgb 255 120 0]]
               [1.0 [:color/rgb 255 230 80]]]
        overrides (vary/by-noise pts 0.012 42
                    (fn [v]
                      (let [t (+ 0.5 (* 0.5 v))]
                        {:style/fill (palette/gradient-map stops t)
                         :node/opacity (+ 0.5 (* 0.5 t))})))]
    {:image/size [600 400]
     :image/background [:color/rgb 10 8 20]
     :image/nodes
     [{:node/type :scatter
       :scatter/shape {:node/type :shape/circle
                       :circle/center [0.0 0.0]
                       :circle/radius 7.0}
       :scatter/positions pts
       :scatter/overrides overrides}]}))

;; --- 16. Fractal Forest ---

(defn ^{:example {:output "art-fractal-forest.png"
                  :title  "Fractal Forest"
                  :desc   "L-system trees with distortion and brush strokes."}}
  fractal-forest []
  {:image/size [600 400]
   :image/background [:color/rgb 220 230 210]
   :image/nodes
   [{:node/type :shape/rect
     :rect/xy [0.0 340.0]
     :rect/size [600.0 60.0]
     :style/fill [:color/rgb 80 60 40]}
    {:node/type :lsystem
     :lsystem/axiom "F"
     :lsystem/rules {"F" "FF+[+F-F-F]-[-F+F+F]"}
     :lsystem/iterations 4
     :lsystem/angle 22.5
     :lsystem/length 3.5
     :lsystem/origin [150 340]
     :lsystem/heading -90.0
     :style/stroke {:color [:color/rgb 60 40 20] :width 1}
     :node/transform [[:transform/distort {:type :roughen :amount 1.5 :seed 42}]]}
    {:node/type :lsystem
     :lsystem/axiom "F"
     :lsystem/rules {"F" "F[+F]F[-F][F]"}
     :lsystem/iterations 4
     :lsystem/angle 20.0
     :lsystem/length 4.0
     :lsystem/origin [350 340]
     :lsystem/heading -90.0
     :style/stroke {:color [:color/rgb 40 60 25] :width 1}
     :node/transform [[:transform/distort {:type :roughen :amount 1 :seed 99}]]}
    {:node/type :lsystem
     :lsystem/axiom "F"
     :lsystem/rules {"F" "F[-F]F[+F]F"}
     :lsystem/iterations 4
     :lsystem/angle 25.7
     :lsystem/length 3.0
     :lsystem/origin [500 340]
     :lsystem/heading -90.0
     :style/stroke {:color [:color/rgb 50 80 30] :width 0.8}
     :node/transform [[:transform/distort {:type :roughen :amount 1.2 :seed 77}]]}]})

;; --- 17. Shape Breath ---

(defn ^{:example {:output "art-shape-breath.gif"
                  :title  "Shape Breath"
                  :desc   "Morphing circle to star with color shift."}}
  shape-breath []
  (let [circle-cmds (:path/commands (scene/regular-polygon [200.0 200.0] 140.0 60))
        star-cmds   (:path/commands (scene/star [200.0 200.0] 170.0 65.0 8))]
    {:frames
     (anim/frames 40
       (fn [t]
         (let [t-morph (anim/ping-pong t)
               eased   (anim/ease-in-out-cubic t-morph)
               cmds    (morph/morph-auto circle-cmds star-cmds eased)
               color   (palette/gradient-map
                         [[0.0 [:color/rgb 50 100 220]]
                          [1.0 [:color/rgb 230 80 80]]]
                         eased)]
           {:image/size [400 400]
            :image/background [:color/rgb 15 15 25]
            :image/nodes
            [{:node/type :shape/path
              :path/commands cmds
              :style/fill color
              :effect/glow {:blur 15
                            :color color
                            :opacity 0.3}}]})))
     :fps 24}))

;; --- 18. Wavy Text ---

(defn ^{:example {:output "art-wavy-text.png"
                  :title  "Wavy Text"
                  :desc   "Text with twist warp and radial gradient fill."}}
  wavy-text []
  {:image/size [500 300]
   :image/background [:color/rgb 15 10 30]
   :image/nodes
   [{:node/type :group
     :group/warp {:type :twist :center [250 150] :amount 0.08}
     :group/children
     [(-> (scene/text-outline "TWIST" {:font/family "Serif" :font/size 100
                                        :font/weight :bold} [70 190])
          (assoc :style/fill {:gradient/type :radial
                              :gradient/center [250 -50]
                              :gradient/radius 300
                              :gradient/stops [[0.0 [:color/rgb 255 100 200]]
                                               [0.5 [:color/rgb 255 200 50]]
                                               [1.0 [:color/rgb 50 200 255]]]})
          (assoc :style/stroke {:color [:color/rgba 255 255 255 0.3] :width 1}))]}]})

;; --- 19. Landscape Typography ---

(defn ^{:example {:output "art-landscape-type.png"
                  :title  "Landscape Typography"
                  :desc   "Voronoi mosaic sunset inside text clip mask."}}
  landscape-type []
  (let [pts (scatter/poisson-disk -30 -240 640 290 35 42)
        cells (voronoi/voronoi-cells pts -30 -240 640 290)
        colored-cells (vec (map-indexed
                             (fn [i cell]
                               (let [[_ py] (nth pts i)
                                     t (/ (+ py 240.0) 290.0)
                                     color (palette/gradient-map
                                             [[0.0 [:color/rgb 10 10 50]]
                                              [0.25 [:color/rgb 60 20 100]]
                                              [0.5 [:color/rgb 200 60 40]]
                                              [0.75 [:color/rgb 255 160 30]]
                                              [1.0 [:color/rgb 255 240 180]]]
                                             t)]
                                 (assoc cell :style/fill color)))
                             cells))]
    {:image/size [600 300]
     :image/background [:color/rgb 10 10 20]
     :image/nodes
     [(scene/text-clip "EIDO"
        {:font/family "SansSerif" :font/size 220 :font/weight :bold}
        [30 230]
        (conj colored-cells
              {:node/type :scatter
               :scatter/shape {:node/type :shape/circle
                               :circle/center [0.0 0.0]
                               :circle/radius 2.0
                               :style/fill [:color/rgb 255 255 230]}
               :scatter/positions (scatter/poisson-disk -20 -230 630 120 22 77)
               :scatter/jitter {:x 2 :y 2 :seed 33}}))]}))

;; --- 20. Venn Booleans ---

(defn ^{:example {:output "art-venn-booleans.png"
                  :title  "Venn Booleans"
                  :desc   "Path boolean operations on overlapping circles."}}
  venn-booleans []
  (let [circle-a (:path/commands (scene/regular-polygon [160.0 200.0] 100.0 60))
        circle-b (:path/commands (scene/regular-polygon [260.0 200.0] 100.0 60))
        circle-c (:path/commands (scene/regular-polygon [210.0 130.0] 100.0 60))
        ab-only (path/intersection circle-a circle-b)
        ac-only (path/intersection circle-a circle-c)
        bc-only (path/intersection circle-b circle-c)
        abc     (path/intersection ab-only circle-c)]
    {:image/size [420 350]
     :image/background [:color/rgb 245 240 230]
     :image/nodes
     [{:node/type :shape/path :path/commands circle-a
       :style/fill [:color/rgba 220 60 60 0.4]
       :style/stroke {:color [:color/rgb 180 40 40] :width 2}}
      {:node/type :shape/path :path/commands circle-b
       :style/fill [:color/rgba 60 60 220 0.4]
       :style/stroke {:color [:color/rgb 40 40 180] :width 2}}
      {:node/type :shape/path :path/commands circle-c
       :style/fill [:color/rgba 60 180 60 0.4]
       :style/stroke {:color [:color/rgb 40 140 40] :width 2}}
      {:node/type :shape/path :path/commands abc
       :style/fill [:color/rgb 255 255 255]}]}))

;; --- 21. Organic Mandala ---

(defn ^{:example {:output "art-organic-mandala.png"
                  :title  "Organic Mandala"
                  :desc   "L-system branches with radial symmetry and per-instance color."}}
  organic-mandala []
  (let [branch-cmds (lsystem/lsystem->path-cmds
                      "F" {"F" "F[-F]F[+F]F"} 3 25.7 8.0 [300 300] -90.0)
        n 10
        overrides (vary/by-index n
                    (fn [i] {:style/stroke
                             {:color (palette/gradient-map
                                       [[0.0 [:color/rgb 180 60 30]]
                                        [0.5 [:color/rgb 255 180 50]]
                                        [1.0 [:color/rgb 60 180 80]]]
                                       (/ (double i) n))
                              :width 0.8}}))]
    {:image/size [600 600]
     :image/background [:color/rgb 15 12 20]
     :image/nodes
     [{:node/type :symmetry
       :symmetry/type :radial
       :symmetry/n n
       :symmetry/center [300 300]
       :symmetry/overrides overrides
       :group/children
       [{:node/type :shape/path
         :path/commands branch-cmds
         :style/stroke {:color [:color/rgb 200 100 50] :width 0.8}}]}]}))

(comment
  ;; REPL convenience — render a single example
  (require '[eido.core :as eido])
  (eido/render (ink-landscape) {:output "images/art-ink-landscape.png"})
  (eido/render (mandala) {:output "images/art-mandala.png"})

  ;; Render an animation
  (let [{:keys [frames fps]} (calligraphy-flow)]
    (eido/render frames {:output "images/art-calligraphy-flow.gif" :fps fps})))
