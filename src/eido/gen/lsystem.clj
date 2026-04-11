(ns eido.gen.lsystem
  "Lindenmayer systems: string rewriting and turtle-graphics interpretation.
  Generates branching, fractal, and botanical structures from simple rules.

  Rules can be plain strings (classic L-system) or vectors of alternatives
  (constrained L-system). When alternatives are provided with :bounds,
  the system searches for rule choices that keep growth within the canvas.

  Symbols: F=draw, G=move, +=turn right, -=turn left, [=push, ]=pop, |=reverse."
  (:require
    [eido.gen.prob :as prob]))

;; --- rule presets ---
;;
;; Each preset is a map of character -> vector of alternatives,
;; ordered from fullest growth to smallest. Use with :bounds to
;; get constrained expansion that fills available space.

(def bush
  "Bushy plant with symmetric branching."
  {"F" ["FF+[+F-F-F]-[-F+F+F]" "F+[+F]-[-F]" "F"]})

(def fern
  "Asymmetric fern with curving fronds."
  {"F" ["FF-[-F+F+F]+[+F-F-F]" "F-[F]+[F]" "F"]})

(def coral
  "Dense branching coral structure."
  {"F" ["FF+F-F+F+FF" "F+F-F" "F"]})

(def lightning
  "Jagged branching lightning bolt."
  {"F" ["F[+F]F[-F]F" "F[+F][-F]" "F"]})

(def seaweed
  "Swaying organic tendrils."
  {"F" ["FF[-F++F][+F--F]++F--F" "F[-F][+F]" "F"]})

(def tree
  "Classic branching tree with taper."
  {"F" ["FF+[+F-F]-[-F+F]" "F+F-F" "F"]})

;; --- string rewriting ---

(defn expand-string
  "Rewrites axiom by applying rules for n iterations.
  Characters not in rules pass through unchanged.
  Rules can be strings (classic) or vectors of strings (uses first alternative)."
  [axiom rules iterations]
  (loop [s axiom i 0]
    (if (>= i iterations)
      s
      (recur (apply str (map (fn [c]
                               (let [r (get rules (str c))]
                                 (cond
                                   (nil? r)    (str c)
                                   (vector? r) (first r)
                                   :else       r)))
                             s))
             (inc i)))))

;; --- turtle interpreter ---

(defn interpret
  "Interprets an L-system string via turtle graphics.
  Returns scene-format path commands.
  opts: :angle (degrees), :length, :origin [x y], :heading (degrees)."
  [expanded opts]
  (let [angle-rad (* (double (:angle opts)) (/ Math/PI 180.0))
        heading   (* (double (get opts :heading 0.0)) (/ Math/PI 180.0))
        length    (double (:length opts))
        [ox oy]   (:origin opts [0 0])]
    (loop [chars (seq expanded)
           x     (double ox)
           y     (double oy)
           dir   heading
           stack []
           cmds  [[:move-to [x y]]]
           drawing? false]
      (if-not (seq chars)
        cmds
        (let [c (first chars)
              rest-chars (rest chars)]
          (case c
            \F (let [nx (+ x (* length (Math/cos dir)))
                     ny (+ y (* length (Math/sin dir)))]
                 (recur rest-chars nx ny dir stack
                        (conj cmds [:line-to [nx ny]])
                        true))
            \G (let [nx (+ x (* length (Math/cos dir)))
                     ny (+ y (* length (Math/sin dir)))]
                 (recur rest-chars nx ny dir stack
                        (conj cmds [:move-to [nx ny]])
                        false))
            \+ (recur rest-chars x y (+ dir angle-rad) stack cmds drawing?)
            \- (recur rest-chars x y (- dir angle-rad) stack cmds drawing?)
            \| (recur rest-chars x y (+ dir Math/PI) stack cmds drawing?)
            \[ (recur rest-chars x y dir
                      (conj stack [x y dir])
                      cmds drawing?)
            \] (let [[sx sy sd] (peek stack)]
                 (recur rest-chars sx sy sd
                        (pop stack)
                        (conj cmds [:move-to [sx sy]])
                        false))
            ;; Unknown symbol — skip
            (recur rest-chars x y dir stack cmds drawing?)))))))

;; --- constrained expansion (when rules have vector alternatives) ---

(defn- has-alternatives?
  "Returns true if any rule value is a vector of alternatives."
  [rules]
  (some vector? (vals rules)))

(defn- turtle-bounds
  "Computes bounding box of turtle-graphics commands."
  [commands angle-deg length origin heading-deg]
  (let [angle-rad (* (double angle-deg) (/ Math/PI 180.0))
        heading   (* (double heading-deg) (/ Math/PI 180.0))
        length    (double length)
        [ox oy]   origin]
    (loop [chars (seq commands)
           x (double ox) y (double oy) dir heading stack []
           min-x (double ox) min-y (double oy)
           max-x (double ox) max-y (double oy)]
      (if-not (seq chars)
        [min-x min-y max-x max-y]
        (case (first chars)
          (\F \G) (let [nx (+ x (* length (Math/cos dir)))
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
                 min-x min-y max-x max-y))))))

(defn- within-bounds? [commands angle length origin heading bounds]
  (let [[bx by bw bh] bounds
        [min-x min-y max-x max-y] (turtle-bounds commands angle length origin heading)]
    (and (>= min-x (double bx)) (>= min-y (double by))
         (<= max-x (+ (double bx) (double bw)))
         (<= max-y (+ (double by) (double bh))))))

(defn- expand-with-choices [s rules choices]
  (let [idx (volatile! 0)]
    (apply str
      (map (fn [c]
             (let [key (str c)
                   alts (get rules key)]
               (if (and alts (vector? alts))
                 (let [i @idx
                       choice (get choices i 0)]
                   (vswap! idx inc)
                   (get alts (min choice (dec (count alts)))))
                 (if alts (str alts) (str c)))))
           s))))

(defn- count-sites [s rules]
  (count (filter #(and (contains? rules (str %))
                       (vector? (get rules (str %))))
                 s)))

(defn- constrained-expand
  "Expands axiom with bounded rule alternatives.
  Tries full expansion first, backs off individual branches when bounds overflow."
  [axiom rules opts]
  (let [iterations (get opts :iterations 3)
        angle      (get opts :angle 22.5)
        length     (get opts :length 5.0)
        origin     (get opts :origin [200 400])
        heading    (get opts :heading -90)
        bounds     (:bounds opts)
        seed       (get opts :seed 42)
        max-alts   (apply max 1 (map (fn [v] (if (vector? v) (count v) 1)) (vals rules)))
        rng        (prob/make-rng seed)]
    (loop [current axiom, iter 0]
      (if (>= iter iterations)
        current
        (let [n-sites (count-sites current rules)]
          (if (zero? n-sites)
            current
            (let [base     (vec (repeat n-sites 0))
                  base-exp (expand-with-choices current rules base)]
              (if (or (nil? bounds)
                      (within-bounds? base-exp angle length origin heading bounds))
                (recur base-exp (inc iter))
                (let [attempts   (min 200 (long (Math/pow max-alts (min n-sites 4))))
                      candidates (mapv (fn [_]
                                         (mapv (fn [_]
                                                 (if (< (.nextDouble ^java.util.Random rng) 0.5)
                                                   0
                                                   (.nextInt ^java.util.Random rng max-alts)))
                                               (range n-sites)))
                                       (range attempts))
                      sorted     (sort-by #(reduce + %) candidates)
                      valid      (first
                                   (filter
                                     (fn [choices]
                                       (let [exp (expand-with-choices current rules choices)]
                                         (within-bounds? exp angle length origin heading bounds)))
                                     sorted))]
                  (if valid
                    (recur (expand-with-choices current rules valid) (inc iter))
                    current))))))))))

;; --- scale-to-fit ---

(defn- scale-commands [cmds target-bounds]
  (let [[tx ty tw th] target-bounds
        margin (* (double (min tw th)) 0.05)
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
          (let [dx (+ (double tx) margin)
                dy (+ (double ty) margin)
                dw (- (double tw) (* 2.0 margin))
                dh (- (double th) (* 2.0 margin))
                scale (min (/ dw src-w) (/ dh src-h))
                cx (+ dx (/ (- dw (* src-w scale)) 2.0))
                cy (+ dy (/ (- dh (* src-h scale)) 2.0))
                xform (fn [[x y]]
                        [(+ cx (* (- (double x) min-x) scale))
                         (+ cy (* (- (double y) min-y) scale))])]
            (mapv (fn [[cmd & args]]
                    (case cmd
                      :move-to [:move-to (xform (first args))]
                      :line-to [:line-to (xform (first args))]
                      [cmd]))
                  cmds)))))))

;; --- main pipeline ---

(defn lsystem->path-cmds
  "Full pipeline: expand axiom with rules, then interpret via turtle.
  Returns path commands.

  opts:
    :iterations — number of expansion steps (default 3)
    :angle      — turn angle in degrees
    :length     — step length
    :origin     — [x y] start position (default [0 0])
    :heading    — initial direction in degrees (default 0)

  When rules contain vector alternatives (e.g. {\"F\" [\"FF+F\" \"F\" \"\"]}):
    :bounds [x y w h] — canvas constraint; growth stays within.
                         Result is scaled to fill the bounds.
    :seed (42)        — controls which rule choices are made when
                         the full expansion overflows bounds."
  [axiom rules opts]
  (if (has-alternatives? rules)
    ;; Constrained mode: search for valid expansion, then scale to fit
    (let [expanded (constrained-expand axiom rules opts)
          cmds     (interpret expanded opts)]
      (if-let [bounds (:bounds opts)]
        (scale-commands cmds bounds)
        cmds))
    ;; Classic mode: simple expand + interpret
    (let [expanded (expand-string axiom rules (get opts :iterations 3))]
      (interpret expanded opts))))

(comment
  ;; Classic L-system (unchanged)
  (lsystem->path-cmds "F" {"F" "FF+[+F-F-F]-[-F+F+F]"}
    {:iterations 3 :angle 22.5 :length 4.0 :origin [200 400] :heading -90.0})

  ;; Constrained with preset — fills 400x400 canvas
  (lsystem->path-cmds "F" bush
    {:iterations 4 :angle 22.5 :length 5.0
     :origin [200 380] :heading -90
     :bounds [0 0 400 400] :seed 42})

  ;; All presets
  (doseq [[name rules] [["bush" bush] ["fern" fern] ["coral" coral]
                         ["lightning" lightning] ["seaweed" seaweed] ["tree" tree]]]
    (let [cmds (lsystem->path-cmds "F" rules
                 {:iterations 4 :angle 22.5 :length 5
                  :origin [200 380] :heading -90
                  :bounds [0 0 400 400] :seed 42})]
      (println name ":" (count cmds) "commands")))
  )
