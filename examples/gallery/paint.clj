(ns gallery.paint
  "Paint engine gallery — procedural brushwork, painterly effects,
  and composable generative paint techniques.

  Each example demonstrates specific paint engine features using
  the user-facing API. Examples are organized by what they showcase."
  {:category "Paint Engine"}
  (:require
    [eido.gen.noise :as noise]
    [eido.gen.prob :as prob]))

;; --- brush presets and basic strokes ---

(defn ^{:example {:output "paint-brush-sampler.png"
                  :title  "Brush Sampler"
                  :desc   "All major brush families on one canvas: dry, ink, marker, paint, and tools."
                  :tags   ["paint" "presets" "sampler"]}}
  brush-sampler []
  (let [w 700 h 600
        mk (fn [y brush color label]
              {:paint/brush brush :paint/color color :paint/seed (hash label)
               :paint/points (mapv (fn [i] [(+ 40 (* i 30)) (+ y (* 8 (noise/perlin2d (* i 0.1) y {:seed (hash label)})))
                                            (+ 0.3 (* 0.5 (Math/sin (* i 0.15)))) 0 0 0])
                                   (range 21))})
        rows [;; Dry media
              [40  :pencil      [:color/rgb 40 35 30]]
              [80  :graphite    [:color/rgb 35 30 25]]
              [120 :charcoal    [:color/rgb 25 20 15]]
              [160 :chalk       [:color/rgb 180 60 40]]
              [200 :pastel      [:color/rgb 60 100 160]]
              [240 :conte       [:color/rgb 80 50 30]]
              ;; Ink & pen
              [300 :ink         [:color/rgb 10 8 5]]
              [340 :brush-pen   [:color/rgb 10 8 5]]
              [380 :felt-tip    [:color/rgb 30 30 100]]
              [420 :ballpoint   [:color/rgb 20 20 60]]
              ;; Marker & paint
              [480 :flat-marker [:color/rgb 220 180 50]]
              [520 :watercolor  [:color/rgb 40 100 170]]
              [560 :oil         [:color/rgb 180 50 30]]]]
    {:image/size [w h] :image/background [:color/rgb 242 237 225]
     :image/nodes
     [{:node/type :paint/surface :paint/size [w h]
       :paint/strokes (mapv (fn [[y brush color]] (mk y brush color (name brush))) rows)}]}))

;; --- stroke texture: jitter and grain ---

(defn ^{:example {:output "paint-jitter-comparison.png"
                  :title  "Jitter Comparison"
                  :desc   "Same stroke with no jitter (top), light jitter (middle), heavy jitter (bottom)."
                  :tags   ["paint" "jitter" "texture" "comparison"]}}
  jitter-comparison []
  (let [w 600 h 300
        base {:brush/type :brush/dab
              :brush/tip {:tip/shape :ellipse :tip/hardness 0.5}
              :brush/paint {:paint/opacity 0.15 :paint/flow 0.8 :paint/spacing 0.05}}
        pts (fn [y seed] (mapv (fn [i] [(+ 40 (* i 26)) (+ y (* 10 (noise/perlin2d (* i 0.08) 0 {:seed seed})))
                                        (+ 0.4 (* 0.5 (Math/sin (* i 0.12)))) 0 0 0])
                                (range 22)))]
    {:image/size [w h] :image/background [:color/rgb 240 235 220]
     :image/nodes
     [{:node/type :paint/surface :paint/size [w h]
       :paint/strokes
       [{:paint/brush base
         :paint/color [:color/rgb 60 40 30] :paint/radius 12.0 :paint/seed 1
         :paint/points (pts 60 1)}
        {:paint/brush (assoc base :brush/jitter {:jitter/position 0.1 :jitter/opacity 0.2 :jitter/size 0.08})
         :paint/color [:color/rgb 60 40 30] :paint/radius 12.0 :paint/seed 2
         :paint/points (pts 150 2)}
        {:paint/brush (assoc base :brush/jitter {:jitter/position 0.25 :jitter/opacity 0.4 :jitter/size 0.2 :jitter/angle 0.3})
         :paint/color [:color/rgb 60 40 30] :paint/radius 12.0 :paint/seed 3
         :paint/points (pts 240 3)}]}]}))

(defn ^{:example {:output "paint-grain-types.png"
                  :title  "Grain Types"
                  :desc   "Six grain types applied to identical strokes: fBm, turbulence, ridge, fiber, weave, canvas."
                  :tags   ["paint" "grain" "texture" "comparison"]}}
  grain-types []
  (let [w 600 h 400
        types [:fbm :turbulence :ridge :fiber :weave :canvas]
        mk (fn [y grain-type]
              {:paint/brush {:brush/type :brush/dab
                             :brush/tip {:tip/shape :ellipse :tip/hardness 0.5}
                             :brush/paint {:paint/opacity 0.2 :paint/flow 0.8 :paint/spacing 0.05}
                             :brush/grain {:grain/type grain-type :grain/scale 0.1 :grain/contrast 0.5}}
               :paint/color [:color/rgb 50 35 25] :paint/radius 14.0 :paint/seed (hash grain-type)
               :paint/points (mapv (fn [i] [(+ 40 (* i 26)) y (+ 0.5 (* 0.4 (Math/sin (* i 0.15)))) 0 0 0])
                                   (range 22))})]
    {:image/size [w h] :image/background [:color/rgb 235 228 215]
     :image/nodes
     [{:node/type :paint/surface :paint/size [w h]
       :paint/strokes (vec (map-indexed (fn [i t] (mk (+ 50 (* i 60)) t)) types))}]}))

;; --- blend modes and impasto ---

(defn ^{:example {:output "paint-blend-modes.png"
                  :title  "Blend Modes"
                  :desc   "Three blend modes: source-over (default), glazed (max), opaque (heavy coverage)."
                  :tags   ["paint" "blend" "glazed" "opaque" "comparison"]}}
  blend-modes []
  (let [w 600 h 300
        mk (fn [y blend-mode]
              (let [b {:brush/type :brush/dab
                       :brush/tip {:tip/shape :ellipse :tip/hardness 0.4}
                       :brush/paint {:paint/opacity 0.3 :paint/flow 0.8 :paint/spacing 0.04
                                     :paint/blend blend-mode}}]
                ;; Two overlapping strokes to show how they interact
                [{:paint/brush b :paint/color [:color/rgb 200 60 30] :paint/radius 16.0 :paint/seed 1
                  :paint/points (mapv (fn [i] [(+ 40 (* i 14)) (+ y (* 15 (Math/sin (* i 0.1)))) 0.7 0 0 0]) (range 40))}
                 {:paint/brush b :paint/color [:color/rgb 30 60 200] :paint/radius 16.0 :paint/seed 2
                  :paint/points (mapv (fn [i] [(+ 40 (* i 14)) (+ y (* -15 (Math/sin (* i 0.1)))) 0.7 0 0 0]) (range 40))}]))]
    {:image/size [w h] :image/background [:color/rgb 242 237 225]
     :image/nodes
     [{:node/type :paint/surface :paint/size [w h]
       :paint/strokes (vec (mapcat (fn [[y mode]] (mk y mode))
                                   [[60 :source-over] [160 :glazed] [260 :opaque]]))}]}))

(defn ^{:example {:output "paint-impasto-demo.png"
                  :title  "Impasto Height"
                  :desc   "Thick paint with directional lighting from height planes. Top: flat. Bottom: impasto."
                  :tags   ["paint" "impasto" "height" "lighting"]}}
  impasto-demo []
  (let [w 600 h 250
        flat {:brush/type :brush/dab
              :brush/tip {:tip/shape :ellipse :tip/hardness 0.5 :tip/aspect 1.3}
              :brush/paint {:paint/opacity 0.6 :paint/flow 0.9 :paint/spacing 0.06 :paint/blend :opaque}
              :brush/jitter {:jitter/position 0.08 :jitter/opacity 0.12 :jitter/size 0.1}}
        thick (assoc flat :brush/impasto {:impasto/height 0.6})
        pts (fn [y seed] (mapv (fn [i] [(+ 40 (* i 20)) (+ y (* 8 (noise/perlin2d (* i 0.08) 0 {:seed seed})))
                                        (+ 0.5 (* 0.4 (Math/sin (* i 0.12)))) 0 0 0])
                                (range 27)))]
    {:image/size [w h] :image/background [:color/rgb 238 232 218]
     :image/nodes
     [{:node/type :paint/surface :paint/size [w h]
       :paint/strokes
       [{:paint/brush flat :paint/color [:color/rgb 180 60 30] :paint/radius 16.0 :paint/seed 1 :paint/points (pts 70 1)}
        {:paint/brush thick :paint/color [:color/rgb 180 60 30] :paint/radius 16.0 :paint/seed 2 :paint/points (pts 180 2)}]}]}))

;; --- spatter and wet media ---

(defn ^{:example {:output "paint-spatter-modes.png"
                  :title  "Spatter Modes"
                  :desc   "Two spatter modes: scatter (perpendicular) and spray (cone along stroke)."
                  :tags   ["paint" "spatter" "spray" "comparison"]}}
  spatter-modes []
  (let [w 600 h 250
        mk (fn [y mode color seed]
              (let [b {:brush/type :brush/dab
                       :brush/tip {:tip/shape :ellipse :tip/hardness 0.15}
                       :brush/paint {:paint/opacity 0.1 :paint/flow 0.7 :paint/spacing 0.035}
                       :brush/spatter {:spatter/threshold 0.3 :spatter/density 0.5 :spatter/spread 1.5
                                       :spatter/size [0.05 0.15] :spatter/opacity [0.15 0.5]
                                       :spatter/mode mode}
                       :brush/jitter {:jitter/position 0.06 :jitter/opacity 0.1}}]
                {:paint/brush b :paint/color color :paint/radius 12.0 :paint/seed seed
                 :paint/points (mapv (fn [i] [(+ 60 (* i 24)) y (+ 0.4 (* 0.5 (Math/sin (* i 0.12)))) 0 0 0])
                                     (range 22))}))]
    {:image/size [w h] :image/background [:color/rgb 242 238 228]
     :image/nodes
     [{:node/type :paint/surface :paint/size [w h]
       :paint/strokes
       [(mk 80 :scatter [:color/rgb 200 40 30] 42)
        (mk 170 :spray [:color/rgb 30 120 50] 99)]}]}))

(defn ^{:example {:output "paint-wet-granulation.png"
                  :title  "Wet Media Granulation"
                  :desc   "Watercolor with increasing granulation: none, light, heavy. Shows salt-crystal texture."
                  :tags   ["paint" "watercolor" "granulation" "wet-media" "comparison"]}}
  wet-granulation []
  (let [w 600 h 300
        mk (fn [y gran]
              (let [b {:brush/type :brush/dab
                       :brush/tip {:tip/shape :ellipse :tip/hardness 0.25}
                       :brush/paint {:paint/opacity 0.07 :paint/flow 0.5 :paint/spacing 0.04}
                       :brush/wet {:wet/enabled true :wet/deposit 0.35 :wet/diffusion 0.25
                                   :wet/diffusion-steps 6 :wet/edge-darken 0.35
                                   :wet/edge-sharpness 2.0 :wet/granulation gran}
                       :brush/jitter {:jitter/position 0.12 :jitter/opacity 0.2 :jitter/size 0.1}}]
                {:paint/brush b :paint/color [:color/hsl 200 0.5 0.5] :paint/radius 30.0 :paint/seed (hash gran)
                 :paint/points (mapv (fn [i] [(+ 50 (* i 25)) (+ y (* 10 (noise/perlin2d (* i 0.06) 0 {:seed (hash gran)})))
                                              (+ 0.4 (* 0.5 (Math/sin (* i 0.1)))) 0 0 0])
                                     (range 22))}))]
    {:image/size [w h] :image/background [:color/rgb 242 237 225]
     :image/nodes
     [{:node/type :paint/surface :paint/size [w h]
       :paint/strokes [(mk 60 0.0) (mk 155 0.3) (mk 250 0.7)]}]}))

;; --- subtractive color mixing ---

(defn ^{:example {:output "paint-subtractive-mixing.png"
                  :title  "Subtractive Pigment Mixing"
                  :desc   "Three primary paint colors crossing: blue+yellow=green, red+blue=purple, red+yellow=orange."
                  :tags   ["paint" "subtractive" "color-mixing" "pigment"]}}
  subtractive-mixing []
  (let [w 500 h 400
        sb {:brush/type :brush/dab
            :brush/tip {:tip/shape :ellipse :tip/hardness 0.08}
            :brush/paint {:paint/opacity 0.05 :paint/flow 0.45 :paint/spacing 0.025
                          :paint/blend :subtractive}
            :brush/wet {:wet/enabled true :wet/deposit 0.2 :wet/diffusion 0.3
                        :wet/diffusion-steps 12 :wet/edge-darken 0.1 :wet/edge-sharpness 1.5}
            :brush/jitter {:jitter/position 0.1 :jitter/opacity 0.15 :jitter/size 0.1}}]
    {:image/size [w h] :image/background [:color/rgb 245 240 228]
     :image/nodes
     [{:node/type :group :paint/surface {:paint/size [w h]}
       :group/children
       [;; Blue horizontal band
        {:node/type :shape/path
         :path/commands [[:move-to [30 200]] [:line-to [470 200]]]
         :paint/brush sb :paint/color [:color/rgb 40 90 210] :paint/radius 55.0
         :paint/pressure [[0.0 0.5] [0.5 0.8] [1.0 0.5]]}
        ;; Yellow diagonal — crosses blue to make green
        {:node/type :shape/path
         :path/commands [[:move-to [120 20]] [:line-to [200 380]]]
         :paint/brush sb :paint/color [:color/rgb 235 215 45] :paint/radius 50.0
         :paint/pressure [[0.0 0.5] [0.5 0.8] [1.0 0.5]]}
        ;; Red diagonal — crosses blue to make purple
        {:node/type :shape/path
         :path/commands [[:move-to [380 20]] [:line-to [300 380]]]
         :paint/brush sb :paint/color [:color/rgb 210 45 35] :paint/radius 50.0
         :paint/pressure [[0.0 0.5] [0.5 0.8] [1.0 0.5]]}]}]}))

(defn ^{:example {:output "paint-oil-color-blend.png"
                  :title  "Oil Color Blending"
                  :desc   "Thick oil paint smudged together: red+blue, blue+yellow, red+yellow."
                  :tags   ["paint" "oil" "subtractive" "color-mixing" "smudge"]}}
  oil-color-blend []
  (let [w 600 h 250
        ob {:brush/type :brush/dab
            :brush/tip {:tip/shape :ellipse :tip/hardness 0.15 :tip/aspect 1.4}
            :brush/paint {:paint/opacity 0.12 :paint/flow 0.8 :paint/spacing 0.02
                          :paint/blend :subtractive}
            :brush/jitter {:jitter/position 0.12 :jitter/opacity 0.2
                           :jitter/size 0.15 :jitter/angle 0.1}}
        pair (fn [x color-a color-b seed]
               ;; Multiple passes per color for richer coverage
               (vec (concat
                 (for [i (range 3)]
                   {:node/type :shape/path
                    :path/commands [[:move-to [(+ x (* i 4)) 30]] [:line-to [(+ x 85 (* i 4)) 220]]]
                    :paint/brush ob :paint/color color-a :paint/radius 22.0 :paint/seed (+ seed i)
                    :paint/pressure [[0.0 0.5] [0.5 1.0] [1.0 0.6]]})
                 (for [i (range 3)]
                   {:node/type :shape/path
                    :path/commands [[:move-to [(+ x 150 (* i 4)) 30]] [:line-to [(+ x 65 (* i 4)) 220]]]
                    :paint/brush ob :paint/color color-b :paint/radius 22.0 :paint/seed (+ seed 10 i)
                    :paint/pressure [[0.0 0.5] [0.5 1.0] [1.0 0.6]]}))))]
    {:image/size [w h] :image/background [:color/rgb 238 232 218]
     :image/nodes
     [{:node/type :group :paint/surface {:paint/size [w h]}
       :group/children
       (vec (concat
              (pair 30  [:color/rgb 200 35 30]  [:color/rgb 30 60 200] 10)
              (pair 220 [:color/rgb 30 60 200]  [:color/rgb 235 210 35] 20)
              (pair 410 [:color/rgb 200 35 30]  [:color/rgb 235 210 35] 30)))}]}))

(defn ^{:example {:output "paint-acrylic-color-blend.png"
                  :title  "Acrylic Color Blending"
                  :desc   "Glossy acrylic paint mixing: saturated, smooth coverage."
                  :tags   ["paint" "acrylic" "subtractive" "color-mixing" "glossy"]}}
  acrylic-color-blend []
  (let [w 600 h 250
        ab {:brush/type :brush/dab
            :brush/tip {:tip/shape :ellipse :tip/hardness 0.8}
            :brush/paint {:paint/opacity 0.18 :paint/flow 0.95 :paint/spacing 0.02
                          :paint/blend :subtractive}}
        pair (fn [x color-a color-b seed]
               (vec (concat
                 (for [i (range 3)]
                   {:node/type :shape/path
                    :path/commands [[:move-to [(+ x (* i 4)) 30]] [:line-to [(+ x 85 (* i 4)) 220]]]
                    :paint/brush ab :paint/color color-a :paint/radius 22.0 :paint/seed (+ seed i)
                    :paint/pressure [[0.0 0.5] [0.5 1.0] [1.0 0.6]]})
                 (for [i (range 3)]
                   {:node/type :shape/path
                    :path/commands [[:move-to [(+ x 150 (* i 4)) 30]] [:line-to [(+ x 65 (* i 4)) 220]]]
                    :paint/brush ab :paint/color color-b :paint/radius 22.0 :paint/seed (+ seed 10 i)
                    :paint/pressure [[0.0 0.5] [0.5 1.0] [1.0 0.6]]}))))]
    {:image/size [w h] :image/background [:color/rgb 240 238 232]
     :image/nodes
     [{:node/type :group :paint/surface {:paint/size [w h]}
       :group/children
       (vec (concat
              (pair 30  [:color/rgb 210 30 25]  [:color/rgb 25 55 210] 10)
              (pair 220 [:color/rgb 25 55 210]  [:color/rgb 240 220 30] 20)
              (pair 410 [:color/rgb 210 30 25]  [:color/rgb 240 220 30] 30)))}]}))

(defn ^{:example {:output "paint-gouache-color-blend.png"
                  :title  "Gouache Color Blending"
                  :desc   "Matte gouache mixing: opaque, flat coverage with chalky blending zones."
                  :tags   ["paint" "gouache" "subtractive" "color-mixing" "matte"]}}
  gouache-color-blend []
  (let [w 600 h 250
        gb {:brush/type :brush/dab
            :brush/tip {:tip/shape :ellipse :tip/hardness 0.55}
            :brush/paint {:paint/opacity 0.25 :paint/flow 0.85 :paint/spacing 0.03
                          :paint/blend :subtractive}
            :brush/grain {:grain/type :canvas :grain/scale 0.06 :grain/contrast 0.35}
            :brush/jitter {:jitter/position 0.03 :jitter/opacity 0.05}}
        pair (fn [x color-a color-b seed]
               (vec (concat
                 (for [i (range 3)]
                   {:node/type :shape/path
                    :path/commands [[:move-to [(+ x (* i 4)) 30]] [:line-to [(+ x 85 (* i 4)) 220]]]
                    :paint/brush gb :paint/color color-a :paint/radius 22.0 :paint/seed (+ seed i)
                    :paint/pressure [[0.0 0.5] [0.5 1.0] [1.0 0.6]]})
                 (for [i (range 3)]
                   {:node/type :shape/path
                    :path/commands [[:move-to [(+ x 150 (* i 4)) 30]] [:line-to [(+ x 65 (* i 4)) 220]]]
                    :paint/brush gb :paint/color color-b :paint/radius 22.0 :paint/seed (+ seed 10 i)
                    :paint/pressure [[0.0 0.5] [0.5 1.0] [1.0 0.6]]}))))]
    {:image/size [w h] :image/background [:color/rgb 235 230 220]
     :image/nodes
     [{:node/type :group :paint/surface {:paint/size [w h]}
       :group/children
       (vec (concat
              (pair 30  [:color/rgb 195 35 30]  [:color/rgb 30 50 190] 10)
              (pair 220 [:color/rgb 30 50 190]  [:color/rgb 230 210 35] 20)
              (pair 410 [:color/rgb 195 35 30]  [:color/rgb 230 210 35] 30)))}]}))

;; --- generator compositions ---

(defn ^{:example {:output "paint-gen-charcoal-flow.png"
                  :title  "Charcoal Flow Field"
                  :desc   "Flow field streamlines rendered with charcoal on textured paper."
                  :tags   ["paint" "flow-field" "charcoal" "generative"]}}
  gen-charcoal-flow []
  {:image/size [600 600] :image/background [:color/rgb 230 222 208]
   :image/nodes
   [{:node/type :group
     :paint/surface {:paint/size [600 600] :substrate/tooth 0.3}
     :group/children
     [{:node/type :flow-field
       :flow/bounds [20 20 560 560]
       :flow/opts {:density 25 :steps 50 :noise-scale 0.005 :step-length 2.5 :seed 31}
       :paint/brush :charcoal
       :paint/color [:color/rgb 30 25 20]
       :paint/radius 3.0
       :paint/pressure [[0.0 0.1] [0.2 0.6] [0.8 0.7] [1.0 0.05]]}]}]})

(defn ^{:example {:output "paint-gen-ink-topo.png"
                  :title  "Ink Contour Map"
                  :desc   "Contour lines rendered with brush-pen — hand-drawn topographic map."
                  :tags   ["paint" "contour" "ink" "generative"]}}
  gen-ink-topo []
  {:image/size [600 600] :image/background [:color/rgb 242 237 225]
   :image/nodes
   [{:node/type :group :paint/surface {:paint/size [600 600]}
     :group/children
     [{:node/type :contour
       :contour/bounds [20 20 560 560]
       :contour/opts {:thresholds (mapv #(- (* % 0.12) 0.7) (range 12))
                      :resolution 3.0 :noise-scale 0.008 :seed 42}
       :paint/brush :brush-pen
       :paint/color [:color/rgb 20 15 8]
       :paint/radius 1.8
       :paint/pressure [[0.0 0.2] [0.3 0.7] [0.7 0.8] [1.0 0.15]]}]}]})

(defn ^{:example {:output "paint-gen-watercolor-mandala.png"
                  :title  "Watercolor Mandala"
                  :desc   "8-fold radial symmetry with watercolor washes — wet edges bloom outward."
                  :tags   ["paint" "symmetry" "watercolor" "generative"]}}
  gen-watercolor-mandala []
  (let [wb {:brush/type :brush/dab
            :brush/tip {:tip/shape :ellipse :tip/hardness 0.25}
            :brush/paint {:paint/opacity 0.05 :paint/flow 0.5 :paint/spacing 0.04}
            :brush/wet {:wet/enabled true :wet/deposit 0.3 :wet/diffusion 0.25
                        :wet/diffusion-steps 6 :wet/edge-darken 0.3 :wet/edge-sharpness 2.0
                        :wet/granulation 0.3}
            :brush/jitter {:jitter/position 0.1 :jitter/opacity 0.2 :jitter/size 0.1}}]
    {:image/size [600 600] :image/background [:color/rgb 242 237 225]
     :image/nodes
     [{:node/type :group :paint/surface {:paint/size [600 600]}
       :group/children
       [{:node/type :symmetry :symmetry/type :radial :symmetry/n 8
         :symmetry/center [300 300]
         :group/children
         [{:node/type :shape/path
           :path/commands [[:move-to [300 300]] [:curve-to [320 200] [360 180] [380 120]]]
           :paint/brush wb :paint/color [:color/hsl 200 0.5 0.55] :paint/radius 20.0
           :paint/pressure [[0.0 0.8] [0.5 1.0] [1.0 0.3]]}
          {:node/type :shape/path
           :path/commands [[:move-to [310 280]] [:curve-to [340 220] [370 200] [350 140]]]
           :paint/brush wb :paint/color [:color/hsl 30 0.45 0.55] :paint/radius 15.0
           :paint/pressure [[0.0 0.6] [0.5 0.9] [1.0 0.2]]}]}]}]}))

(defn ^{:example {:output "paint-gen-botanical.png"
                  :title  "Botanical L-System"
                  :desc   "Branching plant structure via L-system, rendered with conte crayon."
                  :tags   ["paint" "lsystem" "botanical" "generative"]}}
  gen-botanical []
  {:image/size [600 700] :image/background [:color/rgb 238 232 218]
   :image/nodes
   [{:node/type :group
     :paint/surface {:paint/size [600 700] :substrate/tooth 0.25}
     :group/children
     [{:node/type :lsystem
       :lsystem/axiom "F"
       :lsystem/rules {"F" "FF+[+F-F-F]-[-F+F+F]"}
       :lsystem/iterations 4
       :lsystem/angle 22
       :lsystem/length 6.0
       :lsystem/origin [300 650]
       :lsystem/heading -90
       :paint/brush :conte
       :paint/color [:color/rgb 45 35 25]
       :paint/radius 2.5
       :paint/pressure [[0.0 0.8] [0.5 0.6] [1.0 0.2]]}]}]})

(defn ^{:example {:output "paint-gen-pastel-voronoi.png"
                  :title  "Pastel Voronoi"
                  :desc   "Voronoi cell edges rendered with soft pastel on toned paper."
                  :tags   ["paint" "voronoi" "pastel" "generative"]}}
  gen-pastel-voronoi []
  (let [rng (prob/make-rng 42)
        pts (mapv (fn [_] [(+ 30 (* 540 (.nextDouble rng))) (+ 30 (* 540 (.nextDouble rng)))]) (range 30))]
    {:image/size [600 600] :image/background [:color/rgb 160 148 130]
     :image/nodes
     [{:node/type :group
       :paint/surface {:paint/size [600 600] :substrate/tooth 0.35}
       :group/children
       [{:node/type :voronoi
         :voronoi/points pts :voronoi/bounds [0 0 600 600]
         :paint/brush :soft-pastel :paint/color [:color/rgb 220 210 185]
         :paint/radius 4.0 :paint/pressure [[0.0 0.3] [0.5 0.8] [1.0 0.3]]}]}]}))

(defn ^{:example {:output "paint-gen-delaunay-web.png"
                  :title  "Delaunay Ink Web"
                  :desc   "Delaunay triangulation with technical pen — geometric precision meets handmade ink."
                  :tags   ["paint" "delaunay" "ink" "generative"]}}
  gen-delaunay-web []
  (let [rng (prob/make-rng 88)
        pts (mapv (fn [_] [(+ 40 (* 520 (.nextDouble rng))) (+ 40 (* 520 (.nextDouble rng)))]) (range 40))]
    {:image/size [600 600] :image/background [:color/rgb 245 242 232]
     :image/nodes
     [{:node/type :group :paint/surface {:paint/size [600 600]}
       :group/children
       [{:node/type :delaunay
         :delaunay/points pts :delaunay/bounds [0 0 600 600]
         :paint/brush :technical-pen :paint/color [:color/rgb 20 18 15] :paint/radius 0.8}]}]}))

(defn ^{:example {:output "paint-gen-oil-symmetry.png"
                  :title  "Oil Bilateral Symmetry"
                  :desc   "Bilateral mirror with thick impasto oil paint — Rorschach meets color field."
                  :tags   ["paint" "symmetry" "oil" "impasto" "generative"]}}
  gen-oil-symmetry []
  (let [ob {:brush/type :brush/dab
            :brush/tip {:tip/shape :ellipse :tip/hardness 0.5 :tip/aspect 1.4}
            :brush/paint {:paint/opacity 0.6 :paint/flow 0.85 :paint/spacing 0.06 :paint/blend :opaque}
            :brush/impasto {:impasto/height 0.5}
            :brush/smudge {:smudge/mode :smear :smudge/amount 0.35 :smudge/length 0.5}
            :brush/jitter {:jitter/position 0.1 :jitter/opacity 0.15 :jitter/size 0.1 :jitter/angle 0.08}}]
    {:image/size [600 600] :image/background [:color/rgb 235 228 215]
     :image/nodes
     [{:node/type :group :paint/surface {:paint/size [600 600]}
       :group/children
       [{:node/type :symmetry :symmetry/type :bilateral :symmetry/axis :vertical
         :symmetry/center [300 300]
         :group/children
         [{:node/type :shape/path
           :path/commands [[:move-to [310 80]] [:curve-to [380 180] [340 300] [400 420]]]
           :paint/brush ob :paint/color [:color/rgb 180 50 35] :paint/radius 18.0 :paint/seed 1
           :paint/pressure [[0.0 0.4] [0.3 1.0] [0.7 0.9] [1.0 0.3]]}
          {:node/type :shape/path
           :path/commands [[:move-to [320 120]] [:curve-to [360 200] [330 350] [370 480]]]
           :paint/brush ob :paint/color [:color/rgb 40 80 150] :paint/radius 14.0 :paint/seed 2
           :paint/pressure [[0.0 0.3] [0.4 0.9] [0.8 0.8] [1.0 0.2]]}
          {:node/type :shape/path
           :path/commands [[:move-to [340 160]] [:curve-to [400 260] [350 380] [380 520]]]
           :paint/brush ob :paint/color [:color/rgb 200 170 50] :paint/radius 10.0 :paint/seed 3
           :paint/pressure [[0.0 0.5] [0.5 1.0] [1.0 0.4]]}]}]}]}))

(defn ^{:example {:output "paint-gen-flow-calligraphy.png"
                  :title  "Flow Calligraphy"
                  :desc   "Sparse flow field with brush-pen dynamics — calligraphic marks guided by noise."
                  :tags   ["paint" "flow-field" "calligraphy" "generative"]}}
  gen-flow-calligraphy []
  {:image/size [600 600] :image/background [:color/rgb 242 237 225]
   :image/nodes
   [{:node/type :group :paint/surface {:paint/size [600 600]}
     :group/children
     [{:node/type :flow-field
       :flow/bounds [40 40 520 520]
       :flow/opts {:density 10 :steps 60 :noise-scale 0.004 :step-length 3.5 :seed 7}
       :paint/brush {:brush/type :brush/dab
                     :brush/tip {:tip/shape :ellipse :tip/hardness 0.75 :tip/aspect 2.0}
                     :brush/paint {:paint/opacity 0.55 :paint/flow 0.9 :paint/spacing 0.025}
                     :brush/jitter {:jitter/angle 0.06}}
       :paint/color [:color/rgb 12 10 6] :paint/radius 3.5
       :paint/pressure [[0.0 0.05] [0.15 0.7] [0.5 1.0] [0.85 0.6] [1.0 0.03]]}]}]})

(comment
  (require '[eido.core :as eido])
  ;; Presets & basics
  (eido/render (brush-sampler) {:output "/tmp/paint-brush-sampler.png"})
  ;; Texture
  (eido/render (jitter-comparison) {:output "/tmp/paint-jitter-comparison.png"})
  (eido/render (grain-types) {:output "/tmp/paint-grain-types.png"})
  ;; Blend & impasto
  (eido/render (blend-modes) {:output "/tmp/paint-blend-modes.png"})
  (eido/render (impasto-demo) {:output "/tmp/paint-impasto-demo.png"})
  ;; Spatter & wet
  (eido/render (spatter-modes) {:output "/tmp/paint-spatter-modes.png"})
  (eido/render (wet-granulation) {:output "/tmp/paint-wet-granulation.png"})
  ;; Generator compositions
  (eido/render (gen-charcoal-flow) {:output "/tmp/paint-gen-charcoal-flow.png"})
  (eido/render (gen-ink-topo) {:output "/tmp/paint-gen-ink-topo.png"})
  (eido/render (gen-watercolor-mandala) {:output "/tmp/paint-gen-watercolor-mandala.png"})
  (eido/render (gen-botanical) {:output "/tmp/paint-gen-botanical.png"})
  (eido/render (gen-pastel-voronoi) {:output "/tmp/paint-gen-pastel-voronoi.png"})
  (eido/render (gen-delaunay-web) {:output "/tmp/paint-gen-delaunay-web.png"})
  (eido/render (gen-oil-symmetry) {:output "/tmp/paint-gen-oil-symmetry.png"})
  (eido/render (gen-flow-calligraphy) {:output "/tmp/paint-gen-flow-calligraphy.png"}))
