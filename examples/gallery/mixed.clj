(ns gallery.mixed
  "Mixed 2D/3D gallery — 3D meshes composing freely with 2D elements."
  {:category "Mixed 2D/3D"}
  (:require
    [eido.animate :as anim]
    [eido.scene :as scene]
    [eido.scene3d :as s3d]))

;; --- 1. Neon Orbit ---

(defn ^{:example {:output "mixed-neon-orbit.gif"
                  :title  "Neon Orbit"
                  :desc   "Rotating 3D torus wreathed in orbiting 2D color halos and pulsing concentric rings."
                  :tags   ["3d" "animation" "color" "glow"]}}
  neon-orbit []
  (let [frame-fn
        (fn [t]
          (let [cx     250
                cy     250
                angle  (* t 2.0 Math/PI)
                proj   (s3d/perspective
                         {:scale 90 :origin [cx cy]
                          :yaw angle :pitch -0.35 :distance 5.5})
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
                ;; Pulsing concentric rings
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
            {:image/size       [500 500]
             :image/background [:color/rgb 10 6 22]
             :image/nodes      (into [] (concat rings [torus-3d] halos))}))]
    {:frames (anim/frames 60 frame-fn) :fps 30}))

;; --- 2. Crystal Garden ---

(defn ^{:example {:output "mixed-crystal-garden.gif"
                  :title  "Crystal Garden"
                  :desc   "Faceted 3D crystal spires with swaying 2D grass and rising sparkle particles."
                  :tags   ["3d" "animation" "particles" "color"]}}
  crystal-garden []
  (let [frame-fn
        (fn [t]
          (let [proj   (s3d/perspective
                         {:scale 70 :origin [300 300]
                          :yaw (* 0.2 (Math/sin (* t 2 Math/PI)))
                          :pitch -0.55 :distance 7.0})
                light  {:light/direction [0.0 0.6 1.0]
                        :light/ambient   0.35
                        :light/intensity 0.65}
                ;; Crystal definitions
                crystals [{:x -2.5 :z  0.3 :h 2.5 :r 0.5  :fill [230 50 180]}
                          {:x -1.0 :z -0.5 :h 3.8 :r 0.6  :fill [40 160 220]}
                          {:x  0.0 :z  0.2 :h 4.5 :r 0.55 :fill [140 60 220]}
                          {:x  1.2 :z -0.3 :h 3.2 :r 0.5  :fill [40 200 140]}
                          {:x  2.3 :z  0.4 :h 4.0 :r 0.55 :fill [220 180 40]}]
                ;; 3D crystal cones with gentle sway
                gems (mapv (fn [{:keys [x z h r fill]}]
                             (let [sway (* 0.15 (Math/sin (+ (* t 3 Math/PI) (* x 0.5))))
                                   [cr cg cb] fill
                                   mesh (-> (s3d/cone-mesh r h 6)
                                            (s3d/rotate-mesh :z sway)
                                            (s3d/translate-mesh [x 0 z]))]
                               (s3d/render-mesh proj mesh
                                 {:style {:style/fill [:color/rgb cr cg cb]
                                          :style/stroke {:color [:color/rgb (+ cr 20) (+ cg 40) (+ cb 20)]
                                                         :width 0.5}}
                                  :light light})))
                           crystals)
                ;; 2D grass blades
                grass (mapv (fn [i]
                              (let [gx   (+ 15 (* i 8))
                                    gh   (* 14 (+ 0.4 (* 0.6 (Math/sin (+ (* i 0.8) (* t 5))))))
                                    sway (* 5 (Math/sin (+ (* i 0.4) (* t 7))))
                                    g    (+ 90 (int (* 60 (Math/sin (+ (* i 0.3) 1.0)))))]
                                {:node/type    :shape/line
                                 :line/from    [gx 320]
                                 :line/to      [(+ gx sway) (- 320 gh)]
                                 :style/stroke {:color [:color/rgb 35 g 30] :width 1.5}}))
                            (range 72))
                ;; 2D rising sparkles
                sparkles (mapv (fn [i]
                                 (let [phase (mod (+ (* i 0.618) t) 1.0)
                                       hue   (mod (+ (* i 55) (* t 90)) 360)]
                                   {:node/type     :shape/circle
                                    :circle/center [(+ 100 (* i 90) (* 20 (Math/sin (+ (* i 2.7) (* t 4)))))
                                                    (- 310 (* 280 phase))]
                                    :circle/radius (+ 1.0 (* 3.5 (- 1.0 phase)))
                                    :node/opacity  (* 0.9 (- 1.0 phase))
                                    :style/fill    [:color/hsl hue 0.9 0.75]}))
                               (range 6))]
            {:image/size       [600 400]
             :image/background [:color/rgb 8 10 18]
             :image/nodes      (into [] (concat grass gems sparkles))}))]
    {:frames (anim/frames 60 frame-fn) :fps 30}))

;; --- 3. Solar System ---

(defn ^{:example {:output "mixed-solar-system.gif"
                  :title  "Solar System"
                  :desc   "Shaded 3D planet with 2D orbital ellipses, trailing moons, and a twinkling star field."
                  :tags   ["3d" "animation" "dashing" "color"]}}
  solar-system []
  (let [frame-fn
        (fn [t]
          (let [cx 250 cy 250
                angle (* t 2.0 Math/PI)
                proj  (s3d/perspective
                        {:scale 100 :origin [cx cy]
                         :yaw 0.3 :pitch -0.55 :distance 5.0})
                light {:light/direction [1.0 0.8 0.5]
                       :light/ambient 0.12 :light/intensity 0.88}
                ;; 3D rotating planet
                planet (let [mesh (-> (s3d/sphere-mesh 1.2 24 12)
                                      (s3d/rotate-mesh :y angle))]
                         (s3d/render-mesh proj mesh
                           {:style {:style/fill   [:color/rgb 30 90 190]
                                    :style/stroke {:color [:color/rgb 40 110 210]
                                                   :width 0.3}}
                            :light light}))
                ;; 2D orbital data
                orbits [{:rx 105 :hue 20 :speed 1.0  :moon-r 7}
                        {:rx 150 :hue 80 :speed 0.7  :moon-r 5}
                        {:rx 195 :hue 160 :speed 0.45 :moon-r 8}
                        {:rx 235 :hue 280 :speed 0.3  :moon-r 4}]
                ;; 2D dashed orbit ellipses
                rings (mapv (fn [{:keys [rx hue]}]
                              {:node/type      :shape/ellipse
                               :ellipse/center [cx cy]
                               :ellipse/rx     rx
                               :ellipse/ry     (* rx 0.35)
                               :node/opacity   0.25
                               :style/stroke   {:color [:color/hsl hue 0.5 0.5]
                                                :width 1 :dash [5 5]}})
                            orbits)
                ;; 2D moon trails
                trails (into []
                         (for [{:keys [rx speed hue]} orbits
                               trail (range 12)]
                           (let [ry (* rx 0.35)
                                 ma (- (* angle speed) (* trail 0.06))
                                 fade (/ 1.0 (+ 1.0 (* 1.5 trail)))]
                             {:node/type     :shape/circle
                              :circle/center [(+ cx (* rx (Math/cos ma)))
                                              (+ cy (* ry (Math/sin ma)))]
                              :circle/radius (* 3.5 fade)
                              :node/opacity  (* 0.4 fade)
                              :style/fill    [:color/hsl hue 0.7 0.6]})))
                ;; 2D moons
                moons (mapv (fn [{:keys [rx speed moon-r hue]}]
                              (let [ry (* rx 0.35)
                                    ma (* angle speed)]
                                {:node/type     :shape/circle
                                 :circle/center [(+ cx (* rx (Math/cos ma)))
                                                 (+ cy (* ry (Math/sin ma)))]
                                 :circle/radius moon-r
                                 :style/fill    [:color/hsl hue 0.8 0.55]
                                 :style/stroke  {:color [:color/hsl hue 0.6 0.75]
                                                 :width 1}}))
                            orbits)
                ;; 2D twinkling stars
                stars (mapv (fn [i]
                              (let [blink (+ 0.2 (* 0.8 (Math/abs
                                                           (Math/sin (+ (* i 1.1) (* t 5))))))]
                                {:node/type     :shape/circle
                                 :circle/center [(mod (* i 137.508) 500)
                                                 (mod (* i 91.123) 500)]
                                 :circle/radius (if (zero? (mod i 9)) 1.8 0.7)
                                 :node/opacity  blink
                                 :style/fill    [:color/rgb 255 255 255]}))
                            (range 50))]
            {:image/size       [500 500]
             :image/background [:color/rgb 4 4 12]
             :image/nodes      (into [] (concat stars rings trails [planet] moons))}))]
    {:frames (anim/frames 60 frame-fn) :fps 30}))
