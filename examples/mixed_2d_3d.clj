(ns examples.mixed-2d-3d
  (:require
    [eido.animate :as anim]
    [eido.core :as eido]
    [eido.scene :as scene]
    [eido.scene3d :as s3d]))

;; --- 1. Neon Orbit ---
;; Rotating torus wreathed in orbiting color halos and rippling rings

(defn neon-orbit [t]
  (let [size   [500 500]
        cx     250
        cy     250
        angle  (* t 2.0 Math/PI)
        proj   (s3d/perspective
                 {:scale    90
                  :origin   [cx cy]
                  :yaw      angle
                  :pitch    -0.35
                  :distance 5.5})
        light  {:light/direction [0.6 1.0 0.4]
                :light/ambient   0.2
                :light/intensity 0.8}
        ;; 3D torus — hot magenta
        torus-3d (s3d/torus proj [0 0 0] 1.8 0.5
                   {:style {:style/fill [:color/rgb 255 50 160]
                            :style/stroke {:color [:color/rgb 255 120 200]
                                           :width 0.4}}
                    :light light
                    :ring-segments 36
                    :tube-segments 18})
        ;; Orbiting 2D halos — rainbow palette
        n-halos 10
        halos   (scene/radial n-halos cx cy 170
                  (fn [x y a]
                    (let [i     (int (/ (* a n-halos) (* 2 Math/PI)))
                          phase (+ angle (* i 0.6))
                          pulse (+ 0.5 (* 0.5 (Math/sin phase)))
                          hue   (mod (+ (* i 36) (* t 360)) 360)
                          r     (+ 6 (* 16 pulse))]
                      {:node/type     :shape/circle
                       :circle/center [x y]
                       :circle/radius r
                       :node/opacity  (* 0.75 pulse)
                       :style/fill    [:color/hsl hue 0.95 0.6]})))
        ;; Pulsing concentric rings — expanding outward
        rings   (mapv (fn [i]
                        (let [r     (+ 30 (* i 30))
                              phase (- (* t 2 Math/PI) (* i 0.4))
                              alpha (* 0.2 (+ 0.5 (* 0.5 (Math/sin phase))))
                              hue   (mod (+ (* i 50) (* t 120)) 360)]
                          {:node/type     :shape/circle
                           :circle/center [cx cy]
                           :circle/radius r
                           :node/opacity  alpha
                           :style/stroke  {:color [:color/hsl hue 0.7 0.6]
                                           :width 1.5}}))
                      (range 8))]
    {:image/size       size
     :image/background [:color/rgb 10 6 22]
     :image/nodes      (into [] (concat rings [torus-3d] halos))}))

;; --- 2. Crystal Garden ---
;; Faceted crystal spires sprouting from earth, sparkles drifting upward

(defn crystal-garden [t]
  (let [size   [600 400]
        proj   (s3d/perspective
                 {:scale    70
                  :origin   [300 300]
                  :yaw      (* 0.2 (Math/sin (* t 2 Math/PI)))
                  :pitch    -0.55
                  :distance 7.0})
        light  {:light/direction [0.0 0.6 1.0]
                :light/ambient   0.35
                :light/intensity 0.65}
        ;; Crystal definitions — tighter cluster, taller
        ;; Colors in RGB for 3D shading compatibility
        crystals [{:x -2.5 :z  0.3 :h 2.5 :r 0.5  :fill [230 50 180] :stroke [240 100 210] :phase 0.0}
                  {:x -1.0 :z -0.5 :h 3.8 :r 0.6  :fill [40 160 220] :stroke [80 190 240]  :phase 0.05}
                  {:x  0.0 :z  0.2 :h 4.5 :r 0.55 :fill [140 60 220] :stroke [170 110 240] :phase 0.1}
                  {:x  1.2 :z -0.3 :h 3.2 :r 0.5  :fill [40 200 140] :stroke [80 220 170]  :phase 0.0}
                  {:x  2.3 :z  0.4 :h 4.0 :r 0.55 :fill [220 180 40] :stroke [240 200 80]  :phase 0.05}]
        ;; Gentle pulsing crystals
        crystal-nodes
        (mapv (fn [{:keys [x z h r fill stroke phase]}]
                (let [pulse (+ 0.9 (* 0.1 (Math/sin (+ (* t 4 Math/PI) phase))))
                      sway  (* 0.15 (Math/sin (+ (* t 3 Math/PI) (* x 0.5))))
                      [fr fg fb] fill
                      [sr sg sb] stroke
                      mesh  (-> (s3d/cone-mesh (* r pulse) h 6)
                                (s3d/rotate-mesh :z sway)
                                (s3d/translate-mesh [x 0 z]))]
                  (s3d/render-mesh proj mesh
                    {:style {:style/fill   [:color/rgb fr fg fb]
                             :style/stroke {:color [:color/rgb sr sg sb]
                                            :width 0.5}}
                     :light light})))
              crystals)
        ;; Ground — subtle gradient feel with layered lines
        ground  (mapv (fn [i]
                        (let [y  (+ 320 (* i 3))
                              al (- 0.3 (* i 0.04))]
                          {:node/type     :shape/line
                           :line/from     [0 y]
                           :line/to       [600 y]
                           :node/opacity  (max 0.05 al)
                           :style/stroke  {:color [:color/rgb 40 60 35]
                                           :width 1.5}}))
                      (range 8))
        ;; Grass blades swaying
        grass   (mapv (fn [i]
                        (let [gx   (+ 15 (* i 8))
                              gh   (* 14 (+ 0.4 (* 0.6 (Math/sin (+ (* i 0.8) (* t 5))))))
                              sway (* 5 (Math/sin (+ (* i 0.4) (* t 7))))
                              g    (+ 90 (int (* 60 (Math/sin (+ (* i 0.3) 1.0)))))]
                          {:node/type     :shape/line
                           :line/from     [gx 320]
                           :line/to       [(+ gx sway) (- 320 gh)]
                           :style/stroke  {:color [:color/rgb 35 g 30]
                                           :width 1.5}}))
                      (range 72))
        ;; Sparkles rising from crystal tips
        sparkles (mapv (fn [i]
                         (let [sx    (+ 100 (* i 90))
                               phase (mod (+ (* i 0.618) t) 1.0)
                               sy    (- 310 (* 280 phase))
                               drift (* 20 (Math/sin (+ (* i 2.7) (* t 4))))
                               alpha (* 0.9 (- 1.0 phase))
                               r     (+ 1.0 (* 3.5 (- 1.0 phase)))
                               hue   (mod (+ (* i 55) (* t 90)) 360)]
                           {:node/type     :shape/circle
                            :circle/center [(+ sx drift) sy]
                            :circle/radius r
                            :node/opacity  alpha
                            :style/fill    [:color/hsl hue 0.9 0.75]}))
                       (range 6))]
    {:image/size       size
     :image/background [:color/rgb 8 10 18]
     :image/nodes      (into [] (concat ground grass crystal-nodes sparkles))}))

;; --- 3. Solar System ---
;; Shaded planet with orbital rings, trailing moons, and twinkling stars

(defn solar-system [t]
  (let [size   [500 500]
        cx     250
        cy     250
        angle  (* t 2.0 Math/PI)
        proj   (s3d/perspective
                 {:scale    100
                  :origin   [cx cy]
                  :yaw      0.3
                  :pitch    -0.55
                  :distance 5.0})
        light  {:light/direction [1.0 0.8 0.5]
                :light/ambient   0.12
                :light/intensity 0.88}
        ;; Rotating planet
        planet  (let [mesh (-> (s3d/sphere-mesh 1.2 24 12)
                               (s3d/rotate-mesh :y angle))]
                  (s3d/render-mesh proj mesh
                    {:style {:style/fill   [:color/rgb 30 90 190]
                             :style/stroke {:color [:color/rgb 40 110 210]
                                            :width 0.3}}
                     :light light}))
        ;; Orbital ellipses — dashed, subtle
        orbit-data [{:rx 105 :hue  20 :speed 1.0   :moon-r 7  :trail-hue  30}
                    {:rx 150 :hue  80 :speed 0.7   :moon-r 5  :trail-hue  90}
                    {:rx 195 :hue 160 :speed 0.45  :moon-r 8  :trail-hue 170}
                    {:rx 235 :hue 280 :speed 0.3   :moon-r 4  :trail-hue 290}]
        orbits  (mapv (fn [{:keys [rx hue]}]
                        (let [ry (* rx 0.35)]
                          {:node/type      :shape/ellipse
                           :ellipse/center [cx cy]
                           :ellipse/rx     rx
                           :ellipse/ry     ry
                           :node/opacity   0.25
                           :style/stroke   {:color [:color/hsl hue 0.5 0.5]
                                            :width 1
                                            :dash  [5 5]}}))
                      orbit-data)
        ;; Moon trails — fading dots
        trails  (into []
                  (for [{:keys [rx speed trail-hue]} orbit-data
                        trail (range 12)]
                    (let [ry   (* rx 0.35)
                          ma   (- (* angle speed) (* trail 0.06))
                          mx   (+ cx (* rx (Math/cos ma)))
                          my   (+ cy (* ry (Math/sin ma)))
                          fade (/ 1.0 (+ 1.0 (* 1.5 trail)))]
                      {:node/type     :shape/circle
                       :circle/center [mx my]
                       :circle/radius (* 3.5 fade)
                       :node/opacity  (* 0.4 fade)
                       :style/fill    [:color/hsl trail-hue 0.7 0.6]})))
        ;; Moons
        moons   (mapv (fn [{:keys [rx speed moon-r hue]}]
                        (let [ry (* rx 0.35)
                              ma (* angle speed)
                              mx (+ cx (* rx (Math/cos ma)))
                              my (+ cy (* ry (Math/sin ma)))]
                          {:node/type     :shape/circle
                           :circle/center [mx my]
                           :circle/radius moon-r
                           :style/fill    [:color/hsl hue 0.8 0.55]
                           :style/stroke  {:color [:color/hsl hue 0.6 0.75]
                                           :width 1}}))
                      orbit-data)
        ;; Twinkling star field
        stars   (mapv (fn [i]
                        (let [sx    (mod (* i 137.508) 500)
                              sy    (mod (* i 91.123) 500)
                              blink (+ 0.2 (* 0.8 (Math/abs
                                                     (Math/sin (+ (* i 1.1) (* t 5))))))
                              big?  (zero? (mod i 9))]
                          {:node/type     :shape/circle
                           :circle/center [sx sy]
                           :circle/radius (if big? 1.8 0.7)
                           :node/opacity  blink
                           :style/fill    [:color/rgb 255 255
                                           (if big? 220 255)]}))
                      (range 50))]
    {:image/size       size
     :image/background [:color/rgb 4 4 12]
     :image/nodes      (into [] (concat stars orbits trails [planet] moons))}))

(comment
  ;; Render all three as GIFs
  (eido/render-to-gif (anim/frames 60 neon-orbit)
    "images/mixed-neon-orbit.gif" 30)

  (eido/render-to-gif (anim/frames 60 crystal-garden)
    "images/mixed-crystal-garden.gif" 30)

  (eido/render-to-gif (anim/frames 60 solar-system)
    "images/mixed-solar-system.gif" 30))
