(ns eido.gen.voronoi
  "Voronoi diagram and Delaunay triangulation from seed points.
  Produces cell polygons and dual-graph edges for territorial partitions.")

;; --- half-plane clipping ---

(defn- dot ^double [[x1 y1] [x2 y2]]
  (+ (* (double x1) (double x2)) (* (double y1) (double y2))))

(defn- vsub [[x1 y1] [x2 y2]]
  [(- (double x1) (double x2)) (- (double y1) (double y2))])

(defn- vadd [[x1 y1] [x2 y2]]
  [(+ (double x1) (double x2)) (+ (double y1) (double y2))])

(defn- vscale [[x y] ^double s]
  [(* (double x) s) (* (double y) s)])

(defn- midpoint [a b]
  (vscale (vadd a b) 0.5))

(defn- clip-polygon
  "Clips polygon by the half-plane closer to point a than to point b.
  Uses the perpendicular bisector of a and b as the clipping line."
  [polygon a b]
  (let [mid (midpoint a b)
        ;; Normal pointing from b toward a
        normal (vsub a b)
        ;; For each vertex, compute signed distance to the bisector line
        signed-dist (fn [p] (dot (vsub p mid) normal))]
    (loop [i 0 result []]
      (if (>= i (count polygon))
        result
        (let [curr (nth polygon i)
              next-v (nth polygon (mod (inc i) (count polygon)))
              d-curr (signed-dist curr)
              d-next (signed-dist next-v)]
          (recur (inc i)
                 (cond
                   ;; Both inside
                   (and (>= d-curr 0) (>= d-next 0))
                   (conj result next-v)
                   ;; Going from inside to outside
                   (and (>= d-curr 0) (< d-next 0))
                   (let [t (/ d-curr (- d-curr d-next))]
                     (conj result (vadd curr (vscale (vsub next-v curr) t))))
                   ;; Going from outside to inside
                   (and (< d-curr 0) (>= d-next 0))
                   (let [t (/ d-curr (- d-curr d-next))
                         intersection (vadd curr (vscale (vsub next-v curr) t))]
                     (conj result intersection next-v))
                   ;; Both outside
                   :else result)))))))

;; --- Voronoi cells ---

(defn voronoi-cells
  "Generates Voronoi cell polygons from seed points.
  bounds: [x y w h] clipping region.
  Returns a vector of :shape/path nodes (closed polygons), one per point.
  Uses half-plane intersection: O(n^2) per cell."
  [points bounds]
  (let [[bx by bw bh] bounds
        bx (double bx) by (double by)
        bw (double bw) bh (double bh)
        bbox [[bx by]
              [(+ bx bw) by]
              [(+ bx bw) (+ by bh)]
              [bx (+ by bh)]]]
    (mapv (fn [i]
            (let [pi (nth points i)
                  cell (reduce (fn [poly j]
                                 (if (= i j)
                                   poly
                                   (clip-polygon poly pi (nth points j))))
                               bbox
                               (range (count points)))]
              {:node/type     :shape/path
               :path/commands (if (seq cell)
                                (into [[:move-to (first cell)]]
                                      (conj (mapv (fn [p] [:line-to p]) (rest cell))
                                            [:close]))
                                [[:move-to [0 0]] [:close]])}))
          (range (count points)))))

;; --- Delaunay edges ---

(defn- cells-share-edge?
  "Returns true if two Voronoi cells share an edge (have 2+ close vertices)."
  [cell-a cell-b]
  (let [eps 0.5
        pts-a (keep (fn [[cmd & args]]
                      (when (#{:move-to :line-to} cmd) (first args)))
                    (:path/commands cell-a))
        pts-b (keep (fn [[cmd & args]]
                      (when (#{:move-to :line-to} cmd) (first args)))
                    (:path/commands cell-b))]
    (>= (count
          (filter (fn [pa]
                    (some (fn [pb]
                            (< (Math/sqrt (+ (* (- (double (pa 0)) (double (pb 0)))
                                                (- (double (pa 0)) (double (pb 0))))
                                             (* (- (double (pa 1)) (double (pb 1)))
                                                (- (double (pa 1)) (double (pb 1))))))
                               eps))
                          pts-b))
                  pts-a))
        2)))

(defn delaunay-edges
  "Generates Delaunay triangulation edges as line nodes.
  bounds: [x y w h] clipping region.
  Derived from Voronoi: two seeds are neighbors if their cells share an edge."
  [points bounds]
  (let [cells (voronoi-cells points bounds)
        n     (count points)]
    (into []
          (keep (fn [[i j]]
                  (when (cells-share-edge? (nth cells i) (nth cells j))
                    (let [[x1 y1] (nth points i)
                          [x2 y2] (nth points j)]
                      {:node/type :shape/line
                       :line/from [x1 y1]
                       :line/to   [x2 y2]}))))
          (for [i (range n) j (range (inc i) n)] [i j]))))

(comment
  (voronoi-cells [[50 50] [150 50] [100 150]] [0 0 200 200])
  (delaunay-edges [[50 50] [150 50] [100 150]] [0 0 200 200])
  )
