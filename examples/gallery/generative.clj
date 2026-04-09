(ns gallery.generative
  "Generative art showcase — circle packing, subdivision, reaction-diffusion,
  boids, path aesthetics, weighted palettes, and long-form series."
  {:category "Generative"}
  (:require
    [eido.animate :as anim]
    [eido.color.palette :as palette]
    [eido.gen.boids :as boids]
    [eido.gen.ca :as ca]
    [eido.gen.circle :as circle]
    [eido.gen.flow :as flow]
    [eido.gen.noise :as noise]
    [eido.gen.prob :as prob]
    [eido.gen.series :as series]
    [eido.gen.subdivide :as subdivide]
    [eido.path.aesthetic :as aesthetic]
    [eido.scene :as scene]))

;; --- 1. Circle Pack with Weighted Palette ---

(defn ^{:example {:output "gen-circle-pack.png"
                  :title  "Circle Pack"
                  :desc   "Packed circles with weighted sunset palette — rare accents."}}
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
              :style/stroke  {:color [:color/rgba 40 30 20 80] :width 0.5}})
           circles colors)}))

;; --- 2. Mondrian Subdivision ---

(defn ^{:example {:output "gen-mondrian.png"
                  :title  "Mondrian Grid"
                  :desc   "Recursive subdivision with primary color accents."}}
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
                  :desc   "Gray-Scott reaction-diffusion at the coral preset."}}
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
                  :desc   "Starling-like flocking with tight cohesion and wide alignment."}}
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
                  :desc   "Flow field streamlines with dashed + smoothed paths."}}
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
                  :desc   "9 editions of a parametric design driven by eido.gen.series."}}
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

(comment
  (circle-pack-palette)
  (mondrian)
  (dashed-flow)
  (series-grid))
