(ns gallery.showcase
  "Creative showcase — pushing Eido's features in novel combinations."
  {:category "Showcase"}
  (:require
    [eido.animate :as anim]
    [eido.flow :as flow]
    [eido.noise :as noise]
    [eido.palette :as palette]
    [eido.scatter :as scatter]
    [eido.scene :as scene]
    [eido.scene3d :as s3d]
    [eido.voronoi :as voronoi]))

;; --- 1. Aurora Borealis ---

(defn ^{:example {:output "showcase-aurora.gif"
                  :title  "Aurora Borealis"
                  :desc   "Shimmering curtains of light driven by layered 3D noise."}}
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
                    :circle/radius (if (zero? (mod i 7)) 1.5 0.6)
                    :node/opacity  blink
                    :style/fill    [:color/rgb 255 255 240]})))]
         {:image/size [w h]
          :image/background [:color/rgb 5 5 20]
          :image/nodes (into stars curtains)})))
   :fps 24})

;; --- 2. Moiré Interference ---

(defn ^{:example {:output "showcase-moire.gif"
                  :title  "Moire Interference"
                  :desc   "Two rotating line grids creating mesmerizing interference patterns."}}
  moire []
  {:frames
   (anim/frames 90
     (fn [t]
       (let [size 400
             cx 200 cy 200
             angle1 (* t Math/PI 0.5)
             angle2 (- (* t Math/PI 0.5))
             make-grid
             (fn [angle alpha]
               (vec
                 (for [i (range -30 31)]
                   (let [offset (* i 8)
                         cos-a (Math/cos angle) sin-a (Math/sin angle)
                         x1 (+ cx (* offset cos-a) (* -300 sin-a))
                         y1 (+ cy (* offset sin-a) (* 300 cos-a))
                         x2 (+ cx (* offset cos-a) (* 300 sin-a))
                         y2 (+ cy (* offset sin-a) (* -300 cos-a))]
                     {:node/type    :shape/line
                      :line/from    [x1 y1]
                      :line/to      [x2 y2]
                      :node/opacity alpha
                      :style/stroke {:color [:color/rgb 255 255 255]
                                     :width 1.5}}))))]
         {:image/size [size size]
          :image/background [:color/rgb 0 0 0]
          :image/nodes (into (make-grid angle1 0.5)
                             (make-grid angle2 0.5))})))
   :fps 30})

;; --- 3. Bioluminescent Jellyfish ---

(defn ^{:example {:output "showcase-jellyfish.gif"
                  :title  "Bioluminescent Jellyfish"
                  :desc   "Pulsing jellyfish with flowing tentacles and soft glow."}}
  jellyfish []
  {:frames
   (anim/frames 60
     (fn [t]
       (let [cx 250 cy 160
             pulse (+ 0.85 (* 0.15 (Math/sin (* t 2 Math/PI))))
             bell-r (* 70 pulse)
             ;; Bell dome — layered translucent arcs
             bell (vec
                    (for [i (range 8)]
                      (let [r (- bell-r (* i 4))
                            hue (+ 260 (* i 8))
                            alpha (- 0.25 (* i 0.025))]
                        {:node/type     :shape/arc
                         :arc/center    [cx cy]
                         :arc/rx        r
                         :arc/ry        (* r 0.65)
                         :arc/start     180 :arc/extent 180
                         :arc/mode      :chord
                         :node/opacity  alpha
                         :style/fill    [:color/hsl hue 0.7 0.6]})))
             ;; Tentacles — sine-driven flowing curves
             tentacles
             (vec
               (for [arm (range 7)]
                 (let [base-x (+ cx (* (- arm 3) 18))
                       pts (for [seg (range 20)]
                             (let [seg-t (/ seg 19.0)
                                   sway (* 25 seg-t
                                            (Math/sin (+ (* seg-t 4)
                                                         (* t 2 Math/PI)
                                                         (* arm 0.9))))
                                   x (+ base-x sway)
                                   y (+ (+ cy (* bell-r 0.4)) (* seg-t 180))]
                               [x y]))
                       hue (+ 250 (* arm 15))]
                   {:node/type     :shape/path
                    :path/commands (into [[:move-to (first pts)]]
                                         (mapv #(vector :line-to %) (rest pts)))
                    :stroke/profile :pointed
                    :node/opacity  (- 0.6 (* arm 0.05))
                    :style/stroke  {:color [:color/hsl hue 0.6 0.55]
                                    :width (- 5 (* arm 0.5))}})))
             ;; Bioluminescent specks
             specks (vec
                      (for [i (range 20)]
                        (let [angle (+ (* i 0.314) (* t 1.5))
                              r (+ 30 (* 60 (Math/abs (Math/sin (+ (* i 0.7) t)))))
                              sx (+ cx (* r (Math/cos angle)))
                              sy (+ cy (* r 0.5 (Math/sin angle)) 40)
                              alpha (* 0.7 (Math/abs (Math/sin (+ (* i 1.3) (* t 5)))))]
                          {:node/type     :shape/circle
                           :circle/center [sx sy]
                           :circle/radius 2
                           :node/opacity  alpha
                           :style/fill    [:color/hsl 180 0.9 0.7]})))]
         {:image/size [500 450]
          :image/background [:color/rgb 3 5 20]
          :image/nodes (into [] (concat tentacles bell specks))})))
   :fps 24})

;; --- 4. Bauhaus Composition ---

(defn ^{:example {:output "showcase-bauhaus.png"
                  :title  "Bauhaus Composition"
                  :desc   "Bold geometric abstraction in primary colors with overlapping forms."}}
  bauhaus []
  (let [rng (java.util.Random. 42)]
    {:image/size [500 500]
     :image/background [:color/rgb 245 240 230]
     :image/nodes
     [;; Large red circle
      {:node/type     :shape/circle
       :circle/center [170 200]
       :circle/radius 120
       :style/fill    [:color/rgb 210 35 35]}
      ;; Blue rectangle
      {:node/type     :shape/rect
       :rect/xy       [250 100]
       :rect/size     [180 280]
       :style/fill    [:color/rgb 30 60 160]}
      ;; Yellow triangle
      (assoc (scene/triangle [350 80] [480 350] [220 350])
             :style/fill [:color/rgb 240 200 30])
      ;; Black lines — constructivist grid
      {:node/type :shape/line :line/from [0 350] :line/to [500 350]
       :style/stroke {:color [:color/rgb 20 20 20] :width 4}}
      {:node/type :shape/line :line/from [250 0] :line/to [250 500]
       :style/stroke {:color [:color/rgb 20 20 20] :width 4}}
      {:node/type :shape/line :line/from [0 180] :line/to [500 180]
       :style/stroke {:color [:color/rgb 20 20 20] :width 2}}
      ;; Small accent circles
      {:node/type     :shape/circle
       :circle/center [400 150]
       :circle/radius 30
       :style/fill    [:color/rgb 20 20 20]}
      {:node/type     :shape/circle
       :circle/center [120 400]
       :circle/radius 45
       :style/fill    [:color/rgb 240 200 30]
       :style/stroke  {:color [:color/rgb 20 20 20] :width 3}}
      ;; White overlay circle for depth
      {:node/type     :shape/circle
       :circle/center [300 300]
       :circle/radius 65
       :style/fill    [:color/rgb 245 240 230]
       :style/stroke  {:color [:color/rgb 20 20 20] :width 3}}
      ;; Thin diagonal
      {:node/type :shape/line :line/from [0 0] :line/to [500 500]
       :style/stroke {:color [:color/rgb 20 20 20] :width 1}}]}))

;; --- 5. Crystal Geode ---

(defn ^{:example {:output "showcase-geode.png"
                  :title  "Crystal Geode"
                  :desc   "Voronoi tessellation with gem-like gradient fills and dark edges."}}
  geode []
  (let [pts (scatter/poisson-disk 30 30 470 470 28 77)
        cells (voronoi/voronoi-cells pts 0 0 500 500)
        rng (java.util.Random. 77)
        gem-stops [[0.0 [:color/rgb 20 0 40]]
                   [0.2 [:color/rgb 80 20 120]]
                   [0.4 [:color/rgb 40 100 180]]
                   [0.6 [:color/rgb 60 200 180]]
                   [0.8 [:color/rgb 180 80 200]]
                   [1.0 [:color/rgb 255 200 255]]]]
    {:image/size [500 500]
     :image/background [:color/rgb 10 5 15]
     :image/nodes
     (vec
       (for [[i cell] (map-indexed vector cells)]
         (let [[px py] (nth pts i)
               ;; Distance from center determines color
               dx (- px 250) dy (- py 250)
               dist (/ (Math/sqrt (+ (* dx dx) (* dy dy))) 350.0)
               t (min 1.0 dist)
               color (palette/gradient-map gem-stops t)]
           (-> cell
               (assoc :style/fill color)
               (assoc :style/stroke {:color [:color/rgb 8 3 12] :width 2.5})
               (assoc :node/opacity (+ 0.7 (* 0.3 (- 1.0 t))))))))}))

;; --- 6. Rotating Gem ---

(defn ^{:example {:output "showcase-gem-3d.gif"
                  :title  "Rotating Gem"
                  :desc   "Faceted 3D icosahedron-like gem with colorful face shading."}}
  rotating-gem []
  {:frames
   (anim/frames 60
     (fn [t]
       (let [angle (* t 2 Math/PI)
             proj (s3d/perspective
                    {:scale 110 :origin [200 200]
                     :yaw angle :pitch -0.35 :distance 5})
             light {:light/direction [0.8 1.0 0.5]
                    :light/ambient   0.15
                    :light/intensity 0.85}
             ;; Torus knot — visually interesting rotating shape
             torus (-> (s3d/torus-mesh 1.0 0.4 24 12)
                       (s3d/rotate-mesh :x (* 0.3 (Math/sin (* t Math/PI)))))]
         {:image/size [400 400]
          :image/background [:color/rgb 8 8 18]
          :image/nodes
          [(s3d/render-mesh proj torus
             {:style {:style/fill   [:color/rgb 80 180 220]
                      :style/stroke {:color [:color/rgb 140 230 255]
                                     :width 0.3}}
              :light light})]})))
   :fps 30})

;; --- 7. Sound Wave Visualizer ---

(defn ^{:example {:output "showcase-soundwave.gif"
                  :title  "Sound Wave"
                  :desc   "Layered frequency bands pulsing like an audio spectrum."}}
  sound-wave []
  {:frames
   (anim/frames 60
     (fn [t]
       (let [w 600 h 300
             n-bars 48
             bands
             (vec
               (for [i (range n-bars)]
                 (let [x-norm (/ (double i) n-bars)
                       ;; Simulate multiple frequency components
                       f1 (Math/sin (+ (* x-norm 3 Math/PI) (* t 2 Math/PI)))
                       f2 (* 0.6 (Math/sin (+ (* x-norm 7 Math/PI) (* t 2 Math/PI 1.6))))
                       f3 (* 0.3 (Math/sin (+ (* x-norm 13 Math/PI) (* t 2 Math/PI 2.3))))
                       amplitude (Math/abs (+ f1 f2 f3))
                       bar-h (max 1 (* 100 amplitude))
                       bar-w (/ (double w) n-bars)
                       x (* i bar-w)
                       hue (mod (+ (* x-norm 200) (* t 120)) 360)]
                   {:node/type :shape/rect
                    :rect/xy   [x (- (/ h 2.0) (/ bar-h 2.0))]
                    :rect/size [bar-w bar-h]
                    :rect/corner-radius 2
                    :style/fill [:color/hsl hue 0.85 (+ 0.35 (* 0.25 amplitude))]})))
             ;; Reflection below
             reflection
             (vec
               (for [i (range n-bars)]
                 (let [x-norm (/ (double i) n-bars)
                       f1 (Math/sin (+ (* x-norm 3 Math/PI) (* t 2 Math/PI)))
                       f2 (* 0.6 (Math/sin (+ (* x-norm 7 Math/PI) (* t 2 Math/PI 1.6))))
                       f3 (* 0.3 (Math/sin (+ (* x-norm 13 Math/PI) (* t 2 Math/PI 2.3))))
                       amplitude (Math/abs (+ f1 f2 f3))
                       bar-h (max 1 (* 30 amplitude))
                       bar-w (/ (double w) n-bars)
                       x (* i bar-w)
                       hue (mod (+ (* x-norm 200) (* t 120)) 360)]
                   {:node/type    :shape/rect
                    :rect/xy      [x (+ (/ h 2.0) 5)]
                    :rect/size    [bar-w bar-h]
                    :rect/corner-radius 2
                    :node/opacity 0.15
                    :style/fill   [:color/hsl hue 0.85 0.5]})))]
         {:image/size [w h]
          :image/background [:color/rgb 10 10 18]
          :image/nodes (into bands reflection)})))
   :fps 24})

;; --- 8. Generative Textile ---

(defn ^{:example {:output "showcase-textile.png"
                  :title  "Generative Textile"
                  :desc   "Woven pattern with noise-driven color and hatched texture."}}
  textile []
  (let [cols 20 rows 20
        cell-w 25 cell-h 25
        pal (:earth palette/palettes)]
    {:image/size [500 500]
     :image/background [:color/rgb 240 235 225]
     :image/nodes
     (vec
       (for [row (range rows)
             col (range cols)]
         (let [x (* col cell-w)
               y (* row cell-h)
               v (noise/perlin2d (* col 0.15) (* row 0.15) {:seed 42})
               color-idx (mod (int (* (+ 0.5 (* 0.5 v)) 4.9)) 5)
               ;; Alternate hatch direction per checkerboard
               angle (if (even? (+ row col)) 45 -45)]
           {:node/type    :shape/rect
            :rect/xy      [x y]
            :rect/size    [cell-w cell-h]
            :style/fill   {:fill/type        :hatch
                           :hatch/angle      angle
                           :hatch/spacing    3
                           :hatch/stroke-width 0.6
                           :hatch/color      (nth pal color-idx)
                           :hatch/background [:color/rgb 240 235 225]}
            :style/stroke {:color [:color/rgba 0 0 0 0.1] :width 0.5}})))}))

;; --- 9. Rainstorm ---

(defn ^{:example {:output "showcase-rainstorm.gif"
                  :title  "Rainstorm"
                  :desc   "Falling rain with expanding ripple circles on a dark surface."}}
  rainstorm []
  {:frames
   (anim/frames 60
     (fn [t]
       (let [w 500 h 400
             ;; Rain drops — falling lines
             drops
             (vec
               (for [i (range 80)]
                 (let [x (mod (+ (* i 137.508) (* t 50)) w)
                       phase (mod (+ (* i 0.618) (* t 3)) 1.0)
                       y1 (* phase h)
                       y2 (+ y1 15)
                       alpha (* 0.4 (- 1.0 (* phase 0.3)))]
                   {:node/type    :shape/line
                    :line/from    [x y1]
                    :line/to      [(- x 2) y2]
                    :node/opacity alpha
                    :style/stroke {:color [:color/rgb 150 170 200]
                                   :width 1}})))
             ;; Ripples — expanding circles at ground level
             ground-y 320
             ripples
             (vec
               (for [i (range 12)]
                 (let [rx (mod (* i 137.508 1.3) w)
                       phase (mod (+ (* i 0.37) t) 1.0)
                       r (max 0.5 (* 30 phase))
                       alpha (* 0.4 (- 1.0 phase))]
                   {:node/type     :shape/ellipse
                    :ellipse/center [rx (+ ground-y (* 5 (Math/sin (* i 1.7))))]
                    :ellipse/rx    r
                    :ellipse/ry    (* r 0.3)
                    :node/opacity  alpha
                    :style/stroke  {:color [:color/rgb 100 130 170]
                                    :width 1}})))
             ;; Ground — subtle gradient-like lines
             ground
             (vec
               (for [i (range 6)]
                 (let [y (+ ground-y (* i 4))
                       alpha (- 0.2 (* i 0.03))]
                   {:node/type    :shape/line
                    :line/from    [0 y] :line/to [w y]
                    :node/opacity (max 0.02 alpha)
                    :style/stroke {:color [:color/rgb 60 70 90]
                                   :width 1.5}})))]
         {:image/size [w h]
          :image/background [:color/rgb 15 18 28]
          :image/nodes (into [] (concat ground ripples drops))})))
   :fps 24})

;; --- 10. Cosmic Eye ---

(defn ^{:example {:output "showcase-cosmic-eye.gif"
                  :title  "Cosmic Eye"
                  :desc   "A radial iris pattern with orbiting rings and shifting nebula colors."}}
  cosmic-eye []
  {:frames
   (anim/frames 60
     (fn [t]
       (let [cx 250 cy 250
             ;; Iris — dense radial lines with noise-driven color
             iris
             (vec
               (for [i (range 180)]
                 (let [angle (+ (* i (/ (* 2 Math/PI) 180)) (* t 0.3))
                       r1 35
                       r2 (+ 80 (* 25 (noise/perlin2d
                                         (* 2 (Math/cos angle))
                                         (* 2 (Math/sin angle))
                                         {:seed 42})))
                       x1 (+ cx (* r1 (Math/cos angle)))
                       y1 (+ cy (* r1 (Math/sin angle)))
                       x2 (+ cx (* r2 (Math/cos angle)))
                       y2 (+ cy (* r2 (Math/sin angle)))
                       hue (+ 30 (* 20 (Math/sin (+ (* i 0.1) (* t 3)))))]
                   {:node/type    :shape/line
                    :line/from    [x1 y1]
                    :line/to      [x2 y2]
                    :node/opacity 0.7
                    :style/stroke {:color [:color/hsl hue 0.9 0.45]
                                   :width 1.2}})))
             ;; Pupil
             pupil {:node/type     :shape/circle
                    :circle/center [cx cy]
                    :circle/radius 33
                    :style/fill    [:color/rgb 5 5 10]
                    :effect/glow   {:blur 10 :color [:color/rgb 0 0 0] :opacity 0.5}}
             ;; Pupil highlight
             highlight {:node/type     :shape/circle
                        :circle/center [(- cx 8) (- cy 10)]
                        :circle/radius 8
                        :node/opacity  0.6
                        :style/fill    [:color/rgb 255 255 255]}
             ;; Outer rings
             rings
             (vec
               (for [i (range 5)]
                 (let [r (+ 90 (* i 25))
                       phase (+ (* t 2 Math/PI) (* i 0.8))
                       alpha (- 0.3 (* i 0.04))
                       hue (+ 200 (* i 30) (* 30 (Math/sin phase)))]
                   {:node/type     :shape/circle
                    :circle/center [cx cy]
                    :circle/radius r
                    :node/opacity  alpha
                    :style/stroke  {:color [:color/hsl hue 0.5 0.5]
                                    :width (- 2.5 (* i 0.3))}})))
             ;; Nebula glow behind
             nebula
             (vec
               (for [i (range 6)]
                 (let [angle (+ (* i (/ Math/PI 3)) (* t 0.5))
                       r (+ 100 (* 40 (Math/sin (+ (* i 1.2) (* t 2)))))
                       nx (+ cx (* r (Math/cos angle)))
                       ny (+ cy (* r (Math/sin angle)))
                       hue (+ 220 (* i 25))]
                   {:node/type     :shape/circle
                    :circle/center [nx ny]
                    :circle/radius 80
                    :node/opacity  0.06
                    :style/fill    [:color/hsl hue 0.7 0.5]})))]
         {:image/size [500 500]
          :image/background [:color/rgb 5 5 12]
          :image/nodes (into [] (concat nebula rings iris [pupil highlight]))})))
   :fps 24})

;; --- 11. Topographic Rings ---

(defn ^{:example {:output "showcase-topo-rings.png"
                  :title  "Topographic Rings"
                  :desc   "Concentric elevation contours with warm-to-cool color mapping."}}
  topo-rings []
  (let [cx 250 cy 250
        rings (vec
                (for [i (range 40)]
                  (let [r (+ 10 (* i 6))
                        wobble-pts (for [a (range 0 360 5)]
                                    (let [rad (* a (/ Math/PI 180))
                                          noise-val (noise/perlin2d
                                                      (* 0.03 r (Math/cos rad))
                                                      (* 0.03 r (Math/sin rad))
                                                      {:seed 42})
                                          rr (+ r (* 8 noise-val))
                                          x (+ cx (* rr (Math/cos rad)))
                                          y (+ cy (* rr (Math/sin rad)))]
                                      [x y]))
                        t (/ (double i) 40)
                        color (palette/gradient-map
                                [[0.0 [:color/rgb 30 60 120]]
                                 [0.3 [:color/rgb 40 150 130]]
                                 [0.6 [:color/rgb 180 170 80]]
                                 [1.0 [:color/rgb 200 80 40]]]
                                t)]
                    {:node/type :shape/path
                     :path/commands (into [[:move-to (first wobble-pts)]]
                                          (conj (mapv #(vector :line-to %) (rest wobble-pts))
                                                [:close]))
                     :style/stroke {:color color :width (if (zero? (mod i 5)) 1.5 0.6)}})))]
    {:image/size [500 500]
     :image/background [:color/rgb 245 240 230]
     :image/nodes rings}))

;; --- 12. Neon Grid ---

(defn ^{:example {:output "showcase-neon-grid.gif"
                  :title  "Neon Grid"
                  :desc   "Retro wireframe grid receding into the horizon with pulsing neon."}}
  neon-grid []
  {:frames
   (anim/frames 40
     (fn [t]
       (let [w 500 h 350
             horizon-y 120
             ;; Horizontal lines receding
             h-lines (vec
                       (for [i (range 15)]
                         (let [progress (/ (double i) 14)
                               y (+ horizon-y (* (- h horizon-y)
                                                  (* progress progress)))
                               alpha (+ 0.2 (* 0.6 progress))
                               hue (+ 280 (* 40 (Math/sin (+ (* i 0.5) (* t 4)))))]
                           {:node/type    :shape/line
                            :line/from    [0 y]
                            :line/to      [w y]
                            :node/opacity alpha
                            :style/stroke {:color [:color/hsl hue 0.9 0.6]
                                           :width (+ 0.5 (* 1.5 progress))}})))
             ;; Vertical lines converging to vanishing point
             v-lines (vec
                       (for [i (range 20)]
                         (let [x-bottom (+ -50 (* i (/ 600.0 19)))
                               vanish-x 250
                               alpha 0.35
                               hue (+ 300 (* 20 (Math/sin (+ (* i 0.3) (* t 3)))))]
                           {:node/type    :shape/line
                            :line/from    [x-bottom h]
                            :line/to      [vanish-x horizon-y]
                            :node/opacity alpha
                            :style/stroke {:color [:color/hsl hue 0.8 0.5]
                                           :width 0.8}})))
             ;; Sun circle at horizon
             sun-pulse (+ 0.8 (* 0.2 (Math/sin (* t 2 Math/PI))))
             sun {:node/type     :shape/circle
                  :circle/center [250 horizon-y]
                  :circle/radius (* 50 sun-pulse)
                  :node/opacity  0.3
                  :style/fill    [:color/hsl 320 0.8 0.6]}]
         {:image/size [w h]
          :image/background [:color/rgb 8 5 18]
          :image/nodes (into [sun] (concat v-lines h-lines))})))
   :fps 20})

;; --- 13. Zen Garden ---

(defn ^{:example {:output "showcase-zen-garden.png"
                  :title  "Zen Garden"
                  :desc   "Raked sand patterns with placed stones — stippled and hatched."}}
  zen-garden []
  (let [w 500 h 400
        ;; Raked sand — curved parallel lines
        rake-lines
        (vec
          (for [i (range 30)]
            (let [y-base (+ 20 (* i 12))
                  pts (for [x (range 0 (inc w) 8)]
                        (let [;; Bend around stone positions
                              d1 (Math/sqrt (+ (Math/pow (- x 150) 2) (Math/pow (- y-base 180) 2)))
                              d2 (Math/sqrt (+ (Math/pow (- x 350) 2) (Math/pow (- y-base 250) 2)))
                              bend1 (if (< d1 80) (* 12 (/ (- 80 d1) 80.0)) 0)
                              bend2 (if (< d2 60) (* 10 (/ (- 60 d2) 60.0)) 0)]
                          [x (+ y-base bend1 bend2)]))]
              {:node/type :shape/path
               :path/commands (into [[:move-to (first pts)]]
                                    (mapv #(vector :line-to %) (rest pts)))
               :style/stroke {:color [:color/rgb 190 180 160] :width 0.7}})))
        ;; Stones — stippled circles
        stones
        [{:node/type :shape/circle
          :circle/center [150 180] :circle/radius 30
          :style/fill {:fill/type :stipple
                       :stipple/density 0.5 :stipple/radius 0.8 :stipple/seed 42
                       :stipple/color [:color/rgb 80 75 65]
                       :stipple/background [:color/rgb 210 200 180]}
          :style/stroke {:color [:color/rgb 100 90 75] :width 1}}
         {:node/type :shape/circle
          :circle/center [350 250] :circle/radius 22
          :style/fill {:fill/type :stipple
                       :stipple/density 0.6 :stipple/radius 0.7 :stipple/seed 99
                       :stipple/color [:color/rgb 70 65 55]
                       :stipple/background [:color/rgb 210 200 180]}
          :style/stroke {:color [:color/rgb 90 80 65] :width 1}}
         {:node/type :shape/circle
          :circle/center [360 230] :circle/radius 12
          :style/fill {:fill/type :stipple
                       :stipple/density 0.4 :stipple/radius 0.6 :stipple/seed 77
                       :stipple/color [:color/rgb 85 80 70]
                       :stipple/background [:color/rgb 210 200 180]}
          :style/stroke {:color [:color/rgb 100 90 75] :width 0.8}}]]
    {:image/size [w h]
     :image/background [:color/rgb 210 200 180]
     :image/nodes (into rake-lines stones)}))

;; --- 14. Orbiting Spheres ---

(defn ^{:example {:output "showcase-orbits.gif"
                  :title  "Orbiting Spheres"
                  :desc   "Colored spheres in circular orbits around a central point."}}
  orbiting-spheres []
  {:frames
   (anim/frames 60
     (fn [t]
       (let [proj (s3d/perspective
                    {:scale 90 :origin [250 250]
                     :yaw 0.3 :pitch -0.45 :distance 6})
             light {:light/direction [0.7 1.0 0.4]
                    :light/ambient 0.25 :light/intensity 0.75}
             angle (* t 2 Math/PI)
             ;; Orbit trails — fading dots behind each sphere
             trails
             (vec
               (for [i (range 5)
                     trail (range 8)]
                 (let [orbit-r (+ 1.8 (* i 0.7))
                       speed (/ 1.0 (+ 1.0 (* i 0.3)))
                       a (- (+ (* angle speed) (* i (/ (* 2 Math/PI) 5)))
                            (* trail 0.08))
                       x (* orbit-r (Math/cos a))
                       z (* orbit-r (Math/sin a))
                       ;; Project to 2D for trail dots
                       hue (* i 72)
                       fade (/ 1.0 (+ 1.0 trail))]
                   {:node/type     :shape/circle
                    :circle/center [(+ 250 (* 90 x 0.15)) (+ 250 (* 90 z 0.08))]
                    :circle/radius (* 3 fade)
                    :node/opacity  (* 0.3 fade)
                    :style/fill    [:color/hsl hue 0.8 0.6]})))
             spheres
             (for [i (range 5)]
               (let [orbit-r (+ 1.8 (* i 0.7))
                     speed (/ 1.0 (+ 1.0 (* i 0.3)))
                     a (+ (* angle speed) (* i (/ (* 2 Math/PI) 5)))
                     x (* orbit-r (Math/cos a))
                     z (* orbit-r (Math/sin a))
                     hue (* i 72)
                     mesh (s3d/sphere-mesh 0.4 12 8)]
                 (s3d/render-mesh proj
                   (s3d/translate-mesh mesh [x 0 z])
                   {:style {:style/fill [:color/hsl hue 0.8 0.55]}
                    :light light})))
             center (s3d/render-mesh proj
                      (s3d/sphere-mesh 0.6 16 10)
                      {:style {:style/fill [:color/rgb 255 240 200]}
                       :light light})]
         {:image/size [500 500]
          :image/background [:color/rgb 5 5 12]
          :image/nodes (into (into trails [center]) spheres)})))
   :fps 24})

;; --- 15. Pixel Dissolve ---

(defn ^{:example {:output "showcase-dissolve.gif"
                  :title  "Pixel Dissolve"
                  :desc   "A grid of squares that scatter and reassemble with color shifts."}}
  pixel-dissolve []
  {:frames
   (anim/frames 50
     (fn [t]
       (let [cols 20 rows 20
             cell 18
             t-ping (anim/ping-pong t)
             eased (anim/ease-in-out-cubic t-ping)]
         {:image/size [400 400]
          :image/background [:color/rgb 15 15 20]
          :image/nodes
          (vec
            (for [row (range rows)
                  col (range cols)]
              (let [home-x (+ 20 (* col cell))
                    home-y (+ 20 (* row cell))
                    ;; Each cell scatters to a unique direction
                    rng (java.util.Random. (long (+ (* row 100) col)))
                    scatter-x (* (.nextGaussian rng) 80 eased)
                    scatter-y (* (.nextGaussian rng) 80 eased)
                    x (+ home-x scatter-x)
                    y (+ home-y scatter-y)
                    hue (mod (+ (* col 12) (* row 12) (* t 180)) 360)
                    size (* cell (- 1.0 (* 0.3 eased)))]
                {:node/type :shape/rect
                 :rect/xy [x y]
                 :rect/size [size size]
                 :rect/corner-radius (* 4 eased)
                 :style/fill [:color/hsl hue 0.8 (+ 0.4 (* 0.2 eased))]})))})))
   :fps 20})

;; --- 16. Stained Glass Rose ---

(defn ^{:example {:output "showcase-rose.png"
                  :title  "Stained Glass Rose"
                  :desc   "Rose curve voronoi tessellation with jewel-toned fills."}}
  stained-glass-rose []
  (let [;; Generate points along a rose curve + scattered background
        rose-pts (for [a (range 0 360 4)]
                   (let [rad (* a (/ Math/PI 180))
                         r (* 150 (Math/cos (* 4 rad)))
                         x (+ 250 (* r (Math/cos rad)))
                         y (+ 250 (* r (Math/sin rad)))]
                     [x y]))
        bg-pts (scatter/poisson-disk 10 10 490 490 40 42)
        all-pts (vec (concat rose-pts bg-pts))
        cells (voronoi/voronoi-cells all-pts 0 0 500 500)
        n-rose (count (seq rose-pts))
        jewels [[:color/rgb 180 30 50] [:color/rgb 30 60 160] [:color/rgb 160 40 140]
                [:color/rgb 40 140 80] [:color/rgb 200 140 30] [:color/rgb 50 120 180]
                [:color/rgb 180 80 30]]]
    {:image/size [500 500]
     :image/background [:color/rgb 20 15 10]
     :image/nodes
     (vec
       (map-indexed
         (fn [i cell]
           (let [on-rose? (< i n-rose)
                 color (if on-rose?
                         (nth jewels (mod i (count jewels)))
                         [:color/rgb 25 20 15])
                 alpha (if on-rose? 0.85 0.3)]
             (-> cell
                 (assoc :style/fill color)
                 (assoc :node/opacity alpha)
                 (assoc :style/stroke {:color [:color/rgb 15 10 5] :width 2}))))
         cells))}))

;; --- 17. Heartbeat ---

(defn ^{:example {:output "showcase-heartbeat.gif"
                  :title  "Heartbeat"
                  :desc   "An ECG-style trace that pulses across the screen."}}
  heartbeat []
  {:frames
   (anim/frames 60
     (fn [t]
       (let [w 500 h 200
             ;; ECG waveform function
             ecg (fn [x]
                   (let [phase (mod x 1.0)]
                     (cond
                       (< phase 0.1) 0
                       (< phase 0.15) (* -15 (/ (- phase 0.1) 0.05))
                       (< phase 0.2) (* 60 (/ (- phase 0.15) 0.05))
                       (< phase 0.25) (* -20 (/ (- phase 0.2) 0.05))
                       (< phase 0.35) (* 5 (Math/sin (* (/ (- phase 0.25) 0.1) Math/PI)))
                       :else 0)))
             ;; Scroll exactly 1 full ECG cycle per loop
             pts (for [i (range 300)]
                   (let [x-norm (/ (double i) 300)
                         x (* x-norm w)
                         wave-x (+ (* x-norm 3.0) (* t 3.0))
                         y (+ (/ h 2.0) (ecg wave-x))]
                     [x y]))
             ;; Trail — fading line
             trail {:node/type :shape/path
                    :path/commands (into [[:move-to (first pts)]]
                                         (mapv #(vector :line-to %) (rest pts)))
                    :style/stroke {:color [:color/rgb 0 220 100] :width 2}}
             ;; Glow dot at the leading edge
             [lead-x lead-y] (last pts)
             dot {:node/type     :shape/circle
                  :circle/center [lead-x lead-y]
                  :circle/radius 4
                  :style/fill    [:color/rgb 0 255 120]}
             ;; Grid lines
             grid (vec
                    (concat
                      (for [i (range 0 (inc w) 25)]
                        {:node/type :shape/line
                         :line/from [i 0] :line/to [i h]
                         :node/opacity 0.08
                         :style/stroke {:color [:color/rgb 0 200 100] :width 0.5}})
                      (for [i (range 0 (inc h) 25)]
                        {:node/type :shape/line
                         :line/from [0 i] :line/to [w i]
                         :node/opacity 0.08
                         :style/stroke {:color [:color/rgb 0 200 100] :width 0.5}})))]
         {:image/size [w h]
          :image/background [:color/rgb 5 10 5]
          :image/nodes (into grid [trail dot])})))
   :fps 24})

;; --- 18. Prism ---

(defn ^{:example {:output "showcase-prism.png"
                  :title  "Prism"
                  :desc   "Light beam splitting through a prism into a spectrum."}}
  prism []
  (let [;; Prism triangle
        prism-shape (assoc (scene/triangle [250 120] [180 300] [320 300])
                           :style/fill [:color/rgba 180 200 220 0.3]
                           :style/stroke {:color [:color/rgb 200 210 220] :width 2})
        ;; Incoming white beam
        beam {:node/type :shape/path
              :path/commands [[:move-to [0 210]]
                              [:line-to [250 210]]]
              :style/stroke {:color [:color/rgb 255 255 255] :width 3}}
        ;; Spectrum rays fanning out
        spectrum-colors [[:color/rgb 255 0 0] [:color/rgb 255 127 0]
                         [:color/rgb 255 255 0] [:color/rgb 0 255 0]
                         [:color/rgb 0 0 255] [:color/rgb 75 0 130]
                         [:color/rgb 148 0 211]]
        rays (vec
               (for [i (range 7)]
                 (let [angle (+ 0.15 (* i 0.06))
                       end-x (+ 250 (* 300 (Math/cos angle)))
                       end-y (+ 210 (* 300 (Math/sin angle)))]
                   {:node/type :shape/path
                    :path/commands [[:move-to [250 210]]
                                    [:line-to [end-x end-y]]]
                    :node/opacity 0.8
                    :style/stroke {:color (nth spectrum-colors i)
                                   :width 2.5}})))]
    {:image/size [600 400]
     :image/background [:color/rgb 10 10 15]
     :image/nodes (into [beam prism-shape] rays)}))

;; --- 19. Breathing Mandala ---

(defn ^{:example {:output "showcase-breathing-mandala.gif"
                  :title  "Breathing Mandala"
                  :desc   "Layered radial symmetry that expands and contracts with color cycling."}}
  breathing-mandala []
  {:frames
   (anim/frames 50
     (fn [t]
       (let [cx 250 cy 250
             breath (anim/ping-pong t)
             eased (anim/ease-in-out-cubic breath)
             layers
             (vec
               (for [layer (range 4)]
                 (let [n-petals (+ 6 (* layer 2))
                       base-r (+ 30 (* layer 45))
                       r (* base-r (+ 0.7 (* 0.3 eased)))
                       petal-w (* r 0.15)
                       hue (mod (+ (* layer 60) (* t 120)) 360)
                       rotation (+ (* layer 0.2) (* t Math/PI 0.5))]
                   {:node/type :symmetry
                    :symmetry/type :radial
                    :symmetry/n n-petals
                    :symmetry/center [cx cy]
                    :group/children
                    [{:node/type :shape/ellipse
                      :ellipse/center [cx (- cy (* r 0.6))]
                      :ellipse/rx petal-w
                      :ellipse/ry (* r 0.45)
                      :node/opacity (- 0.6 (* layer 0.1))
                      :style/fill [:color/hsl hue 0.7 0.5]
                      :style/stroke {:color [:color/hsl hue 0.5 0.35] :width 0.5}}]
                    :node/transform [[:transform/rotate rotation]]})))
             ;; Center dot
             center {:node/type :shape/circle
                     :circle/center [cx cy]
                     :circle/radius (* 15 (+ 0.8 (* 0.2 eased)))
                     :style/fill [:color/rgb 255 240 200]}]
         {:image/size [500 500]
          :image/background [:color/rgb 12 10 18]
          :image/nodes (conj layers center)})))
   :fps 20})

;; --- 20. Pixel Rain (Matrix) ---

(defn ^{:example {:output "showcase-matrix.gif"
                  :title  "Digital Rain"
                  :desc   "Cascading columns of characters fading into the dark."}}
  digital-rain []
  {:frames
   (anim/frames 40
     (fn [t]
       (let [w 400 h 500
             cols 30
             col-w (/ (double w) cols)
             drops
             (vec
               (for [col (range cols)
                     row (range 25)]
                 (let [rng (java.util.Random. (long col))
                       ;; Speed is integer multiple so it loops at t=1
                       speed (+ 1 (.nextInt rng 3))
                       offset (* (.nextDouble rng) h)
                       head-y (mod (+ (* t speed h) offset) h)
                       y (- head-y (* row 16))
                       x (+ 2 (* col col-w))
                       fade (max 0 (- 1.0 (* row 0.05)))
                       brightness (if (zero? row) 1.0 (* 0.7 fade))]
                   (when (and (> y -10) (< y (+ h 10)) (> fade 0.05))
                     {:node/type     :shape/rect
                      :rect/xy       [x y]
                      :rect/size     [(- col-w 2) 12]
                      :rect/corner-radius 1
                      :node/opacity  (* fade 0.8)
                      :style/fill    [:color/rgb
                                      (int (* 30 brightness))
                                      (int (* 255 brightness))
                                      (int (* 60 brightness))]}))))]
         {:image/size [w h]
          :image/background [:color/rgb 0 5 0]
          :image/nodes (vec (remove nil? drops))})))
   :fps 15})

(comment
  (require '[eido.core :as eido])
  (eido/render (bauhaus) {:output "showcase-bauhaus.png"})
  (eido/render (geode) {:output "showcase-geode.png"})
  (eido/render (textile) {:output "showcase-textile.png"})
  (eido/render (prism) {:output "showcase-prism.png"})
  (let [{:keys [frames fps]} (aurora)]
    (eido/render frames {:output "showcase-aurora.gif" :fps fps})))
