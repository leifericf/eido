(ns eido.particle
  (:require
    [eido.animate :as anim]
    [eido.color :as color]
    [eido.math3d :as m3]))

;; --- private vector math (2D and 3D) ---

(defn- v+ [a b]
  (mapv + a b))

(defn- v- [a b]
  (mapv - a b))

(defn- v* [v s]
  (mapv #(* (double %) s) v))

(defn- v-mag [v]
  (Math/sqrt (double (reduce + (map #(* (double %) %) v)))))

(defn- v-normalize [v]
  (let [m (v-mag v)]
    (if (< m 1e-10)
      (vec (repeat (count v) 0.0))
      (mapv #(/ % m) v))))

(defn- v-zero
  "Returns a zero vector of the given dimension."
  [dim]
  (vec (repeat dim 0.0)))

(defn- dims
  "Returns the dimensionality of a particle system config (2 or 3)."
  [config]
  (count (get-in config [:particle/emitter :emitter/position] [0 0])))

;; --- seeded PRNG (pure, deterministic) ---

(defn- make-rng
  "Creates a java.util.Random from a seed."
  ^java.util.Random [seed]
  (java.util.Random. (long seed)))

(defn- rng-double
  "Returns a double in [0, 1) from the RNG."
  [^java.util.Random rng]
  (.nextDouble rng))

(defn- rng-range
  "Returns a double in [min, max] from the RNG."
  [^java.util.Random rng mn mx]
  (+ (double mn) (* (rng-double rng) (- (double mx) mn))))

(defn- rng-direction
  "Returns a unit vector within spread radians of base-dir.
  Works for both 2D and 3D base directions."
  [^java.util.Random rng base-dir spread]
  (if (= 3 (count base-dir))
    ;; 3D: perturb direction within a cone
    (let [[bx by bz] base-dir
          base-theta (Math/atan2 bx bz)
          base-phi   (Math/asin (max -1.0 (min 1.0 (double by))))
          dtheta (* (- (* 2.0 (rng-double rng)) 1.0) (double spread))
          dphi   (* (- (* 2.0 (rng-double rng)) 1.0) (double spread))
          theta  (+ base-theta dtheta)
          phi    (max (- (/ Math/PI -2.0)) (min (/ Math/PI 2.0) (+ base-phi dphi)))
          cp     (Math/cos phi)]
      [(* cp (Math/sin theta))
       (Math/sin phi)
       (* cp (Math/cos theta))])
    ;; 2D
    (let [[bx by] base-dir
          base-angle (Math/atan2 by bx)
          offset (* (- (* 2.0 (rng-double rng)) 1.0) (double spread))
          angle (+ base-angle offset)]
      [(Math/cos angle) (Math/sin angle)])))

;; --- emitter ---

(defn- spawn-position
  "Returns a spawn position for the given emitter type.
  Works for both 2D ([x y]) and 3D ([x y z]) positions."
  [^java.util.Random rng emitter]
  (case (:emitter/type emitter)
    :point (:emitter/position emitter)
    :line  (let [p1 (:emitter/position emitter)
                 p2 (:emitter/position-to emitter)
                 t  (rng-double rng)]
             (v+ p1 (v* (v- p2 p1) t)))
    :circle (let [pos (:emitter/position emitter)
                  r   (:emitter/radius emitter)
                  angle (* (rng-double rng) 2.0 Math/PI)
                  dist (* (Math/sqrt (rng-double rng)) r)]
              (if (= 3 (count pos))
                ;; 3D: circle in the XZ plane by default
                (v+ pos [(* dist (Math/cos angle)) 0.0 (* dist (Math/sin angle))])
                ;; 2D
                (v+ pos [(* dist (Math/cos angle)) (* dist (Math/sin angle))])))
    :sphere (let [pos (:emitter/position emitter)
                  r   (:emitter/radius emitter)
                  ;; Uniform point in sphere via rejection-free method
                  theta (* (rng-double rng) 2.0 Math/PI)
                  phi   (Math/acos (- 1.0 (* 2.0 (rng-double rng))))
                  dist  (* (Math/cbrt (rng-double rng)) r)]
              (v+ pos [(* dist (Math/sin phi) (Math/cos theta))
                       (* dist (Math/cos phi))
                       (* dist (Math/sin phi) (Math/sin theta))]))
    :area  (let [pos  (:emitter/position emitter)
                 size (:emitter/size emitter)]
             (if (= 3 (count pos))
               ;; 3D: area in XZ plane
               (let [[w _ d] size]
                 (v+ pos [(rng-range rng (/ w -2.0) (/ w 2.0))
                          0.0
                          (rng-range rng (/ d -2.0) (/ d 2.0))]))
               ;; 2D
               (let [[w h] size]
                 (v+ pos [(rng-range rng (/ w -2.0) (/ w 2.0))
                          (rng-range rng (/ h -2.0) (/ h 2.0))]))))))

(defn- spawn-particle
  "Creates a new particle from emitter config."
  [^java.util.Random rng emitter config id]
  (let [dir       (or (:emitter/direction emitter) [0 -1])
        spread    (or (:emitter/spread emitter) 0.0)
        [smin smax] (let [s (:emitter/speed emitter)]
                      (if (vector? s) s [s s]))
        speed     (rng-range rng smin smax)
        direction (if (pos? spread)
                    (rng-direction rng dir spread)
                    (v-normalize dir))
        [lmin lmax] (let [l (:particle/lifetime config)]
                      (if (vector? l) l [l l]))
        lifetime  (rng-range rng lmin lmax)]
    {:id       id
     :pos      (spawn-position rng emitter)
     :vel      (v* direction speed)
     :age      0.0
     :lifetime lifetime
     :mass     1.0
     :seed     (.nextLong rng)}))

(defn- emit
  "Emits new particles for one time step. Returns vector of new particles."
  [^java.util.Random rng emitter config dt sim-time next-id]
  (if-let [burst (:emitter/burst emitter)]
    ;; Burst: emit all at once on first frame
    (if (< sim-time dt)
      (mapv #(spawn-particle rng emitter config (+ next-id %))
            (range burst))
      [])
    ;; Continuous: emit based on rate
    (let [rate     (or (:emitter/rate emitter) 10)
          count    (int (* rate dt))
          ;; Use fractional accumulation for sub-frame emission
          frac     (- (* rate dt) count)
          count    (if (< (rng-double rng) frac) (inc count) count)]
      (mapv #(spawn-particle rng emitter config (+ next-id %))
            (range count)))))

;; --- forces ---

(defn- apply-force
  "Computes acceleration from a single force on a particle.
  Returns a vector matching the particle's dimensionality."
  [force particle]
  (case (:force/type force)
    :gravity (:force/acceleration force)
    :drag    (let [coeff (:force/coefficient force)
                   vel   (:vel particle)]
               (v* vel (- coeff)))
    :wind    (v* (v-normalize (:force/direction force))
                 (:force/strength force))
    (v-zero (count (:pos particle)))))

(defn- net-acceleration
  "Sums all forces into a net acceleration vector."
  [forces particle]
  (reduce (fn [acc f] (v+ acc (apply-force f particle)))
          (v-zero (count (:pos particle)))
          forces))

;; --- integration and lifecycle ---

(defn- alive? [particle]
  (< (:age particle) (:lifetime particle)))

(defn- integrate
  "Euler integration step for a single particle."
  [particle forces dt]
  (let [accel   (net-acceleration forces particle)
        new-vel (v+ (:vel particle) (v* accel dt))
        new-pos (v+ (:pos particle) (v* new-vel dt))]
    (assoc particle
      :pos new-pos
      :vel new-vel
      :age (+ (:age particle) dt))))

(defn- step
  "Advances the simulation by one time step. Pure function."
  [config dt state]
  (let [emitter  (:particle/emitter config)
        forces   (or (:particle/forces config) [])
        max-n    (or (:particle/max-count config) 500)
        ;; Emit new particles
        new      (emit (:rng state) emitter config dt (:time state) (:next-id state))
        ;; Integrate existing particles
        updated  (->> (:particles state)
                      (mapv #(integrate % forces dt))
                      (filterv alive?))
        ;; Combine and cap
        combined (into updated (take (- max-n (count updated)) new))]
    {:particles combined
     :rng       (:rng state)
     :next-id   (+ (:next-id state) (count new))
     :time      (+ (:time state) dt)}))

;; --- over-lifetime sampling ---

(defn- life-fraction
  "Normalized age [0, 1] of a particle."
  [particle]
  (min 1.0 (/ (:age particle) (:lifetime particle))))

(def ^:private sample-curve-memo
  (memoize
    (fn [curve t]
      (cond
        (nil? curve)       nil
        (empty? curve)     nil
        (= 1 (count curve)) (first curve)
        :else
        (let [n    (dec (count curve))
              idx  (* (double t) n)
              i    (min (int (Math/floor idx)) (dec n))
              frac (- idx i)
              a    (nth curve i)
              b    (nth curve (inc i))]
          (if (vector? a)
            (color/lerp a b frac)
            (anim/lerp a b frac)))))))

(defn- sample-curve
  "Interpolates a value from an over-lifetime curve at position t [0, 1].
  Curves are vectors of stops sampled linearly. Numbers use animate/lerp,
  color vectors use color/lerp."
  [curve t]
  (sample-curve-memo curve t))

;; --- rendering ---

(defn- project-pos
  "Projects a particle position to 2D screen coordinates.
  For 2D particles, returns the position as-is.
  For 3D particles, projects through the config's :particle/projection."
  [pos config]
  (if (= 3 (count pos))
    (m3/project (:particle/projection config) pos)
    pos))

(defn- render-particle
  "Converts an internal particle to an Eido node map."
  [particle config]
  (let [lf      (life-fraction particle)
        [px py] (project-pos (:pos particle) config)
        radius  (or (sample-curve (:particle/size config) lf) 4.0)
        opacity (or (sample-curve (:particle/opacity config) lf) 1.0)
        fill    (if-let [palette (:particle/colors config)]
                  (nth palette (mod (:id particle) (count palette)))
                  (or (sample-curve (:particle/color config) lf)
                      [:color/rgb 255 255 255]))]
    (case (or (:particle/shape config) :circle)
      :circle {:node/type     :shape/circle
               :circle/center [px py]
               :circle/radius radius
               :style/fill    fill
               :node/opacity  opacity}
      :rect   {:node/type   :shape/rect
               :rect/xy     [(- px radius) (- py radius)]
               :rect/size   [(* 2.0 radius) (* 2.0 radius)]
               :style/fill  fill
               :node/opacity opacity})))

(defn- render-particles
  "Converts all alive particles to a vector of Eido node maps."
  [particles config]
  (mapv #(render-particle % config) particles))

;; --- public API ---

(defn states
  "Runs a particle simulation and returns a lazy seq of raw simulation states.
  Each state contains :particles (internal particle maps with :pos, :vel, :age, etc.).

  Use this when you need per-frame control over rendering, e.g. orbiting cameras
  for 3D particles. Pair with render-frame to convert states to nodes.

  config: particle system configuration map
  n:      number of frames to produce
  opts:   {:fps 30} — frames per second (determines time step)

  Returns a lazy seq of n state maps."
  [config n opts]
  (let [fps  (or (:fps opts) 30)
        dt   (/ 1.0 (double fps))
        seed (or (:particle/seed config) 42)
        init {:particles [] :rng (make-rng seed) :next-id 0 :time 0.0}]
    (->> (iterate (partial step config dt) init)
         (rest)
         (take n))))

(defn render-frame
  "Renders a simulation state to a vector of Eido node maps.
  Optionally override the projection for this frame (useful for orbiting cameras).

  state:  a simulation state from `states`
  config: the particle system configuration map
  opts:   optional {:projection proj} to override :particle/projection for this frame"
  ([state config]
   (render-particles (:particles state) config))
  ([state config opts]
   (if-let [proj (:projection opts)]
     (render-particles (:particles state) (assoc config :particle/projection proj))
     (render-particles (:particles state) config))))

(defn simulate
  "Runs a particle simulation and returns a lazy seq of node-vectors.
  Each element is a vector of Eido node maps ready for :image/nodes.

  config: particle system configuration map
  n:      number of frames to produce
  opts:   {:fps 30} — frames per second (determines time step)

  Returns a lazy seq of n vectors. Use vec to realize for indexed access."
  [config n opts]
  (map #(render-frame % config) (states config n opts)))

(defn with-position
  "Returns config with the emitter repositioned."
  [config pos]
  (assoc-in config [:particle/emitter :emitter/position] pos))

(defn with-seed
  "Returns config with a different seed."
  [config seed]
  (assoc config :particle/seed seed))

;; --- presets ---

(def fire
  "Fire effect: upward flames with orange-to-red color shift."
  {:particle/emitter  {:emitter/type      :area
                       :emitter/position  [0 0]
                       :emitter/size      [50 6]
                       :emitter/rate      50
                       :emitter/direction [0 -1]
                       :emitter/spread    0.35
                       :emitter/speed     [40 100]}
   :particle/lifetime [0.4 1.2]
   :particle/forces   [{:force/type :gravity :force/acceleration [0 -50]}
                       {:force/type :drag :force/coefficient 0.2}]
   :particle/size     [8 12 8 3]
   :particle/opacity  [0.2 1.0 0.7 0.0]
   :particle/color    [[:color/rgb 255 255 180]
                       [:color/rgb 255 200 20]
                       [:color/rgb 255 80 0]
                       [:color/rgb 180 20 0]]
   :particle/shape    :circle
   :particle/seed     42})

(def confetti
  "Confetti burst: colorful rectangles falling with gravity.
  Note: uses per-particle random colors from :particle/colors palette."
  {:particle/emitter  {:emitter/type      :point
                       :emitter/position  [0 0]
                       :emitter/burst     100
                       :emitter/direction [0 -1]
                       :emitter/spread    1.3
                       :emitter/speed     [120 350]}
   :particle/lifetime [2.0 4.0]
   :particle/forces   [{:force/type :gravity :force/acceleration [0 150]}
                       {:force/type :drag :force/coefficient 0.6}]
   :particle/size     [5 5 4]
   :particle/opacity  [1.0 1.0 0.8 0.0]
   :particle/colors   [[:color/rgb 255 50 80]
                       [:color/rgb 50 200 80]
                       [:color/rgb 50 120 255]
                       [:color/rgb 255 220 40]
                       [:color/rgb 255 100 200]
                       [:color/rgb 100 220 255]]
   :particle/shape    :rect
   :particle/seed     42})

(def snow
  "Snowfall: gentle drift downward from a line emitter."
  {:particle/emitter  {:emitter/type       :line
                       :emitter/position   [0 0]
                       :emitter/position-to [400 0]
                       :emitter/rate       15
                       :emitter/direction  [0 1]
                       :emitter/spread     0.15
                       :emitter/speed      [20 60]}
   :particle/lifetime [3.0 6.0]
   :particle/forces   [{:force/type :gravity :force/acceleration [0 10]}
                       {:force/type :wind :force/direction [1 0] :force/strength 8}]
   :particle/size     [2 3 4 3 2]
   :particle/opacity  [0.0 0.7 0.8 0.7 0.0]
   :particle/color    [[:color/rgba 255 255 255 0.9]]
   :particle/shape    :circle
   :particle/seed     42})

(def sparks
  "Sparks burst: bright, fast, short-lived points."
  {:particle/emitter  {:emitter/type      :point
                       :emitter/position  [0 0]
                       :emitter/burst     40
                       :emitter/direction [0 -1]
                       :emitter/spread    Math/PI
                       :emitter/speed     [150 400]}
   :particle/lifetime [0.2 0.8]
   :particle/forces   [{:force/type :gravity :force/acceleration [0 200]}
                       {:force/type :drag :force/coefficient 0.8}]
   :particle/size     [3 2 1]
   :particle/opacity  [1.0 0.8 0.0]
   :particle/color    [[:color/rgb 255 255 200]
                       [:color/rgb 255 200 50]
                       [:color/rgb 255 80 0]]
   :particle/shape    :circle
   :particle/seed     42})

(def smoke
  "Smoke: slow-rising, expanding, fading gray clouds."
  {:particle/emitter  {:emitter/type      :point
                       :emitter/position  [0 0]
                       :emitter/rate      12
                       :emitter/direction [0 -1]
                       :emitter/spread    0.3
                       :emitter/speed     [15 40]}
   :particle/lifetime [1.5 3.0]
   :particle/forces   [{:force/type :gravity :force/acceleration [0 -15]}
                       {:force/type :drag :force/coefficient 0.4}]
   :particle/size     [3 8 14 18]
   :particle/opacity  [0.0 0.3 0.2 0.0]
   :particle/color    [[:color/rgb 180 180 180]
                       [:color/rgb 140 140 140]
                       [:color/rgb 100 100 100]]
   :particle/shape    :circle
   :particle/seed     42})

(def fountain
  "Fountain: upward spray arcing back down under gravity."
  {:particle/emitter  {:emitter/type      :point
                       :emitter/position  [0 0]
                       :emitter/rate      40
                       :emitter/direction [0 -1]
                       :emitter/spread    0.2
                       :emitter/speed     [150 250]}
   :particle/lifetime [1.2 2.5]
   :particle/forces   [{:force/type :gravity :force/acceleration [0 180]}]
   :particle/size     [2 4 3 1]
   :particle/opacity  [0.3 0.9 0.7 0.0]
   :particle/color    [[:color/rgb 150 210 255]
                       [:color/rgb 80 160 255]
                       [:color/rgb 30 80 220]]
   :particle/shape    :circle
   :particle/seed     42})

(comment
  ;; Fire example
  (require '[eido.core :as eido]
           '[eido.animate :as anim])

  (let [particles (vec (simulate
                         (with-position fire [200 350])
                         60 {:fps 30}))]
    (eido/render
      (anim/frames 60
        (fn [t]
          {:image/size [400 400]
           :image/background [:color/rgb 20 15 10]
           :image/nodes (nth particles (int (* t 59)))}))
      {:output "/tmp/fire.gif" :fps 30}))

  ;; Confetti example
  (let [particles (vec (simulate
                         (with-position confetti [200 200])
                         90 {:fps 30}))]
    (anim/frames 90
      (fn [t]
        {:image/size [400 400]
         :image/background [:color/rgb 250 250 250]
         :image/nodes (nth particles (int (* t 89)))})))
  )
