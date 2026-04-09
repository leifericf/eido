(ns gallery.particles
  "Particle gallery — data-driven particle effects composed with scene elements."
  {:category "Particles"}
  (:require
    [eido.animate :as anim]
    [eido.gen.particle :as particle]
    [eido.scene3d :as s3d]))

;; --- 1. Campfire ---

(defn ^{:example {:output "particle-campfire.gif"
                  :title  "Campfire"
                  :desc   "Fire and ember particles over log silhouettes with a pulsing glow."
                  :tags   ["particles" "animation" "glow"]}}
  campfire []
  (let [;; Fire rising from the base
        fire-frames  (vec (particle/simulate
                            (particle/with-position particle/fire [200 320])
                            60 {:fps 30}))
        ;; Slow-rising embers with a separate seed
        ember-config (-> particle/sparks
                         (particle/with-position [200 325])
                         (particle/with-seed 77)
                         (assoc :particle/emitter
                                {:emitter/type :area
                                 :emitter/position [200 325]
                                 :emitter/size [60 8]
                                 :emitter/rate 8
                                 :emitter/direction [0 -1]
                                 :emitter/spread 0.6
                                 :emitter/speed [20 60]})
                         (assoc :particle/lifetime [1.0 3.0])
                         (assoc :particle/size [1 2 1])
                         (assoc :particle/opacity [0.0 1.0 0.8 0.0])
                         (assoc :particle/color [[:color/rgb 255 200 50]
                                                 [:color/rgb 255 120 20]
                                                 [:color/rgb 200 60 0]])
                         (assoc :particle/forces
                                [{:force/type :gravity
                                  :force/acceleration [0 -20]}
                                 {:force/type :wind
                                  :force/direction [1 0]
                                  :force/strength 5}]))
        ember-frames (vec (particle/simulate ember-config 60 {:fps 30}))
        frame-fn
        (fn [t]
          (let [i    (int (* t 59))
                glow (+ 0.15 (* 0.05 (Math/sin (* t 8 Math/PI))))]
            {:image/size [400 400]
             :image/background [:color/rgb 8 5 15]
             :image/nodes
             (into
               [{:node/type :shape/circle
                 :circle/center [200 330]
                 :circle/radius (+ 80 (* 20 (Math/sin (* t 6 Math/PI))))
                 :style/fill [:color/rgb 255 80 0]
                 :node/opacity glow}
                {:node/type :shape/rect
                 :rect/xy [155 335] :rect/size [90 12]
                 :style/fill [:color/rgb 30 15 5]
                 :node/transform [[:transform/rotate -0.15]]}
                {:node/type :shape/rect
                 :rect/xy [165 340] :rect/size [80 10]
                 :style/fill [:color/rgb 25 12 3]
                 :node/transform [[:transform/rotate 0.1]]}]
               (concat (nth fire-frames i)
                       (nth ember-frames i)))}))]
    {:frames (anim/frames 60 frame-fn) :fps 30}))

;; --- 2. Fireworks ---

(defn ^{:example {:output "particle-fireworks.gif"
                  :title  "Fireworks"
                  :desc   "Three staggered bursts in red, blue, and gold."
                  :tags   ["particles" "animation" "color"]}}
  fireworks []
  (let [make-burst
        (fn [pos seed colors]
          (vec (particle/simulate
                 (-> particle/sparks
                     (particle/with-position pos)
                     (particle/with-seed seed)
                     (assoc :particle/emitter
                            {:emitter/type :point
                             :emitter/position pos
                             :emitter/burst 60
                             :emitter/direction [0 -1]
                             :emitter/spread Math/PI
                             :emitter/speed [80 250]})
                     (assoc :particle/lifetime [0.5 1.5])
                     (assoc :particle/size [3 4 2 1])
                     (assoc :particle/opacity [1.0 0.9 0.5 0.0])
                     (assoc :particle/color colors))
                 90 {:fps 30})))
        burst1 (make-burst [120 150] 11
                 [[:color/rgb 255 100 100] [:color/rgb 255 50 50]
                  [:color/rgb 200 0 0]])
        burst2 (make-burst [280 120] 22
                 [[:color/rgb 100 200 255] [:color/rgb 50 150 255]
                  [:color/rgb 0 80 200]])
        burst3 (make-burst [200 180] 33
                 [[:color/rgb 255 220 80] [:color/rgb 255 180 0]
                  [:color/rgb 200 100 0]])
        frame-fn
        (fn [t]
          (let [i (int (* t 89))]
            {:image/size [400 400]
             :image/background [:color/rgb 5 5 15]
             :image/nodes
             (into []
               (concat (nth burst1 i)
                       (if (>= i 10) (nth burst2 (- i 10)) [])
                       (if (>= i 20) (nth burst3 (- i 20)) [])))}))]
    {:frames (anim/frames 90 frame-fn) :fps 30}))

;; --- 3. Snowfall ---

(defn ^{:example {:output "particle-snowfall.gif"
                  :title  "Snowfall"
                  :desc   "Gentle snow drifting over moonlit mountain silhouettes."
                  :tags   ["particles" "animation"]}}
  snowfall []
  (let [snow-config (-> particle/snow
                        (assoc-in [:particle/emitter :emitter/position] [-20 -10])
                        (assoc-in [:particle/emitter :emitter/position-to] [420 -10])
                        (assoc :particle/max-count 200))
        snow-frames (vec (particle/simulate snow-config 90 {:fps 30}))
        mountain1   [[-10 400] [50 280] [120 310] [160 240] [220 290]
                     [260 220] [300 260] [350 200] [410 300] [410 400]]
        mountain2   [[-10 400] [30 320] [100 340] [180 280] [250 310]
                     [320 260] [380 300] [410 340] [410 400]]
        frame-fn
        (fn [t]
          (let [i (int (* t 89))]
            {:image/size [400 400]
             :image/background [:color/rgb 15 20 40]
             :image/nodes
             (into
               [{:node/type :shape/path
                 :path/commands (into [[:move-to (first mountain2)]]
                                  (conj (mapv (fn [p] [:line-to p])
                                              (rest mountain2))
                                        [:close]))
                 :style/fill [:color/rgb 25 35 55]}
                {:node/type :shape/path
                 :path/commands (into [[:move-to (first mountain1)]]
                                  (conj (mapv (fn [p] [:line-to p])
                                              (rest mountain1))
                                        [:close]))
                 :style/fill [:color/rgb 35 45 70]}
                {:node/type :shape/circle
                 :circle/center [320 80] :circle/radius 25
                 :style/fill [:color/rgb 220 225 240]
                 :node/opacity 0.8}
                {:node/type :shape/circle
                 :circle/center [320 80] :circle/radius 50
                 :style/fill [:color/rgb 180 190 220]
                 :node/opacity 0.1}]
               (nth snow-frames i))}))]
    {:frames (anim/frames 90 frame-fn) :fps 30}))

;; --- 4. 3D Fountain with Orbiting Camera ---

(defn ^{:example {:output "particle-fountain-3d.gif"
                  :title  "3D Fountain with Orbiting Camera"
                  :desc   "Particles in 3D space projected through an orbiting perspective camera, depth-sorted with pillars."
                  :tags   ["particles" "3d" "animation"]}}
  fountain-3d []
  (let [config {:particle/emitter {:emitter/type :circle
                                    :emitter/position [0.0 0.0 0.0]
                                    :emitter/radius 0.3
                                    :emitter/rate 50
                                    :emitter/direction [0 1 0]
                                    :emitter/spread 0.25
                                    :emitter/speed [3 6]}
                :particle/lifetime [1.0 2.0]
                :particle/forces [{:force/type :gravity
                                   :force/acceleration [0 -5 0]}]
                :particle/size [2 4 3 1]
                :particle/opacity [0.3 0.9 0.6 0.0]
                :particle/color [[:color/rgb 150 220 255]
                                 [:color/rgb 80 160 255]
                                 [:color/rgb 30 80 200]]
                :particle/seed 42
                :particle/max-count 300}
        ;; Simulate once — raw states with 3D positions
        sim-states (vec (particle/states config 90 {:fps 30}))
        pillar-positions [[3 0 0] [-3 0 0] [0 0 3] [0 0 -3]
                          [2.1 0 2.1] [-2.1 0 2.1]
                          [2.1 0 -2.1] [-2.1 0 -2.1]]
        frame-fn
        (fn [t]
          (let [i    (int (* t 89))
                ;; Camera orbits a full circle
                proj (s3d/perspective {:scale 45 :origin [200 270]
                                       :yaw (* t 2.0 Math/PI)
                                       :pitch -0.4 :distance 10})
                light {:light/direction [0.5 0.8 0.4]
                       :light/ambient 0.25 :light/intensity 0.75}
                ;; Re-render particles with this frame's projection
                particles (particle/render-frame
                            (nth sim-states i) config
                            {:projection proj})
                ;; Static 3D pillars
                pillars (mapv
                          (fn [pos]
                            (s3d/cylinder proj pos 0.25 2.5
                              {:style {:style/fill [:color/rgb 140 120 100]
                                       :style/stroke {:color [:color/rgb 80 70 60]
                                                      :width 0.3}}
                               :light light :segments 8}))
                          pillar-positions)]
            {:image/size [400 400]
             :image/background [:color/rgb 8 8 20]
             ;; depth-sort interleaves particles and mesh faces
             :image/nodes (s3d/depth-sort pillars particles)}))]
    {:frames (anim/frames 90 frame-fn) :fps 30}))

;; --- 5. Volcanic Eruption ---

(defn ^{:example {:output "particle-volcano-3d.gif"
                  :title  "Volcanic Eruption"
                  :desc   "3D lava and smoke particles erupting from a scene3d cone mesh."
                  :tags   ["particles" "3d" "animation" "color"]}}
  volcanic-eruption []
  (let [proj  (s3d/perspective {:scale 50 :origin [200 340]
                                 :yaw 0.3 :pitch -0.2 :distance 10})
        light {:light/direction [0.5 0.8 0.3]
               :light/ambient 0.3 :light/intensity 0.7}
        lava (vec (particle/simulate
                    {:particle/emitter {:emitter/type :sphere
                                        :emitter/position [0 3.5 0]
                                        :emitter/radius 0.4
                                        :emitter/rate 35
                                        :emitter/direction [0 1 0]
                                        :emitter/spread 0.5
                                        :emitter/speed [3 8]}
                     :particle/lifetime [0.6 1.8]
                     :particle/forces [{:force/type :gravity
                                        :force/acceleration [0 -4 0]}]
                     :particle/size [3 5 4 2]
                     :particle/opacity [0.5 1.0 0.8 0.0]
                     :particle/color [[:color/rgb 255 255 150]
                                      [:color/rgb 255 200 20]
                                      [:color/rgb 255 60 0]
                                      [:color/rgb 150 20 0]]
                     :particle/projection proj
                     :particle/seed 42
                     :particle/max-count 250}
                    90 {:fps 30}))
        volcano (s3d/cone proj [0 0 0] 3.0 3.5
                  {:style {:style/fill [:color/rgb 80 50 30]
                           :style/stroke {:color [:color/rgb 60 35 20]
                                          :width 0.5}}
                   :light light :segments 16})
        frame-fn
        (fn [t]
          {:image/size [400 400]
           :image/background [:color/rgb 12 8 18]
           :image/nodes (into [volcano]
                              (nth lava (int (* t 89))))})]
    {:frames (anim/frames 90 frame-fn) :fps 30}))
