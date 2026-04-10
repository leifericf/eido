(ns eido.io.polyline
  "Extracts polyline data from compiled IR for CNC/plotter/laser export.
  Converts all geometry to sequences of [x y] points."
  (:require
    [eido.text :as text]))

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
  (let [step (/ (* 2.0 Math/PI) segments)
        pts  (mapv (fn [i]
                     (let [a (* i step)]
                       [(+ cx (* r (Math/cos a)))
                        (+ cy (* r (Math/sin a)))]))
                   (range segments))]
    ;; Close: repeat first point
    (conj pts (first pts))))

(defn- ellipse-polyline
  "Approximates an ellipse as a polygon."
  [cx cy rx ry segments]
  (let [step (/ (* 2.0 Math/PI) segments)
        pts  (mapv (fn [i]
                     (let [a (* i step)]
                       [(+ cx (* rx (Math/cos a)))
                        (+ cy (* ry (Math/sin a)))]))
                   (range segments))]
    (conj pts (first pts))))

(defn- arc-polyline
  "Approximates an arc as a polyline."
  [cx cy rx ry start extent segments]
  (let [start-rad (Math/toRadians start)
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

;; --- op dispatch ---

(defn- op->polylines
  "Extracts polylines from a single IR op.
  Returns a vector of polylines."
  [op flatness segments]
  (case (:op op)
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
    :buffer (into [] (mapcat #(op->polylines % flatness segments)) (:ops op))
    ;; Unknown op types produce no polylines
    []))

;; --- public API ---

(defn extract-polylines
  "Extracts all polyline data from compiled IR.
  Returns {:polylines [[[x1 y1] [x2 y2] ...] ...] :bounds [w h]}.

  Options:
    :flatness — curve subdivision tolerance (default 0.5)
    :segments — number of segments for circle/ellipse/arc approximation (default 64)"
  [ir opts]
  (let [flatness (get opts :flatness 0.5)
        segments (get opts :segments 64)
        ops      (:ir/ops ir)
        polys    (into [] (mapcat #(op->polylines % flatness segments)) ops)]
    {:polylines polys
     :bounds    (:ir/size ir)}))

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