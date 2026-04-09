(ns eido.gen.stipple)

;; --- Poisson disk sampling ---

(defn poisson-disk
  "Generates well-distributed points via Poisson disk sampling.
  Returns a vector of [x y] within [bx, bx+bw] x [by, by+bh].
  min-dist: minimum distance between points. seed: for determinism."
  [bx by bw bh min-dist seed]
  (let [rng        (java.util.Random. (long seed))
        cell-size  (/ (double min-dist) (Math/sqrt 2.0))
        grid-w     (int (Math/ceil (/ bw cell-size)))
        grid-h     (int (Math/ceil (/ bh cell-size)))
        grid       (object-array (* grid-w grid-h))
        k          30
        bx         (double bx)
        by         (double by)
        bw         (double bw)
        bh         (double bh)
        min-dist   (double min-dist)
        min-dist-sq (* min-dist min-dist)]
    (letfn [(grid-idx [x y]
              (let [gx (int (/ (- x bx) cell-size))
                    gy (int (/ (- y by) cell-size))]
                (when (and (>= gx 0) (< gx grid-w) (>= gy 0) (< gy grid-h))
                  (+ (* gy grid-w) gx))))
            (valid? [x y]
              (when (and (>= x bx) (<= x (+ bx bw))
                         (>= y by) (<= y (+ by bh)))
                (let [gx (int (/ (- x bx) cell-size))
                      gy (int (/ (- y by) cell-size))]
                  (loop [dx -2]
                    (if (> dx 2)
                      true
                      (if (loop [dy -2]
                            (if (> dy 2)
                              true
                              (let [nx (+ gx dx)
                                    ny (+ gy dy)]
                                (if (and (>= nx 0) (< nx grid-w)
                                         (>= ny 0) (< ny grid-h))
                                  (if-let [p (aget grid (+ (* ny grid-w) nx))]
                                    (let [[px py] p
                                          ddx (- x px)
                                          ddy (- y py)]
                                      (if (< (+ (* ddx ddx) (* ddy ddy)) min-dist-sq)
                                        false
                                        (recur (inc dy))))
                                    (recur (inc dy)))
                                  (recur (inc dy))))))
                        (recur (inc dx))
                        false))))))]
      (let [x0 (+ bx (* (.nextDouble rng) bw))
            y0 (+ by (* (.nextDouble rng) bh))]
        (when-let [idx (grid-idx x0 y0)]
          (aset grid idx [x0 y0]))
        ;; Use ArrayList for O(1) random removal (swap with last, removeLast)
        (let [active (java.util.ArrayList. ^java.util.Collection (vector [x0 y0]))]
          (loop [points [[x0 y0]]]
            (if (.isEmpty active)
              points
              (let [ri     (.nextInt rng (.size active))
                    [ax ay] (.get active ri)
                    result (loop [j 0 found nil]
                             (if (or found (>= j k))
                               found
                               (let [angle (* 2.0 Math/PI (.nextDouble rng))
                                     r     (+ min-dist (* (.nextDouble rng) min-dist))
                                     nx    (+ ax (* r (Math/cos angle)))
                                     ny    (+ ay (* r (Math/sin angle)))]
                                 (if (valid? nx ny)
                                   (recur (inc j) [nx ny])
                                   (recur (inc j) nil)))))]
                (if result
                  (let [[nx ny] result]
                    (when-let [idx (grid-idx nx ny)]
                      (aset grid idx [nx ny]))
                    (.add active result)
                    (recur (conj points result)))
                  ;; Swap with last for O(1) removal
                  (let [last-idx (dec (.size active))]
                    (when (not= ri last-idx)
                      (.set active ri (.get active last-idx)))
                    (.remove active last-idx)
                    (recur points)))))))))))

;; --- stipple fill expansion ---

(defn stipple-fill->nodes
  "Generates circle nodes for a stipple fill within bounds.
  spec: :stipple/density (0-1), :stipple/radius, :stipple/seed,
        :stipple/color."
  [bx by bw bh spec]
  (let [density (get spec :stipple/density 0.5)
        radius  (get spec :stipple/radius 1.0)
        seed    (get spec :stipple/seed 0)
        color   (get spec :stipple/color [:color/rgb 0 0 0])
        ;; Scale min-dist inversely with density
        min-dist (* (double radius) 3.0 (- 1.5 (min 1.0 (double density))))
        pts     (poisson-disk bx by bw bh min-dist seed)]
    (mapv (fn [[x y]]
            {:node/type      :shape/circle
             :circle/center  [x y]
             :circle/radius  radius
             :style/fill     color})
          pts)))

(comment
  (count (poisson-disk 0 0 100 100 5 42))
  (stipple-fill->nodes 0 0 100 100 {:stipple/density 0.5 :stipple/radius 1 :stipple/seed 42})
  )
