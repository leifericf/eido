(ns gallery.showcase
  "Feature showcase — demonstrating new capabilities: OKLAB color,
  simplex noise, watercolor texture, flow collision, Chaikin curves,
  media presets, palette extraction, geometric distributions, and more."
  {:category "Showcase"}
  (:require
    [eido.animate :as anim]
    [eido.color :as color]
    [eido.color.palette :as palette]
    [eido.gen.contour :as contour]
    [eido.gen.flow :as flow]
    [eido.gen.noise :as noise]
    [eido.gen.prob :as prob]
    [eido.gen.scatter :as scatter]
    [eido.path :as path]
    [eido.path.aesthetic :as aesthetic]
    [eido.scene :as scene]
    [eido.scene3d :as s3d]
    [eido.gen.voronoi :as voronoi]
    [eido.texture :as texture]))

;; --- 1. Aurora Borealis ---

(defn ^{:example {:output "showcase-aurora.gif"
                  :title  "Aurora Borealis"
                  :desc   "Shimmering curtains of light driven by layered 3D noise."
                  :tags   ["noise" "animation" "opacity" "color"]}}
  aurora []
  {:frames
   (anim/frames 80
     (fn [t]
       (let [w 600 h 350
             curtains
             (vec
               (for [layer (range 4)
                     x (range 0 w 3)]
                 (let [nx (* x 0.006)
                       ny (* layer 1.5)
                       nz (* t 2.0)
                       wave (noise/perlin3d nx ny nz {:seed (+ 10 layer)})
                       y-base (+ 80 (* layer 30) (* 60 wave))
                       height (+ 80 (* 50 (Math/abs wave)))
                       hue (+ 120 (* layer 25) (* 30 wave))
                       alpha (* (- 0.35 (* layer 0.06))
                                (+ 0.6 (* 0.4 (Math/sin (+ (* x 0.02) (* t 6))))))]
                   {:node/type    :shape/line
                    :line/from    [x y-base]
                    :line/to      [x (+ y-base height)]
                    :node/opacity alpha
                    :style/stroke {:color [:color/hsl hue 0.8 0.6]
                                   :width 3}})))
             stars
             (vec
               (for [i (range 60)]
                 (let [sx (mod (* i 137.508) w)
                       sy (mod (* i 59.7) (* h 0.5))
                       blink (+ 0.3 (* 0.7 (Math/abs (Math/sin (+ (* i 2.1) (* t 8))))))]
                   {:node/type     :shape/circle
                    :circle/center [sx sy]
                    :circle/radius 1.0
                    :node/opacity  blink
                    :style/fill    [:color/rgb 255 255 240]})))]
         {:image/size       [w h]
          :image/background [:color/rgb 5 5 20]
          :image/nodes      (into stars curtains)})))
   :animation/fps 20})

;; --- 2. Crystal Geode ---

(defn ^{:example {:output "showcase-crystal-geode.png"
                  :title  "Crystal Geode"
                  :desc   "3D spherical mesh with faceted crystal surface."
                  :tags   ["3d" "mesh" "deformation"]}}
  crystal-geode []
  (let [proj (s3d/perspective {:scale 120 :origin [250 280]
                               :yaw 0.6 :pitch -0.35 :distance 5})
        mesh (s3d/sphere-mesh 1.2 {:segments 20 :rings 14})
        light {:light/direction [-1 -1 -1] :light/ambient 0.3 :light/intensity 0.8}
        style {:style/fill [:color/rgb 160 120 200]
               :style/stroke {:color [:color/rgb 100 70 150] :width 0.3}}
        group (s3d/render-mesh proj mesh {:style style :light light})]
    {:image/size [500 500]
     :image/background [:color/rgb 15 10 25]
     :image/nodes [group]}))

;; --- 3. Neon Orbit ---

(defn ^{:example {:output "showcase-neon-orbit.gif"
                  :title  "Neon Orbit"
                  :desc   "Glowing particles orbiting a central mass."
                  :tags   ["animation" "glow" "particles"]}}
  neon-orbit []
  {:frames
   (anim/frames 60
     (fn [t]
       (let [w 400 h 400
             cx 200 cy 200
             particles
             (vec
               (for [i (range 30)]
                 (let [phase  (* i 0.2094)
                       radius (+ 60 (* i 4.5))
                       speed  (+ 0.5 (/ 1.0 (+ 1 i)))
                       angle  (+ phase (* t 2.0 Math/PI speed))
                       x      (+ cx (* radius (Math/cos angle)))
                       y      (+ cy (* radius (Math/sin angle)))
                       hue    (mod (+ (* i 12) (* t 360)) 360)
                       r      (+ 2 (/ 10.0 (+ 1 (* i 0.5))))]
                   {:node/type     :shape/circle
                    :circle/center [x y]
                    :circle/radius r
                    :style/fill    [:color/hsl hue 1.0 0.6]
                    :effect/glow   {:blur 8
                                    :color [:color/hsl hue 1.0 0.5]
                                    :opacity 0.5}})))]
         {:image/size [w h]
          :image/background [:color/rgb 5 5 15]
          :image/nodes particles})))
   :animation/fps 20})

;; --- 4. OKLAB Gradient Sunset ---

(defn ^{:example {:output "show-oklab-sunset.png"
                  :title  "OKLAB Sunset"
                  :desc   "Perceptually uniform gradient bands using OKLAB interpolation."
                  :tags   ["oklab" "gradient" "color"]}}
  oklab-sunset []
  (let [bands 50
        w 600 h 400
        band-h (/ (double h) bands)]
    {:image/size [w h]
     :image/background :midnightblue
     :image/nodes
     (mapv (fn [i]
             (let [t (/ (double i) bands)
                   c (if (< t 0.5)
                       (color/lerp-oklab :midnightblue :orangered (* t 2.0))
                       (color/lerp-oklab :orangered :gold (* (- t 0.5) 2.0)))]
               {:node/type :shape/rect
                :rect/xy [0 (* i band-h)]
                :rect/size [w (+ band-h 1)]
                :style/fill c}))
           (range bands))}))

;; --- 5. Simplex Flow ---

(defn ^{:example {:output "show-simplex-flow.png"
                  :title  "Simplex Flow"
                  :desc   "Flow field using OpenSimplex2 noise with collision detection."
                  :tags   ["simplex" "flow-field" "collision"]}}
  simplex-flow []
  (let [w 600 h 400
        lines (flow/flow-field [0 0 w h]
                {:density 12 :steps 80 :step-length 2.5
                 :noise-scale 0.006 :seed 77
                 :collision-distance 6.0})]
    {:image/size [w h]
     :image/background :ivory
     :image/nodes
     (mapv (fn [node]
             (let [cmds (:path/commands node)
                   styled (aesthetic/stylize cmds (aesthetic/ink-preset 42))]
               (assoc node
                 :path/commands styled
                 :style/stroke {:color [:color/rgba 30 40 80 0.7] :width 1.0}
)))
           lines)}))

;; --- 6. Watercolor Bloom ---

(defn ^{:example {:output "show-watercolor-bloom.png"
                  :title  "Watercolor Bloom"
                  :desc   "Translucent layered copies with jitter deformation."
                  :tags   ["watercolor" "texture" "layering"]}}
  watercolor-bloom []
  (let [petals
        (for [i (range 7)]
          (let [angle (* i (/ (* 2 Math/PI) 7))
                cx (+ 300 (* 80 (Math/cos angle)))
                cy (+ 200 (* 80 (Math/sin angle)))
                r 65
                pts (mapv (fn [j]
                            (let [a (* j (/ (* 2 Math/PI) 30))
                                  wobble (+ r (* 8 (Math/sin (* 3 a))))]
                              [(+ cx (* wobble (Math/cos a)))
                               (+ cy (* wobble (Math/sin a)))]))
                          (range 30))
                cmds (conj (scene/points->path pts true) [:close])
                hue (mod (+ 340 (* i 8)) 360)]
            (texture/watercolor
              {:node/type :shape/path
               :path/commands cmds
               :style/fill [:color/hsla hue 0.6 0.55 1.0]}
              {:layers 25 :opacity 0.04 :amount 4.0
               :seed (+ 100 i)})))
        center (texture/watercolor
                 {:node/type :shape/path
                  :path/commands (conj (scene/points->path
                                         (mapv (fn [j]
                                                 (let [a (* j (/ (* 2 Math/PI) 20))]
                                                   [(+ 300 (* 25 (Math/cos a)))
                                                    (+ 200 (* 25 (Math/sin a)))]))
                                               (range 20))
                                         true)
                                       [:close])
                  :style/fill [:color/hsla 50 0.7 0.5 1.0]}
                 {:layers 20 :opacity 0.05 :amount 3.0 :seed 999})]
    {:image/size [600 400]
     :image/background :linen
     :image/nodes (conj (vec petals) center)}))

;; --- 7. Chaikin Spirograph ---

(defn ^{:example {:output "show-chaikin-spiral.png"
                  :title  "Chaikin Spirograph"
                  :desc   "Sharp polygonal spirograph smoothed by Chaikin corner-cutting."
                  :tags   ["chaikin" "smoothing" "geometry"]}}
  chaikin-spiral []
  (let [w 500 h 500
        n 200
        pts (mapv (fn [i]
                    (let [t (/ (double i) n)
                          r1 180 r2 75 k 7
                          a (* t 2 Math/PI k)
                          x (+ 250 (* (+ r1 (* r2 (Math/cos a)))
                                      (Math/cos (* t 2 Math/PI)) 0.6))
                          y (+ 250 (* (+ r1 (* r2 (Math/cos a)))
                                      (Math/sin (* t 2 Math/PI)) 0.6))]
                      [x y]))
                  (range n))
        raw-cmds (scene/points->path pts false)
        smooth (aesthetic/chaikin-commands raw-cmds {:iterations 4})]
    {:image/size [w h]
     :image/background [:color/rgb 15 12 25]
     :image/nodes
     [{:node/type :shape/path
       :path/commands smooth
       :style/stroke {:color [:color/rgba 100 200 255 0.6] :width 0.8}
}]}))

;; --- 8. Palette Wheel ---

(defn ^{:example {:output "show-palette-wheel.png"
                  :title  "Palette Wheel"
                  :desc   "Warm, cool, vivid, and muted variations of a single palette."
                  :tags   ["palette" "manipulation" "oklab"]}}
  palette-wheel []
  (let [base (:sunset palette/palettes)
        variations [["Original" base]
                    ["Warmer" (palette/warmer base 20)]
                    ["Cooler" (palette/cooler base 20)]
                    ["Muted" (palette/muted base 0.4)]
                    ["Vivid" (palette/vivid base 0.3)]
                    ["Darker" (palette/darker base 0.3)]
                    ["Lighter" (palette/lighter base 0.3)]]
        bar-h 40
        w 500 h (* bar-h (count variations))
        n (count base)]
    {:image/size [w h]
     :image/background :white
     :image/nodes
     (vec (mapcat
       (fn [row-i [_label pal]]
         (let [bar-w (/ (double w) n)]
           (mapv (fn [ci]
                   {:node/type :shape/rect
                    :rect/xy [(* ci bar-w) (* row-i bar-h)]
                    :rect/size [bar-w bar-h]
                    :style/fill (nth pal ci)})
                 (range n))))
       (range) variations))}))

;; --- 9. Stippled Orbits ---

(defn ^{:example {:output "show-stippled-orbits.png"
                  :title  "Stippled Orbits"
                  :desc   "Points scattered on concentric circles with jitter."
                  :tags   ["geometric" "scatter" "circle"]}}
  stippled-orbits []
  (let [w 500 h 500
        cx 250.0 cy 250.0
        all-pts (into []
                  (mapcat
                    (fn [ri]
                      (let [r (* ri 28)
                            n-pts (+ 10 (* ri 8))
                            pts (prob/scatter-on-circle n-pts r [cx cy] (+ 42 ri))]
                        (scatter/jitter pts {:amount 2.5 :seed (+ 100 ri)})))
                    (range 1 9)))]
    {:image/size [w h]
     :image/background [:color/rgb 20 18 30]
     :image/nodes
     (mapv (fn [[x y]]
             {:node/type :shape/circle
              :circle/center [x y]
              :circle/radius 1.5
              :style/fill [:color/rgba 200 180 255 0.7]})
           all-pts)}))

;; --- 10. Simplex Contour Terrain ---

(defn ^{:example {:output "show-contour-terrain.png"
                  :title  "Simplex Contour Terrain"
                  :desc   "Topographic contour lines from simplex noise."
                  :tags   ["contour" "simplex" "terrain"]}}
  contour-terrain []
  (let [w 600 h 400
        levels (mapv (fn [i] (- -0.5 (* i -0.12))) (range 10))
        pal (palette/sort-by-lightness
              [[:color/rgb 30 60 30] [:color/rgb 60 120 50]
               [:color/rgb 140 170 60] [:color/rgb 200 190 100]
               [:color/rgb 180 140 80] [:color/rgb 140 100 60]
               [:color/rgb 100 80 60] [:color/rgb 220 210 190]
               [:color/rgb 240 240 245] [:color/rgb 255 255 255]])]
    {:image/size [w h]
     :image/background [:color/rgb 20 30 20]
     :image/nodes
     (vec (mapcat
       (fn [li level]
         (let [contours (contour/contour-lines noise/simplex2d 0 0 w h
                          {:threshold level :resolution 4
                           :noise-scale 0.008 :seed 42})
               c (nth pal (min li (dec (count pal))))]
           (mapv (fn [path-node]
                   (assoc path-node
                     :style/stroke {:color c :width 1.2}
))
                 contours)))
       (range) levels))}))

;; --- 11. Pencil Sketch ---

(defn ^{:example {:output "show-pencil-sketch.png"
                  :title  "Pencil Sketch"
                  :desc   "Geometric forms rendered with pencil media preset."
                  :tags   ["pencil" "preset" "aesthetic"]}}
  pencil-sketch []
  (let [w 500 h 500
        shapes (for [i (range 5)]
                 (let [cx (+ 100 (* i 75))
                       cy (+ 250 (* 40 (Math/sin (* i 0.8))))
                       r (+ 30 (* 15 (Math/sin (* i 1.3))))
                       pts (mapv (fn [j]
                                   (let [a (* j (/ (* 2 Math/PI) 6))]
                                     [(+ cx (* r (Math/cos a)))
                                      (+ cy (* r (Math/sin a)))]))
                                 (range 6))
                       cmds (conj (scene/points->path pts true) [:close])
                       styled (aesthetic/stylize cmds
                                [{:op :smooth :samples 24}
                                 {:op :jitter :amount 0.6 :density 0.8
                                  :seed (+ 42 i)}])]
                   {:node/type :shape/path
                    :path/commands styled
                    :style/stroke {:color [:color/rgba 50 40 30 0.8]
                                   :width 1.0}
}))]
    {:image/size [w h]
     :image/background [:color/rgb 250 245 235]
     :image/nodes (vec shapes)}))

;; --- 12. Inset Frames ---

(defn ^{:example {:output "show-inset-frames.png"
                  :title  "Inset Frames"
                  :desc   "Nested polygon insets creating concentric frames."
                  :tags   ["inset" "polygon" "nesting"]}}
  inset-frames []
  (let [w 500 h 500
        outer [[50 50] [450 50] [450 450] [50 450]]
        layers 12
        pal (palette/sort-by-lightness (:earth palette/palettes))]
    {:image/size [w h]
     :image/background :ivory
     :image/nodes
     (vec (keep
       (fn [i]
         (let [poly (path/inset outer (* i 15))]
           (when (>= (count poly) 3)
             {:node/type :shape/path
              :path/commands (conj (scene/points->path poly true) [:close])
              :style/fill (nth pal (mod i (count pal)))
              :style/stroke {:color [:color/rgba 40 30 20 0.6]
                             :width 1}})))
       (range layers)))}))

;; --- 13. Disc Scatter ---

(defn ^{:example {:output "show-disc-scatter.png"
                  :title  "Disc Scatter"
                  :desc   "Points uniformly inside a disc — colored by distance."
                  :tags   ["geometric" "distribution" "circle"]}}
  disc-scatter []
  (let [w 500 h 500
        pts (prob/scatter-in-circle 800 200.0 [250.0 250.0] 42)]
    {:image/size [w h]
     :image/background [:color/rgb 10 10 15]
     :image/nodes
     (mapv (fn [[x y]]
             (let [dx (- x 250) dy (- y 250)
                   dist (Math/sqrt (+ (* dx dx) (* dy dy)))
                   t (/ dist 200.0)
                   c (color/lerp-oklab :deepskyblue :hotpink t)]
               {:node/type :shape/circle
                :circle/center [x y]
                :circle/radius (+ 1.0 (* 2.0 (- 1.0 t)))
                :style/fill c}))
           pts)}))

;; --- 14. Pareto Cityscape ---

(defn ^{:example {:output "show-pareto-city.png"
                  :title  "Pareto Cityscape"
                  :desc   "Building heights follow a Pareto distribution."
                  :tags   ["pareto" "distribution" "architecture"]}}
  pareto-city []
  (let [w 600 h 400
        n 40
        heights (prob/pareto n 1.5 20.0 42)
        bar-w (/ (double w) n)
        max-h 350.0]
    {:image/size [w h]
     :image/background [:color/rgb 15 20 40]
     :image/nodes
     (mapv (fn [i]
             (let [raw-h (min max-h (double (nth heights i)))
                   x (* i bar-w)
                   y (- h raw-h)
                   t (/ raw-h max-h)
                   c (color/lerp-oklab [:color/rgb 40 60 100]
                                       [:color/rgb 255 200 80] t)]
               {:node/type :shape/rect
                :rect/xy [x y]
                :rect/size [(- bar-w 2) raw-h]
                :style/fill c}))
           (range n))}))

;; --- 15. OKLCH Hue Ring ---

(defn ^{:example {:output "show-oklch-ring.png"
                  :title  "OKLCH Hue Ring"
                  :desc   "Full hue rotation in perceptually uniform OKLCH space."
                  :tags   ["oklch" "color-space" "hue"]}}
  oklch-ring []
  (let [w 500 h 500
        n 72
        cx 250 cy 250
        outer-r 200 inner-r 130]
    {:image/size [w h]
     :image/background [:color/rgb 30 30 30]
     :image/nodes
     (mapv (fn [i]
             (let [hue (* (/ (double i) n) 360.0)
                   a1 (* (/ (double i) n) 2 Math/PI)
                   a2 (* (/ (double (inc i)) n) 2 Math/PI)
                   pts [[(+ cx (* outer-r (Math/cos a1)))
                         (+ cy (* outer-r (Math/sin a1)))]
                        [(+ cx (* outer-r (Math/cos a2)))
                         (+ cy (* outer-r (Math/sin a2)))]
                        [(+ cx (* inner-r (Math/cos a2)))
                         (+ cy (* inner-r (Math/sin a2)))]
                        [(+ cx (* inner-r (Math/cos a1)))
                         (+ cy (* inner-r (Math/sin a1)))]]]
               {:node/type :shape/path
                :path/commands (conj (scene/points->path pts true) [:close])
                :style/fill (color/oklch 0.7 0.15 hue)}))
           (range n))}))

;; --- 16. Margin Composition ---

(defn ^{:example {:output "show-margin-composition.png"
                  :title  "Margin Composition"
                  :desc   "Dense flow field cropped with scene margin control."
                  :tags   ["margin" "composition" "flow-field"]}}
  margin-composition []
  (let [w 500 h 500
        margin 40
        lines (flow/flow-field [0 0 w h]
                {:density 8 :steps 100 :step-length 2
                 :noise-scale 0.008 :seed 33})
        pal (:ocean palette/palettes)]
    (scene/with-margin
      {:image/size [w h]
       :image/background :white
       :image/nodes
       (vec (map-indexed
         (fn [i node]
           (-> node
               (assoc :style/stroke {:color (nth pal (mod i (count pal)))
                                     :width 1.2})
               (dissoc :style/fill)))
         lines))}
      margin)))

;; --- 17. Simplified Curves ---

(defn ^{:example {:output "show-simplified-paths.png"
                  :title  "Simplified Curves"
                  :desc   "Douglas-Peucker simplification at varying epsilon."
                  :tags   ["simplify" "douglas-peucker" "optimization"]}}
  simplified-paths []
  (let [w 600 h 400
        n 80
        raw-pts (mapv (fn [i]
                        (let [x (* (/ (double i) n) w)
                              y (+ 200 (* 60 (Math/sin (* i 0.15)))
                                       (* 30 (noise/simplex2d (* i 0.1) 0.5)))]
                          [x y]))
                      (range n))
        epsilons [0 2 8 25]
        offsets [50 150 250 350]]
    {:image/size [w h]
     :image/background :white
     :image/nodes
     (vec (mapcat
       (fn [eps y-off]
         (let [pts (if (zero? eps)
                     raw-pts
                     (path/simplify raw-pts (double eps)))
               offset-pts (mapv (fn [[x y]]
                                  [x (+ (- y 200) y-off)])
                                pts)
               cmds (scene/points->path offset-pts false)]
           [{:node/type :shape/path
             :path/commands cmds
             :style/stroke {:color :steelblue :width 1.5}}]))
       epsilons offsets))}))

;; --- 18. Split & Color ---

(defn ^{:example {:output "show-split-paths.png"
                  :title  "Split & Color"
                  :desc   "A single curve split into segments, each a different hue."
                  :tags   ["split" "curve" "color"]}}
  split-paths []
  (let [w 600 h 400
        pts (mapv (fn [i]
                    (let [x (* (/ (double i) 60) w)
                          y (+ 200 (* 120 (Math/sin (* i 0.12)))
                                   (* 40 (Math/cos (* i 0.3))))]
                      [x y]))
                  (range 60))
        cmds (scene/points->path pts false)
        segments (path/split-at-length cmds 80)
        n-seg (count segments)
        pal (mapv #(color/oklch 0.65 0.2 %)
                  (range 0 360 (/ 360.0 (max 1 n-seg))))]
    {:image/size [w h]
     :image/background [:color/rgb 245 245 250]
     :image/nodes
     (vec (map-indexed
       (fn [i seg]
         {:node/type :shape/path
          :path/commands seg
          :style/stroke {:color (nth pal (mod i (count pal))) :width 3}})
       segments))}))

;; --- 19. Ink Botanical ---

(defn ^{:example {:output "show-ink-botanical.png"
                  :title  "Ink Botanical"
                  :desc   "Organic leaf shapes with ink media preset."
                  :tags   ["ink" "preset" "botanical"]}}
  ink-botanical []
  (let [w 500 h 500
        leaf (fn [cx cy size angle seed]
               (let [pts (mapv
                           (fn [i]
                             (let [t (/ (double i) 20)
                                   a (* t 2 Math/PI)
                                   r (* size (+ 0.5 (* 0.5
                                         (Math/cos (* 2 a)))))]
                               [(+ cx (* r (Math/cos (+ a angle))))
                                (+ cy (* r (Math/sin (+ a angle))))]))
                           (range 20))
                     cmds (conj (scene/points->path pts true) [:close])
                     styled (aesthetic/stylize cmds
                              (aesthetic/ink-preset seed))]
                 {:node/type :shape/path
                  :path/commands styled
                  :style/stroke {:color [:color/rgba 20 60 20 0.8]
                                 :width 1.2}
                  :style/fill [:color/rgba 60 120 40 0.15]}))
        leaves (for [i (range 8)]
                 (let [a (* i (/ (* 2 Math/PI) 8))
                       r (+ 80 (* 30 (Math/sin (* i 2.1))))]
                   (leaf (+ 250 (* r (Math/cos a)))
                         (+ 250 (* r (Math/sin a)))
                         (+ 40 (* 20 (Math/sin (* i 1.7))))
                         a (+ 42 i))))]
    {:image/size [w h]
     :image/background [:color/rgb 250 248 240]
     :image/nodes (vec leaves)}))

;; --- 20. Eased Gradients ---

(defn ^{:example {:output "show-eased-gradient.png"
                  :title  "Eased Gradients"
                  :desc   "Non-linear gradient interpolation with different easing."
                  :tags   ["gradient" "easing" "color"]}}
  eased-gradients []
  (let [w 600 h 400
        stops [[0.0 :navy] [0.5 :crimson] [1.0 :gold]]
        easings [nil
                 (fn [t] (* t t))
                 (fn [t] (* t t t))
                 (fn [t] (Math/sqrt t))]
        strip-h (/ (double h) (count easings))
        cols 200]
    {:image/size [w h]
     :image/background :white
     :image/nodes
     (vec (mapcat
       (fn [ei easing]
         (let [y0 (* ei strip-h)
               col-w (/ (double w) cols)]
           (mapv (fn [ci]
                   (let [t (/ (double ci) cols)
                         c (palette/gradient-map stops t
                             (when easing {:easing easing}))]
                     {:node/type :shape/rect
                      :rect/xy [(* ci col-w) y0]
                      :rect/size [(+ col-w 1) strip-h]
                      :style/fill c}))
                 (range cols))))
       (range) easings))}))

;; --- 21. Clipped Flow ---

(defn ^{:example {:output "show-clipped-flow.png"
                  :title  "Clipped Flow"
                  :desc   "Flow field paths trimmed to a circular boundary."
                  :tags   ["clipping" "flow-field" "bounds"]}}
  clipped-flow []
  (let [w 500 h 500
        cx 250.0 cy 250.0 r 180.0
        lines (flow/flow-field [0 0 w h]
                {:density 10 :steps 60 :step-length 2
                 :noise-scale 0.007 :seed 55})]
    {:image/size [w h]
     :image/background [:color/rgb 250 248 240]
     :image/nodes
     (into
       [{:node/type :shape/circle
         :circle/center [cx cy]
         :circle/radius r
         :style/stroke {:color [:color/rgba 60 60 60 0.3] :width 1}}]
       (mapcat
         (fn [node]
           (let [cmds (:path/commands node)
                 trimmed (path/trim-to-bounds cmds
                           [(- cx r) (- cy r) (* 2 r) (* 2 r)])]
             (mapv (fn [seg]
                     {:node/type :shape/path
                      :path/commands seg
                      :style/stroke {:color [:color/rgba 40 80 140 0.5]
                                     :width 1}})
                   trimmed)))
         lines))}))

;; --- 22. Triangular Peaks ---

(defn ^{:example {:output "show-triangular-mtns.png"
                  :title  "Triangular Peaks"
                  :desc   "Mountain silhouettes with triangular-distributed heights."
                  :tags   ["triangular" "distribution" "landscape"]}}
  triangular-mountains []
  (let [w 600 h 400
        ranges
        (for [row (range 4)]
          (let [base-y (+ 200 (* row 50))
                peaks (sort (prob/triangular 12 0.0 (double w) (* w 0.4) (+ 42 row)))
                peak-h (prob/triangular 12 30.0 (- 200.0 (* row 30)) 80.0 (+ 99 row))
                alpha (- 1.0 (* row 0.2))
                blue (+ 30 (* row 40))
                pts (vec (concat
                      [[0 base-y]]
                      (mapcat (fn [x ph]
                                [[x base-y] [x (- base-y (double ph))]])
                              peaks peak-h)
                      [[(double w) base-y]]))]
            {:node/type :shape/path
             :path/commands (conj (scene/points->path pts false) [:close])
             :style/fill [:color/rgba blue (+ 40 (* row 20))
                          (+ 80 (* row 20)) alpha]}))]
    {:image/size [w h]
     :image/background [:color/rgb 200 210 230]
     :image/nodes (vec ranges)}))

;; --- 23. Contrast Grid ---

(defn ^{:example {:output "show-contrast-grid.png"
                  :title  "Contrast Grid"
                  :desc   "Color pairs showing WCAG contrast ratios."
                  :tags   ["contrast" "accessibility" "color"]}}
  contrast-grid []
  (let [w 600 h 280
        colors [:navy :red :forestgreen :purple :darkorange :teal]
        bg-colors [:white :lightyellow :lavender]
        cell-w 95 cell-h 55]
    {:image/size [w h]
     :image/background :whitesmoke
     :image/nodes
     (vec (mapcat
       (fn [bi bg]
         (mapcat
           (fn [ci fg]
             (let [x (+ 15 (* ci cell-w))
                   y (+ 15 (* bi (+ cell-h 20)))]
               [{:node/type :shape/rect
                 :rect/xy [x y]
                 :rect/size [cell-w cell-h]
                 :style/fill bg
                 :style/stroke {:color :gray :width 0.5}}
                {:node/type :shape/rect
                 :rect/xy [(+ x 10) (+ y 10)]
                 :rect/size [30 30]
                 :style/fill fg}]))
           (range) colors))
       (range) bg-colors))}))

(comment
  (aurora)
  (crystal-geode)
  (neon-orbit)
  (oklab-sunset)
  (simplex-flow)
  (watercolor-bloom)
  (chaikin-spiral)
  (palette-wheel)
  (stippled-orbits)
  (contour-terrain)
  (pencil-sketch)
  (inset-frames)
  (disc-scatter)
  (pareto-city)
  (oklch-ring)
  (margin-composition)
  (simplified-paths)
  (split-paths)
  (ink-botanical)
  (eased-gradients)
  (clipped-flow)
  (triangular-mountains)
  (contrast-grid))
