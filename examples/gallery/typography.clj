(ns gallery.typography
  "Typography gallery — text as vector path outlines with fills, effects, and 3D."
  {:category "Typography"}
  (:require
    [eido.animate :as anim]
    [eido.scene :as scene]
    [eido.scene3d :as s3d]
    [eido.text :as text]))

;; --- 1. Gradient Text with Shadow ---

(defn ^{:example {:output "text-gradient-shadow.png"
                  :title  "Gradient Text with Shadow"
                  :desc   "Layered text using text-stack — a shadow layer offset by a few pixels, then a gradient fill on top."
                  :tags   ["typography" "gradients" "shadows"]}}
  gradient-text-with-shadow []
  {:image/size [600 200]
   :image/background [:color/rgb 15 15 25]
   :image/nodes
   [(scene/text-stack "Typography" [40 140]
      {:font/family "Serif" :font/size 72 :font/weight :bold}
      [;; shadow layer
       {:style/fill [:color/rgba 0 0 0 0.4]
        :node/transform [[:transform/translate 3 3]]}
       ;; gradient fill
       {:style/fill {:gradient/type :linear
                     :gradient/from [0 60]
                     :gradient/to   [500 140]
                     :gradient/stops [[0.0 [:color/rgb 255 100 120]]
                                      [0.5 [:color/rgb 255 220 100]]
                                      [1.0 [:color/rgb 100 200 255]]]}}])]})

;; --- 2. Per-Glyph Rainbow ---

(defn ^{:example {:output "text-rainbow-glyphs.png"
                  :title  "Per-Glyph Rainbow"
                  :desc   "Each glyph styled independently using :shape/text-glyphs, cycling hue by 40 degrees per character."
                  :tags   ["typography" "color"]}}
  per-glyph-rainbow []
  {:image/size [600 160]
   :image/background [:color/rgb 20 20 35]
   :image/nodes
   [{:node/type    :shape/text-glyphs
     :text/content "CHROMATIC"
     :text/font    {:font/family "SansSerif" :font/size 64 :font/weight :bold}
     :text/origin  [20 110]
     :text/glyphs  (vec (map-indexed
                          (fn [i _]
                            {:glyph/index i
                             :style/fill [:color/hsl (mod (* i 40) 360) 0.85 0.6]})
                          "CHROMATIC"))
     :style/fill   [:color/rgb 255 255 255]}]})

;; --- 3. Neon Glow ---

(defn ^{:example {:output "text-neon-glow.png"
                  :title  "Neon Glow"
                  :desc   "A blurred copy behind crisp text creates a glow effect using :group/filter [:blur 8]."
                  :tags   ["typography" "glow" "filters"]}}
  neon-glow []
  {:image/size [500 200]
   :image/background [:color/rgb 10 10 20]
   :image/nodes
   [;; glow layer (blurred)
    {:node/type :group
     :group/composite :src-over
     :group/filter [:blur 8]
     :group/children
     [{:node/type   :shape/text
       :text/content "NEON"
       :text/font   {:font/family "SansSerif" :font/size 80 :font/weight :bold}
       :text/origin [70 130]
       :style/fill  [:color/rgb 0 255 200]}]}
    ;; crisp text on top
    {:node/type   :shape/text
     :text/content "NEON"
     :text/font   {:font/family "SansSerif" :font/size 80 :font/weight :bold}
     :text/origin [70 130]
     :style/fill  [:color/rgb 200 255 240]}]})

;; --- 4. Animated Circular Text ---

(defn ^{:example {:output "text-circular.gif"
                  :title  "Animated Circular Text"
                  :desc   "Text following a circular path, rotating around a gradient sphere."
                  :tags   ["typography" "animation" "gradients"]}}
  animated-circular-text []
  (let [frame-fn
        (fn [t]
          (let [r 130 cx 200 cy 200]
            {:image/size [400 400]
             :image/background [:color/rgb 245 243 238]
             :image/nodes
             [{:node/type :shape/text-on-path
               :text/content "EIDO\u00B7DATA\u00B7ART\u00B7"
               :text/font {:font/family "SansSerif" :font/size 20 :font/weight :bold}
               :text/path (let [steps 64
                                offset (* t 2 Math/PI)]
                            (into [[:move-to [(+ cx (* r (Math/cos offset)))
                                              (+ cy (* r (Math/sin offset)))]]]
                                  (map (fn [i]
                                         (let [a (+ offset (* (/ (inc i) steps)
                                                               2 Math/PI))]
                                           [:line-to [(+ cx (* r (Math/cos a)))
                                                       (+ cy (* r (Math/sin a)))]]))
                                       (range steps))))
               :text/spacing 2
               :style/fill [:color/rgb 60 60 80]}
              {:node/type :shape/circle
               :circle/center [cx cy]
               :circle/radius 80
               :style/fill {:gradient/type :radial
                            :gradient/center [cx cy]
                            :gradient/radius 80
                            :gradient/stops [[0.0 [:color/rgb 255 120 100]]
                                              [1.0 [:color/rgb 200 60 120]]]}}]}))]
    {:frames (anim/frames 90 frame-fn) :fps 30}))

;; --- 5. Rotating 3D Extruded Text ---

(defn ^{:example {:output "text-3d-rotate.gif"
                  :title  "Rotating 3D Extruded Text"
                  :desc   "Each letter extruded in 3D with phase-shifted sine waves driving vertical bounce and pitch rock."
                  :tags   ["typography" "3d" "animation"]}}
  rotating-3d-text []
  (let [letters "EIDO"
        font {:font/family "SansSerif" :font/size 36 :font/weight :bold}
        glyph-data (text/text->glyph-paths letters font)
        advance (text/text-advance letters font)
        half-w (/ advance 2.0)
        letter-xs (mapv (fn [i]
                          (let [[gx] (:position (nth glyph-data i))
                                next-gx (if (< i (dec (count letters)))
                                          ((:position (nth glyph-data (inc i))) 0)
                                          advance)]
                            (* 3.0 (- (+ gx (/ (- next-gx gx) 2.0)) half-w))))
                        (range (count letters)))
        frame-fn
        (fn [t]
          {:image/size [600 350]
           :image/background [:color/rgb 20 22 35]
           :image/nodes
           (vec (map-indexed
                  (fn [i c]
                    (let [;; vertical wave (2 cycles)
                          wave (* 20.0 (Math/sin (- (* t 2 Math/PI 2) (* i 1.0))))
                          ;; pitch rock (2 cycles, offset phase)
                          rock (* 0.4 (Math/sin (+ (* t 2 Math/PI 2)
                                                    (* i 1.5) (* Math/PI 0.5))))
                          proj (s3d/perspective
                                 {:scale 3.0
                                  :origin [(+ 300 (nth letter-xs i))
                                           (- 175 wave)]
                                  :yaw 0.0
                                  :pitch (+ (* Math/PI 0.5) rock)
                                  :distance 250})]
                      (s3d/text-3d proj (str c) font
                        {:depth 12
                         :style {:style/fill [:color/rgb 255 140 60]}
                         :light {:light/direction [0 -1 0.5]
                                 :light/ambient 0.25
                                 :light/intensity 0.75}})))
                  letters))})]
    {:frames (anim/frames 60 frame-fn) :fps 30}))
