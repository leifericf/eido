(ns eido.gen.boids
  "Boids flocking simulation with steering behaviors.
  Separation, alignment, cohesion, plus optional seek/flee/wander.
  Uses spatial grid for efficient neighbor lookup."
  (:require
    [eido.gen.noise :as noise]
    [eido.gen.prob :as prob]))

;; --- vector math ---

(defn- v+ [[x1 y1] [x2 y2]]
  [(+ (double x1) (double x2)) (+ (double y1) (double y2))])

(defn- v- [[x1 y1] [x2 y2]]
  [(- (double x1) (double x2)) (- (double y1) (double y2))])

(defn- v* [[x y] s]
  (let [s (double s)]
    [(* (double x) s) (* (double y) s)]))

(defn- v-mag [[x y]]
  (Math/sqrt (+ (* (double x) (double x)) (* (double y) (double y)))))

(defn- v-normalize [[x y]]
  (let [m (v-mag [x y])]
    (if (< m 1e-10)
      [0.0 0.0]
      [(/ (double x) m) (/ (double y) m)])))

(defn- v-limit [[x y] max-mag]
  (let [m (v-mag [x y])]
    (if (<= m (double max-mag))
      [x y]
      (v* (v-normalize [x y]) max-mag))))

(defn- v-dist-sq [[x1 y1] [x2 y2]]
  (let [dx (- (double x2) (double x1))
        dy (- (double y2) (double y1))]
    (+ (* dx dx) (* dy dy))))

;; --- spatial grid for neighbor lookup ---

(defn- build-spatial-grid
  "Builds a grid mapping cell -> boids for O(n) neighbor queries."
  [boids cell-size bx by bw bh]
  (let [cs (double cell-size)
        gw (int (Math/ceil (/ (double bw) cs)))
        gh (int (Math/ceil (/ (double bh) cs)))
        grid (object-array (* (max 1 gw) (max 1 gh)))]
    (doseq [b boids]
      (let [[px py] (:pos b)
            gx (int (/ (- (double px) (double bx)) cs))
            gy (int (/ (- (double py) (double by)) cs))
            gx (max 0 (min gx (dec (max 1 gw))))
            gy (max 0 (min gy (dec (max 1 gh))))
            idx (+ (* gy (max 1 gw)) gx)
            existing (aget grid idx)]
        (aset grid idx (if existing (conj existing b) [b]))))
    {:grid grid :gw (max 1 gw) :gh (max 1 gh)
     :cell-size cs :bx (double bx) :by (double by)}))

(defn- query-neighbors
  "Returns boids within radius of pos, excluding self (by :id)."
  [{:keys [^objects grid gw gh cell-size bx by]} pos radius self-id]
  (let [[px py] pos
        r   (double radius)
        r-sq (* r r)
        cs  (double cell-size)
        cx  (int (/ (- (double px) bx) cs))
        cy  (int (/ (- (double py) by) cs))
        rc  (int (Math/ceil (/ r cs)))
        gw  (int gw)
        gh  (int gh)]
    (into []
          (comp cat
                (filter (fn [b]
                          (and (not= (:id b) self-id)
                               (<= (v-dist-sq pos (:pos b)) r-sq)))))
          (for [dx (range (- rc) (inc rc))
                dy (range (- rc) (inc rc))
                :let [nx (+ cx dx) ny (+ cy dy)]
                :when (and (>= nx 0) (< nx gw)
                           (>= ny 0) (< ny gh))
                :let [cell (aget grid (+ (* ny gw) nx))]
                :when cell]
            cell))))

;; --- steering behaviors ---

(defn separation
  "Computes steering force away from nearby boids."
  [boid neighbors radius strength]
  (if-not (seq neighbors)
    [0.0 0.0]
    (let [r-sq (* (double radius) (double radius))
          sum (reduce (fn [acc n]
                        (let [d-sq (v-dist-sq (:pos boid) (:pos n))]
                          (if (and (pos? d-sq) (<= d-sq r-sq))
                            (let [diff (v- (:pos boid) (:pos n))
                                  d (Math/sqrt d-sq)]
                              (v+ acc (v* (v-normalize diff) (/ 1.0 d))))
                            acc)))
                      [0.0 0.0] neighbors)]
      (v* (v-normalize sum) (double strength)))))

(defn alignment
  "Computes steering force toward average heading of neighbors."
  [boid neighbors radius strength]
  (if-not (seq neighbors)
    [0.0 0.0]
    (let [r-sq (* (double radius) (double radius))
          [sx sy cnt]
          (reduce (fn [[sx sy cnt] n]
                    (if (<= (v-dist-sq (:pos boid) (:pos n)) r-sq)
                      (let [[vx vy] (:vel n)]
                        [(+ sx (double vx)) (+ sy (double vy)) (inc cnt)])
                      [sx sy cnt]))
                  [0.0 0.0 0] neighbors)]
      (if (zero? cnt)
        [0.0 0.0]
        (let [avg [(/ sx cnt) (/ sy cnt)]
              steer (v- avg (:vel boid))]
          (v* (v-normalize steer) (double strength)))))))

(defn cohesion
  "Computes steering force toward centroid of neighbors."
  [boid neighbors radius strength]
  (if-not (seq neighbors)
    [0.0 0.0]
    (let [r-sq (* (double radius) (double radius))
          [sx sy cnt]
          (reduce (fn [[sx sy cnt] n]
                    (if (<= (v-dist-sq (:pos boid) (:pos n)) r-sq)
                      (let [[nx ny] (:pos n)]
                        [(+ sx (double nx)) (+ sy (double ny)) (inc cnt)])
                      [sx sy cnt]))
                  [0.0 0.0 0] neighbors)]
      (if (zero? cnt)
        [0.0 0.0]
        (let [centroid [(/ sx cnt) (/ sy cnt)]
              steer (v- centroid (:pos boid))]
          (v* (v-normalize steer) (double strength)))))))

(defn seek
  "Computes steering force toward a target point."
  [boid target max-speed strength]
  (let [desired (v- target (:pos boid))
        steer (v- (v* (v-normalize desired) (double max-speed)) (:vel boid))]
    (v* (v-normalize steer) (double strength))))

(defn flee
  "Computes steering force away from a point within flee-radius."
  [boid target flee-radius strength]
  (let [d-sq (v-dist-sq (:pos boid) target)]
    (if (> d-sq (* (double flee-radius) (double flee-radius)))
      [0.0 0.0]
      (let [desired (v- (:pos boid) target)]
        (v* (v-normalize desired) (double strength))))))

(defn- wander-force
  "Computes noise-based wander steering."
  [boid noise-scale tick strength]
  (let [[px py] (:pos boid)
        ns (double noise-scale)
        angle (* (noise/perlin2d (* (double px) ns) (* (double py) ns)
                                 {:seed (+ (:id boid) tick)})
                 Math/PI 2.0)]
    (v* [(Math/cos angle) (Math/sin angle)] (double strength))))

(defn- bounds-steer
  "Computes soft boundary steering force."
  [boid bx by bw bh margin strength]
  (let [[px py] (:pos boid)
        px (double px) py (double py)
        bx (double bx) by (double by)
        bw (double bw) bh (double bh)
        m (double margin) s (double strength)
        fx (cond (< px (+ bx m)) (* s (/ (- (+ bx m) px) m))
                 (> px (- (+ bx bw) m)) (* (- s) (/ (- px (- (+ bx bw) m)) m))
                 :else 0.0)
        fy (cond (< py (+ by m)) (* s (/ (- (+ by m) py) m))
                 (> py (- (+ by bh) m)) (* (- s) (/ (- py (- (+ by bh) m)) m))
                 :else 0.0)]
    [fx fy]))

;; --- simulation ---

(defn init-flock
  "Creates initial flock state.
  opts: :count (50), :bounds [bx by bw bh], :max-speed (2.0), :seed (42).
  Returns {:boids [...] :tick 0}."
  [opts]
  (let [n    (get opts :count 50)
        [bx by bw bh] (get opts :bounds [0 0 600 400])
        seed (get opts :seed 42)
        ms   (get opts :max-speed 2.0)
        rng  (prob/make-rng seed)]
    {:boids (mapv (fn [i]
                    {:id  i
                     :pos [(+ (double bx) (* (.nextDouble rng) (double bw)))
                           (+ (double by) (* (.nextDouble rng) (double bh)))]
                     :vel [(* (- (* 2.0 (.nextDouble rng)) 1.0) (double ms))
                           (* (- (* 2.0 (.nextDouble rng)) 1.0) (double ms))]})
                  (range n))
     :tick 0}))

(defn step-flock
  "Advances flock by one tick. config contains behavior parameters."
  [{:keys [boids tick]} config]
  (let [max-speed (get config :max-speed 3.0)
        max-force (get config :max-force 0.15)
        [bx by bw bh] (get config :bounds [0 0 600 400])
        margin   (get config :bounds-margin 40)
        sep-cfg  (get config :separation {})
        ali-cfg  (get config :alignment {})
        coh-cfg  (get config :cohesion {})
        wander-cfg (get config :wander nil)
        seek-cfg   (get config :seek nil)
        flee-cfg   (get config :flee nil)
        ;; Build spatial grid with max radius
        max-r (max (get sep-cfg :radius 25)
                   (get ali-cfg :radius 50)
                   (get coh-cfg :radius 50))
        sgrid (build-spatial-grid boids max-r bx by bw bh)]
    {:boids
     (mapv (fn [b]
             (let [neighbors (query-neighbors sgrid (:pos b) max-r (:id b))
                   ;; Steering forces
                   f-sep (separation b neighbors
                           (get sep-cfg :radius 25) (get sep-cfg :strength 1.5))
                   f-ali (alignment b neighbors
                           (get ali-cfg :radius 50) (get ali-cfg :strength 1.0))
                   f-coh (cohesion b neighbors
                           (get coh-cfg :radius 50) (get coh-cfg :strength 1.0))
                   f-bounds (bounds-steer b bx by bw bh margin 0.5)
                   f-wander (if wander-cfg
                              (wander-force b
                                (get wander-cfg :noise-scale 0.01)
                                tick
                                (get wander-cfg :strength 0.5))
                              [0.0 0.0])
                   f-seek (if seek-cfg
                            (seek b (:target seek-cfg) max-speed
                              (get seek-cfg :strength 0.5))
                            [0.0 0.0])
                   f-flee (if flee-cfg
                            (flee b (:target flee-cfg)
                              (get flee-cfg :radius 100)
                              (get flee-cfg :strength 1.0))
                            [0.0 0.0])
                   ;; Sum all forces and limit
                   force (-> [0.0 0.0]
                             (v+ f-sep) (v+ f-ali) (v+ f-coh)
                             (v+ f-bounds) (v+ f-wander)
                             (v+ f-seek) (v+ f-flee)
                             (v-limit max-force))
                   new-vel (v-limit (v+ (:vel b) force) max-speed)
                   new-pos (v+ (:pos b) new-vel)]
               (assoc b :pos new-pos :vel new-vel)))
           boids)
     :tick (inc tick)}))

(defn simulate-flock
  "Returns a vector of n flock states."
  [config n opts]
  (let [flock (init-flock config)]
    (vec (take n (iterate #(step-flock % config) flock)))))

;; --- rendering ---

(defn flock->nodes
  "Renders a flock state as scene nodes.
  opts: :shape (:triangle or :circle), :size (6), :style (style map)."
  [{:keys [boids]} opts]
  (let [shape (get opts :shape :triangle)
        size  (double (get opts :size 6))
        style (get opts :style {})]
    (mapv (fn [{[px py] :pos [vx vy] :vel}]
            (let [angle (Math/atan2 (double vy) (double vx))
                  half  (/ size 2.0)]
              (case shape
                :triangle
                (let [;; Triangle pointing in direction of velocity
                      tip-x (+ (double px) (* size (Math/cos angle)))
                      tip-y (+ (double py) (* size (Math/sin angle)))
                      l-x (+ (double px) (* half (Math/cos (+ angle 2.5))))
                      l-y (+ (double py) (* half (Math/sin (+ angle 2.5))))
                      r-x (+ (double px) (* half (Math/cos (- angle 2.5))))
                      r-y (+ (double py) (* half (Math/sin (- angle 2.5))))]
                  (merge {:node/type     :shape/path
                          :path/commands [[:move-to [tip-x tip-y]]
                                          [:line-to [l-x l-y]]
                                          [:line-to [r-x r-y]]
                                          [:close]]}
                         style))
                :circle
                (merge {:node/type     :shape/circle
                        :circle/center [px py]
                        :circle/radius half}
                       style))))
          boids)))

;; --- presets ---

(def classic
  "Classic boids — balanced separation/alignment/cohesion."
  {:count 80
   :bounds [0 0 600 400]
   :max-speed 3.0
   :max-force 0.15
   :separation {:radius 25 :strength 1.5}
   :alignment  {:radius 50 :strength 1.0}
   :cohesion   {:radius 50 :strength 1.0}
   :bounds-margin 40
   :seed 42})

(def murmuration
  "Starling murmuration — tight cohesion, wide alignment."
  {:count 200
   :bounds [0 0 800 600]
   :max-speed 4.0
   :max-force 0.2
   :separation {:radius 15 :strength 2.0}
   :alignment  {:radius 80 :strength 1.5}
   :cohesion   {:radius 60 :strength 0.8}
   :bounds-margin 60
   :seed 42})

(comment
  (def f (init-flock classic))
  (step-flock f classic)
  (count (:boids (first (simulate-flock classic 10 {}))))
  (flock->nodes f {:shape :triangle :size 8 :style {:style/fill [:color/rgb 50 50 50]}}))
