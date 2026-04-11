(ns eido.gen.coloring
  "Graph coloring via constraint satisfaction.
  Assigns colors to regions (Voronoi cells, subdivision rects, or any
  indexed set with adjacency) such that no two adjacent regions share
  a color. Uses core.logic CLP(FD) for the constraint solver."
  (:require
    [clojure.core.logic :as l]
    [clojure.core.logic.fd :as fd]
    [eido.gen.prob :as prob]))

;; --- adjacency helpers ---

(defn- vertices-from-path
  "Extracts vertex coordinates from a :shape/path node."
  [path-node]
  (keep (fn [[cmd & args]]
          (when (#{:move-to :line-to} cmd) (first args)))
        (:path/commands path-node)))

(defn- close-enough?
  "Returns true if two 2D points are within epsilon distance."
  [[x1 y1] [x2 y2]]
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
  Two rects are adjacent if they share an edge (overlap on one axis,
  coincide on the other within epsilon).
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
                x1 (double x1) y1 (double y1)
                w1 (double w1) h1 (double h1)
                x2 (double x2) y2 (double y2)
                w2 (double w2) h2 (double h2)
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
  adjacency:  collection of [i j] pairs (undirected edges, i < j)
  n-colors:   number of available colors
  Returns a vector of color indices (0-based), or nil if unsatisfiable."
  [n-regions adjacency n-colors]
  (when (pos? n-regions)
    (if (empty? adjacency)
      (vec (repeat n-regions 0))
      (let [vars   (vec (repeatedly n-regions l/lvar))
            domain (fd/interval 0 (dec n-colors))]
        (first
          (l/run 1 [q]
            (l/== q vars)
            (l/everyg #(fd/in % domain) vars)
            (l/everyg (fn [[i j]]
                        (fd/!= (nth vars i) (nth vars j)))
                      (seq adjacency))))))))

;; --- scene node conversion ---

(defn color-regions
  "Applies graph coloring to scene nodes.
  nodes:     vector of scene nodes (any type with map structure)
  adjacency: collection of [i j] pairs
  palette:   vector of colors
  opts:
    :seed (0) — shuffles palette mapping for visual variety
  Returns nodes with :style/fill applied, or nil if no valid coloring exists."
  ([nodes adjacency palette] (color-regions nodes adjacency palette {}))
  ([nodes adjacency palette opts]
   (let [n          (count nodes)
         nc         (count palette)
         assignment (solve n adjacency nc)]
     (when assignment
       (let [seed     (get opts :seed 0)
             shuffled (prob/shuffle-seeded palette seed)]
         (mapv (fn [node color-idx]
                 (assoc node :style/fill (nth shuffled (long color-idx))))
               nodes assignment))))))

(comment
  ;; 4 regions in a square, each adjacent to 2 neighbors
  (solve 4 #{[0 1] [1 2] [2 3] [0 3]} 3)
  ;; => [0 1 0 1]

  ;; Triangle needs 3 colors
  (solve 3 #{[0 1] [1 2] [0 2]} 2)
  ;; => nil (2 colors insufficient for K3)

  (solve 3 #{[0 1] [1 2] [0 2]} 3)
  ;; => [0 1 2]

  ;; Voronoi + coloring
  (require '[eido.gen.voronoi :as voronoi])
  (let [points [[50 50] [150 50] [100 150] [50 150] [150 150]]
        bounds [0 0 200 200]
        cells  (voronoi/voronoi-cells points bounds)
        adj    (cells-adjacency cells)
        palette [[:color/rgb 230 80 80]
                 [:color/rgb 80 180 80]
                 [:color/rgb 80 80 230]
                 [:color/rgb 230 200 60]]]
    (color-regions cells adj palette {:seed 42}))
  )
