(ns eido.gen.coloring
  "Graph coloring via constraint satisfaction.
  Assigns colors to regions (Voronoi cells, subdivision rects, or any
  indexed set with adjacency) such that no two adjacent regions share
  a color. Uses core.logic CLP(FD) for the constraint solver.

  Key creative controls:
    :pin       — fix specific regions to specific colors
    :weights   — prefer certain colors globally
    :weight-fn — vary color preference spatially per region"
  (:require
    [clojure.core.logic :as l]
    [clojure.core.logic.fd :as fd]
    [eido.gen.prob :as prob]))

;; --- adjacency helpers ---

(defn- vertices-from-path [path-node]
  (keep (fn [[cmd & args]]
          (when (#{:move-to :line-to} cmd) (first args)))
        (:path/commands path-node)))

(defn- close-enough? [[x1 y1] [x2 y2]]
  (let [dx (- (double x1) (double x2))
        dy (- (double y1) (double y2))]
    (< (+ (* dx dx) (* dy dy)) 0.25)))

(defn cells-adjacency
  "Computes adjacency from path-based regions (Voronoi cells, polygons).
  Two regions are adjacent if they share 2+ vertices (within epsilon).
  Returns a set of [i j] pairs (i < j)."
  [cells]
  (let [n     (count cells)
        verts (mapv #(vec (vertices-from-path %)) cells)]
    (into #{}
      (keep (fn [[i j]]
              (let [va (nth verts i)
                    vb (nth verts j)]
                (when (>= (count (filter (fn [pa]
                                           (some #(close-enough? pa %) vb))
                                         va))
                          2)
                  [i j]))))
      (for [i (range n) j (range (inc i) n)] [i j]))))

(defn rects-adjacency
  "Computes adjacency from subdivision rects.
  rects: vector of {:rect [x y w h]} maps.
  Returns a set of [i j] pairs (i < j)."
  [rects]
  (let [n   (count rects)
        eps 1.0]
    (into #{}
      (keep
        (fn [[i j]]
          (let [[x1 y1 w1 h1] (:rect (nth rects i))
                [x2 y2 w2 h2] (:rect (nth rects j))
                x1 (double x1) y1 (double y1) w1 (double w1) h1 (double h1)
                x2 (double x2) y2 (double y2) w2 (double w2) h2 (double h2)
                x-overlap? (and (< x1 (+ x2 w2)) (< x2 (+ x1 w1)))
                y-overlap? (and (< y1 (+ y2 h2)) (< y2 (+ y1 h1)))
                x-touch?   (or (< (Math/abs (- (+ x1 w1) x2)) eps)
                               (< (Math/abs (- (+ x2 w2) x1)) eps))
                y-touch?   (or (< (Math/abs (- (+ y1 h1) y2)) eps)
                               (< (Math/abs (- (+ y2 h2) y1)) eps))]
            (when (or (and x-overlap? y-touch?)
                      (and y-overlap? x-touch?))
              [i j]))))
      (for [i (range n) j (range (inc i) n)] [i j]))))

;; --- constraint solver ---

(defn solve
  "Solves a graph coloring problem via CLP(FD).
  n-regions:  number of regions to color
  adjacency:  collection of [i j] pairs
  n-colors:   number of available colors
  opts:
    :pin {}  — map of region-idx -> color-idx (fix specific regions)
  Returns a vector of color indices (0-based), or nil if unsatisfiable."
  ([n-regions adjacency n-colors] (solve n-regions adjacency n-colors {}))
  ([n-regions adjacency n-colors opts]
   (when (pos? n-regions)
     (if (and (empty? adjacency) (empty? (:pin opts)))
       (vec (repeat n-regions 0))
       (let [vars   (vec (repeatedly n-regions l/lvar))
             domain (fd/interval 0 (dec n-colors))
             pins   (get opts :pin {})]
         (first
           (l/run 1 [q]
             (l/== q vars)
             (l/everyg #(fd/in % domain) vars)
             ;; Pin constraints
             (l/everyg (fn [[idx color-idx]]
                         (fd/== (nth vars idx) color-idx))
                       pins)
             ;; Adjacency constraints
             (l/everyg (fn [[i j]]
                         (fd/!= (nth vars i) (nth vars j)))
                       (seq adjacency)))))))))

;; --- scene node conversion ---

(defn- weighted-pin-map
  "Computes pin map from weight-fn: for each region, call weight-fn
  and pin the region to its highest-weighted color index."
  [n-regions nodes weight-fn pin-ratio seed]
  (let [prefs   (mapv (fn [i]
                         (let [weights (weight-fn i (nth nodes i))]
                           (when (seq weights)
                             (key (apply max-key val weights)))))
                       (range n-regions))
        n-pins  (long (* n-regions (double pin-ratio)))
        indices (prob/shuffle-seeded (vec (range n-regions)) seed)]
    (into {}
      (keep (fn [i]
              (when-let [pref (nth prefs i)]
                [i pref])))
      (take n-pins indices))))

(defn color-regions
  "Applies graph coloring to scene nodes.
  nodes:     vector of scene nodes
  adjacency: collection of [i j] pairs
  palette:   vector of colors
  opts:
    :seed      (0)    — shuffles palette mapping for variety
    :pin       {}     — map of region-idx -> color (fix specific regions)
    :weight-fn nil    — (fn [region-idx node] {color-idx weight})
                        spatial color preference per region
    :pin-ratio (0.3)  — fraction of regions to pin when using weight-fn
  Returns nodes with :style/fill applied, or nil if no valid coloring exists."
  ([nodes adjacency palette] (color-regions nodes adjacency palette {}))
  ([nodes adjacency palette opts]
   (let [n          (count nodes)
         nc         (count palette)
         seed       (get opts :seed 0)
         ;; Shuffle palette first — all pin logic works in shuffled space
         shuffled   (prob/shuffle-seeded palette seed)
         color->idx (zipmap shuffled (range))
         ;; Explicit pins: map colors to indices in shuffled palette
         explicit-pins (get opts :pin {})
         idx-pins   (into {}
                      (map (fn [[region-idx color]]
                             [region-idx (get color->idx color 0)]))
                      explicit-pins)
         ;; Weight-fn pins (already in color-idx space)
         weight-fn  (:weight-fn opts)
         pin-ratio  (get opts :pin-ratio 0.3)
         wf-pins    (when weight-fn
                      (weighted-pin-map n nodes weight-fn pin-ratio seed))
         all-pins   (merge wf-pins idx-pins)
         ;; Solve
         assignment (solve n adjacency nc {:pin all-pins})]
     (when assignment
       (mapv (fn [node color-idx]
               (assoc node :style/fill (nth shuffled (long color-idx))))
             nodes assignment)))))

(comment
  ;; Basic coloring
  (solve 4 #{[0 1] [1 2] [2 3] [0 3]} 3)

  ;; With pinning
  (solve 4 #{[0 1] [1 2] [2 3] [0 3]} 3 {:pin {0 2, 2 1}})

  ;; Weight-fn example: prefer warm colors in the center
  (let [weight-fn (fn [i node]
                    (let [[x y] (or (:circle/center node) [200 200])
                          dist (Math/sqrt (+ (* (- x 200) (- x 200))
                                             (* (- y 200) (- y 200))))]
                      (if (< dist 100)
                        {0 3.0 1 1.0 2 1.0 3 1.0}   ;; prefer color 0 near center
                        {0 1.0 1 1.0 2 3.0 3 1.0})))] ;; prefer color 2 at edges
    weight-fn)
  )
