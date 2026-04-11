(ns eido.gen.grammar
  "Constrained shape grammars: L-system expansion with bounds checking.

  Standard L-systems expand blindly — they don't know about canvas
  boundaries. This generator adds two capabilities:

  1. Bounded expansion: multiple alternative rules per symbol (from full
     growth to no growth). The solver tries the fullest expansion first
     and backs off individual branches when they exceed bounds.

  2. Scale-to-fit: after expansion, the result is automatically scaled
     and centered to fill the target canvas, making effective use of
     the available space."
  (:require
    [eido.gen.prob :as prob]))

;; --- geometry helpers ---

(defn- turtle-bounds
  "Computes the bounding box of turtle-graphics commands.
  Returns [min-x min-y max-x max-y]."
  [commands angle-deg length origin heading-deg]
  (let [angle-rad (* (double angle-deg) (/ Math/PI 180.0))
        heading   (* (double heading-deg) (/ Math/PI 180.0))
        length    (double length)
        [ox oy]   origin]
    (loop [chars (seq commands)
           x (double ox) y (double oy)
           dir heading
           stack []
           min-x (double ox) min-y (double oy)
           max-x (double ox) max-y (double oy)]
      (if-not (seq chars)
        [min-x min-y max-x max-y]
        (let [c (first chars)]
          (case c
            (\F \G)
            (let [nx (+ x (* length (Math/cos dir)))
                  ny (+ y (* length (Math/sin dir)))]
              (recur (rest chars) nx ny dir stack
                     (min min-x nx) (min min-y ny)
                     (max max-x nx) (max max-y ny)))
            \+ (recur (rest chars) x y (+ dir angle-rad) stack
                      min-x min-y max-x max-y)
            \- (recur (rest chars) x y (- dir angle-rad) stack
                      min-x min-y max-x max-y)
            \| (recur (rest chars) x y (+ dir Math/PI) stack
                      min-x min-y max-x max-y)
            \[ (recur (rest chars) x y dir (conj stack [x y dir])
                      min-x min-y max-x max-y)
            \] (if (seq stack)
                 (let [[sx sy sd] (peek stack)]
                   (recur (rest chars) sx sy sd (pop stack)
                          min-x min-y max-x max-y))
                 (recur (rest chars) x y dir stack
                        min-x min-y max-x max-y))
            (recur (rest chars) x y dir stack
                   min-x min-y max-x max-y)))))))

(defn- within-bounds?
  "Checks if turtle path stays within [bx by bw bh]."
  [commands angle length origin heading bounds]
  (let [[bx by bw bh] bounds
        [min-x min-y max-x max-y]
        (turtle-bounds commands angle length origin heading)]
    (and (>= min-x (double bx)) (>= min-y (double by))
         (<= max-x (+ (double bx) (double bw)))
         (<= max-y (+ (double by) (double bh))))))

;; --- constrained expansion ---

(defn- expand-with-choices
  "Expands string s using indexed rule choices."
  [s rules choices]
  (let [idx (volatile! 0)]
    (apply str
      (map (fn [c]
             (let [key (str c)
                   alts (get rules key)]
               (if alts
                 (let [i @idx
                       choice (get choices i 0)]
                   (vswap! idx inc)
                   (get alts (min choice (dec (count alts)))))
                 (str c))))
           s))))

(defn- count-sites [s rules]
  (count (filter #(contains? rules (str %)) s)))

(defn solve
  "Finds rule choices that keep an L-system within bounds.

  axiom:  starting string (e.g. \"F\")
  rules:  map of character -> vector of alternative replacements
          ordered from fullest to smallest (first = most growth)
  opts:
    :iterations (3)     — expansion steps
    :angle (22.5)       — turtle turn angle (degrees)
    :length (5.0)       — turtle step length
    :origin [200 400]   — turtle start position
    :heading (-90)      — initial direction (degrees)
    :bounds [0 0 w h]   — bounding box constraint
    :seed (42)          — controls variety in rule selection

  Returns the expanded string that fits within bounds."
  [axiom rules opts]
  (let [iterations (get opts :iterations 3)
        angle      (get opts :angle 22.5)
        length     (get opts :length 5.0)
        origin     (get opts :origin [200 400])
        heading    (get opts :heading -90)
        bounds     (get opts :bounds [0 0 400 400])
        seed       (get opts :seed 42)
        max-alts   (apply max 1 (map count (vals rules)))
        rng        (prob/make-rng seed)]
    (loop [current axiom, iter 0]
      (if (>= iter iterations)
        current
        (let [n-sites (count-sites current rules)]
          (if (zero? n-sites)
            current
            ;; Strategy: try full expansion first (all zeros = biggest rule).
            ;; If it fits, use it. If not, randomly back off individual sites.
            (let [full-choices (vec (repeat n-sites 0))
                  full-expanded (expand-with-choices current rules full-choices)]
              (if (within-bounds? full-expanded angle length origin heading bounds)
                ;; Full expansion fits — use it
                (recur full-expanded (inc iter))
                ;; Full expansion overflows — try random combinations,
                ;; preferring lower values (more growth)
                (let [attempts (min 200 (long (Math/pow max-alts (min n-sites 4))))
                      candidates
                      (mapv (fn [_]
                              (mapv (fn [_] (.nextInt ^java.util.Random rng max-alts))
                                    (range n-sites)))
                            (range attempts))
                      ;; Sort by total growth (lower sum = more growth)
                      sorted (sort-by #(reduce + %) candidates)
                      valid  (first
                               (filter
                                 (fn [choices]
                                   (let [exp (expand-with-choices current rules choices)]
                                     (within-bounds? exp angle length origin heading bounds)))
                                 sorted))]
                  (if valid
                    (recur (expand-with-choices current rules valid) (inc iter))
                    ;; Nothing fits — return current
                    current))))))))))

;; --- turtle interpreter with scale-to-fit ---

(defn- interpret-turtle
  "Interprets expanded L-system string into path commands."
  [expanded angle-deg length origin heading-deg]
  (let [angle-rad (* (double angle-deg) (/ Math/PI 180.0))
        length    (double length)
        heading   (* (double heading-deg) (/ Math/PI 180.0))
        [ox oy]   origin]
    (loop [chars (seq expanded)
           x (double ox) y (double oy)
           dir heading
           stack []
           cmds [[:move-to [ox oy]]]]
      (if-not (seq chars)
        cmds
        (case (first chars)
          \F (let [nx (+ x (* length (Math/cos dir)))
                   ny (+ y (* length (Math/sin dir)))]
               (recur (rest chars) nx ny dir stack
                      (conj cmds [:line-to [nx ny]])))
          \G (let [nx (+ x (* length (Math/cos dir)))
                   ny (+ y (* length (Math/sin dir)))]
               (recur (rest chars) nx ny dir stack
                      (conj cmds [:move-to [nx ny]])))
          \+ (recur (rest chars) x y (+ dir angle-rad) stack cmds)
          \- (recur (rest chars) x y (- dir angle-rad) stack cmds)
          \| (recur (rest chars) x y (+ dir Math/PI) stack cmds)
          \[ (recur (rest chars) x y dir (conj stack [x y dir]) cmds)
          \] (if (seq stack)
               (let [[sx sy sd] (peek stack)]
                 (recur (rest chars) sx sy sd (pop stack)
                        (conj cmds [:move-to [sx sy]])))
               (recur (rest chars) x y dir stack cmds))
          (recur (rest chars) x y dir stack cmds))))))

(defn- scale-commands
  "Scales and centers path commands to fit within target bounds.
  Adds margin as a fraction of the target size."
  [cmds target-bounds margin-frac]
  (let [[tx ty tw th] target-bounds
        margin (* (double (min tw th)) (double margin-frac))
        ;; Compute current bounding box from commands
        points (keep (fn [[cmd & args]]
                       (when (#{:move-to :line-to} cmd) (first args)))
                     cmds)
        xs (mapv first points)
        ys (mapv second points)]
    (if (or (empty? xs) (empty? ys))
      cmds
      (let [min-x (reduce min xs) max-x (reduce max xs)
            min-y (reduce min ys) max-y (reduce max ys)
            src-w (- max-x min-x) src-h (- max-y min-y)]
        (if (or (< src-w 0.001) (< src-h 0.001))
          cmds
          (let [dest-x (+ (double tx) margin)
                dest-y (+ (double ty) margin)
                dest-w (- (double tw) (* 2.0 margin))
                dest-h (- (double th) (* 2.0 margin))
                scale  (min (/ dest-w src-w) (/ dest-h src-h))
                ;; Center in destination
                cx     (+ dest-x (/ (- dest-w (* src-w scale)) 2.0))
                cy     (+ dest-y (/ (- dest-h (* src-h scale)) 2.0))
                xform  (fn [[x y]]
                          [(+ cx (* (- (double x) min-x) scale))
                           (+ cy (* (- (double y) min-y) scale))])]
            (mapv (fn [[cmd & args]]
                    (case cmd
                      :move-to [:move-to (xform (first args))]
                      :line-to [:line-to (xform (first args))]
                      :quad-to [:quad-to (xform (first args)) (xform (second args))]
                      [cmd]))
                  cmds)))))))

(defn grammar->path-cmds
  "Full constrained grammar pipeline.
  Expands axiom with bounded rules, then interprets as turtle graphics,
  then scales to fill the target canvas.

  opts: see `solve`, plus:
    :scale-to-fit? (true) — scale and center result to fill bounds
    :margin (0.05)        — margin fraction when scaling"
  [axiom rules opts]
  (let [expanded (solve axiom rules opts)
        angle    (get opts :angle 22.5)
        length   (get opts :length 5.0)
        origin   (get opts :origin [200 400])
        heading  (get opts :heading -90)
        bounds   (get opts :bounds [0 0 400 400])
        cmds     (interpret-turtle expanded angle length origin heading)]
    (if (get opts :scale-to-fit? true)
      (scale-commands cmds bounds (get opts :margin 0.05))
      cmds)))

(comment
  ;; Bush that fills 400x400
  (let [rules {"F" ["FF+[+F-F-F]-[-F+F+F]" "F+[+F]-[-F]" "F"]}]
    (grammar->path-cmds "F" rules
      {:iterations 4 :angle 22.5 :length 5.0
       :origin [200 380] :heading -90
       :bounds [0 0 400 400] :seed 42}))

  ;; Koch filling canvas
  (let [rules {"F" ["F+F-F-F+F" "F+F-F" "F"]}]
    (grammar->path-cmds "F" rules
      {:iterations 4 :angle 90 :length 3
       :origin [50 300] :heading 0
       :bounds [0 0 400 400] :seed 1}))
  )
