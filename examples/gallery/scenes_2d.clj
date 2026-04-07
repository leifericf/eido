(ns gallery.scenes-2d
  "2D gallery — grids, color manipulation, and animation helpers."
  {:category "2D Scenes"}
  (:require
    [eido.animate :as anim]
    [eido.color :as color]
    [eido.scene :as scene]))

;; --- 1. Spiral Rainbow ---

(defn ^{:example {:output "spiral-grid.gif"
                  :title  "Spiral Rainbow"
                  :desc   "A rotating spiral wave where hue follows the angle and pulse follows the distance from center."}}
  spiral-rainbow []
  {:frames
   (anim/frames 60
     (fn [t]
       {:image/size [400 400]
        :image/background [:color/rgb 10 10 18]
        :image/nodes
        (scene/grid 20 20
          (fn [col row]
            (let [cx (- (/ col 9.5) 1.0)
                  cy (- (/ row 9.5) 1.0)
                  dist (Math/sqrt (+ (* cx cx) (* cy cy)))
                  angle (Math/atan2 cy cx)
                  spiral (mod (+ (* dist 2.0)
                                 (* angle (/ 1.0 Math/PI))
                                 (- (* t 3.0))) 1.0)
                  pulse (/ (+ 1.0 (Math/sin (* spiral 2.0 Math/PI))) 2.0)
                  radius (+ 2 (* 8 pulse))
                  hue (mod (+ (* angle (/ 180.0 Math/PI)) 180 (* t 360)) 360)]
              {:node/type :shape/circle
               :circle/center [(+ 12 (* col 19.8)) (+ 12 (* row 19.8))]
               :circle/radius radius
               :style/fill [:color/hsl hue 0.9 (+ 0.3 (* 0.35 pulse))]})))}))
   :fps 24})

;; --- 2. Sine Interference ---

(defn ^{:example {:output "sine-field.gif"
                  :title  "Sine Interference"
                  :desc   "Three overlapping sine waves at different frequencies create organic, shifting patterns."}}
  sine-interference []
  {:frames
   (anim/frames 50
     (fn [t]
       {:image/size [400 400]
        :image/background [:color/rgb 10 10 18]
        :image/nodes
        (scene/grid 20 20
          (fn [col row]
            (let [x (/ col 19.0)
                  y (/ row 19.0)
                  v (/ (+ (Math/sin (+ (* x 8) (* t 2 Math/PI)))
                          (Math/sin (+ (* y 6) (* t 2 Math/PI 1.3)))
                          (Math/sin (+ (* (+ x y) 5) (* t 2 Math/PI 0.7)))) 3.0)
                  pulse (/ (+ v 1.0) 2.0)
                  radius (+ 3 (* 7 pulse))
                  hue (mod (+ (* pulse 200) (* t 360) 180) 360)]
              {:node/type :shape/circle
               :circle/center [(+ 12 (* col 19.8)) (+ 12 (* row 19.8))]
               :circle/radius radius
               :style/fill [:color/hsl hue 0.9 (+ 0.35 (* 0.3 pulse))]})))}))
   :fps 24})

;; --- 3. Breathing Wave ---

(defn ^{:example {:output "breathing-grid.gif"
                  :title  "Breathing Wave"
                  :desc   "A diagonal wave where cells expand and contract with staggered timing, hue shifting along the diagonal."}}
  breathing-wave []
  {:frames
   (anim/frames 50
     (fn [t]
       {:image/size [400 400]
        :image/background [:color/rgb 245 243 238]
        :image/nodes
        (scene/grid 14 14
          (fn [col row]
            (let [delay (/ (+ col row) 26.0)
                  phase (mod (- (* t 2.0) delay) 1.0)
                  breath (/ (+ 1.0 (Math/sin (* phase 2.0 Math/PI))) 2.0)
                  size (+ 3 (* 10 breath))
                  hue (mod (+ (* (+ col row) 14) (* t 120)) 360)]
              {:node/type :shape/circle
               :circle/center [(+ 18 (* col 27)) (+ 18 (* row 27))]
               :circle/radius size
               :style/fill [:color/hsl hue (+ 0.5 (* 0.4 breath)) 0.48]})))}))
   :fps 24})

;; --- 4. Dancing Bars ---

(defn ^{:example {:output "dancing-bars.gif"
                  :title  "Dancing Bars"
                  :desc   "Vertical bars with height, position, and color driven by overlapping sine waves."}}
  dancing-bars []
  {:frames
   (anim/frames 50
     (fn [t]
       {:image/size [400 400]
        :image/background [:color/rgb 10 10 18]
        :image/nodes
        (vec (for [col (range 30)]
               (let [x-norm (/ col 29.0)
                     wave1 (Math/sin (+ (* x-norm 4 Math/PI) (* t 2 Math/PI)))
                     wave2 (Math/sin (+ (* x-norm 6 Math/PI) (* t 2 Math/PI 1.7)))
                     combined (/ (+ wave1 wave2) 2.0)
                     height (+ 40 (* 140 (/ (+ combined 1.0) 2.0)))
                     y-center (+ 200 (* 60 (Math/sin (+ (* x-norm 3 Math/PI)
                                                         (* t 2 Math/PI 0.5)))))
                     width (+ 4 (* 6 (/ (+ combined 1.0) 2.0)))
                     hue (mod (+ (* x-norm 360) (* t 200)) 360)]
                 {:node/type :shape/rect
                  :rect/xy [(- (* (+ 0.5 col) (/ 400.0 30)) (/ width 2))
                             (- y-center (/ height 2))]
                  :rect/size [width height]
                  :style/fill [:color/hsl hue 0.85
                                (+ 0.35 (* 0.3 (/ (+ combined 1.0) 2.0)))]})))}))
   :fps 24})

;; --- 5. Tentacles ---

(defn ^{:example {:output "tentacles.gif"
                  :title  "Tentacles"
                  :desc   "Eight arms spiral outward from the center, wobbling and shifting color along their length."}}
  tentacles []
  {:frames
   (anim/frames 60
     (fn [t]
       {:image/size [400 400]
        :image/background [:color/rgb 10 10 18]
        :image/nodes
        (vec (for [arm (range 8)]
               (let [base-angle (+ (* arm (/ (* 2 Math/PI) 8)) (* t Math/PI 0.3))
                     hue (* arm 45)]
                 {:node/type :group
                  :group/children
                  (vec (for [seg (range 25)]
                         (let [seg-t (/ seg 24.0)
                               r (* seg-t 180)
                               wobble (* 30 seg-t
                                        (Math/sin (+ (* seg-t 8) (* t 2 Math/PI)
                                                     (* arm 0.7))))
                               angle (+ base-angle
                                        (* seg-t 0.8
                                           (Math/sin (+ (* t 2 Math/PI) arm))))
                               x (+ 200 (* (+ r wobble) (Math/cos angle)))
                               y (+ 200 (* (+ r wobble) (Math/sin angle)))
                               size (max 1 (- 10 (* seg-t 8)))
                               seg-hue (mod (+ hue (* seg-t 90) (* t 120)) 360)]
                           {:node/type :shape/circle
                            :circle/center [x y]
                            :circle/radius size
                            :node/opacity (- 1.0 (* seg-t 0.6))
                            :style/fill [:color/hsl seg-hue 0.85 0.55]})))})))}))
   :fps 24})

;; --- 6. Pendulum Wave ---

(defn ^{:example {:output "pendulum-wave.gif"
                  :title  "Pendulum Wave"
                  :desc   "15 pendulums with increasing frequencies create wave patterns."}}
  pendulum-wave []
  {:frames
   (anim/frames 80
     (fn [t]
       {:image/size [500 400]
        :image/background [:color/rgb 245 243 238]
        :image/nodes
        (vec (for [p (range 15)]
               (let [freq (+ 6 p)
                     angle (* (Math/sin (* t 2 Math/PI freq)) 0.9)
                     pivot-x (+ 30 (* p 31.4))
                     bob-x (+ pivot-x (* 250 (Math/sin angle)))
                     bob-y (* 250 (Math/cos angle))
                     hue (* p 24)]
                 {:node/type :group
                  :group/children
                  [{:node/type :shape/path
                    :path/commands [[:move-to [pivot-x 20]]
                                    [:line-to [bob-x (+ 20 bob-y)]]]
                    :style/stroke {:color [:color/rgb 80 80 80] :width 1}}
                   {:node/type :shape/circle
                    :circle/center [bob-x (+ 20 bob-y)]
                    :circle/radius 10
                    :style/fill [:color/hsl hue 0.75 0.5]}]})))}))
   :fps 30})

;; --- 7. Particle Galaxy ---

(defn ^{:example {:output "galaxy.gif"
                  :title  "Particle Galaxy"
                  :desc   "300 particles orbiting with Keplerian speeds and 3 spiral arms."}}
  particle-galaxy []
  {:frames
   (anim/frames 60
     (fn [t]
       {:image/size [500 500]
        :image/background [:color/rgb 5 5 12]
        :image/nodes
        (vec (for [p (range 300)]
               (let [arm (mod p 3)
                     base-r (+ 15 (* (Math/sqrt (/ p 300.0)) 220))
                     speed (/ 1.0 (Math/sqrt (max 1 base-r)))
                     angle (+ (* t 2 Math/PI speed 3)
                              (/ (* p 137.508) 50.0)
                              (* arm (/ (* 2 Math/PI) 3))
                              (* (/ base-r 300.0) 1.5))
                     r (+ base-r (* 8 (Math/sin (+ (* t 6 Math/PI) (* p 137.508)))))
                     x (+ 250 (* r (Math/cos angle)))
                     y (+ 250 (* r (Math/sin angle)))
                     hue (mod (+ 200 (* -200 (/ base-r 230.0))) 360)
                     bright (- 1.0 (* 0.5 (/ base-r 230.0)))
                     size (max 1 (- 4 (* 2.5 (/ base-r 230.0))))]
                 {:node/type :shape/circle
                  :circle/center [x y]
                  :circle/radius size
                  :node/opacity (min 1.0 bright)
                  :style/fill [:color/hsl hue 0.8 (* 0.55 bright)]})))}))
   :fps 24})

;; --- 8. Op Art ---

(defn ^{:example {:output "op-art.gif"
                  :title  "Op Art"
                  :desc   "Concentric rings that wobble to create an optical illusion, using only black and white."}}
  op-art []
  {:frames
   (anim/frames 50
     (fn [t]
       {:image/size [400 400]
        :image/background [:color/rgb 255 255 255]
        :image/nodes
        (vec (for [ring (reverse (range 40))]
               (let [phase (+ (* t 2 Math/PI) (* ring 0.3))
                     wobble (* 15 (Math/sin phase))
                     r (+ (* ring 7) wobble)]
                 {:node/type :shape/circle
                  :circle/center [(+ 200 (* 5 (Math/sin (+ phase 1.5))))
                                  (+ 200 (* 5 (Math/cos phase)))]
                  :circle/radius (max 1 r)
                  :style/fill (if (even? ring)
                                [:color/rgb 0 0 0]
                                [:color/rgb 255 255 255])})))}))
   :fps 24})

;; --- 9. Lissajous Curve ---

(defn ^{:example {:output "lissajous.gif"
                  :title  "Lissajous Curve"
                  :desc   "A 3:2 Lissajous figure traced with a rainbow trail that fades with age."}}
  lissajous-curve []
  {:frames
   (anim/frames 60
     (fn [t]
       {:image/size [400 400]
        :image/background [:color/rgb 5 5 12]
        :image/nodes
        (vec (for [j (range 200)]
               (let [s (/ j 200.0)
                     phase (* (+ t s) 2 Math/PI)
                     x (+ 200 (* 160 (Math/sin (* phase 3))))
                     y (+ 200 (* 160 (Math/cos (* phase 2))))
                     age (- 1.0 s)
                     hue (mod (* (+ t s) 720) 360)]
                 {:node/type :shape/circle
                  :circle/center [x y]
                  :circle/radius (+ 1 (* 5 age age))
                  :node/opacity (* age age)
                  :style/fill [:color/hsl hue 0.9 (+ 0.3 (* 0.4 age))]})))}))
   :fps 30})

;; --- 10. Cellular Automaton ---

(defn ^{:example {:output "cellular.gif"
                  :title  "Cellular Automaton"
                  :desc   "Evolving cellular patterns driven by sine wave interference, rendered as glowing colored cells."}}
  cellular-automaton []
  {:frames
   (anim/frames 40
     (fn [t]
       {:image/size [400 400]
        :image/background [:color/rgb 10 10 18]
        :image/nodes
        (scene/grid 25 25
          (fn [col row]
            (let [sum (+ (Math/sin (+ (* col 0.7) (* t 5)))
                         (Math/cos (+ (* row 0.8) (* t 4)))
                         (Math/sin (+ (* (+ col row) 0.4) (* t 3)))
                         (Math/cos (+ (* (Math/abs (- col row)) 0.6) (* t 6))))]
              (when (> sum 0.5)
                (let [glow (max 0 (/ (- sum 0.5) 3.5))
                      hue (mod (+ (* col 8) (* row 8) (* t 200)) 360)]
                  {:node/type :shape/rect
                   :rect/xy [(+ 2 (* col 15.8)) (+ 2 (* row 15.8))]
                   :rect/size [14 14]
                   :node/opacity (+ 0.6 (* 0.4 glow))
                   :style/fill [:color/hsl hue 0.9 (+ 0.35 (* 0.3 glow))]})))))}))
   :fps 15})

;; --- 11. Kaleidoscope ---

(defn ^{:example {:output "kaleidoscope.gif"
                  :title  "Kaleidoscope"
                  :desc   "Eight-fold rotational symmetry with orbiting, pulsing dots."}}
  kaleidoscope []
  {:frames
   (anim/frames 60
     (fn [t]
       {:image/size [400 400]
        :image/background [:color/rgb 5 5 12]
        :image/nodes
        (vec (for [sym (range 8)
                   shape (range 6)]
               (let [angle (+ (* sym (/ Math/PI 4)) (* t Math/PI 0.25))
                     shape-r (+ 30 (* shape 25))
                     shape-angle (+ angle (* shape 0.8)
                                    (* (Math/sin (+ (* t 4 Math/PI) (* shape 1.2))) 0.3))
                     x (+ 200 (* shape-r (Math/cos shape-angle)))
                     y (+ 200 (* shape-r (Math/sin shape-angle)))
                     hue (mod (+ (* sym 45) (* shape 30) (* t 180)) 360)
                     size (+ 5 (* 8 (/ (+ 1 (Math/sin (+ (* t 3 Math/PI) shape sym))) 2.0)))]
                 {:node/type :shape/circle
                  :circle/center [x y]
                  :circle/radius size
                  :node/opacity 0.75
                  :style/fill [:color/hsl hue 0.85 0.55]})))}))
   :fps 24})

;; --- 12. Star Burst ---

(defn ^{:example {:output "star-burst.gif"
                  :title  "Star Burst"
                  :desc   "Rotating gradient stars with staggered pulsing, using radial gradients and cubic easing."}}
  star-burst []
  {:frames
   (anim/frames 60
     (fn [t]
       {:image/size [400 400]
        :image/background [:color/name "midnightblue"]
        :image/nodes
        (vec
          (for [i (range 6)
                :let [rotation (* (+ t (* i 0.167)) 2 Math/PI (/ 1.0 6))
                      pulse (anim/ease-in-out-cubic
                              (anim/ping-pong (mod (+ t (* i 0.12)) 1.0)))
                      outer (+ 60 (* 50 pulse))
                      inner (* outer 0.4)
                      hue (mod (+ (* i 60) (* t 360)) 360)]]
            {:node/type :group
             :node/transform [[:transform/translate 200 200]
                              [:transform/rotate rotation]]
             :node/opacity (+ 0.5 (* 0.5 pulse))
             :group/children
             [(merge (scene/star [0 0] outer inner 5)
                     {:style/fill
                      {:gradient/type :radial
                       :gradient/center [0 0]
                       :gradient/radius outer
                       :gradient/stops
                       [[0.0 [:color/hsl hue 0.95 0.7]]
                        [1.0 [:color/hsl (mod (+ hue 60) 360) 0.9 0.35]]]}})]}))}))
   :fps 30})

;; --- 13. Blooming Tree ---

(defn- tree-branches [^java.util.Random rng x y len angle depth max-depth growth sway]
  (when (and (pos? depth) (> growth 0))
    (let [g (min 1.0 (* growth (+ 1.0 (* 0.3 depth))))
          sway-amt (* sway 0.05 depth (Math/sin (* depth 2.3)))
          a (+ angle sway-amt)
          x2 (+ x (* len g (Math/sin a)))
          y2 (- y (* len g (Math/cos a)))
          thickness (max 1 (* 2.5 depth g))
          brown-g (max 0 (min 255 (int (+ 60 (* 15 depth)))))
          branch {:node/type :shape/line
                  :line/from [x y] :line/to [x2 y2]
                  :style/stroke {:color [:color/rgb 90 brown-g 30]
                                 :width thickness :cap :round}}
          left  (tree-branches rng x2 y2 (* len 0.7) (- a 0.45)
                  (dec depth) max-depth growth sway)
          right (tree-branches rng x2 y2 (* len 0.7) (+ a 0.45)
                  (dec depth) max-depth growth sway)
          leaf (when (and (<= depth 2) (> g 0.8))
                 [{:node/type :shape/circle
                   :circle/center [x2 y2]
                   :circle/radius (* 3 (- g 0.5))
                   :style/fill [:color/rgb (+ 30 (.nextInt rng 60))
                                (+ 140 (.nextInt rng 80))
                                (+ 20 (.nextInt rng 40))]}])]
      (concat [branch] left right leaf))))

(defn ^{:example {:output "tree.gif"
                  :title  "Blooming Tree"
                  :desc   "A recursive fractal tree that grows from trunk to full canopy, then sways in the wind."}}
  blooming-tree []
  {:frames
   (anim/frames 90
     (fn [t]
       (let [rng (java.util.Random. 42)
             growth (* t 3.0)
             sway (* (max 0 (- t 0.4)) 8.0 (Math/sin (* t 6 Math/PI)))]
         {:image/size [450 450]
          :image/background [:color/rgb 20 20 30]
          :image/nodes (vec (tree-branches rng 225 420 90 0 9 9 growth sway))})))
   :fps 24})

;; --- 14. Sierpinski Triangle ---

(defn- sierpinski [ax ay bx by cx cy depth hue-offset t]
  (if (zero? depth)
    (let [hue (mod (+ hue-offset (* t 360)) 360)
          c (color/resolve-color [:color/hsl hue 0.75 0.5])]
      [{:node/type :shape/path
        :path/commands [[:move-to [ax ay]] [:line-to [bx by]]
                        [:line-to [cx cy]] [:close]]
        :style/fill [:color/rgb (:r c) (:g c) (:b c)]}])
    (let [abx (* 0.5 (+ ax bx)) aby (* 0.5 (+ ay by))
          bcx (* 0.5 (+ bx cx)) bcy (* 0.5 (+ by cy))
          acx (* 0.5 (+ ax cx)) acy (* 0.5 (+ ay cy))]
      (concat (sierpinski ax ay abx aby acx acy (dec depth) hue-offset t)
              (sierpinski abx aby bx by bcx bcy (dec depth) (+ hue-offset 25) t)
              (sierpinski acx acy bcx bcy cx cy (dec depth) (+ hue-offset 50) t)))))

(defn ^{:example {:output "sierpinski.gif"
                  :title  "Sierpinski Triangle"
                  :desc   "The classic fractal, built up one recursion depth at a time with shifting colors."}}
  sierpinski-triangle []
  {:frames
   (anim/frames 60
     (fn [t]
       (let [depth (int (Math/floor (+ 1 (* t 6))))]
         {:image/size [500 450]
          :image/background [:color/rgb 20 20 30]
          :image/nodes (vec (sierpinski 250 30 460 410 40 410 depth 0 t))})))
   :fps 10})

;; --- 15. Koch Snowflake ---

(defn- koch-edge [[x1 y1] [x2 y2] depth]
  (if (zero? depth)
    [[x1 y1] [x2 y2]]
    (let [dx (- x2 x1) dy (- y2 y1)
          ax (+ x1 (/ dx 3)) ay (+ y1 (/ dy 3))
          bx (- x2 (/ dx 3)) by (- y2 (/ dy 3))
          px (+ (* 0.5 (+ ax bx)) (* (/ (Math/sqrt 3) 6) (- y1 y2)))
          py (+ (* 0.5 (+ ay by)) (* (/ (Math/sqrt 3) 6) (- x2 x1)))]
      (concat (koch-edge [x1 y1] [ax ay] (dec depth))
              (koch-edge [ax ay] [px py] (dec depth))
              (koch-edge [px py] [bx by] (dec depth))
              (koch-edge [bx by] [x2 y2] (dec depth))))))

(defn- koch-snowflake [cx cy r depth]
  (let [pts (for [i (range 3)]
              (let [a (- (* i (/ (* 2 Math/PI) 3)) (/ Math/PI 2))]
                [(+ cx (* r (Math/cos a))) (+ cy (* r (Math/sin a)))]))
        edges (mapcat #(koch-edge (nth pts %) (nth pts (mod (inc %) 3)) depth)
                      (range 3))
        commands (into [[:move-to (first edges)]]
                       (conj (mapv (fn [p] [:line-to p]) (rest edges))
                             [:close]))]
    {:node/type :shape/path :path/commands commands}))

(defn ^{:example {:output "koch.gif"
                  :title  "Koch Snowflake"
                  :desc   "A Koch snowflake that gains detail with each frame, the boundary growing ever more intricate."}}
  koch-snowflake-scene []
  {:frames
   (anim/frames 60
     (fn [t]
       (let [depth (int (Math/floor (+ 0.5 (* t 5))))
             c (color/resolve-color [:color/hsl (* t 40) 0.8 0.5])]
         {:image/size [450 450]
          :image/background [:color/rgb 20 20 30]
          :image/nodes
          [(assoc (koch-snowflake 225 235 180 depth)
             :style/fill [:color/rgb (:r c) (:g c) (:b c)])]})))
   :fps 10})

;; --- REPL ---

(comment
  (require '[eido.core :as eido])
  (eido/render (spiral-rainbow) {:output "spiral-grid.gif" :fps 24})
  (eido/render (sine-interference) {:output "sine-field.gif" :fps 24})
  (eido/render (breathing-wave) {:output "breathing-grid.gif" :fps 24})
  (eido/render (dancing-bars) {:output "dancing-bars.gif" :fps 24})
  (eido/render (tentacles) {:output "tentacles.gif" :fps 24})
  (eido/render (pendulum-wave) {:output "pendulum-wave.gif" :fps 30})
  (eido/render (particle-galaxy) {:output "galaxy.gif" :fps 24})
  (eido/render (op-art) {:output "op-art.gif" :fps 24})
  (eido/render (lissajous-curve) {:output "lissajous.gif" :fps 30})
  (eido/render (cellular-automaton) {:output "cellular.gif" :fps 15})
  (eido/render (kaleidoscope) {:output "kaleidoscope.gif" :fps 24})
  (eido/render (star-burst) {:output "star-burst.gif" :fps 30})
  (eido/render (blooming-tree) {:output "tree.gif" :fps 24})
  (eido/render (sierpinski-triangle) {:output "sierpinski.gif" :fps 10})
  (eido/render (koch-snowflake-scene) {:output "koch.gif" :fps 10}))
