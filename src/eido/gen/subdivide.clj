(ns eido.gen.subdivide
  "Recursive rectangular subdivision for compositional layouts.
  Produces Mondrian-style grids and binary space partitions."
  (:require
    [eido.gen.prob :as prob]))

(defn- child-seed
  "Derives a deterministic child seed from parent seed, depth, and index."
  ^long [^long seed ^long depth ^long index]
  (unchecked-add
    (unchecked-multiply seed 31)
    (unchecked-add (unchecked-multiply depth 7919) index)))

(defn- subdivide*
  "Recursive subdivision implementation."
  [x y w h depth max-depth min-size split-range h-bias padding ^java.util.Random rng]
  (let [x (double x) y (double y)
        w (double w) h (double h)
        pad (double padding)
        [split-lo split-hi] split-range]
    (if (or (>= depth max-depth)
            (and (< w (* 2.0 (double min-size)))
                 (< h (* 2.0 (double min-size)))))
      [{:rect [x y w h] :depth depth}]
      (let [;; Choose orientation: horizontal split if tall, vertical if wide,
            ;; otherwise use h-bias
            prefer-h (cond
                       (> h (* 2.0 w)) true
                       (> w (* 2.0 h)) false
                       :else (< (.nextDouble rng) (double h-bias)))
            ratio (+ (double split-lo)
                     (* (.nextDouble rng) (- (double split-hi) (double split-lo))))]
        (if prefer-h
          ;; Horizontal split: top and bottom
          (let [h1 (* h ratio)
                h2 (- h h1 pad)]
            (if (or (< h1 (double min-size)) (< h2 (double min-size)))
              [{:rect [x y w h] :depth depth}]
              (into (subdivide* x y w h1
                      (inc depth) max-depth min-size split-range h-bias padding rng)
                    (subdivide* x (+ y h1 pad) w h2
                      (inc depth) max-depth min-size split-range h-bias padding rng))))
          ;; Vertical split: left and right
          (let [w1 (* w ratio)
                w2 (- w w1 pad)]
            (if (or (< w1 (double min-size)) (< w2 (double min-size)))
              [{:rect [x y w h] :depth depth}]
              (into (subdivide* x y w1 h
                      (inc depth) max-depth min-size split-range h-bias padding rng)
                    (subdivide* (+ x w1 pad) y w2 h
                      (inc depth) max-depth min-size split-range h-bias padding rng)))))))))

(defn subdivide
  "Recursively subdivides a rectangle into smaller rectangles.
  Returns a vector of {:rect [x y w h] :depth n} maps.
  opts:
    :depth       (4)      — max recursion depth
    :min-size    (20.0)   — stop splitting below this dimension
    :split-range [0.3 0.7] — random split ratio range
    :h-bias      (0.5)    — probability of horizontal split (vs vertical)
    :padding     (0.0)    — gap between sub-rects
    :seed        (42)"
  [x y w h opts]
  (let [max-depth   (get opts :depth 4)
        min-size    (get opts :min-size 20.0)
        split-range (get opts :split-range [0.3 0.7])
        h-bias      (get opts :h-bias 0.5)
        padding     (get opts :padding 0.0)
        seed        (get opts :seed 42)
        rng         (prob/make-rng seed)]
    (subdivide* x y w h 0 max-depth min-size split-range h-bias padding rng)))

;; --- conversion to scene nodes ---

(defn subdivide->nodes
  "Converts subdivision output to rect scene nodes.
  color-fn: (fn [{:rect [x y w h] :depth n}] -> color)."
  [rects color-fn]
  (mapv (fn [{[x y w h] :rect :as cell}]
          {:node/type  :shape/rect
           :rect/xy    [x y]
           :rect/size  [w h]
           :style/fill (color-fn cell)})
        rects))

;; --- convenience helpers ---

(defn ^{:convenience true :convenience-for 'eido.gen.subdivide/subdivide->nodes}
  subdivide->palette-nodes
  "Subdivision rects to styled nodes with palette colors.
  Wraps (subdivide->nodes rects (fn [...] (prob/pick palette seed)))."
  ([rects palette seed]
   (subdivide->palette-nodes rects palette seed {}))
  ([rects palette seed style]
   (mapv (fn [{[x y w h] :rect :as cell}]
           (merge {:node/type :shape/rect :rect/xy [x y] :rect/size [w h]
                   :style/fill (prob/pick palette (+ (hash cell) (long seed)))}
                  style))
         rects)))

(comment
  (subdivide 0 0 400 400 {:seed 42})
  (subdivide 0 0 400 400 {:depth 3 :padding 4 :seed 42})
  (subdivide->nodes
    (subdivide 0 0 400 400 {:seed 42})
    (fn [{d :depth}] [:color/hsl (* d 60) 0.7 0.5])))
