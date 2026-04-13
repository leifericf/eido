(ns eido.io.polyline
  "Extracts polyline data from compiled IR for CNC/plotter/laser export.
  Converts all geometry to sequences of [x y] points."
  (:require
    [eido.text :as text])
  (:import
    [java.awt.geom AffineTransform Point2D$Double]))

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

;; --- op dispatch ---

(defn- op->polylines
  "Extracts polylines from a single IR op, baking the op's :transforms
  into the output points. Returns a vector of polylines."
  [op flatness segments]
  (let [at    (transforms->affine (:transforms op))
        polys (case (:op op)
                :path   (let [scene-cmds (ir-commands->scene-commands (:commands op))
                              flat       (text/flatten-commands scene-cmds flatness)]
                          (commands->polylines flat))
                :rect   (let [{:keys [x y w h]} op]
                          [(rect-polyline x y w h)])
                :circle (let [{:keys [cx cy r]} op]
                          [(circle-polyline cx cy r segments)])
                :ellipse (let [{:keys [cx cy rx ry]} op]
                           [(ellipse-polyline cx cy rx ry segments)])
                :arc    (let [{:keys [cx cy rx ry start extent]} op]
                          [(arc-polyline cx cy rx ry start extent segments)])
                :line   (let [{:keys [x1 y1 x2 y2]} op]
                          [[[x1 y1] [x2 y2]]])
                :buffer (into [] (mapcat #(op->polylines % flatness segments))
                              (:ops op))
                ;; Unknown op types produce no polylines
                [])]
    (if (.isIdentity at)
      polys
      (mapv #(transform-polyline at %) polys))))

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