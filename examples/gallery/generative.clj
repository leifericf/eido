(ns gallery.generative
  "Generative art showcase — circle packing, subdivision, reaction-diffusion,
  boids, path aesthetics, weighted palettes, and long-form series."
  {:category "Generative"}
  (:require
    [eido.animate :as anim]
    [eido.color :as color]
    [eido.color.palette :as palette]
    [eido.gen.boids :as boids]
    [eido.gen.ca :as ca]
    [eido.gen.circle :as circle]
    [eido.gen.contour :as contour]
    [eido.gen.flow :as flow]
    [eido.gen.hatch :as hatch]
    [eido.gen.lsystem :as lsystem]
    [eido.gen.noise :as noise]
    [eido.gen.prob :as prob]
    [eido.gen.scatter :as scatter]
    [eido.gen.series :as series]
    [eido.gen.stipple :as stipple]
    [eido.gen.subdivide :as subdivide]
    [eido.gen.vary :as vary]
    [eido.gen.voronoi :as voronoi]
    [eido.path.aesthetic :as aesthetic]
    [eido.path.morph :as morph]
    [eido.scene :as scene]))

;; --- 1. Circle Pack with Weighted Palette ---

(defn ^{:example {:output "gen-circle-pack.png"
                  :title  "Circle Pack"
                  :desc   "Packed circles with weighted sunset palette — rare accents."
                  :tags   ["circle-packing" "palette" "color"]}}
  circle-pack-palette []
  (let [w 600 h 600
        pal    (:sunset palette/palettes)
        weights [3 2 2 1 5]
        circles (circle/circle-pack 20 20 560 560
                  {:min-radius 4 :max-radius 45 :padding 2
                   :max-circles 300 :seed 42})
        colors  (palette/weighted-sample pal weights (count circles) 42)]
    {:image/size [w h]
     :image/background [:color/rgb 250 245 235]
     :image/nodes
     (mapv (fn [{[x y] :center r :radius} color]
             {:node/type     :shape/circle
              :circle/center [x y]
              :circle/radius r
              :style/fill    color
              :style/stroke  {:color [:color/rgba 40 30 20 0.3] :width 0.5}})
           circles colors)}))

;; --- 2. Mondrian Subdivision ---

(defn ^{:example {:output "gen-mondrian.png"
                  :title  "Mondrian Grid"
                  :desc   "Recursive subdivision with primary color accents."
                  :tags   ["subdivision" "color"]}}
  mondrian []
  (let [w 600 h 600
        rects (subdivide/subdivide 10 10 580 580
                {:depth 4 :min-size 40 :padding 6 :seed 77})
        colors [[:color/rgb 245 245 240]
                [:color/rgb 245 245 240]
                [:color/rgb 245 245 240]
                [:color/rgb 220 30 30]
                [:color/rgb 30 60 180]
                [:color/rgb 245 220 40]]]
    {:image/size [w h]
     :image/background [:color/rgb 20 20 20]
     :image/nodes
     (mapv (fn [{[x y rw rh] :rect :as cell}]
             (let [color (prob/pick colors (+ (hash cell) 42))]
               {:node/type    :shape/rect
                :rect/xy      [x y]
                :rect/size    [rw rh]
                :style/fill   color
                :style/stroke {:color [:color/rgb 20 20 20] :width 4}}))
           rects)}))

;; --- 3. Reaction-Diffusion Coral ---

(defn ^{:example {:output "gen-coral.gif"
                  :title  "Coral Growth"
                  :desc   "Gray-Scott reaction-diffusion at the coral preset."
                  :tags   ["reaction-diffusion" "animation" "color"]}}
  coral []
  (let [gw 120 gh 120
        cell-size 5
        w (* gw cell-size) h (* gh cell-size)
        init (ca/rd-grid gw gh :center-seed 42)
        params (:coral ca/rd-presets)
        ;; Pre-compute states at intervals
        states (loop [g init i 0 acc []]
                 (if (>= i 600)
                   acc
                   (let [g' (ca/rd-run g params 10)]
                     (recur g' (+ i 10)
                            (if (zero? (mod i 10))
                              (conj acc g')
                              acc)))))]
    {:frames
     (anim/frames (count states)
       (fn [t]
         (let [idx (min (int (* t (dec (count states)))) (dec (count states)))
               g   (nth states idx)]
           {:image/size [w h]
            :image/background [:color/rgb 10 20 40]
            :image/nodes
            (ca/rd->nodes g cell-size
              (fn [a b]
                (let [v (min 1.0 (* b 4))]
                  [:color/rgb
                   (int (+ 10 (* 80 v)))
                   (int (+ 20 (* 120 v)))
                   (int (+ 40 (* 180 (- 1.0 (* a 0.3)))))])))})
         ))
     :fps 15}))

;; --- 4. Boids Murmuration ---

(defn ^{:example {:output "gen-murmuration.gif"
                  :title  "Murmuration"
                  :desc   "Starling-like flocking with tight cohesion and wide alignment."
                  :tags   ["boids" "animation"]}}
  murmuration []
  (let [w 800 h 600
        config (assoc boids/murmuration
                 :bounds [0 0 w h]
                 :count 150
                 :seed 42)
        frames (boids/simulate-flock config 120 {})]
    {:frames
     (anim/frames (count frames)
       (fn [t]
         (let [idx (min (int (* t (dec (count frames)))) (dec (count frames)))
               flock (nth frames idx)]
           {:image/size [w h]
            :image/background [:color/rgb 200 210 230]
            :image/nodes
            (boids/flock->nodes flock
              {:shape :triangle :size 6
               :style {:style/fill [:color/rgb 30 30 40]}})})))
     :fps 30}))

;; --- 5. Dashed Flow Field ---

(defn ^{:example {:output "gen-dashed-flow.png"
                  :title  "Dashed Flow"
                  :desc   "Flow field streamlines with dashed + smoothed paths."
                  :tags   ["flow-field" "dashing" "smoothing" "palette"]}}
  dashed-flow []
  (let [w 600 h 600
        paths (flow/flow-field 20 20 560 560
                {:density 25 :steps 40 :step-size 3 :seed 42})
        pal (:ocean palette/palettes)]
    {:image/size [w h]
     :image/background [:color/rgb 245 245 240]
     :image/nodes
     (vec
       (mapcat
         (fn [path-node i]
           (let [cmds (:path/commands path-node)
                 smoothed (aesthetic/smooth-commands cmds {:samples 40})
                 dashes (aesthetic/dash-commands smoothed {:dash [12.0 6.0]})
                 color (nth pal (mod i (count pal)))]
             (mapv (fn [dash-cmds]
                     {:node/type     :shape/path
                      :path/commands dash-cmds
                      :style/stroke  {:color color :width 1.5}})
                   (or dashes []))))
         paths (range)))}))

;; --- 6. Series Preview Grid ---

(defn ^{:example {:output "gen-series-grid.png"
                  :title  "Series Preview"
                  :desc   "9 editions of a parametric design driven by eido.gen.series."
                  :tags   ["noise" "color" "math"]}}
  series-grid []
  (let [w 600 h 600
        cell 190
        spec {:hue       {:type :uniform :lo 0.0 :hi 360.0}
              :density   {:type :gaussian :mean 12.0 :sd 3.0}
              :radius    {:type :uniform :lo 3.0 :hi 15.0}
              :style-key {:type :choice :options [:filled :stroked :both]}}
        editions (series/series-range spec 12345 0 9)]
    {:image/size [w h]
     :image/background [:color/rgb 30 30 35]
     :image/nodes
     (vec
       (mapcat
         (fn [params idx]
           (let [col (mod idx 3)
                 row (quot idx 3)
                 ox  (+ 10 (* col (+ cell 5)))
                 oy  (+ 10 (* row (+ cell 5)))
                 {:keys [hue density radius style-key]} params
                 n (int (max 4 density))
                 pts (for [i (range n)
                           j (range n)]
                       [(+ ox (* (/ cell n) (+ i 0.5)))
                        (+ oy (* (/ cell n) (+ j 0.5)))])
                 color [:color/hsl hue 0.7 0.55]]
             (mapv (fn [[x y]]
                     (let [r (+ radius (* 2 (noise/perlin2d (* x 0.03) (* y 0.03))))]
                       (merge
                         {:node/type     :shape/circle
                          :circle/center [x y]
                          :circle/radius (max 1 r)}
                         (case style-key
                           :filled  {:style/fill color}
                           :stroked {:style/stroke {:color color :width 1.5}}
                           :both    {:style/fill color
                                     :style/stroke {:color [:color/rgb 255 255 255] :width 0.5}}))))
                   pts)))
         editions (range)))}))

;; --- 7. Subdivision + Circle Packing Composite ---

(defn ^{:example {:output "gen-subdiv-pack.png"
                  :title  "Subdivided Packing"
                  :desc   "Each subdivision cell filled with a different circle pack and palette."
                  :tags   ["subdivision" "circle-packing" "palette"]}}
  subdiv-pack []
  (let [w 600 h 600
        rects (subdivide/subdivide 10 10 580 580
                {:depth 3 :min-size 80 :padding 6 :seed 55})
        pals [(:ocean palette/palettes) (:sunset palette/palettes)
              (:fire palette/palettes) (:forest palette/palettes)]]
    {:image/size [w h]
     :image/background [:color/rgb 25 25 30]
     :image/nodes
     (vec (mapcat
            (fn [{[x y rw rh] :rect} i]
              (let [pal (nth pals (mod i (count pals)))
                    circles (circle/circle-pack x y rw rh
                              {:min-radius 2 :max-radius (/ (min rw rh) 4)
                               :padding 1.5 :max-circles 60 :seed (+ i 100)})
                    colors (palette/weighted-sample pal [1 2 3 2 1]
                             (count circles) (+ i 200))]
                (mapv (fn [{[cx cy] :center r :radius} c]
                        {:node/type :shape/circle
                         :circle/center [cx cy] :circle/radius r
                         :style/fill c})
                      circles colors)))
            rects (range)))}))

;; --- 8. Voronoi Stained Glass with Weighted Colors ---

(defn ^{:example {:output "gen-voronoi-glass.png"
                  :title  "Voronoi Glass"
                  :desc   "Voronoi cells with jewel-toned weighted palette and dark leading."
                  :tags   ["voronoi" "scatter" "palette" "color"]}}
  voronoi-glass []
  (let [w 600 h 600
        pts (scatter/poisson-disk 20 20 560 560 45 42)
        cells (voronoi/voronoi-cells pts 0 0 w h)
        jewels [[:color/rgb 180 30 50] [:color/rgb 40 100 180]
                [:color/rgb 220 170 30] [:color/rgb 60 160 80]
                [:color/rgb 140 50 160] [:color/rgb 220 120 50]]
        weights [2 3 1 2 1 1]]
    {:image/size [w h]
     :image/background [:color/rgb 20 20 25]
     :image/nodes
     (vec (map-indexed
            (fn [i cell]
              (assoc cell
                :style/fill (prob/pick-weighted jewels weights (+ i 42))
                :style/stroke {:color [:color/rgb 20 20 25] :width 3}))
            cells))}))

;; --- 9. Reaction-Diffusion Spots ---

(defn ^{:example {:output "gen-rd-spots.png"
                  :title  "Leopard Spots"
                  :desc   "Reaction-diffusion spots preset with warm animal coloring."
                  :tags   ["reaction-diffusion" "color"]}}
  rd-spots []
  (let [gw 200 gh 200 cs 3
        g (ca/rd-run (ca/rd-grid gw gh :center-seed 77)
            (:spots ca/rd-presets) 2000)]
    {:image/size [(* gw cs) (* gh cs)]
     :image/background [:color/rgb 210 180 130]
     :image/nodes
     (ca/rd->nodes g cs
       (fn [_a b]
         (let [v (min 1.0 (* b 3))]
           [:color/rgb
            (int (- 210 (* 160 v)))
            (int (- 180 (* 140 v)))
            (int (- 130 (* 90 v)))])))}))

;; --- 10. Jittered Circle Grid ---

(defn ^{:example {:output "gen-jitter-grid.png"
                  :title  "Trembling Grid"
                  :desc   "A perfect grid where each element trembles with gaussian-distributed displacement."
                  :tags   ["jitter" "color" "math"]}}
  jitter-grid []
  (let [w 600 h 600 cols 15 rows 15
        dx (/ (double w) (inc cols))
        dy (/ (double h) (inc rows))
        jitter-x (prob/gaussian (* cols rows) 0.0 8.0 42)
        jitter-y (prob/gaussian (* cols rows) 0.0 8.0 99)]
    {:image/size [w h]
     :image/background [:color/rgb 245 242 235]
     :image/nodes
     (vec (for [row (range rows) col (range cols)]
            (let [i (+ (* row cols) col)
                  cx (+ (* (inc col) dx) (nth jitter-x i))
                  cy (+ (* (inc row) dy) (nth jitter-y i))
                  hue (* (/ (+ cx cy) (+ w h)) 360)]
              {:node/type :shape/circle
               :circle/center [cx cy]
               :circle/radius 12
               :style/fill [:color/hsl hue 0.5 0.6]
               :style/stroke {:color [:color/rgba 0 0 0 0.15] :width 0.5}})))}))

;; --- 11. Flow Field with Noise-Colored Strokes ---

(defn ^{:example {:output "gen-noise-flow.png"
                  :title  "Painted Flow"
                  :desc   "Flow field streamlines colored by noise, smoothed for a painted feel."
                  :tags   ["flow-field" "noise" "smoothing" "color"]}}
  noise-flow []
  (let [w 700 h 500
        paths (flow/flow-field 20 20 660 460
                {:density 18 :steps 50 :step-size 3 :seed 77})]
    {:image/size [w h]
     :image/background [:color/rgb 20 18 25]
     :image/nodes
     (mapv (fn [path-node i]
             (let [cmds (:path/commands path-node)
                   smoothed (aesthetic/smooth-commands cmds {:samples 40})
                   [sx sy] (second (first cmds))
                   hue (* 360 (noise/perlin2d (* sx 0.005) (* sy 0.005) {:seed 10}))]
               {:node/type :shape/path
                :path/commands smoothed
                :node/opacity 0.7
                :style/stroke {:color [:color/hsl (mod (+ 200 (* 80 hue)) 360) 0.7 0.55]
                               :width (+ 1 (* 2 (Math/abs (noise/perlin2d (* i 0.3) 0 {:seed 50}))))}}))
           paths (range))}))

;; --- 12. Boids with Trail Afterimages ---

(defn ^{:example {:output "gen-boids-trails.gif"
                  :title  "Boid Trails"
                  :desc   "Flocking boids leaving fading afterimage trails."
                  :tags   ["boids" "animation" "opacity"]}}
  boids-trails []
  (let [w 600 h 450
        config (assoc boids/classic :count 40 :bounds [0 0 w h] :seed 88 :max-speed 4.0)
        all-frames (boids/simulate-flock config 100 {})
        trail-len 8]
    {:frames
     (anim/frames (count all-frames)
       (fn [t]
         (let [i (min (int (* t (dec (count all-frames)))) (dec (count all-frames)))
               trail-indices (range (max 0 (- i trail-len)) (inc i))]
           {:image/size [w h]
            :image/background [:color/rgb 15 15 25]
            :image/nodes
            (vec (mapcat
                   (fn [fi]
                     (let [age (- i fi)
                           opacity (/ 1.0 (inc (* age 1.5)))
                           flock (nth all-frames fi)]
                       (mapv (fn [{[px py] :pos}]
                               {:node/type :shape/circle
                                :circle/center [px py]
                                :circle/radius (- 4 (* age 0.3))
                                :node/opacity opacity
                                :style/fill [:color/hsl (mod (* fi 3) 360) 0.6 0.6]})
                             (:boids flock))))
                   trail-indices))})))
     :fps 24}))

;; --- 13. Cellular Automata Quilt ---

(defn ^{:example {:output "gen-ca-quilt.png"
                  :title  "CA Quilt"
                  :desc   "Cellular automata snapshots at different generations, stitched into a quilt."
                  :tags   ["cellular-automata" "color"]}}
  ca-quilt []
  (let [w 600 h 600
        cell-w 10 grid-size 28
        base (ca/ca-grid grid-size grid-size :random 42)
        generations [10 25 40 55 70 85 100 120 150]
        grids (reductions (fn [g _] (ca/ca-run g :life 1)) base (range 150))
        selected (mapv #(nth (vec grids) (min % (dec (count grids)))) generations)
        hues [0 40 80 160 200 240 280 320 350]]
    {:image/size [w h]
     :image/background [:color/rgb 30 28 35]
     :image/nodes
     (vec (mapcat
            (fn [grid idx]
              (let [col (mod idx 3) row (quot idx 3)
                    ox (+ 10 (* col 197)) oy (+ 10 (* row 197))
                    cs (/ 185.0 grid-size)
                    hue (nth hues idx)]
                (mapv (fn [node]
                        (let [[nx ny] (:rect/xy node)]
                          (assoc node
                            :rect/xy [(+ ox nx) (+ oy ny)]
                            :rect/size [cs cs]
                            :style/fill [:color/hsl hue 0.5 0.45])))
                      (ca/ca->nodes grid cs {:style/fill [:color/rgb 0 0 0]}))))
            selected (range)))}))

;; --- 14. Stippled Gradient Sphere ---

(defn ^{:example {:output "gen-stipple-gradient.png"
                  :title  "Stippled Sphere"
                  :desc   "A sphere effect using density-varying stipple dots."
                  :tags   ["stipple" "scatter"]}}
  stipple-gradient []
  (let [w 500 h 500 cx 250 cy 250 r 200]
    {:image/size [w h]
     :image/background [:color/rgb 245 242 235]
     :image/nodes
     (let [pts (scatter/poisson-disk 30 30 440 440 6 42)]
       (vec (keep
              (fn [[x y]]
                (let [dx (- x cx) dy (- y cy)
                      dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
                  (when (< dist r)
                    (let [norm (/ dist r)
                          size (* 3.5 (- 1.0 (* norm norm)))]
                      (when (> size 0.3)
                        {:node/type :shape/circle
                         :circle/center [x y]
                         :circle/radius size
                         :style/fill [:color/rgb 30 30 40]})))))
              pts)))}))

;; --- 15. Contour Elevation Map ---

(defn ^{:example {:output "gen-contour-elevation.png"
                  :title  "Elevation Map"
                  :desc   "Dense contour lines with altitude-banded earth tones."
                  :tags   ["contour" "noise" "palette"]}}
  contour-elevation []
  (let [w 600 h 450
        thresholds (mapv #(- (* % 0.1) 0.5) (range 12))
        earth-tones [[:color/rgb 40 80 60] [:color/rgb 60 100 70]
                     [:color/rgb 100 140 80] [:color/rgb 160 180 100]
                     [:color/rgb 200 190 120] [:color/rgb 180 150 100]
                     [:color/rgb 160 120 80] [:color/rgb 140 100 70]
                     [:color/rgb 120 80 50] [:color/rgb 100 60 40]
                     [:color/rgb 80 45 30] [:color/rgb 60 30 20]]]
    {:image/size [w h]
     :image/background [:color/rgb 230 225 210]
     :image/nodes
     [{:node/type :contour
       :contour/bounds [0 0 w h]
       :contour/opts {:thresholds thresholds
                      :resolution 3
                      :noise-scale 0.008
                      :seed 42}
       :style/stroke {:color [:color/rgb 80 60 40] :width 0.8}}]}))

;; --- 16. Gaussian Dot Cloud ---

(defn ^{:example {:output "gen-dot-cloud.png"
                  :title  "Dot Cloud"
                  :desc   "Gaussian-distributed dots forming a soft, nebula-like cloud."
                  :tags   ["scatter" "noise" "opacity" "color"]}}
  dot-cloud []
  (let [w 600 h 600 n 2000
        xs (prob/gaussian n 300.0 100.0 42)
        ys (prob/gaussian n 300.0 100.0 99)
        sizes (prob/gaussian n 3.0 1.5 77)]
    {:image/size [w h]
     :image/background [:color/rgb 10 8 20]
     :image/nodes
     (mapv (fn [x y s i]
             (let [hue (+ 220 (* 60 (noise/perlin2d (* x 0.005) (* y 0.005))))]
               {:node/type :shape/circle
                :circle/center [x y]
                :circle/radius (max 0.5 s)
                :node/opacity (max 0.1 (min 0.7 (/ 3.0 (max 1 s))))
                :style/fill [:color/hsl hue 0.6 0.6]}))
           xs ys sizes (range))}))

;; --- 17. L-System with Dashed Branches ---

(defn ^{:example {:output "gen-lsystem-dashed.png"
                  :title  "Dashed Sapling"
                  :desc   "L-system tree with dashed and jittered branch strokes."
                  :tags   ["l-system" "dashing" "jitter"]}}
  lsystem-dashed []
  (let [w 500 h 600
        cmds (lsystem/lsystem->path-cmds
               "F" {"F" "FF+[+F-F-F]-[-F+F+F]"}
               3 25 10 [250 580] -90)
        jittered (aesthetic/jittered-commands cmds {:amount 1.5 :seed 42})
        dashes (aesthetic/dash-commands jittered {:dash [8.0 4.0]})]
    {:image/size [w h]
     :image/background [:color/rgb 250 248 240]
     :image/nodes
     (mapv (fn [d]
             {:node/type :shape/path :path/commands d
              :style/stroke {:color [:color/rgb 60 80 50] :width 1.2}})
           (or dashes [cmds]))}))

;; --- 18. RD Mitosis Animation ---

(defn ^{:example {:output "gen-mitosis.gif"
                  :title  "Mitosis"
                  :desc   "Reaction-diffusion cell division with the mitosis preset."
                  :tags   ["reaction-diffusion" "animation"]}}
  mitosis []
  (let [gw 150 gh 150 cs 3
        init (ca/rd-grid gw gh :center-seed 42)
        params (:mitosis ca/rd-presets)
        states (loop [g init i 0 acc []]
                 (if (>= i 800)
                   acc
                   (let [g' (ca/rd-run g params 15)]
                     (recur g' (+ i 15)
                            (if (zero? (mod i 15)) (conj acc g') acc)))))]
    {:frames
     (anim/frames (count states)
       (fn [t]
         (let [idx (min (int (* t (dec (count states)))) (dec (count states)))
               g (nth states idx)]
           {:image/size [(* gw cs) (* gh cs)]
            :image/background [:color/rgb 240 235 225]
            :image/nodes
            (ca/rd->nodes g cs
              (fn [a b]
                (let [v (min 1.0 (* b 5))]
                  [:color/rgb
                   (int (- 240 (* 200 v)))
                   (int (- 235 (* 160 v)))
                   (int (- 225 (* 100 v)))])))})))
     :fps 15}))

;; --- 19. Packed Subdivisions with Hatching ---

(defn ^{:example {:output "gen-hatch-subdiv.png"
                  :title  "Hatched Grid"
                  :desc   "Subdivision cells alternating between solid fills and cross-hatching."
                  :tags   ["subdivision" "hatching" "palette"]}}
  hatch-subdiv []
  (let [w 600 h 600
        rects (subdivide/subdivide 15 15 570 570
                {:depth 3 :min-size 50 :padding 5 :seed 33})
        pal (:earth palette/palettes)]
    {:image/size [w h]
     :image/background [:color/rgb 245 240 230]
     :image/nodes
     (mapv (fn [{[x y rw rh] :rect :as cell} i]
             (let [use-hatch (prob/coin 0.45 (+ i 42))
                   color (prob/pick pal (+ i 99))]
               (if use-hatch
                 {:node/type :shape/rect :rect/xy [x y] :rect/size [rw rh]
                  :style/fill {:fill/type :hatch
                               :hatch/angle (prob/pick [30 45 60 -30 -45] (+ i 7))
                               :hatch/spacing (+ 4 (mod i 3))
                               :hatch/stroke-width 0.8
                               :hatch/color color
                               :hatch/background [:color/rgb 245 240 230]}
                  :style/stroke {:color [:color/rgb 60 50 40] :width 1}}
                 {:node/type :shape/rect :rect/xy [x y] :rect/size [rw rh]
                  :style/fill color
                  :style/stroke {:color [:color/rgb 60 50 40] :width 1}})))
           rects (range))}))

;; --- 20. Smooth Morphing Waves ---

(defn ^{:example {:output "gen-morph-wave.gif"
                  :title  "Morphing Waves"
                  :desc   "Layered wave paths that smoothly morph between shapes."
                  :tags   ["smoothing" "animation" "color"]}}
  morph-wave []
  (let [w 600 h 400
        make-wave (fn [seed amp freq phase]
                    (let [pts (for [x (range 0 (inc w) 4)]
                                [x (+ 200 (* amp (Math/sin (+ (* x freq) phase))))])]
                      (into [[:move-to (first pts)]]
                            (mapv (fn [p] [:line-to p]) (rest pts)))))]
    {:frames
     (anim/frames 80
       (fn [t]
         (let [layers
               (vec (for [i (range 6)]
                      (let [phase (* t Math/PI 4)
                            amp (+ 30 (* 25 (Math/sin (+ (* i 0.8) phase))))
                            freq (+ 0.01 (* i 0.003))
                            y-off (* i 30)
                            wave (make-wave i amp freq (+ phase (* i 1.2)))
                            smoothed (aesthetic/smooth-commands wave {:samples 50})
                            hue (mod (+ (* i 50) (* t 120)) 360)]
                        {:node/type :shape/path
                         :path/commands smoothed
                         :node/transform [[:transform/translate 0 (- y-off 60)]]
                         :node/opacity 0.7
                         :style/stroke {:color [:color/hsl hue 0.6 0.5] :width 2.5}})))]
           {:image/size [w h]
            :image/background [:color/rgb 15 12 25]
            :image/nodes layers})))
     :fps 24}))

;; --- 21. Circle Pack Portrait (Text Outline) ---

(defn ^{:example {:output "gen-text-pack.png"
                  :title  "Packed Type"
                  :desc   "The letter A filled with packed circles using a weighted palette."
                  :tags   ["circle-packing" "typography" "palette"]}}
  text-pack []
  (let [w 500 h 500
        text-cmds (eido.text/text->path-commands "A"
                    {:font/family "SansSerif" :font/size 400
                     :font/weight :bold})
        ;; Font coords have Y pointing up; flip and center in canvas
        transform-pt (fn [[x y]] [(+ 70 (* 1.2 x)) (+ 420 (* 1.2 y))])
        scaled (mapv (fn [[cmd & args]]
                       (case cmd
                         :move-to  [:move-to (transform-pt (first args))]
                         :line-to  [:line-to (transform-pt (first args))]
                         :curve-to [:curve-to
                                    (transform-pt (first args))
                                    (transform-pt (second args))
                                    (transform-pt (nth args 2))]
                         :quad-to  [:quad-to
                                    (transform-pt (first args))
                                    (transform-pt (second args))]
                         :close [:close]))
                     text-cmds)
        circles (circle/circle-pack-in-path scaled
                  {:min-radius 2 :max-radius 18 :padding 1.5
                   :max-circles 400 :seed 42})
        pal (:neon palette/palettes)]
    {:image/size [w h]
     :image/background [:color/rgb 15 15 20]
     :image/nodes
     (mapv (fn [{[x y] :center r :radius} i]
             {:node/type :shape/circle
              :circle/center [x y] :circle/radius r
              :style/fill (nth pal (mod i (count pal)))
              :node/opacity 0.85})
           circles (range))}))

;; --- 22. Subdivision Depth Gradient ---

(defn ^{:example {:output "gen-depth-gradient.png"
                  :title  "Depth Gradient"
                  :desc   "Subdivision where deeper cells are darker, creating an organic shadow map."
                  :tags   ["subdivision" "gradients"]}}
  depth-gradient []
  (let [w 600 h 600
        rects (subdivide/subdivide 10 10 580 580
                {:depth 6 :min-size 15 :split-range [0.25 0.75]
                 :padding 2 :seed 42})
        max-d (apply max (map :depth rects))]
    {:image/size [w h]
     :image/background [:color/rgb 240 238 230]
     :image/nodes
     (mapv (fn [{[x y rw rh] :rect d :depth}]
             (let [t (/ (double d) max-d)
                   lightness (- 0.85 (* 0.7 t))]
               {:node/type :shape/rect :rect/xy [x y] :rect/size [rw rh]
                :style/fill [:color/hsl 210 0.3 lightness]}))
           rects)}))

;; --- 23. Boids Seek and Flee ---

(defn ^{:example {:output "gen-boids-seek.gif"
                  :title  "Predator and Prey"
                  :desc   "Boids seeking a point while fleeing another — dynamic tension."
                  :tags   ["boids" "animation"]}}
  boids-seek []
  (let [w 600 h 450
        config {:count 60 :bounds [0 0 w h] :max-speed 3.5 :max-force 0.18
                :separation {:radius 20 :strength 1.5}
                :alignment {:radius 40 :strength 0.8}
                :cohesion {:radius 40 :strength 0.6}
                :bounds-margin 30 :seed 42}
        frames (vec (take 120
                      (iterate
                        (fn [flock]
                          (let [t (* (:tick flock) 0.05)
                                seek-pt [(+ 300 (* 150 (Math/cos t)))
                                         (+ 225 (* 100 (Math/sin (* t 1.3))))]
                                flee-pt [(+ 300 (* 120 (Math/cos (+ t 3.14))))
                                         (+ 225 (* 80 (Math/sin (+ (* t 0.7) 1.5))))]]
                            (boids/step-flock flock
                              (assoc config
                                :seek {:target seek-pt :strength 0.8}
                                :flee {:target flee-pt :radius 80 :strength 1.5}))))
                        (boids/init-flock config))))]
    {:frames
     (anim/frames (count frames)
       (fn [t]
         (let [i (min (int (* t (dec (count frames)))) (dec (count frames)))
               flock (nth frames i)
               tick (* (:tick flock) 0.05)
               seek-pt [(+ 300 (* 150 (Math/cos tick)))
                         (+ 225 (* 100 (Math/sin (* tick 1.3))))]
               flee-pt [(+ 300 (* 120 (Math/cos (+ tick 3.14))))
                         (+ 225 (* 80 (Math/sin (+ (* tick 0.7) 1.5))))]]
           {:image/size [w h]
            :image/background [:color/rgb 240 238 230]
            :image/nodes
            (into
              [{:node/type :shape/circle :circle/center seek-pt :circle/radius 8
                :style/fill [:color/rgb 50 150 50] :node/opacity 0.5}
               {:node/type :shape/circle :circle/center flee-pt :circle/radius 8
                :style/fill [:color/rgb 200 50 50] :node/opacity 0.5}]
              (boids/flock->nodes flock
                {:shape :triangle :size 7
                 :style {:style/fill [:color/rgb 40 50 70]}}))})))
     :fps 24}))

;; --- 24. Noise Terrain Stripes ---

(defn ^{:example {:output "gen-terrain-stripes.png"
                  :title  "Terrain Stripes"
                  :desc   "Horizontal lines displaced by noise, creating a topographic ribbon effect."
                  :tags   ["noise" "smoothing" "color"]}}
  terrain-stripes []
  (let [w 700 h 500 n-lines 40 spacing (/ (double h) (inc n-lines))]
    {:image/size [w h]
     :image/background [:color/rgb 250 248 240]
     :image/nodes
     (vec (for [i (range n-lines)]
            (let [base-y (* (inc i) spacing)
                  pts (for [x (range 0 (inc w) 3)]
                        [x (+ base-y (* 25 (noise/perlin2d (* x 0.008) (* i 0.3) {:seed 42})))])
                  cmds (into [[:move-to (first pts)]]
                             (mapv (fn [p] [:line-to p]) (rest pts)))
                  smoothed (aesthetic/smooth-commands cmds {:samples 60})
                  t (/ (double i) n-lines)]
              {:node/type :shape/path
               :path/commands smoothed
               :style/stroke {:color [:color/hsl (+ 200 (* 40 t)) 0.4 (+ 0.3 (* 0.3 t))]
                              :width 1.5}})))}))

;; --- 25. Weighted Series Showcase ---

(defn ^{:example {:output "gen-series-showcase.png"
                  :title  "Edition Gallery"
                  :desc   "25 editions of a parametric design showing weighted-choice diversity."
                  :tags   ["color" "math"]}}
  series-showcase []
  (let [w 750 h 750 cell 140
        spec {:bg-hue   {:type :uniform :lo 0.0 :hi 360.0}
              :shape    {:type :weighted-choice
                         :options [:circles :lines :dots]
                         :weights [4 3 2]}
              :density  {:type :gaussian :mean 8.0 :sd 2.0}
              :accent   {:type :boolean :probability 0.2}}
        editions (series/series-range spec 999 0 25)]
    {:image/size [w h]
     :image/background [:color/rgb 25 25 30]
     :image/nodes
     (vec (mapcat
            (fn [{:keys [bg-hue shape density accent]} idx]
              (let [col (mod idx 5) row (quot idx 5)
                    ox (+ 10 (* col (+ cell 6)))
                    oy (+ 10 (* row (+ cell 6)))
                    n (max 3 (int density))
                    bg-color [:color/hsl bg-hue 0.15 0.15]
                    fg-color [:color/hsl bg-hue 0.6 0.55]
                    accent-color (if accent [:color/hsl (mod (+ bg-hue 180) 360) 0.8 0.6] fg-color)]
                (into [{:node/type :shape/rect :rect/xy [ox oy] :rect/size [cell cell]
                        :style/fill bg-color}]
                      (case shape
                        :circles
                        (for [i (range n) j (range n)]
                          {:node/type :shape/circle
                           :circle/center [(+ ox (* (/ cell n) (+ i 0.5)))
                                           (+ oy (* (/ cell n) (+ j 0.5)))]
                           :circle/radius (/ cell n 3.0)
                           :style/fill (if (and accent (zero? (mod (+ i j) 5)))
                                         accent-color fg-color)})
                        :lines
                        (for [i (range n)]
                          {:node/type :shape/line
                           :line/from [(+ ox 10) (+ oy (* (/ cell n) (+ i 0.5)))]
                           :line/to [(+ ox cell -10) (+ oy (* (/ cell n) (+ i 0.5)))]
                           :style/stroke {:color fg-color :width 2}})
                        :dots
                        (for [i (range (* n 2))]
                          (let [x (+ ox 10 (* (mod (* i 37) (- cell 20)) 1.0))
                                y (+ oy 10 (* (mod (* i 59) (- cell 20)) 1.0))]
                            {:node/type :shape/circle
                             :circle/center [x y]
                             :circle/radius 2.5
                             :style/fill fg-color}))))))
            editions (range)))}))

;; --- 26. RD Waves ---

(defn ^{:example {:output "gen-rd-ripple.png"
                  :title  "Ripple"
                  :desc   "Reaction-diffusion ripple preset with cool blue coloring."
                  :tags   ["reaction-diffusion" "color"]}}
  rd-ripple []
  (let [gw 200 gh 150 cs 3
        g (ca/rd-run (ca/rd-grid gw gh :center-seed 42)
            (:ripple ca/rd-presets) 1200)]
    {:image/size [(* gw cs) (* gh cs)]
     :image/background [:color/rgb 5 10 30]
     :image/nodes
     (ca/rd->nodes g cs
       (fn [_a b]
         (let [v (min 1.0 (* b 3))]
           [:color/rgb
            (int (* 40 v))
            (int (+ 10 (* 150 v)))
            (int (+ 30 (* 220 v)))])))}))

(comment
  (circle-pack-palette)
  (mondrian)
  (dashed-flow)
  (series-grid)
  (subdiv-pack)
  (voronoi-glass)
  (rd-spots)
  (jitter-grid)
  (noise-flow)
  (boids-trails)
  (ca-quilt)
  (stipple-gradient)
  (contour-elevation)
  (dot-cloud)
  (lsystem-dashed)
  (mitosis)
  (hatch-subdiv)
  (morph-wave)
  (text-pack)
  (depth-gradient)
  (boids-seek)
  (terrain-stripes)
  (series-showcase)
  (rd-ripple))
