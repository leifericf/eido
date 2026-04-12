(ns gallery.paint
  "Paint engine gallery — procedural brushwork, painterly effects,
  and composable generative paint techniques."
  {:category "Paint Engine"}
  (:require
    [eido.gen.noise :as noise]
    [eido.gen.prob :as prob]))

;; --- 1. Chalk Sketch ---

(defn ^{:example {:output "paint-chalk-sketch.png"
                  :title  "Chalk Sketch"
                  :desc   "Procedural chalk strokes on warm paper."
                  :tags   ["paint" "chalk" "dry-media"]}}
  chalk-sketch []
  (let [w 600 h 400
        rng (prob/make-rng 42)
        ;; Generate some gentle organic curves
        make-stroke (fn [y-base color seed]
                      (let [points (mapv (fn [i]
                                           (let [x (* i 30.0)
                                                 y (+ y-base
                                                      (* 20.0 (noise/perlin2d
                                                                 (* x 0.008)
                                                                 (* seed 0.5)
                                                                 {:seed seed})))
                                                 p (+ 0.3 (* 0.7 (noise/perlin2d
                                                                     (* x 0.01) 0.0
                                                                     {:seed (+ seed 100)})))]
                                             [x y (max 0.1 p) 0 0 0]))
                                         (range 21))]
                        {:paint/brush :chalk
                         :paint/color color
                         :paint/radius (+ 8.0 (* 6.0 (.nextDouble rng)))
                         :paint/points points
                         :paint/seed seed}))]
    {:image/size [w h]
     :image/background [:color/rgb 245 238 225]
     :image/nodes
     [{:node/type :paint/surface
       :paint/size [w h]
       :paint/strokes
       [(make-stroke 80  [:color/rgb 100 70 45] 1)
        (make-stroke 130 [:color/rgb 140 90 50] 2)
        (make-stroke 180 [:color/rgb 80 55 35]  3)
        (make-stroke 230 [:color/rgb 110 75 40] 4)
        (make-stroke 280 [:color/rgb 90 60 38]  5)
        (make-stroke 320 [:color/rgb 120 80 48] 6)]}]}))

;; --- 2. Ink Flow ---

(defn ^{:example {:output "paint-ink-flow.png"
                  :title  "Ink Flow"
                  :desc   "Painted flow field streamlines with ink brush."
                  :tags   ["paint" "ink" "flow-field" "generative"]}}
  ink-flow []
  (let [w 600 h 600]
    {:image/size [w h]
     :image/background [:color/rgb 252 250 245]
     :image/nodes
     [{:node/type :group
       :paint/surface {:paint/size [w h]}
       :group/children
       [{:node/type :flow-field
         :flow/bounds [30 30 540 540]
         :flow/opts {:density 20 :steps 40 :noise-scale 0.006
                     :step-length 3.0 :seed 77}
         :paint/brush :ink
         :paint/color [:color/rgb 15 12 8]
         :paint/radius 2.0}]}]}))

;; --- 3. Layered Strokes ---

(defn ^{:example {:output "paint-layered-strokes.png"
                  :title  "Layered Strokes"
                  :desc   "Multiple painted paths with pressure curves."
                  :tags   ["paint" "pressure" "layered" "calligraphy"]}}
  layered-strokes []
  (let [w 600 h 400]
    {:image/size [w h]
     :image/background [:color/rgb 250 248 242]
     :image/nodes
     [{:node/type :group
       :paint/surface {:paint/size [w h]}
       :group/children
       [;; Background wash strokes
        {:node/type :shape/path
         :path/commands [[:move-to [40 200]]
                         [:curve-to [150 100] [300 300] [560 180]]]
         :paint/brush :watercolor
         :paint/color [:color/hsl 210 0.4 0.7]
         :paint/radius 30.0
         :paint/pressure [[0.0 0.5] [0.3 0.8] [0.7 0.9] [1.0 0.3]]}
        {:node/type :shape/path
         :path/commands [[:move-to [60 260]]
                         [:curve-to [200 180] [400 340] [540 240]]]
         :paint/brush :watercolor
         :paint/color [:color/hsl 200 0.35 0.65]
         :paint/radius 25.0
         :paint/pressure [[0.0 0.3] [0.5 0.7] [1.0 0.2]]}
        ;; Foreground ink lines
        {:node/type :shape/path
         :path/commands [[:move-to [80 180]]
                         [:curve-to [200 80] [350 280] [520 160]]]
         :paint/brush :ink
         :paint/color [:color/rgb 20 15 10]
         :paint/radius 4.0
         :paint/pressure [[0.0 0.1] [0.2 0.8] [0.8 0.9] [1.0 0.05]]}
        {:node/type :shape/path
         :path/commands [[:move-to [100 300]]
                         [:curve-to [250 220] [380 320] [500 260]]]
         :paint/brush :ink
         :paint/color [:color/rgb 25 18 12]
         :paint/radius 3.0
         :paint/pressure [[0.0 0.05] [0.3 0.7] [0.7 0.8] [1.0 0.1]]}]}]}))

(comment
  (require '[eido.core :as eido])
  (eido/render (chalk-sketch) {:output "/tmp/paint-chalk-sketch.png"})
  (eido/render (ink-flow) {:output "/tmp/paint-ink-flow.png"})
  (eido/render (layered-strokes) {:output "/tmp/paint-layered-strokes.png"}))
