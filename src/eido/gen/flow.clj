(ns eido.gen.flow
  "Flow field path generation from noise-based vector fields.
  Traces streamlines with optional collision avoidance for even spacing."
  (:require
    [eido.gen.noise :as noise]))

;; --- spatial grid for collision detection ---

(defn- make-point-grid
  "Creates a spatial grid for point proximity queries."
  [cell-size bx by bw bh]
  (let [cs (double cell-size)
        gw (max 1 (long (Math/ceil (/ (double bw) cs))))
        gh (max 1 (long (Math/ceil (/ (double bh) cs))))]
    {:cells     (object-array (* gw gh))
     :cell-size cs
     :gw        gw
     :gh        gh
     :bx        (double bx)
     :by        (double by)}))

(defn- grid-add-point!
  "Adds a point to the spatial grid."
  [grid x y]
  (let [^objects cells (:cells grid)
        cs (double (:cell-size grid))
        gw (long (:gw grid))
        gh (long (:gh grid))
        bx (double (:bx grid))
        by (double (:by grid))
        gx (min (dec gw) (max 0 (long (/ (- (double x) bx) cs))))
        gy (min (dec gh) (max 0 (long (/ (- (double y) by) cs))))
        idx (int (+ gx (* gy gw)))
        existing (aget cells idx)]
    (aset cells idx (if existing (conj existing [x y]) [[x y]]))))

(defn- grid-has-nearby?
  "Returns true if any stored point is within dist of (x, y)."
  [grid x y dist]
  (let [^objects cells (:cells grid)
        cs (double (:cell-size grid))
        gw (long (:gw grid))
        gh (long (:gh grid))
        bx (double (:bx grid))
        by (double (:by grid))
        x (double x) y (double y) dist (double dist)
        dist-sq (* dist dist)
        rc (long (Math/ceil (/ dist cs)))
        cx (long (/ (- x bx) cs))
        cy (long (/ (- y by) cs))
        min-gx (max 0 (- cx rc))
        max-gx (min (dec gw) (+ cx rc))
        min-gy (max 0 (- cy rc))
        max-gy (min (dec gh) (+ cy rc))]
    (boolean
      (some (fn [[gx gy]]
              (let [pts (aget cells (int (+ gx (* gy gw))))]
                (when pts
                  (some (fn [[px py]]
                          (let [ddx (- x (double px))
                                ddy (- y (double py))]
                            (<= (+ (* ddx ddx) (* ddy ddy)) dist-sq)))
                        pts))))
            (for [gy (range min-gy (inc max-gy))
                  gx (range min-gx (inc max-gx))]
              [gx gy])))))

;; --- streamline tracing ---

(defn- trace-streamline
  "Traces a single streamline from a starting point.
  bounds: [bx by bw bh] region.
  collision: {:grid g :dist d} or nil for no collision avoidance.
  Returns a vector of [x y] points, or nil if too short."
  [x0 y0 [bx by bw bh] collision
   {:keys [step-length steps noise-scale seed min-length]
    :or   {step-length 2.0 steps 50 noise-scale 0.005
           seed 0 min-length 3}}]
  (let [bx  (double bx)
        by  (double by)
        bw  (double bw)
        bh  (double bh)
        sl  (double step-length)
        ns  (double noise-scale)
        opts (when seed {:seed seed})
        grid (:grid collision)
        cd   (when-let [d (:dist collision)] (double d))]
    (loop [x   (double x0)
           y   (double y0)
           i   0
           pts [[x0 y0]]]
      (if (>= i steps)
        (when (>= (count pts) min-length) pts)
        (let [angle (* 2.0 Math/PI (noise/perlin2d (* x ns) (* y ns) opts))
              nx    (+ x (* sl (Math/cos angle)))
              ny    (+ y (* sl (Math/sin angle)))]
          (cond
            (or (< nx bx) (> nx (+ bx bw))
                (< ny by) (> ny (+ by bh)))
            (when (>= (count pts) min-length) pts)

            (and grid cd (grid-has-nearby? grid nx ny cd))
            (when (>= (count pts) min-length) pts)

            :else
            (recur nx ny (inc i) (conj pts [nx ny]))))))))

(defn flow-field
  "Generates path nodes from a noise-based flow field within bounds.
  bounds: [x y w h] region.
  Returns a vector of :shape/path nodes (streamlines).
  opts: :density (20), :step-length (2.0), :steps (50),
        :noise-scale (0.005), :seed (0), :min-length (3),
        :collision-distance (nil — set to enforce minimum spacing between streamlines)."
  [bounds opts]
  (let [[bx by bw bh] bounds
        density    (get opts :density 20)]
    (if-not (and (pos? bw) (pos? bh) (pos? density)
                  (pos? (get opts :step-length 2.0)))
      []
      (let [col-dist   (:collision-distance opts)
            bx         (double bx)
            by         (double by)
            bw         (double bw)
            bh         (double bh)
        cols       (long (Math/ceil (/ bw density)))
        rows       (long (Math/ceil (/ bh density)))
        half-d     (/ (double density) 2.0)
        grid       (when col-dist (make-point-grid (double col-dist) bx by bw bh))
        bounds     [bx by bw bh]
        collision  (when grid {:grid grid :dist col-dist})
        origins    (for [row (range rows) col (range cols)]
                     [(+ bx (* col (double density)) half-d)
                      (+ by (* row (double density)) half-d)])]
    (into []
          (keep (fn [[ox oy]]
                  (let [pts (trace-streamline ox oy bounds collision opts)]
                    (when pts
                      (when grid
                        (doseq [[px py] pts]
                          (grid-add-point! grid px py)))
                      {:node/type     :shape/path
                       :path/commands (into [[:move-to (first pts)]]
                                            (mapv (fn [p] [:line-to p])
                                                  (rest pts)))}))))
          origins)))))

(comment
  (flow-field [0 0 200 200] {:density 20 :steps 40 :noise-scale 0.005 :seed 42})
  (flow-field [0 0 200 200] {:density 20 :steps 40 :seed 42 :collision-distance 8.0})
  )
