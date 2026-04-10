(ns eido.gen.scatter
  (:require
    [eido.gen.noise :as noise]
    [eido.gen.stipple :as stipple]))

;; --- distribution generators ---

(defn grid
  "Generates a regular grid of [x y] positions within bounds."
  [bx by bw bh cols rows]
  (let [dx (/ (double bw) cols)
        dy (/ (double bh) rows)]
    (into []
          (for [row (range rows)
                col (range cols)]
            [(+ bx (* (+ col 0.5) dx))
             (+ by (* (+ row 0.5) dy))]))))

(defn poisson-disk
  "Generates well-distributed points via Poisson disk sampling.
  Delegates to eido.gen.stipple/poisson-disk."
  [bx by bw bh min-dist seed]
  (stipple/poisson-disk bx by bw bh min-dist seed))

(defn noise-field
  "Generates up to n positions biased by noise density.
  Points are placed where noise value exceeds a threshold."
  [bx by bw bh n seed]
  (let [rng (java.util.Random. (long seed))
        bx  (double bx)
        by  (double by)
        bw  (double bw)
        bh  (double bh)]
    (loop [i 0 attempts 0 pts []]
      (if (or (>= i n) (>= attempts (* n 10)))
        pts
        (let [x (+ bx (* (.nextDouble rng) bw))
              y (+ by (* (.nextDouble rng) bh))
              v (noise/perlin2d (* x 0.02) (* y 0.02) {:seed seed})
              ;; Accept with probability proportional to noise value
              threshold (+ 0.5 (* 0.5 v))]
          (if (> (.nextDouble rng) (- 1.0 threshold))
            (recur (inc i) (inc attempts) (conj pts [x y]))
            (recur i (inc attempts) pts)))))))

;; --- scatter node expansion ---

(defn scatter->nodes
  "Creates positioned group nodes from a shape and positions.
  jitter: nil or {:x dx :y dy :seed s} for random displacement."
  [shape positions jitter]
  (let [rng (when jitter (java.util.Random. (long (get jitter :seed 0))))
        jx  (double (get jitter :x 0))
        jy  (double (get jitter :y 0))]
    (mapv (fn [[x y]]
            (let [dx (if rng (* (- (* 2.0 (.nextDouble rng)) 1.0) jx) 0.0)
                  dy (if rng (* (- (* 2.0 (.nextDouble rng)) 1.0) jy) 0.0)]
              {:node/type      :group
               :node/transform [[:transform/translate (+ x dx) (+ y dy)]]
               :group/children [shape]}))
          positions)))

(defn jitter
  "Displaces each point by a Gaussian offset scaled by amount.
  Returns [[x' y'] ...]. Amount controls displacement magnitude:
  0 = no change, higher = more disorder. Uses Gaussian for natural feel."
  [points amount seed]
  (let [rng (java.util.Random. (long seed))
        amt (double amount)]
    (if (zero? amt)
      (vec points)
      (mapv (fn [[x y]]
              [(+ (double x) (* amt (.nextGaussian rng)))
               (+ (double y) (* amt (.nextGaussian rng)))])
            points))))

(comment
  (grid 0 0 100 100 5 5)
  (poisson-disk 0 0 100 100 10 42)
  (noise-field 0 0 100 100 50 42)
  (jitter (grid 0 0 100 100 5 5) 3.0 42)
  (scatter->nodes
    {:node/type :shape/circle :circle/center [0.0 0.0] :circle/radius 3.0
     :style/fill [:color/rgb 255 0 0]}
    [[10.0 20.0] [30.0 40.0]]
    {:x 5 :y 5 :seed 42})
  )
