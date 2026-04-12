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
     :image/background [:color/rgb 240 235 220]
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
     :image/background [:color/rgb 245 242 232]
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
                  :tags   ["paint" "pressure" "layered"]}}
  layered-strokes []
  (let [w 600 h 400]
    {:image/size [w h]
     :image/background [:color/rgb 245 240 228]
     :image/nodes
     [{:node/type :group
       :paint/surface {:paint/size [w h]}
       :group/children
       [{:node/type :shape/path
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

;; --- 4. Charcoal Study ---

(defn ^{:example {:output "paint-charcoal-study.png"
                  :title  "Charcoal Study"
                  :desc   "Charcoal with heavy grain and jitter on textured paper."
                  :tags   ["paint" "charcoal" "dry-media" "texture"]}}
  charcoal-study []
  (let [w 600 h 400
        make-stroke (fn [pts color radius seed]
                      {:node/type :shape/path
                       :path/commands (into [[:move-to (first pts)]]
                                            (mapv (fn [p] [:line-to p]) (rest pts)))
                       :paint/brush :charcoal
                       :paint/color color
                       :paint/radius radius
                       :paint/seed seed
                       :paint/pressure [[0.0 0.3] [0.2 0.8] [0.7 1.0] [1.0 0.2]]})]
    {:image/size [w h]
     :image/background [:color/rgb 235 228 215]
     :image/nodes
     [{:node/type :group
       :paint/surface {:paint/size [w h]
                       :substrate/tooth 0.35}
       :group/children
       [;; Broad shadow strokes
        (make-stroke [[50 300] [200 280] [400 310] [550 290]]
                     [:color/rgb 35 30 25] 25.0 1)
        (make-stroke [[80 260] [250 230] [450 270] [520 250]]
                     [:color/rgb 45 35 28] 20.0 2)
        ;; Mid-tones
        (make-stroke [[40 180] [180 150] [350 190] [560 170]]
                     [:color/rgb 55 45 35] 15.0 3)
        (make-stroke [[60 220] [230 190] [400 230] [530 210]]
                     [:color/rgb 50 40 30] 12.0 4)
        ;; Fine detail strokes
        (make-stroke [[100 120] [200 100] [320 130] [480 110]]
                     [:color/rgb 30 25 20] 6.0 5)
        (make-stroke [[120 150] [250 130] [380 160] [500 140]]
                     [:color/rgb 40 32 25] 5.0 6)]}]}))

;; --- 5. Watercolor Wash ---

(defn ^{:example {:output "paint-watercolor-wash.png"
                  :title  "Watercolor Wash"
                  :desc   "Wet-on-wet watercolor with granulation and edge darkening."
                  :tags   ["paint" "watercolor" "wet-media" "granulation"]}}
  watercolor-wash []
  (let [w 600 h 400
        wet-brush {:brush/type :brush/dab
                   :brush/tip {:tip/shape :ellipse :tip/hardness 0.25}
                   :brush/paint {:paint/opacity 0.06 :paint/flow 0.5 :paint/spacing 0.04}
                   :brush/wet {:wet/enabled true :wet/deposit 0.35
                               :wet/diffusion 0.3 :wet/diffusion-steps 8
                               :wet/edge-darken 0.35 :wet/edge-sharpness 2.0
                               :wet/granulation 0.4 :wet/granulation-scale 0.08}
                   :brush/jitter {:jitter/position 0.15 :jitter/opacity 0.25
                                  :jitter/size 0.12}}]
    {:image/size [w h]
     :image/background [:color/rgb 242 237 225]
     :image/nodes
     [{:node/type :group
       :paint/surface {:paint/size [w h]}
       :group/children
       [{:node/type :shape/path
         :path/commands [[:move-to [30 180]]
                         [:curve-to [150 80] [350 280] [570 160]]]
         :paint/brush wet-brush
         :paint/color [:color/hsl 200 0.55 0.55]
         :paint/radius 40.0
         :paint/pressure [[0.0 0.6] [0.3 1.0] [0.7 0.9] [1.0 0.4]]}
        {:node/type :shape/path
         :path/commands [[:move-to [60 250]]
                         [:curve-to [200 150] [400 320] [540 220]]]
         :paint/brush wet-brush
         :paint/color [:color/hsl 30 0.5 0.55]
         :paint/radius 35.0
         :paint/pressure [[0.0 0.5] [0.5 0.9] [1.0 0.3]]}
        {:node/type :shape/path
         :path/commands [[:move-to [100 120]]
                         [:curve-to [250 60] [380 200] [520 100]]]
         :paint/brush wet-brush
         :paint/color [:color/hsl 120 0.35 0.45]
         :paint/radius 30.0
         :paint/pressure [[0.0 0.4] [0.4 0.8] [1.0 0.3]]}]}]}))

;; --- 6. Oil Impasto ---

(defn ^{:example {:output "paint-oil-impasto.png"
                  :title  "Oil Impasto"
                  :desc   "Thick oil paint with visible texture and smudge."
                  :tags   ["paint" "oil" "impasto" "smudge" "thick"]}}
  oil-impasto []
  (let [w 600 h 400
        thick-brush {:brush/type :brush/dab
                     :brush/tip {:tip/shape :ellipse :tip/hardness 0.55 :tip/aspect 1.3}
                     :brush/paint {:paint/opacity 0.65 :paint/flow 0.9 :paint/spacing 0.07
                                   :paint/blend :opaque}
                     :brush/impasto {:impasto/height 0.5}
                     :brush/smudge {:smudge/mode :smear :smudge/amount 0.4 :smudge/length 0.6}
                     :brush/jitter {:jitter/position 0.1 :jitter/opacity 0.15
                                    :jitter/size 0.12 :jitter/angle 0.08}}
        make-stroke (fn [pts color radius seed]
                      {:node/type :shape/path
                       :path/commands (into [[:move-to (first pts)]]
                                            (mapv (fn [p] [:line-to p]) (rest pts)))
                       :paint/brush thick-brush
                       :paint/color color
                       :paint/radius radius
                       :paint/seed seed
                       :paint/pressure [[0.0 0.5] [0.3 0.9] [0.7 1.0] [1.0 0.4]]})]
    {:image/size [w h]
     :image/background [:color/rgb 238 232 218]
     :image/nodes
     [{:node/type :group
       :paint/surface {:paint/size [w h]}
       :group/children
       [(make-stroke [[40 300] [150 280] [300 320] [450 290] [560 310]]
                     [:color/rgb 35 90 50] 22.0 1)
        (make-stroke [[50 240] [180 210] [340 260] [500 230]]
                     [:color/rgb 180 140 40] 18.0 2)
        (make-stroke [[60 180] [200 140] [380 190] [540 160]]
                     [:color/rgb 200 80 40] 20.0 3)
        (make-stroke [[80 120] [220 90] [400 130] [520 100]]
                     [:color/rgb 60 100 180] 16.0 4)
        ;; Knife smears
        {:node/type :shape/path
         :path/commands [[:move-to [100 200]] [:line-to [300 180]] [:line-to [500 200]]]
         :paint/brush :palette-knife
         :paint/color [:color/rgb 220 200 140]
         :paint/radius 25.0
         :paint/seed 10}]}]}))

;; --- 7. Marker Sketch ---

(defn ^{:example {:output "paint-marker-sketch.png"
                  :title  "Marker Sketch"
                  :desc   "Flat marker with glazed buildup and pen details."
                  :tags   ["paint" "marker" "pen" "sketch" "glazed"]}}
  marker-sketch []
  (let [w 600 h 400]
    {:image/size [w h]
     :image/background [:color/rgb 242 238 228]
     :image/nodes
     [{:node/type :group
       :paint/surface {:paint/size [w h]}
       :group/children
       [;; Marker fills
        {:node/type :shape/path
         :path/commands [[:move-to [80 200]]
                         [:curve-to [200 120] [350 280] [480 180]]]
         :paint/brush :flat-marker
         :paint/color [:color/rgb 255 200 60]
         :paint/radius 20.0
         :paint/pressure [[0.0 0.6] [0.5 1.0] [1.0 0.5]]}
        {:node/type :shape/path
         :path/commands [[:move-to [120 250]]
                         [:curve-to [250 180] [380 300] [500 220]]]
         :paint/brush :flat-marker
         :paint/color [:color/rgb 100 180 230]
         :paint/radius 18.0
         :paint/pressure [[0.0 0.5] [0.5 0.9] [1.0 0.4]]}
        ;; Pen outlines
        {:node/type :shape/path
         :path/commands [[:move-to [60 180]]
                         [:curve-to [180 100] [360 260] [520 160]]]
         :paint/brush :felt-tip
         :paint/color [:color/rgb 30 25 20]
         :paint/radius 2.5
         :paint/pressure [[0.0 0.2] [0.3 0.8] [0.8 0.9] [1.0 0.1]]}
        {:node/type :shape/path
         :path/commands [[:move-to [100 280]]
                         [:curve-to [230 200] [400 310] [530 240]]]
         :paint/brush :ballpoint
         :paint/color [:color/rgb 20 20 60]
         :paint/radius 1.5
         :paint/pressure [[0.0 0.3] [0.5 0.7] [1.0 0.2]]}]}]}))

;; --- 8. Pastel on Toned Paper ---

(defn ^{:example {:output "paint-pastel-toned.png"
                  :title  "Pastel on Toned Paper"
                  :desc   "Soft pastel with heavy grain on textured toned paper."
                  :tags   ["paint" "pastel" "grain" "substrate" "dry-media"]}}
  pastel-toned []
  (let [w 600 h 400
        make-stroke (fn [pts color radius seed]
                      {:node/type :shape/path
                       :path/commands (into [[:move-to (first pts)]]
                                            (mapv (fn [p] [:line-to p]) (rest pts)))
                       :paint/brush :soft-pastel
                       :paint/color color
                       :paint/radius radius
                       :paint/seed seed
                       :paint/pressure [[0.0 0.4] [0.3 0.9] [0.7 1.0] [1.0 0.3]]})]
    {:image/size [w h]
     ;; Warm mid-tone paper
     :image/background [:color/rgb 160 145 125]
     :image/nodes
     [{:node/type :group
       :paint/surface {:paint/size [w h]
                       :substrate/tooth 0.4}
       :group/children
       [;; Dark shadows
        (make-stroke [[60 300] [200 270] [400 310] [540 280]]
                     [:color/rgb 60 45 35] 20.0 1)
        (make-stroke [[80 260] [240 230] [420 270] [520 240]]
                     [:color/rgb 70 50 40] 16.0 2)
        ;; Mid-tone warm colors
        (make-stroke [[40 180] [180 150] [360 190] [560 170]]
                     [:color/rgb 200 140 80] 18.0 3)
        (make-stroke [[50 220] [200 190] [380 230] [540 200]]
                     [:color/rgb 180 120 70] 15.0 4)
        ;; Light highlights
        (make-stroke [[100 100] [250 80] [400 110] [500 90]]
                     [:color/rgb 240 230 200] 14.0 5)
        (make-stroke [[80 140] [220 120] [380 150] [520 130]]
                     [:color/rgb 245 235 210] 12.0 6)]}]}))

;; --- 9. Spatter and Drip ---

(defn ^{:example {:output "paint-spatter-drip.png"
                  :title  "Spatter and Drip"
                  :desc   "Fast strokes with paint scatter and gravity drips."
                  :tags   ["paint" "spatter" "drip" "effects" "dynamic"]}}
  spatter-drip []
  (let [w 600 h 400
        spatter-brush {:brush/type :brush/dab
                       :brush/tip {:tip/shape :ellipse :tip/hardness 0.6}
                       :brush/paint {:paint/opacity 0.5 :paint/flow 0.9 :paint/spacing 0.06}
                       :brush/spatter {:spatter/threshold 0.3
                                       :spatter/density 0.5
                                       :spatter/spread 3.5
                                       :spatter/size [0.03 0.2]
                                       :spatter/opacity [0.2 0.7]
                                       :spatter/mode :scatter}
                       :brush/jitter {:jitter/position 0.1 :jitter/opacity 0.2}}
        drip-brush {:brush/type :brush/dab
                    :brush/tip {:tip/shape :ellipse :tip/hardness 0.5}
                    :brush/paint {:paint/opacity 0.5 :paint/flow 0.85 :paint/spacing 0.05}
                    :brush/spatter {:spatter/threshold 0.25
                                    :spatter/density 0.4
                                    :spatter/spread 4.0
                                    :spatter/size [0.02 0.12]
                                    :spatter/opacity [0.15 0.6]
                                    :spatter/mode :drip}
                    :brush/jitter {:jitter/position 0.08 :jitter/opacity 0.15}}]
    {:image/size [w h]
     :image/background [:color/rgb 242 238 228]
     :image/nodes
     [{:node/type :group
       :paint/surface {:paint/size [w h]}
       :group/children
       [{:node/type :shape/path
         :path/commands [[:move-to [40 200]]
                         [:curve-to [150 100] [350 280] [560 180]]]
         :paint/brush spatter-brush
         :paint/color [:color/rgb 200 40 30]
         :paint/radius 14.0
         :paint/seed 42
         :paint/pressure [[0.0 0.3] [0.3 1.0] [0.7 0.9] [1.0 0.2]]}
        {:node/type :shape/path
         :path/commands [[:move-to [80 100]]
                         [:line-to [300 80]] [:line-to [520 120]]]
         :paint/brush drip-brush
         :paint/color [:color/rgb 30 30 120]
         :paint/radius 10.0
         :paint/seed 77
         :paint/pressure [[0.0 0.5] [0.5 1.0] [1.0 0.4]]}
        {:node/type :shape/path
         :path/commands [[:move-to [100 320]]
                         [:curve-to [200 280] [400 350] [500 300]]]
         :paint/brush spatter-brush
         :paint/color [:color/rgb 20 120 60]
         :paint/radius 12.0
         :paint/seed 99
         :paint/pressure [[0.0 0.4] [0.5 0.9] [1.0 0.3]]}]}]}))

;; --- 10. Mixed Media ---

(defn ^{:example {:output "paint-mixed-media.png"
                  :title  "Mixed Media"
                  :desc   "Ink lines over watercolor washes with chalk highlights."
                  :tags   ["paint" "mixed-media" "ink" "watercolor" "chalk"]}}
  mixed-media []
  (let [w 600 h 400
        wet-brush {:brush/type :brush/dab
                   :brush/tip {:tip/shape :ellipse :tip/hardness 0.3}
                   :brush/paint {:paint/opacity 0.06 :paint/flow 0.5 :paint/spacing 0.04}
                   :brush/wet {:wet/enabled true :wet/deposit 0.3
                               :wet/diffusion 0.25 :wet/diffusion-steps 6
                               :wet/edge-darken 0.3 :wet/edge-sharpness 1.8
                               :wet/granulation 0.3}
                   :brush/jitter {:jitter/position 0.1 :jitter/opacity 0.2}}]
    {:image/size [w h]
     :image/background [:color/rgb 242 237 225]
     :image/nodes
     [{:node/type :group
       :paint/surface {:paint/size [w h]}
       :group/children
       [;; Watercolor washes (background)
        {:node/type :shape/path
         :path/commands [[:move-to [20 200]]
                         [:curve-to [150 100] [350 280] [580 160]]]
         :paint/brush wet-brush
         :paint/color [:color/hsl 210 0.5 0.6]
         :paint/radius 45.0
         :paint/pressure [[0.0 0.5] [0.5 0.9] [1.0 0.3]]}
        {:node/type :shape/path
         :path/commands [[:move-to [50 280]]
                         [:curve-to [200 180] [420 340] [560 250]]]
         :paint/brush wet-brush
         :paint/color [:color/hsl 40 0.45 0.55]
         :paint/radius 35.0
         :paint/pressure [[0.0 0.4] [0.5 0.8] [1.0 0.3]]}
        ;; Ink linework (midground)
        {:node/type :shape/path
         :path/commands [[:move-to [60 160]]
                         [:curve-to [180 80] [360 240] [540 140]]]
         :paint/brush :brush-pen
         :paint/color [:color/rgb 15 12 8]
         :paint/radius 3.5
         :paint/pressure [[0.0 0.1] [0.2 0.7] [0.8 0.9] [1.0 0.05]]}
        {:node/type :shape/path
         :path/commands [[:move-to [80 320]]
                         [:curve-to [220 250] [400 340] [520 280]]]
         :paint/brush :brush-pen
         :paint/color [:color/rgb 20 15 10]
         :paint/radius 3.0
         :paint/pressure [[0.0 0.05] [0.3 0.6] [0.7 0.8] [1.0 0.1]]}
        ;; Chalk highlights (foreground)
        {:node/type :shape/path
         :path/commands [[:move-to [120 140]]
                         [:curve-to [250 100] [380 180] [500 120]]]
         :paint/brush :chalk
         :paint/color [:color/rgb 240 235 210]
         :paint/radius 8.0
         :paint/seed 42
         :paint/pressure [[0.0 0.3] [0.5 0.8] [1.0 0.2]]}]}]}))

(comment
  (require '[eido.core :as eido])
  (eido/render (chalk-sketch) {:output "/tmp/paint-chalk-sketch.png"})
  (eido/render (ink-flow) {:output "/tmp/paint-ink-flow.png"})
  (eido/render (layered-strokes) {:output "/tmp/paint-layered-strokes.png"})
  (eido/render (charcoal-study) {:output "/tmp/paint-charcoal-study.png"})
  (eido/render (watercolor-wash) {:output "/tmp/paint-watercolor-wash.png"})
  (eido/render (oil-impasto) {:output "/tmp/paint-oil-impasto.png"})
  (eido/render (marker-sketch) {:output "/tmp/paint-marker-sketch.png"})
  (eido/render (pastel-toned) {:output "/tmp/paint-pastel-toned.png"})
  (eido/render (spatter-drip) {:output "/tmp/paint-spatter-drip.png"})
  (eido/render (mixed-media) {:output "/tmp/paint-mixed-media.png"}))
