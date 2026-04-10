(ns eido.gen.stipple
  "Poisson disk sampling and stipple fills.
  Generates well-distributed dot patterns for shading and texture.")

;; --- Poisson disk sampling ---

(defn- make-grid-ctx
  "Creates a Poisson disk grid context map."
  [bx by bw bh cell-size]
  (let [gw (int (Math/ceil (/ (double bw) (double cell-size))))
        gh (int (Math/ceil (/ (double bh) (double cell-size))))]
    {:grid      (object-array (* gw gh))
     :cell-size (double cell-size)
     :gw        gw
     :gh        gh
     :bx        (double bx)
     :by        (double by)
     :bw        (double bw)
     :bh        (double bh)}))

(defn- grid-index
  "Returns the flat grid index for a point, or nil if out of bounds."
  [{:keys [bx by cell-size gw gh]} x y]
  (let [gx (int (/ (- (double x) bx) cell-size))
        gy (int (/ (- (double y) by) cell-size))]
    (when (and (>= gx 0) (< gx (int gw))
               (>= gy 0) (< gy (int gh)))
      (+ (* gy (int gw)) gx))))

(defn- neighbor-too-close?
  "Returns true if any point in the grid within 2 cells of [gx,gy]
  is closer than min-dist-sq to [x,y]."
  [{:keys [^objects grid gw gh]} gx gy x y min-dist-sq]
  (let [gw (int gw) gh (int gh)
        gx (int gx) gy (int gy)
        x  (double x) y (double y)
        min-dist-sq (double min-dist-sq)]
    (boolean
      (some (fn [[dx dy]]
              (let [nx (+ gx dx)
                    ny (+ gy dy)]
                (when (and (>= nx 0) (< nx gw)
                           (>= ny 0) (< ny gh))
                  (when-let [p (aget grid (+ (* ny gw) nx))]
                    (let [[px py] p
                          ddx (- x (double px))
                          ddy (- y (double py))]
                      (< (+ (* ddx ddx) (* ddy ddy)) min-dist-sq))))))
            (for [dx (range -2 3) dy (range -2 3)] [dx dy])))))

(defn- valid-candidate?
  "Returns true if point [x,y] is within bounds and not too close
  to any existing point in the grid."
  [{:keys [bx by bw bh cell-size] :as ctx} x y min-dist-sq]
  (let [x (double x) y (double y)]
    (and (>= x bx) (<= x (+ bx bw))
         (>= y by) (<= y (+ by bh))
         (let [gx (int (/ (- x bx) cell-size))
               gy (int (/ (- y by) cell-size))]
           (not (neighbor-too-close? ctx gx gy x y min-dist-sq))))))

(defn poisson-disk
  "Generates well-distributed points via Poisson disk sampling.
  bounds: [x y w h] region. opts: :min-dist (required), :seed (default 0).
  Returns a vector of [x y] within the bounds."
  [bounds opts]
  (let [[bx by bw bh] bounds
        seed        (get opts :seed 0)
        rng         (java.util.Random. (long seed))
        min-dist    (double (:min-dist opts))
        min-dist-sq (* min-dist min-dist)
        cell-size   (/ min-dist (Math/sqrt 2.0))
        ctx         (make-grid-ctx bx by bw bh cell-size)
        ^objects grid (:grid ctx)
        k           30
        bx          (double bx)
        by          (double by)
        bw          (double bw)
        bh          (double bh)
        x0          (+ bx (* (.nextDouble rng) bw))
        y0          (+ by (* (.nextDouble rng) bh))]
    (when-let [idx (grid-index ctx x0 y0)]
      (aset grid idx [x0 y0]))
    ;; Use ArrayList for O(1) random removal (swap with last, removeLast)
    (let [active (java.util.ArrayList. ^java.util.Collection (vector [x0 y0]))]
      (loop [points [[x0 y0]]]
        (if (.isEmpty active)
          points
          (let [ri      (.nextInt rng (.size active))
                [ax ay] (.get active ri)
                result  (loop [j 0 found nil]
                          (if (or found (>= j k))
                            found
                            (let [angle (* 2.0 Math/PI (.nextDouble rng))
                                  r     (+ min-dist (* (.nextDouble rng) min-dist))
                                  nx    (+ (double ax) (* r (Math/cos angle)))
                                  ny    (+ (double ay) (* r (Math/sin angle)))]
                              (if (valid-candidate? ctx nx ny min-dist-sq)
                                (recur (inc j) [nx ny])
                                (recur (inc j) nil)))))]
            (if result
              (let [[nx ny] result]
                (when-let [idx (grid-index ctx (double nx) (double ny))]
                  (aset grid idx [nx ny]))
                (.add active result)
                (recur (conj points result)))
              ;; Swap with last for O(1) removal
              (let [last-idx (dec (.size active))]
                (when (not= ri last-idx)
                  (.set active ri (.get active last-idx)))
                (.remove active last-idx)
                (recur points)))))))))

;; --- stipple fill expansion ---

(defn stipple-fill->nodes
  "Generates circle nodes for a stipple fill within bounds.
  bounds: [x y w h] region.
  spec: :stipple/density (0-1), :stipple/radius, :stipple/seed,
        :stipple/color."
  [bounds spec]
  (let [density (get spec :stipple/density 0.5)
        radius  (get spec :stipple/radius 1.0)
        seed    (get spec :stipple/seed 0)
        color   (get spec :stipple/color [:color/rgb 0 0 0])
        ;; Scale min-dist inversely with density
        min-dist (* (double radius) 3.0 (- 1.5 (min 1.0 (double density))))
        pts     (poisson-disk bounds {:min-dist min-dist :seed seed})]
    (mapv (fn [[x y]]
            {:node/type      :shape/circle
             :circle/center  [x y]
             :circle/radius  radius
             :style/fill     color})
          pts)))

(comment
  (count (poisson-disk [0 0 100 100] {:min-dist 5 :seed 42}))
  (stipple-fill->nodes [0 0 100 100] {:stipple/density 0.5 :stipple/radius 1 :stipple/seed 42})
  )
