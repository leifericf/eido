(ns eido.animate
  "Easing, interpolation, and timing functions for animation.

  Easing functions take a normalized t in [0,1] and return a shaped t.
  Compose with `lerp` for value interpolation, or use `frames`, `stagger`,
  and `cycle-n` for sequencing multi-frame animations.")

(defn progress
  "Returns normalized progress [0.0, 1.0] for frame within total frames.
  Frame 0 returns 0.0, last frame returns 1.0."
  [frame total]
  (if (<= total 1)
    0.0
    (/ (double frame) (dec total))))

(defn ping-pong
  "Converts linear progress to a value that goes 0 -> 1 -> 0.
  t in [0, 1], returns [0, 1]."
  [t]
  (if (<= t 0.5)
    (* 2.0 t)
    (* 2.0 (- 1.0 t))))

(defn cycle-n
  "Returns progress for n full cycles within [0, 1].
  E.g., (cycle-n 2 0.5) completes one full cycle by midpoint."
  [n t]
  (mod (* (double n) t) 1.0))

(defn lerp
  "Linearly interpolates between a and b. t in [0, 1]."
  [a b t]
  (+ (double a) (* (- (double b) (double a)) (double t))))

(defn ease-in
  "Quadratic ease in: slow start, fast end. t in [0, 1]."
  [t]
  (* t t))

(defn ease-out
  "Quadratic ease out: fast start, slow end. t in [0, 1]."
  [t]
  (let [t' (- 1.0 t)]
    (- 1.0 (* t' t'))))

(defn ease-in-out
  "Quadratic ease in-out: slow start and end. t in [0, 1]."
  [t]
  (if (< t 0.5)
    (* 2.0 t t)
    (- 1.0 (* 2.0 (- 1.0 t) (- 1.0 t)))))

;; --- cubic ---

(defn ease-in-cubic
  "Cubic ease in. t in [0, 1]."
  [t]
  (* t t t))

(defn ease-out-cubic
  "Cubic ease out. t in [0, 1]."
  [t]
  (let [t' (- 1.0 t)]
    (- 1.0 (* t' t' t'))))

(defn ease-in-out-cubic
  "Cubic ease in-out. t in [0, 1]."
  [t]
  (if (< t 0.5)
    (* 4.0 t t t)
    (- 1.0 (* 4.0 (- 1.0 t) (- 1.0 t) (- 1.0 t)))))

;; --- quart ---

(defn ease-in-quart
  "Quartic ease in. t in [0, 1]."
  [t]
  (* t t t t))

(defn ease-out-quart
  "Quartic ease out. t in [0, 1]."
  [t]
  (let [t' (- 1.0 t)]
    (- 1.0 (* t' t' t' t'))))

(defn ease-in-out-quart
  "Quartic ease in-out. t in [0, 1]."
  [t]
  (if (< t 0.5)
    (* 8.0 t t t t)
    (- 1.0 (* 8.0 (- 1.0 t) (- 1.0 t) (- 1.0 t) (- 1.0 t)))))

;; --- exponential ---

(defn ease-in-expo
  "Exponential ease in. t in [0, 1]."
  [t]
  (if (zero? t) 0.0 (Math/pow 2.0 (* 10.0 (- t 1.0)))))

(defn ease-out-expo
  "Exponential ease out. t in [0, 1]."
  [t]
  (if (== t 1.0) 1.0 (- 1.0 (Math/pow 2.0 (* -10.0 t)))))

(defn ease-in-out-expo
  "Exponential ease in-out. t in [0, 1]."
  [t]
  (cond
    (zero? t)  0.0
    (== t 1.0) 1.0
    (< t 0.5) (* 0.5 (Math/pow 2.0 (- (* 20.0 t) 10.0)))
    :else      (- 1.0 (* 0.5 (Math/pow 2.0 (- (* -20.0 t) -10.0))))))

;; --- circular ---

(defn ease-in-circ
  "Circular ease in. t in [0, 1]."
  [t]
  (- 1.0 (Math/sqrt (- 1.0 (* t t)))))

(defn ease-out-circ
  "Circular ease out. t in [0, 1]."
  [t]
  (let [t' (- t 1.0)]
    (Math/sqrt (- 1.0 (* t' t')))))

(defn ease-in-out-circ
  "Circular ease in-out. t in [0, 1]."
  [t]
  (if (< t 0.5)
    (* 0.5 (- 1.0 (Math/sqrt (- 1.0 (* 4.0 t t)))))
    (* 0.5 (+ 1.0 (Math/sqrt (- 1.0 (let [v (- (* 2.0 t) 2.0)] (* v v))))))))

;; --- back (overshoot) ---

(def ^:private back-c1 1.70158)
(def ^:private back-c2 (* back-c1 1.525))
(def ^:private back-c3 (+ back-c1 1.0))

(defn ease-in-back
  "Back ease in (overshoots then returns). t in [0, 1]."
  [t]
  (- (* back-c3 t t t) (* back-c1 t t)))

(defn ease-out-back
  "Back ease out (overshoots then settles). t in [0, 1]."
  [t]
  (let [t' (- t 1.0)]
    (+ 1.0 (* back-c3 t' t' t') (* back-c1 t' t'))))

(defn ease-in-out-back
  "Back ease in-out. t in [0, 1]."
  [t]
  (if (< t 0.5)
    (/ (* t t (- (* (+ back-c2 1.0) 2.0 t) back-c2)) 2.0)
    (let [t' (- (* 2.0 t) 2.0)]
      (/ (+ (* t' t' (+ (* (+ back-c2 1.0) t') back-c2)) 2.0) 2.0))))

;; --- elastic ---

(def ^:private elastic-c4 (/ (* 2.0 Math/PI) 3.0))
(def ^:private elastic-c5 (/ (* 2.0 Math/PI) 4.5))

(defn ease-in-elastic
  "Elastic ease in. t in [0, 1]."
  [t]
  (cond
    (zero? t)  0.0
    (== t 1.0) 1.0
    :else (- (* (Math/pow 2.0 (* 10.0 (- t 1.0)))
               (Math/sin (* (- t 1.075) elastic-c4))))))

(defn ease-out-elastic
  "Elastic ease out. t in [0, 1]."
  [t]
  (cond
    (zero? t)  0.0
    (== t 1.0) 1.0
    :else (+ 1.0 (* (Math/pow 2.0 (* -10.0 t))
                     (Math/sin (* (- t 0.075) elastic-c4))))))

(defn ease-in-out-elastic
  "Elastic ease in-out. t in [0, 1]."
  [t]
  (cond
    (zero? t)  0.0
    (== t 1.0) 1.0
    (< t 0.5) (/ (- (* (Math/pow 2.0 (- (* 20.0 t) 10.0))
                       (Math/sin (* (- (* 20.0 t) 11.125) elastic-c5))))
                 2.0)
    :else      (+ (/ (* (Math/pow 2.0 (- (* -20.0 t) -10.0))
                        (Math/sin (* (- (* 20.0 t) 11.125) elastic-c5)))
                     2.0)
                  1.0)))

;; --- bounce ---

(defn ease-out-bounce
  "Bounce ease out. t in [0, 1]."
  [t]
  (let [n1 7.5625
        d1 2.75]
    (cond
      (< t (/ 1.0 d1))
      (* n1 t t)

      (< t (/ 2.0 d1))
      (let [t' (- t (/ 1.5 d1))]
        (+ (* n1 t' t') 0.75))

      (< t (/ 2.5 d1))
      (let [t' (- t (/ 2.25 d1))]
        (+ (* n1 t' t') 0.9375))

      :else
      (let [t' (- t (/ 2.625 d1))]
        (+ (* n1 t' t') 0.984375)))))

(defn ease-in-bounce
  "Bounce ease in. t in [0, 1]."
  [t]
  (- 1.0 (ease-out-bounce (- 1.0 t))))

(defn ease-in-out-bounce
  "Bounce ease in-out. t in [0, 1]."
  [t]
  (if (< t 0.5)
    (* 0.5 (- 1.0 (ease-out-bounce (- 1.0 (* 2.0 t)))))
    (* 0.5 (+ 1.0 (ease-out-bounce (- (* 2.0 t) 1.0))))))

(defn frames
  "Builds a vector of n frames by calling (f t) for each frame,
  where t is normalized progress [0.0, 1.0]."
  [n f]
  (mapv (fn [i] (f (progress i n))) (range n)))

(defn stagger
  "Returns per-element progress for staggered animations.
  i: element index (0-based), n: total elements, t: global progress [0, 1],
  overlap: how much elements overlap (0 = sequential, 1 = all simultaneous).
  Returns local progress for element i, clamped to [0, 1]."
  [i n t overlap]
  (let [duration (/ (+ 1.0 (* (double overlap) (dec n))) (double n))
        offset   (* (double i) (* (- 1.0 (double overlap)) (/ 1.0 (double n))))
        local    (/ (- (double t) offset) duration)]
    (max 0.0 (min 1.0 local))))

;; --- convenience helpers ---

(defn ^{:convenience true}
  pulse
  "Sine oscillation: returns a value in [0, 1] that pulses over time.
  Wraps (/ (+ 1 (sin (* t 2 PI frequency))) 2)."
  ([t] (pulse t 1.0))
  ([t frequency]
   (/ (+ 1.0 (Math/sin (* (double t) 2.0 Math/PI (double frequency)))) 2.0)))

(defn ^{:convenience true}
  fade-linear
  "Linear fade: 1.0 at t=0, 0.0 at t=1. Wraps (- 1.0 t)."
  [t]
  (- 1.0 (min 1.0 (max 0.0 (double t)))))

(defn ^{:convenience true :convenience-for 'eido.animate/fade-linear}
  fade-out
  "Quadratic fade-out (softer tail). Wraps (* (fade-linear t) (fade-linear t))."
  [t]
  (let [f (fade-linear t)] (* f f)))

(defn ^{:convenience true}
  fade-in
  "Quadratic fade-in. Wraps (* t t) clamped to [0, 1]."
  [t]
  (let [t (min 1.0 (max 0.0 (double t)))] (* t t)))

(comment
  ;; Build 60 frames of a pulsing radius
  (frames 60
    (fn [t]
      (lerp 50 150 (ease-in-out (ping-pong t)))))

  ;; Staggered appearance of 5 elements
  (for [t [0.0 0.25 0.5 0.75 1.0]]
    (mapv #(stagger % 5 t 0.3) (range 5)))
  )
