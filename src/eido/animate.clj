(ns eido.animate)

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

(comment
  ;; Build 60 frames of a pulsing radius
  (frames 60
    (fn [t]
      (lerp 50 150 (ease-in-out (ping-pong t)))))

  ;; Staggered appearance of 5 elements
  (for [t [0.0 0.25 0.5 0.75 1.0]]
    (mapv #(stagger % 5 t 0.3) (range 5)))
  )
