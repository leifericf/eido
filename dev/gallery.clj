(ns gallery
  "Artistic expression gallery — showcasing the new toolkit features."
  (:require
    [eido.animate :as anim]
    [eido.core :as eido]
    [eido.noise :as noise]
    [eido.palette :as palette]
    [eido.scatter :as scatter]
    [eido.scene :as scene]))

;; --- 1. Ink Landscape — hatching + distortion + variable-width strokes ---

(defn ink-landscape []
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
    (eido/render
      {:image/size [600 400]
       :image/background [:color/rgb 245 235 220]
       :image/nodes
       (into
         [;; Sun circle with hatch fill
          {:node/type :shape/circle
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
           [;; Mountain with cross-hatch and distortion
            {:node/type :shape/path
             :path/commands mountain-path
             :style/fill {:fill/type :hatch
                          :hatch/layers [{:angle 45 :spacing 6}
                                         {:angle -30 :spacing 8}]
                          :hatch/stroke-width 0.7
                          :hatch/color [:color/rgb 40 30 20]}
             :style/stroke {:color [:color/rgb 30 20 10] :width 2}
             :node/transform [[:transform/distort {:type :roughen :amount 2 :seed 42}]]}
            ;; Foreground calligraphic path
            {:node/type :shape/path
             :path/commands [[:move-to [0.0 350.0]]
                             [:curve-to [100.0 330.0] [200.0 310.0] [300.0 320.0]]
                             [:curve-to [400.0 330.0] [500.0 340.0] [600.0 330.0]]]
             :stroke/profile :brush
             :style/stroke {:color [:color/rgb 30 20 10] :width 8}}]))}
      {:output "images/art-ink-landscape.png"})))

;; --- 2. Starfield — scatter + noise + palette ---

(defn starfield []
  (let [pal (:midnight palette/palettes)
        star-positions (scatter/poisson-disk 0 0 600 400 15 42)
        bright-positions (scatter/noise-field 0 0 600 400 30 99)]
    (eido/render
      {:image/size [600 400]
       :image/background (nth pal 0)
       :image/nodes
       [;; Nebula glow — large blurred circles
        {:node/type :shape/circle
         :circle/center [200.0 180.0]
         :circle/radius 120.0
         :style/fill [:color/rgba 120 60 160 0.15]}
        {:node/type :shape/circle
         :circle/center [400.0 250.0]
         :circle/radius 100.0
         :style/fill [:color/rgba 60 80 180 0.12]}
        ;; Dim stars
        {:node/type :scatter
         :scatter/shape {:node/type :shape/circle
                         :circle/center [0.0 0.0]
                         :circle/radius 1.0
                         :style/fill [:color/rgba 200 200 220 0.6]}
         :scatter/positions star-positions
         :scatter/jitter {:x 2 :y 2 :seed 11}}
        ;; Bright stars with glow
        {:node/type :scatter
         :scatter/shape {:node/type :shape/circle
                         :circle/center [0.0 0.0]
                         :circle/radius 2.5
                         :style/fill [:color/rgb 255 250 230]
                         :effect/glow {:blur 6
                                       :color [:color/rgb 200 200 255]
                                       :opacity 0.4}}
         :scatter/positions bright-positions
         :scatter/jitter {:x 1 :y 1 :seed 22}}]}
      {:output "images/art-starfield.png"})))

;; --- 3. Stipple Portrait — stipple fill with varying density ---

(defn stipple-spheres []
  (eido/render
    {:image/size [400 400]
     :image/background [:color/rgb 245 240 230]
     :image/nodes
     [;; Large sphere — dense stipple
      {:node/type :shape/circle
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
      ;; Small sphere — sparse stipple
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
      ;; Tiny sphere — medium stipple
      {:node/type :shape/circle
       :circle/center [130.0 320.0]
       :circle/radius 35.0
       :style/fill {:fill/type :stipple
                    :stipple/density 0.5
                    :stipple/radius 0.8
                    :stipple/seed 77
                    :stipple/color [:color/rgb 30 30 30]
                    :stipple/background [:color/rgb 245 240 230]}
       :style/stroke {:color [:color/rgb 30 30 30] :width 1}}]}
    {:output "images/art-stipple-spheres.png"}))

;; --- 4. Polka Dot Pop Art — pattern fill + palette + shadows ---

(defn polka-pop []
  (let [pal (:neon palette/palettes)]
    (eido/render
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
               [80.0 320.0 40.0]]))}
      {:output "images/art-polka-pop.png"})))

;; --- 5. Calligraphy Flow — animated variable-width strokes ---

(defn calligraphy-flow []
  (let [frames
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
               :image/nodes (vec paths)})))]
    (eido/render frames {:output "images/art-calligraphy-flow.gif" :fps 30})))

;; --- 6. Decorative Border — path decorators + hatching ---

(defn decorative-frame []
  (let [frame-path [[:move-to [50.0 50.0]]
                    [:line-to [350.0 50.0]]
                    [:line-to [350.0 350.0]]
                    [:line-to [50.0 350.0]]
                    [:close]]]
    (eido/render
      {:image/size [400 400]
       :image/background [:color/rgb 250 245 235]
       :image/nodes
       [;; Central content — hatched rectangle
        {:node/type :shape/rect
         :rect/xy [80.0 80.0]
         :rect/size [240.0 240.0]
         :style/fill {:fill/type :hatch
                      :hatch/layers [{:angle 30 :spacing 12}
                                     {:angle -30 :spacing 12}]
                      :hatch/stroke-width 0.5
                      :hatch/color [:color/rgb 180 160 130]
                      :hatch/background [:color/rgb 250 245 235]}}
        ;; Decorative border — diamonds along frame path
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
        ;; Corner accent circles
        {:node/type :shape/circle
         :circle/center [50.0 50.0]
         :circle/radius 15.0
         :style/fill {:fill/type :stipple
                      :stipple/density 0.8
                      :stipple/radius 0.8
                      :stipple/seed 42
                      :stipple/color [:color/rgb 120 80 40]
                      :stipple/background [:color/rgb 250 245 235]}
         :style/stroke {:color [:color/rgb 120 80 40] :width 1.5}}
        {:node/type :shape/circle
         :circle/center [350.0 50.0]
         :circle/radius 15.0
         :style/fill {:fill/type :stipple
                      :stipple/density 0.8
                      :stipple/radius 0.8
                      :stipple/seed 43
                      :stipple/color [:color/rgb 120 80 40]
                      :stipple/background [:color/rgb 250 245 235]}
         :style/stroke {:color [:color/rgb 120 80 40] :width 1.5}}
        {:node/type :shape/circle
         :circle/center [50.0 350.0]
         :circle/radius 15.0
         :style/fill {:fill/type :stipple
                      :stipple/density 0.8
                      :stipple/radius 0.8
                      :stipple/seed 44
                      :stipple/color [:color/rgb 120 80 40]
                      :stipple/background [:color/rgb 250 245 235]}
         :style/stroke {:color [:color/rgb 120 80 40] :width 1.5}}
        {:node/type :shape/circle
         :circle/center [350.0 350.0]
         :circle/radius 15.0
         :style/fill {:fill/type :stipple
                      :stipple/density 0.8
                      :stipple/radius 0.8
                      :stipple/seed 45
                      :stipple/color [:color/rgb 120 80 40]
                      :stipple/background [:color/rgb 250 245 235]}
         :style/stroke {:color [:color/rgb 120 80 40] :width 1.5}}]}
      {:output "images/art-decorative-frame.png"})))

;; --- 7. Noise Garden — animated noise + scatter + palette ---

(defn noise-garden []
  (let [pal (:forest palette/palettes)
        frames
        (anim/frames 60
          (fn [t]
            (let [flowers
                  (for [i (range 40)]
                    (let [;; Use golden ratio for good spread
                          bx (+ 40 (mod (* i 137.508 1.1) 520))
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
                  ;; Stems
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
               :image/nodes (vec (concat stems flowers))})))]
    (eido/render frames {:output "images/art-noise-garden.gif" :fps 30})))

;; --- run all ---

(defn render-all! []
  (println "Rendering ink landscape...")
  (ink-landscape)
  (println "Rendering starfield...")
  (starfield)
  (println "Rendering stipple spheres...")
  (stipple-spheres)
  (println "Rendering polka pop...")
  (polka-pop)
  (println "Rendering calligraphy flow...")
  (calligraphy-flow)
  (println "Rendering decorative frame...")
  (decorative-frame)
  (println "Rendering noise garden...")
  (noise-garden)
  (println "Done!"))

(comment
  (render-all!)
  (ink-landscape)
  (starfield)
  (stipple-spheres)
  (polka-pop)
  (calligraphy-flow)
  (decorative-frame)
  (noise-garden)
  )
