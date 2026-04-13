(ns eido.io.polyline
  "Extracts polyline data from compiled IR for CNC/plotter/laser export.
  Converts all geometry to sequences of [x y] points."
  (:require
    [eido.text :as text])
  (:import
    [java.awt.geom AffineTransform Path2D$Double Point2D$Double]))

;; --- IR to scene command conversion ---

(defn- ir-commands->scene-commands
  "Converts IR-format path commands ([:move-to x y]) to scene-format
  ([:move-to [x y]]) for use with text/flatten-commands."
  [commands]
  (mapv (fn [cmd]
          (case (nth cmd 0)
            :move-to  [:move-to [(nth cmd 1) (nth cmd 2)]]
            :line-to  [:line-to [(nth cmd 1) (nth cmd 2)]]
            :curve-to [:curve-to [(nth cmd 1) (nth cmd 2)]
                                 [(nth cmd 3) (nth cmd 4)]
                                 [(nth cmd 5) (nth cmd 6)]]
            :quad-to  [:quad-to [(nth cmd 1) (nth cmd 2)]
                                [(nth cmd 3) (nth cmd 4)]]
            :close    [:close]))
        commands))

;; --- point extraction ---

(defn- commands->polylines
  "Splits flattened scene-format commands on :move-to boundaries,
  producing a vector of polylines (each a vector of [x y] points).
  Closed paths repeat the first point at the end."
  [commands]
  (loop [cmds commands
         current nil
         result []]
    (if-not (seq cmds)
      (if (and current (>= (count current) 2))
        (conj result current)
        result)
      (let [[tag & args] (first cmds)]
        (case tag
          :move-to (let [new-result (if (and current (>= (count current) 2))
                                      (conj result current)
                                      result)]
                     (recur (rest cmds) [(first args)] new-result))
          :line-to (recur (rest cmds) (conj (or current []) (first args)) result)
          :close   (let [closed (if (and current (seq current))
                                  (conj current (first current))
                                  current)]
                     (recur (rest cmds) closed result))
          ;; Skip unknown commands
          (recur (rest cmds) current result))))))

;; --- geometry to polylines ---

(defn- circle-polyline
  "Approximates a circle as a regular polygon."
  [cx cy r segments]
  (let [segments (max 3 (long segments))
        step     (/ (* 2.0 Math/PI) segments)
        pts      (mapv (fn [i]
                         (let [a (* i step)]
                           [(+ cx (* r (Math/cos a)))
                            (+ cy (* r (Math/sin a)))]))
                       (range segments))]
    ;; Close: repeat first point
    (conj pts (first pts))))

(defn- ellipse-polyline
  "Approximates an ellipse as a polygon."
  [cx cy rx ry segments]
  (let [segments (max 3 (long segments))
        step     (/ (* 2.0 Math/PI) segments)
        pts      (mapv (fn [i]
                         (let [a (* i step)]
                           [(+ cx (* rx (Math/cos a)))
                            (+ cy (* ry (Math/sin a)))]))
                       (range segments))]
    (conj pts (first pts))))

(defn- arc-polyline
  "Approximates an arc as a polyline."
  [cx cy rx ry start extent segments]
  (let [segments  (max 1 (long segments))
        start-rad (Math/toRadians start)
        ext-rad   (Math/toRadians extent)
        step      (/ ext-rad segments)]
    (mapv (fn [i]
            (let [a (+ start-rad (* i step))]
              [(+ cx (* rx (Math/cos a)))
               (+ cy (* ry (Math/sin a)))]))
          (range (inc segments)))))

(defn- rect-polyline
  "Converts a rect to a closed 4-corner polyline."
  [x y w h]
  [[x y] [(+ x w) y] [(+ x w) (+ y h)] [x (+ y h)] [x y]])

;; --- affine transform baking ---
;;
;; IR ops carry a :transforms vector like [[:translate dx dy] [:rotate rad]
;; [:scale sx sy] [:shear-x k] [:shear-y k]]. The raster renderer applies
;; these to the Graphics2D context at draw time; for polyline export we
;; need to bake them into the points themselves.

(defn- ^AffineTransform transforms->affine
  "Builds a single AffineTransform from an IR :transforms vector.
  Nil or empty input returns the identity transform."
  [transforms]
  (let [at (AffineTransform.)]
    (doseq [[t & args] transforms]
      (case t
        :translate (.translate at (double (first args)) (double (second args)))
        :rotate    (.rotate    at (double (first args)))
        :scale     (let [sx (double (first args))
                         sy (double (or (second args) sx))]
                     (.scale at sx sy))
        :shear-x   (.shear at (double (first args)) 0.0)
        :shear-y   (.shear at 0.0 (double (first args)))
        nil))
    at))

(defn- transform-polyline
  "Applies an AffineTransform to each point in a polyline."
  [^AffineTransform at polyline]
  (if (.isIdentity at)
    polyline
    (let [src (Point2D$Double.)
          dst (Point2D$Double.)]
      (mapv (fn [[x y]]
              (.setLocation src (double x) (double y))
              (.transform at src dst)
              [(.getX dst) (.getY dst)])
            polyline))))

;; --- polyline clipping ---
;;
;; IR ops can carry a :clip key holding another op (the clip mask).
;; Raster rendering passes both through Graphics2D's clip stack so
;; geometry outside the clip is never drawn. For polyline export we
;; have to bake the clip into the points: split each polyline at
;; the clip boundary and keep only the inside portions.
;;
;; The implementation is segment-by-segment analytic clipping: find
;; t-values where the segment crosses any clip-polygon edge, then
;; classify each [t_i, t_{i+1}] interval by midpoint inside/outside
;; (via Path2D.contains which handles non-convex clips correctly).

(defn- ^Path2D$Double polygon->path2d
  "Builds a closed Path2D from a polygon's points."
  [polygon]
  (let [p (Path2D$Double.)]
    (when-let [[x0 y0] (first polygon)]
      (.moveTo p (double x0) (double y0))
      (doseq [[x y] (rest polygon)]
        (.lineTo p (double x) (double y)))
      (.closePath p))
    p))

(defn- segment-edge-t
  "Returns t in (0,1) where segment [P0,P1] crosses edge [Q0,Q1],
  or nil if the segments don't cross in that range."
  [[x0 y0] [x1 y1] [a0 b0] [a1 b1]]
  (let [d1x (- (double x1) (double x0))
        d1y (- (double y1) (double y0))
        d2x (- (double a1) (double a0))
        d2y (- (double b1) (double b0))
        det (- (* d1x d2y) (* d1y d2x))]
    (when-not (zero? det)
      (let [dx (- (double a0) (double x0))
            dy (- (double b0) (double y0))
            t  (/ (- (* dx d2y) (* dy d2x)) det)
            s  (/ (- (* dx d1y) (* dy d1x)) det)]
        (when (and (< 0.0 t) (< t 1.0) (<= 0.0 s) (<= s 1.0))
          t)))))

(defn- inside-portions
  "Returns sub-segments of [P0,P1] that lie inside the clip polygon,
  as a vector of [[start end] ...] point pairs (in segment order)."
  [P0 P1 polygon ^Path2D$Double poly-shape]
  (let [edges (partition 2 1 polygon)
        ts    (->> edges
                   (keep (fn [[Q0 Q1]] (segment-edge-t P0 P1 Q0 Q1)))
                   (concat [0.0 1.0])
                   distinct
                   sort
                   vec)
        lerp  (fn [t]
                [(+ (double (first P0))
                    (* t (- (double (first P1)) (double (first P0)))))
                 (+ (double (second P0))
                    (* t (- (double (second P1)) (double (second P0)))))])]
    (vec
      (keep (fn [[t1 t2]]
              (when (> (- t2 t1) 1e-12)
                (let [[mx my] (lerp (* 0.5 (+ t1 t2)))]
                  (when (.contains poly-shape (double mx) (double my))
                    [(lerp t1) (lerp t2)]))))
            (partition 2 1 ts)))))

(defn- nearly-equal?
  [[x1 y1] [x2 y2]]
  (and (< (Math/abs (- (double x1) (double x2))) 1e-9)
       (< (Math/abs (- (double y1) (double y2))) 1e-9)))

(defn- clip-polyline-by-polygon
  "Clips a polyline against a closed polygon. Returns a vector of
  sub-polylines (in input order) that lie inside the polygon. Each
  sub-polyline has at least two points; degenerate empty polylines
  are dropped."
  [polyline polygon]
  (cond
    (< (count polyline) 2) []
    (< (count polygon) 3)  [polyline]
    :else
    (let [poly-shape (polygon->path2d polygon)]
      (loop [pts     (rest polyline)
             prev    (first polyline)
             current []
             result  []]
        (if (empty? pts)
          (cond-> result (>= (count current) 2) (conj current))
          (let [P1   (first pts)
                subs (inside-portions prev P1 polygon poly-shape)
                [new-current new-result]
                (reduce (fn [[cur res] [start end]]
                          (cond
                            (empty? cur)
                            [[start end] res]
                            (nearly-equal? (peek cur) start)
                            [(conj cur end) res]
                            :else
                            [[start end] (cond-> res
                                           (>= (count cur) 2) (conj cur))]))
                        [current result]
                        subs)]
            (recur (rest pts) P1 new-current new-result)))))))

;; --- op dispatch ---

(declare op->polylines)

(defn- op-geometry->polylines
  "Extracts polylines for an op's geometry, applying the op's own
  :transforms but ignoring :clip and :opacity gates. Building block
  for both top-level extraction (op->polylines) and clip-polygon
  extraction."
  [op flatness segments]
  (let [at    (transforms->affine (:transforms op))
        polys (case (:op op)
                :path    (let [scene-cmds (ir-commands->scene-commands
                                            (:commands op))
                               flat (text/flatten-commands
                                      scene-cmds flatness)]
                           (commands->polylines flat))
                :rect    (let [{:keys [x y w h]} op]
                           [(rect-polyline x y w h)])
                :circle  (let [{:keys [cx cy r]} op]
                           [(circle-polyline cx cy r segments)])
                :ellipse (let [{:keys [cx cy rx ry]} op]
                           [(ellipse-polyline cx cy rx ry segments)])
                :arc     (let [{:keys [cx cy rx ry start extent]} op]
                           [(arc-polyline cx cy rx ry start extent segments)])
                :line    (let [{:keys [x1 y1 x2 y2]} op]
                           [[[x1 y1] [x2 y2]]])
                :buffer  (into [] (mapcat #(op->polylines % flatness segments))
                               (:ops op))
                ;; Unknown op types produce no polylines
                [])]
    (if (.isIdentity at)
      polys
      (mapv #(transform-polyline at %) polys))))

(defn- apply-clip
  "Clips polys against parent-op's :clip geometry. The clip op is
  extracted in the parent's transformed space (parent transforms
  are applied to the clip polygon as well as the geometry, since
  raster rendering applies the parent transform to both)."
  [polys parent-op flatness segments]
  (let [clip-op   (:clip parent-op)
        parent-at (transforms->affine (:transforms parent-op))
        clip-raw  (op-geometry->polylines clip-op flatness segments)
        clip-polys (if (.isIdentity parent-at)
                     clip-raw
                     (mapv #(transform-polyline parent-at %) clip-raw))
        clip-polygon (first clip-polys)]
    (if (and clip-polygon (>= (count clip-polygon) 3))
      (vec (mapcat #(clip-polyline-by-polygon % clip-polygon) polys))
      polys)))

(defn- op->polylines
  "Extracts polylines from a single IR op, baking the op's :transforms
  into the output points and clipping against any :clip geometry.
  Returns a vector of polylines.

  Ops with :opacity 0 are skipped, matching the raster renderer which
  draws nothing at zero alpha. This avoids wasting pen travel on
  shapes the artist has deliberately hidden."
  [op flatness segments]
  (let [polys (if (and (contains? op :opacity)
                       (zero? (double (:opacity op))))
                []
                (op-geometry->polylines op flatness segments))]
    (if (:clip op)
      (apply-clip polys op flatness segments)
      polys)))

;; --- public API ---

(defn extract-polylines
  "Extracts all polyline data from compiled IR.
  Returns {:polylines [[[x1 y1] [x2 y2] ...] ...] :bounds [w h]}.

  Options:
    :flatness — curve subdivision tolerance (default 0.5)
    :segments — number of segments for circle/ellipse/arc approximation (default 64)"
  ([ir] (extract-polylines ir {}))
  ([ir opts]
   (let [flatness (get opts :flatness 0.5)
         segments (get opts :segments 64)
         ops      (:ir/ops ir)
         polys    (into [] (mapcat #(op->polylines % flatness segments)) ops)]
     {:polylines polys
      :bounds    (:ir/size ir)})))

;; --- grouped extraction (for motion-stream backends) ---
;;
;; Unlike `extract-polylines`, which returns a flat vector, this returns
;; polylines grouped by stroke color — the shape motion-stream backends
;; (DXF layers, G-code tool changes, embroidery color changes) all need.
;;
;; Nil-stroke ops form a single {:stroke nil} group. Backends decide
;; what to do with it (e.g., DXF puts them on a default layer;
;; G-code may skip them).

(defn- flatten-op-tree
  "Recursively flattens :buffer ops so grouping sees leaf ops only."
  [ops]
  (into [] (mapcat (fn [op]
                     (if (= :buffer (:op op))
                       (flatten-op-tree (:ops op))
                       [op])))
        ops))

(defn- group-ops-by-stroke
  "Groups leaf ops by :stroke-color, preserving first-seen order.
  Returns a vector of {:stroke <color-map-or-nil> :ops [...]}."
  [ops]
  (let [{:keys [order groups]}
        (reduce (fn [{:keys [order groups]} op]
                  (let [k (:stroke-color op)]
                    {:order  (if (contains? groups k) order (conj order k))
                     :groups (update groups k (fnil conj []) op)}))
                {:order [] :groups {}}
                ops)]
    (mapv (fn [k] {:stroke k :ops (get groups k)}) order)))

(defn ^{:stability :provisional} extract-grouped-polylines
  "Extracts polylines from compiled IR, grouped by stroke color.

  Returns {:groups   [{:stroke <color-map-or-nil>
                       :polylines [[[x y] ...] ...]} ...]
           :bounds   [w h]}.

  Stroke colors are resolved maps like {:r R :g G :b B :a A}, or nil
  for ops without a stroke. Groups appear in the order their stroke
  color was first seen. Leaf ops inside :buffer containers are
  flattened before grouping.

  Options:
    :flatness — curve subdivision tolerance (default 0.5)
    :segments — number of segments for circle/ellipse/arc (default 64)"
  ([ir] (extract-grouped-polylines ir {}))
  ([ir opts]
   (let [flatness (get opts :flatness 0.5)
         segments (get opts :segments 64)
         ops      (flatten-op-tree (:ir/ops ir))
         groups   (group-ops-by-stroke ops)]
     {:groups (mapv (fn [{:keys [stroke ops]}]
                      {:stroke    stroke
                       :polylines (into []
                                        (mapcat #(op->polylines % flatness
                                                                segments))
                                        ops)})
                    groups)
      :bounds (:ir/size ir)})))

;; --- travel optimization ---

(defn- distance-sq
  ^double [[^double x1 ^double y1] [^double x2 ^double y2]]
  (let [dx (- x2 x1) dy (- y2 y1)]
    (+ (* dx dx) (* dy dy))))

(defn ^{:stability :provisional} optimize-travel-polylines
  "Reorders polylines to minimize pen-up travel between them.
  Greedy nearest-neighbor starting from [0 0], picking the polyline
  whose first point is closest to the current position each step.

  Polylines with no first point (empty/nil) are dropped, since they
  produce no motion and can't anchor the nearest-neighbor search.

  Input and output are both vectors of polylines:
  [[[x y] ...] ...]."
  [polylines]
  (let [polylines (filterv (comp some? first) polylines)]
    (if (<= (count polylines) 1)
      (vec polylines)
      (let [n       (count polylines)
            starts  (mapv first polylines)
            visited (boolean-array n)]
        (loop [result    (transient [])
               pos       [0.0 0.0]
               remaining n]
          (if (zero? remaining)
            (persistent! result)
            (let [[best-idx _]
                  (reduce (fn [[_ bd :as best] i]
                            (if (aget visited i)
                              best
                              (let [d (distance-sq pos (nth starts i))]
                                (if (< d bd) [i d] best))))
                          [-1 Double/MAX_VALUE]
                          (range n))]
              (aset visited best-idx true)
              (recur (conj! result (nth polylines best-idx))
                     (nth starts best-idx)
                     (dec remaining)))))))))

(defn polylines->edn
  "Serializes polyline data to an EDN string."
  [data]
  (pr-str data))

(comment
  (require '[eido.engine.compile :as compile])

  (extract-polylines
    (compile/compile
      {:image/size [400 400]
       :image/background [:color/rgb 255 255 255]
       :image/nodes
       [{:node/type :shape/rect
         :rect/xy [50 50]
         :rect/size [100 100]}
        {:node/type :shape/circle
         :circle/center [200 200]
         :circle/radius 50}]})
    {})
  )