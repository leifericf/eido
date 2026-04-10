(ns eido.path
  "Path operations: boolean ops (union, intersection, difference, xor)
  plus re-exports from sub-namespaces:
    eido.path.stroke   — stroke profiles & expansion
    eido.path.distort  — noise/wave distortion
    eido.path.warp     — twist/fisheye/bend/bulge
    eido.path.morph    — path interpolation
    eido.path.decorate — path decoration"
  (:require
    [eido.path.aesthetic :as aesthetic]
    [eido.path.decorate :as decorate]
    [eido.path.distort :as distort]
    [eido.path.morph :as morph]
    [eido.path.stroke :as stroke]
    [eido.path.warp :as warp]
    [eido.text :as text])
  (:import
    [java.awt.geom Area GeneralPath PathIterator]))

;; --- path commands ↔ Java 2D ---

(defn- commands->general-path
  "Builds a GeneralPath from scene-format path commands."
  ^GeneralPath [commands]
  (let [p (GeneralPath.)]
    (doseq [[cmd & args] commands]
      (case cmd
        :move-to  (let [[x y] (first args)]
                    (.moveTo p (double x) (double y)))
        :line-to  (let [[x y] (first args)]
                    (.lineTo p (double x) (double y)))
        :quad-to  (let [[cx cy] (first args)
                        [x y]   (second args)]
                    (.quadTo p (double cx) (double cy)
                               (double x) (double y)))
        :curve-to (let [[c1x c1y] (first args)
                        [c2x c2y] (second args)
                        [x y]     (nth args 2)]
                    (.curveTo p (double c1x) (double c1y)
                                (double c2x) (double c2y)
                                (double x) (double y)))
        :close    (.closePath p)))
    p))

(defn- area->commands
  "Converts a Java 2D Area back to scene-format path commands."
  [^Area area]
  (let [iter (.getPathIterator area nil)
        coords (double-array 6)]
    (loop [cmds []]
      (if (.isDone iter)
        cmds
        (let [seg (.currentSegment iter coords)]
          (.next iter)
          (recur
            (conj cmds
              (case (int seg)
                0 [:move-to [(aget coords 0) (aget coords 1)]]
                1 [:line-to [(aget coords 0) (aget coords 1)]]
                2 [:quad-to [(aget coords 0) (aget coords 1)]
                            [(aget coords 2) (aget coords 3)]]
                3 [:curve-to [(aget coords 0) (aget coords 1)]
                             [(aget coords 2) (aget coords 3)]
                             [(aget coords 4) (aget coords 5)]]
                4 [:close]))))))))

;; --- boolean operations ---

(defn- boolean-op
  "Performs a boolean operation on two path command sequences."
  [cmds-a cmds-b op-fn]
  (let [a (Area. (commands->general-path cmds-a))
        b (Area. (commands->general-path cmds-b))]
    (op-fn a b)
    (area->commands a)))

(defn union
  "Returns path commands for the union of two shapes."
  [cmds-a cmds-b]
  (boolean-op cmds-a cmds-b (fn [^Area a ^Area b] (.add a b))))

(defn intersection
  "Returns path commands for the intersection of two shapes."
  [cmds-a cmds-b]
  (boolean-op cmds-a cmds-b (fn [^Area a ^Area b] (.intersect a b))))

(defn difference
  "Returns path commands for shape A minus shape B."
  [cmds-a cmds-b]
  (boolean-op cmds-a cmds-b (fn [^Area a ^Area b] (.subtract a b))))

(defn xor
  "Returns path commands for the symmetric difference of two shapes."
  [cmds-a cmds-b]
  (boolean-op cmds-a cmds-b (fn [^Area a ^Area b] (.exclusiveOr a b))))

(comment
  (union [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]]
          [:line-to [100.0 100.0]] [:line-to [0.0 100.0]] [:close]]
         [[:move-to [50.0 50.0]] [:line-to [150.0 50.0]]
          [:line-to [150.0 150.0]] [:line-to [50.0 150.0]] [:close]])
  )

;; --- re-exports from sub-namespaces ---

(defmacro ^:private import-fn [target-sym]
  (let [local-name (symbol (name target-sym))]
    `(do (def ~local-name ~target-sym)
         (alter-meta! (var ~local-name) merge
           (dissoc (meta (var ~target-sym)) :name :ns)))))

;; stroke
(import-fn stroke/resolve-profile)
(import-fn stroke/width-at)
(import-fn stroke/outline-commands)

;; distort
(import-fn distort/distort-commands)

;; warp
(import-fn warp/warp-commands)
(import-fn warp/shape->path-commands)
(import-fn warp/warp-node)

;; morph
(import-fn morph/resample)
(import-fn morph/morph)
(import-fn morph/morph-auto)

;; decorate
(import-fn decorate/decorate-path)

;; aesthetic
(import-fn aesthetic/smooth-commands)
(import-fn aesthetic/jittered-commands)
(import-fn aesthetic/dash-commands)
(import-fn aesthetic/chaikin-commands)
(import-fn aesthetic/stylize)
(import-fn aesthetic/ink-preset)
(import-fn aesthetic/pencil-preset)
(import-fn aesthetic/watercolor-preset)

;; --- simplification (Douglas-Peucker) ---

(defn- point-to-line-distance
  "Perpendicular distance from point [px py] to line segment [[x1 y1] [x2 y2]]."
  ^double [[^double px ^double py] [^double x1 ^double y1] [^double x2 ^double y2]]
  (let [dx (- x2 x1) dy (- y2 y1)
        len-sq (+ (* dx dx) (* dy dy))]
    (if (zero? len-sq)
      (Math/sqrt (+ (Math/pow (- px x1) 2) (Math/pow (- py y1) 2)))
      (/ (Math/abs (- (* dy (- px x1)) (* dx (- py y1))))
         (Math/sqrt len-sq)))))

(defn simplify
  "Douglas-Peucker path simplification on [[x y] ...] point vectors.
  Removes points that contribute less than epsilon to the shape."
  [points ^double epsilon]
  (let [n (count points)]
    (if (<= n 2)
      (vec points)
      (let [first-pt (first points)
            last-pt (last points)
            [max-d max-i]
            (reduce (fn [[best-d best-i] i]
                      (let [d (point-to-line-distance (nth points i) first-pt last-pt)]
                        (if (> d best-d) [d i] [best-d best-i])))
                    [0.0 0]
                    (range 1 (dec n)))]
        (if (> max-d epsilon)
          (let [left  (simplify (subvec (vec points) 0 (inc max-i)) epsilon)
                right (simplify (subvec (vec points) max-i) epsilon)]
            (vec (concat (butlast left) right)))
          [first-pt last-pt])))))

(defn simplify-commands
  "Douglas-Peucker simplification on path commands.
  Wraps simplify on the extracted points and rebuilds commands."
  [commands epsilon]
  (let [points (into []
                 (keep (fn [[cmd & args]]
                         (when (#{:move-to :line-to} cmd)
                           (first args))))
                 commands)
        simplified (simplify points epsilon)]
    (if (empty? simplified)
      commands
      (into [[:move-to (first simplified)]]
            (mapv (fn [p] [:line-to p]) (rest simplified))))))

;; --- point-in-polygon (ray casting) ---

(defn contains-point?
  "Tests whether a point [px py] is inside a polygon [[x y] ...].
  Uses the ray-casting algorithm (count horizontal ray crossings)."
  [polygon [^double px ^double py]]
  (let [n (count polygon)]
    (loop [i 0 j (dec n) inside? false]
      (if (>= i n)
        inside?
        (let [[^double xi ^double yi] (nth polygon i)
              [^double xj ^double yj] (nth polygon j)
              crosses? (and (not= (> yi py) (> yj py))
                            (< px (+ xi (/ (* (- xj xi) (- py yi))
                                           (- yj yi)))))]
          (recur (inc i) i (if crosses? (not inside?) inside?)))))))

;; --- polygon inset ---

(defn- line-line-intersection
  "Intersection of two lines defined by point+direction pairs.
  Returns [x y] or nil if parallel."
  [[^double px1 ^double py1] [^double dx1 ^double dy1]
   [^double px2 ^double py2] [^double dx2 ^double dy2]]
  (let [denom (- (* dx1 dy2) (* dy1 dx2))]
    (when-not (< (Math/abs denom) 1e-10)
      (let [t (/ (- (* (- px2 px1) dy2) (* (- py2 py1) dx2)) denom)]
        [(+ px1 (* t dx1)) (+ py1 (* t dy1))]))))

(defn inset
  "Shrinks a closed polygon inward by distance d.
  Returns a vector of [[x y] ...] points.
  Works correctly for convex polygons; concave polygons may produce
  self-intersecting results."
  [polygon d]
  (if (zero? (double d))
    (vec polygon)
    (let [n (count polygon)
        d (double d)
        ;; Compute signed area to determine winding direction
        signed-area (reduce + (map (fn [i]
                                     (let [[^double x1 ^double y1] (nth polygon i)
                                           [^double x2 ^double y2] (nth polygon (mod (inc i) n))]
                                       (- (* x1 y2) (* x2 y1))))
                                   (range n)))
        ;; Positive signed area: inward normal is (-dy, dx)
        ;; Negative signed area: inward normal is (dy, -dx)
        ccw? (pos? signed-area)
        offset-edges
        (mapv (fn [i]
                (let [[^double x1 ^double y1] (nth polygon i)
                      [^double x2 ^double y2] (nth polygon (mod (inc i) n))
                      dx (- x2 x1) dy (- y2 y1)
                      len (Math/sqrt (+ (* dx dx) (* dy dy)))]
                  (if (< len 1e-10)
                    {:point [x1 y1] :dir [dx dy]}
                    (let [[nx ny] (if ccw?
                                    [(/ (- dy) len) (/ dx len)]
                                    [(/ dy len) (/ (- dx) len)])
                          ox1 (+ x1 (* nx d))
                          oy1 (+ y1 (* ny d))]
                      {:point [ox1 oy1] :dir [dx dy]}))))
              (range n))]
    ;; Intersect adjacent offset edges to get new vertices
    ;; Vertex i is the intersection of edge (i-1) and edge (i)
    (vec
      (keep (fn [i]
              (let [e1 (nth offset-edges (mod (dec (+ i n)) n))
                    e2 (nth offset-edges i)]
                (line-line-intersection
                  (:point e1) (:dir e1)
                  (:point e2) (:dir e2))))
            (range n))))))

;; --- curve splitting ---

(defn- path-points
  "Flattens path commands to line segments and extracts points."
  [commands]
  (let [flat (text/flatten-commands commands 0.5)]
    (into []
      (keep (fn [[cmd & args]]
              (when (#{:move-to :line-to} cmd) (first args))))
      flat)))

(defn- cumulative-dists [points]
  (loop [i 1 dists [0.0]]
    (if (>= i (count points))
      dists
      (let [[x0 y0] (nth points (dec i))
            [x1 y1] (nth points i)
            d (Math/sqrt (+ (* (- (double x1) (double x0)) (- (double x1) (double x0)))
                            (* (- (double y1) (double y0)) (- (double y1) (double y0)))))]
        (recur (inc i) (conj dists (+ (peek dists) d)))))))

(defn- lerp-pt [[x1 y1] [x2 y2] t]
  (let [t (double t) inv (- 1.0 t)]
    [(+ (* inv (double x1)) (* t (double x2)))
     (+ (* inv (double y1)) (* t (double y2)))]))

(defn- point-at-dist
  "Finds the point at a given distance along the path."
  [points dists target]
  (let [target (double target)]
    (loop [j 1]
      (if (or (>= j (count points))
              (>= (double (nth dists j)) target))
        (let [j (min j (dec (count points)))
              d0 (double (nth dists (dec j)))
              d1 (double (nth dists j))
              seg-t (if (== d0 d1) 0.0 (/ (- target d0) (- d1 d0)))]
          (lerp-pt (nth points (dec j)) (nth points j) seg-t))
        (recur (inc j))))))

(defn split-at-length
  "Splits path commands into segments of approximately equal arc-length.
  Returns a vector of path-command vectors, one per segment."
  [commands segment-length]
  (let [points (path-points commands)
        dists (cumulative-dists points)
        total (double (peek dists))
        seg-len (double segment-length)
        n-segs (max 1 (int (Math/ceil (/ total seg-len))))]
    (if (<= n-segs 1)
      [(into [[:move-to (first points)]]
             (mapv (fn [p] [:line-to p]) (rest points)))]
      (let [actual-len (/ total n-segs)]
        (mapv (fn [seg-i]
                (let [start-d (* seg-i actual-len)
                      end-d (* (inc seg-i) actual-len)
                      start-pt (point-at-dist points dists start-d)
                      ;; Collect intermediate points within this segment
                      inner-pts (filterv (fn [j]
                                           (let [d (double (nth dists j))]
                                             (and (> d start-d) (< d end-d))))
                                         (range (count points)))
                      end-pt (point-at-dist points dists (min end-d total))]
                  (into [[:move-to start-pt]]
                        (concat
                          (mapv (fn [j] [:line-to (nth points j)]) inner-pts)
                          [[:line-to end-pt]]))))
              (range n-segs))))))

;; --- path interpolation ---

(defn interpolate
  "Blends between two paths at parameter t (0-1).
  Both paths must have the same number and type of commands.
  Returns blended path commands."
  [commands-a commands-b t]
  (let [t (double t) inv (- 1.0 t)]
    (mapv (fn [[cmd-a & args-a] [cmd-b & args-b]]
            (case cmd-a
              :move-to (let [[xa ya] (first args-a)
                             [xb yb] (first args-b)]
                         [:move-to [(+ (* inv (double xa)) (* t (double xb)))
                                    (+ (* inv (double ya)) (* t (double yb)))]])
              :line-to (let [[xa ya] (first args-a)
                             [xb yb] (first args-b)]
                         [:line-to [(+ (* inv (double xa)) (* t (double xb)))
                                    (+ (* inv (double ya)) (* t (double yb)))]])
              :close [:close]
              ;; Default: return command-a unchanged
              (vec (cons cmd-a args-a))))
          commands-a commands-b)))

;; --- path clipping ---

(defn- clip-segment
  "Cohen-Sutherland clipping of line segment to rectangle [bx, bx+bw] x [by, by+bh].
  Returns [clipped-p1 clipped-p2] or nil if fully outside."
  [p1 p2 bx by bw bh]
  (let [bx (double bx) by (double by) bw (double bw) bh (double bh)
        [x1-init y1-init] p1
        [x2-init y2-init] p2
        xmax (+ bx bw) ymax (+ by bh)
        outcode (fn [^double x ^double y]
                  (bit-or (if (< x bx) 1 0) (if (> x xmax) 2 0)
                          (if (< y by) 4 0) (if (> y ymax) 8 0)))]
    (loop [x1 (double x1-init) y1 (double y1-init)
           x2 (double x2-init) y2 (double y2-init) iter 0]
      (let [c1 (outcode x1 y1) c2 (outcode x2 y2)]
        (cond
          (and (zero? c1) (zero? c2)) [[x1 y1] [x2 y2]]
          (pos? (bit-and c1 c2)) nil
          (> iter 8) nil
          :else
          (let [c (if (pos? c1) c1 c2)
                [nx ny] (cond
                          (pos? (bit-and c 8)) [(+ x1 (* (- x2 x1) (/ (- ymax y1) (- y2 y1)))) ymax]
                          (pos? (bit-and c 4)) [(+ x1 (* (- x2 x1) (/ (- by y1) (- y2 y1)))) by]
                          (pos? (bit-and c 2)) [xmax (+ y1 (* (- y2 y1) (/ (- xmax x1) (- x2 x1))))]
                          :else                [bx (+ y1 (* (- y2 y1) (/ (- bx x1) (- x2 x1))))])]
            (if (= c c1)
              (recur nx ny x2 y2 (inc iter))
              (recur x1 y1 nx ny (inc iter)))))))))

(defn trim-to-bounds
  "Clips path commands to a bounding rectangle.
  Returns a vector of path-command vectors (one per visible segment).
  Flattens curves to line segments before clipping."
  [commands bx by bw bh]
  (let [points (path-points commands)
        bx (double bx) by (double by) bw (double bw) bh (double bh)]
    (loop [i 1 segments [] current nil]
      (if (>= i (count points))
        (if current (conj segments current) segments)
        (let [p1 (nth points (dec i))
              p2 (nth points i)
              clipped (clip-segment p1 p2 bx by bw bh)]
          (if clipped
            (let [[cp1 cp2] clipped
                  ;; Continue current segment or start new one
                  seg (if current
                        (conj current [:line-to cp2])
                        [[:move-to cp1] [:line-to cp2]])]
              (recur (inc i) segments seg))
            ;; Outside — flush current segment if any
            (recur (inc i)
                   (if current (conj segments current) segments)
                   nil)))))))
