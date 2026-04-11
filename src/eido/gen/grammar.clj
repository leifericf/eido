(ns eido.gen.grammar
  "Constrained shape grammars: L-system expansion with bounds checking.

  Standard L-systems expand blindly — they don't know about canvas
  boundaries or self-intersection. This generator adds constraint
  awareness: after each expansion step, the resulting geometry is
  checked against bounds. If it exceeds them, alternative production
  rules are tried via core.logic backtracking.

  The approach: define multiple alternative rules for each symbol.
  The solver searches for a combination of rule choices at each
  expansion step that keeps the final geometry within bounds."
  (:require
    [clojure.core.logic :as l]
    [clojure.core.logic.fd :as fd]
    [eido.gen.prob :as prob]))

;; --- geometry helpers ---

(defn- turtle-bounds
  "Computes the bounding box of turtle-graphics path commands.
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
            \] (let [[sx sy sd] (peek stack)]
                 (recur (rest chars) sx sy sd (pop stack)
                        min-x min-y max-x max-y))
            (recur (rest chars) x y dir stack
                   min-x min-y max-x max-y)))))))

(defn- within-bounds?
  "Checks if turtle path stays within [bx by bw bh]."
  [commands angle length origin heading bounds]
  (let [[bx by bw bh] bounds
        bx (double bx) by (double by)
        bw (double bw) bh (double bh)
        [min-x min-y max-x max-y]
        (turtle-bounds commands angle length origin heading)]
    (and (>= min-x bx) (>= min-y by)
         (<= max-x (+ bx bw)) (<= max-y (+ by bh)))))

;; --- constrained expansion ---

(defn- expand-with-choices
  "Expands a string using rule choices (one choice per expansion site).
  rules: map of character -> vector of alternative replacements.
  choices: vector of integers indexing into the alternatives.
  Returns the expanded string."
  [s rules choices]
  (let [choice-idx (atom 0)]
    (apply str
      (map (fn [c]
             (let [key (str c)
                   alts (get rules key)]
               (if alts
                 (let [i @choice-idx
                       choice (get choices i 0)]
                   (swap! choice-idx inc)
                   (get alts (min choice (dec (count alts)))))
                 (str c))))
           s))))

(defn- count-expansion-sites
  "Counts how many characters in s have alternative rules."
  [s rules]
  (count (filter #(contains? rules (str %)) s)))

(defn solve
  "Finds rule choices that keep an L-system within bounds.

  axiom:  starting string (e.g. \"F\")
  rules:  map of character -> vector of alternative replacements
          e.g. {\"F\" [\"FF+[+F-F]-[-F+F]\" \"F+F-F\" \"F\"]}
          The first alternative is the 'full' expansion; shorter
          alternatives allow the solver to constrain growth.
  opts:
    :iterations (3)     — number of expansion steps
    :angle (22.5)       — turtle turn angle in degrees
    :length (5.0)       — turtle step length
    :origin [200 400]   — starting position
    :heading (-90)      — initial direction in degrees
    :bounds [0 0 w h]   — bounding box the result must fit within
    :seed (42)          — controls which valid expansion is chosen

  Returns a string (the expanded L-system) that fits within bounds,
  or nil if no valid expansion exists within the search budget."
  [axiom rules opts]
  (let [iterations (get opts :iterations 3)
        angle      (get opts :angle 22.5)
        length     (get opts :length 5.0)
        origin     (get opts :origin [200 400])
        heading    (get opts :heading -90)
        bounds     (get opts :bounds [0 0 400 400])
        seed       (get opts :seed 42)
        ;; Max alternatives across all rules
        max-alts (apply max 1 (map count (vals rules)))]
    ;; Iterative expansion: at each step, choose which alternative
    ;; to use for each expansion site. Use core.logic to search for
    ;; choices that keep the result in bounds.
    (loop [current axiom
           iter    0
           rng     (prob/make-rng seed)]
      (if (>= iter iterations)
        current
        (let [n-sites (count-expansion-sites current rules)]
          (if (zero? n-sites)
            current
            ;; Try different choice combinations
            (let [;; Seed-shuffled choice order for variety
                  all-choices
                  (let [vars   (vec (repeatedly n-sites l/lvar))
                        domain (fd/interval 0 (dec max-alts))]
                    (l/run 50 [q]
                      (l/== q vars)
                      (l/everyg #(fd/in % domain) vars)))
                  ;; Shuffle and try each until one fits bounds
                  shuffled (prob/shuffle-seeded (vec all-choices) (.nextInt ^java.util.Random rng))
                  valid (first
                          (filter
                            (fn [choices]
                              (let [expanded (expand-with-choices current rules choices)]
                                (within-bounds? expanded angle length origin heading bounds)))
                            shuffled))]
              (if valid
                (recur (expand-with-choices current rules valid)
                       (inc iter)
                       rng)
                ;; No valid expansion found — return current state
                current))))))))

(defn grammar->path-cmds
  "Full constrained grammar pipeline: expand with bounds, then interpret.
  Returns path commands suitable for a :shape/path node.
  See `solve` for opts."
  [axiom rules opts]
  (let [expanded (solve axiom rules opts)
        angle-rad (* (double (get opts :angle 22.5)) (/ Math/PI 180.0))
        length    (double (get opts :length 5.0))
        origin    (get opts :origin [200 400])
        heading   (* (double (get opts :heading -90)) (/ Math/PI 180.0))
        [ox oy]   origin]
    (loop [chars (seq expanded)
           x     (double ox)
           y     (double oy)
           dir   heading
           stack []
           cmds  [[:move-to [ox oy]]]]
      (if-not (seq chars)
        cmds
        (let [c (first chars)]
          (case c
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
            \] (let [[sx sy sd] (peek stack)]
                 (recur (rest chars) sx sy sd (pop stack)
                        (conj cmds [:move-to [sx sy]])))
            (recur (rest chars) x y dir stack cmds)))))))

(comment
  ;; Bush that stays within 400x400 canvas
  (let [rules {"F" ["FF+[+F-F-F]-[-F+F+F]"  ;; full expansion
                     "F+[+F]-[-F]"             ;; medium
                     "F"]}                      ;; no growth (stay small)
        cmds (grammar->path-cmds "F" rules
               {:iterations 4 :angle 22.5 :length 5.0
                :origin [200 380] :heading -90
                :bounds [10 10 380 380] :seed 42})]
    {:node/type :shape/path :path/commands cmds})

  ;; Compare different seeds
  (doseq [s [1 2 3 42]]
    (let [rules {"F" ["FF+[+F-F]-[-F+F]" "F+F-F" "F"]}
          expanded (solve "F" rules
                     {:iterations 3 :angle 25 :length 8
                      :origin [200 380] :heading -90
                      :bounds [20 20 360 360] :seed s})]
      (println "Seed" s ":" (count expanded) "chars")))
  )
