(ns eido.gen.circle
  "Circle packing: variable-radius non-overlapping circle placement.
  Uses greedy rejection sampling with spatial grid acceleration."
  (:require
    [eido.color.palette :as palette]
    [eido.gen.prob :as prob])
  (:import
    [java.awt.geom Area GeneralPath]))

;; --- spatial grid ---

(defn- make-grid [cell-size bx by bw bh]
  (let [gw (int (Math/ceil (/ (double bw) (double cell-size))))
        gh (int (Math/ceil (/ (double bh) (double cell-size))))]
    {:cells   (object-array (* gw gh))
     :cell-size (double cell-size)
     :gw gw :gh gh
     :bx (double bx) :by (double by)}))

(defn- grid-xy [{:keys [bx by cell-size]} x y]
  [(int (/ (- (double x) bx) cell-size))
   (int (/ (- (double y) by) cell-size))])

(defn- grid-add! [{:keys [^objects cells gw]} gx gy circle]
  (let [idx (+ (* (int gy) (int gw)) (int gx))
        existing (aget cells idx)
        v (if existing (conj existing circle) [circle])]
    (aset cells idx v)))

(defn- grid-neighbors
  "Returns all circles in cells within radius-cells of [gx, gy]."
  [{:keys [^objects cells gw gh]} gx gy radius-cells]
  (let [gw (int gw) gh (int gh)
        gx (int gx) gy (int gy)
        rc (int (Math/ceil (double radius-cells)))]
    (into []
          cat
          (for [dx (range (- rc) (inc rc))
                dy (range (- rc) (inc rc))
                :let [nx (+ gx dx)
                      ny (+ gy dy)]
                :when (and (>= nx 0) (< nx gw)
                           (>= ny 0) (< ny gh))
                :let [v (aget cells (+ (* ny gw) nx))]
                :when v]
            v))))

;; --- circle packing ---

(defn- overlaps-any?
  "Returns true if a circle at [x y] with radius r overlaps any existing circle."
  [x y r neighbors padding]
  (let [x (double x) y (double y)
        r (double r) pad (double padding)]
    (loop [i 0]
      (if (>= i (count neighbors))
        false
        (let [{[nx ny] :center nr :radius} (nth neighbors i)
              dx (- x (double nx))
              dy (- y (double ny))
              min-d (+ r (double nr) pad)]
          (if (< (+ (* dx dx) (* dy dy)) (* min-d min-d))
            true
            (recur (inc i))))))))

(defn- find-radius
  "Finds the largest valid radius for a circle at [x y], or nil if none valid."
  [x y neighbors min-r max-r padding]
  (let [x (double x) y (double y)
        min-r (double min-r) pad (double padding)]
    ;; Find the maximum radius that fits: min of (dist - neighbor-r - padding) for all neighbors
    (loop [i 0 best (double max-r)]
      (if (>= i (count neighbors))
        (when (>= best min-r) best)
        (let [{[nx ny] :center nr :radius} (nth neighbors i)
              dx (- x (double nx))
              dy (- y (double ny))
              dist (Math/sqrt (+ (* dx dx) (* dy dy)))
              avail (- dist (double nr) pad)]
          (if (< avail min-r)
            nil
            (recur (inc i) (min best avail))))))))

(defn circle-pack
  "Packs circles within a rectangular region.
  Returns a vector of {:center [x y] :radius r} maps.
  opts:
    :min-radius  (2.0)   — smallest circle
    :max-radius  (40.0)  — largest circle
    :padding     (1.0)   — gap between circles
    :attempts    (500)   — max failed attempts before stopping
    :max-circles (1000)  — cap on total circles
    :seed        (42)
    :bounds-fn   — optional (fn [x y] -> boolean) for non-rectangular containment"
  [bounds opts]
  (let [[bx by bw bh] bounds]
    (if-not (and (pos? bw) (pos? bh))
      []
      (let [min-r   (get opts :min-radius 2.0)
            max-r   (get opts :max-radius 40.0)
            padding (get opts :padding 1.0)
            max-att (get opts :attempts 500)
            max-n   (get opts :max-circles 1000)
            seed    (get opts :seed 42)
            bounds? (get opts :bounds-fn nil)
            rng     (prob/make-rng seed)
            bx-d    (double bx) by-d (double by)
            bw-d    (double bw) bh-d (double bh)
            cell    (double (+ max-r max-r padding))
            grid    (make-grid cell bx by bw bh)
            rc      (Math/ceil (/ (+ max-r max-r padding) cell))]
        (loop [n 0 fails 0 circles []]
          (if (or (>= n max-n) (>= fails max-att))
            circles
            (let [x (+ bx-d (* (.nextDouble rng) bw-d))
                  y (+ by-d (* (.nextDouble rng) bh-d))]
              (if (and bounds? (not (bounds? x y)))
                (recur n (inc fails) circles)
                (let [[gx gy] (grid-xy grid x y)
                      neighbors (grid-neighbors grid gx gy rc)
                      r (find-radius x y neighbors min-r max-r padding)]
                  (if r
                    (let [circle {:center [x y] :radius r}]
                      (grid-add! grid gx gy circle)
                      (recur (inc n) 0 (conj circles circle)))
                    (recur n (inc fails) circles)))))))))))

;; --- path containment ---

(defn- path->area
  "Converts path commands to a java.awt.geom.Area for containment testing."
  ^Area [commands]
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
    (Area. p)))

(defn circle-pack-in-path
  "Packs circles within an arbitrary closed path.
  Uses java.awt.geom.Area for containment testing."
  [path-commands opts]
  (let [^Area area (path->area path-commands)
        bounds (.getBounds2D area)
        bx (.getX bounds) by (.getY bounds)
        bw (.getWidth bounds) bh (.getHeight bounds)]
    (circle-pack [bx by bw bh]
      (assoc opts :bounds-fn (fn [x y] (.contains area (double x) (double y)))))))

;; --- conversion to scene nodes ---

(defn pack->nodes
  "Converts circle-pack output to scene nodes.
  opts: :style (base style map applied to each circle)"
  [circles opts]
  (let [style (get opts :style {})]
    (mapv (fn [{[x y] :center r :radius}]
            (merge
              {:node/type     :shape/circle
               :circle/center [x y]
               :circle/radius r}
              style))
          circles)))

;; --- convenience helpers ---

(defn ^{:convenience true :convenience-for 'eido.gen.circle/pack->nodes}
  pack->colored-nodes
  "Circle pack to styled nodes with palette colors.
  opts: :seed (default 0), :weights (optional), :style (optional base style map).
  Wraps (pack->nodes circles) + (palette/weighted-sample palette)."
  ([circles palette] (pack->colored-nodes circles palette {}))
  ([circles palette opts]
   (let [n       (count circles)
         weights (:weights opts)
         seed    (get opts :seed 0)
         style   (:style opts)
         pn      (count palette)
         colors  (if (zero? pn)
                   (vec (repeat n nil))
                   (if weights
                     (palette/weighted-sample palette weights n seed)
                     (mapv #(nth palette (mod % pn)) (range n))))]
     (mapv (fn [{[x y] :center r :radius} color]
             (merge {:node/type :shape/circle :circle/center [x y]
                     :circle/radius r :style/fill color} style))
           circles colors))))

(comment
  (count (circle-pack [0 0 200 200] {:seed 42}))
  (circle-pack [0 0 100 100] {:min-radius 5 :max-radius 20 :seed 42})
  (pack->nodes (circle-pack [0 0 100 100] {:max-circles 5 :seed 42})
               {:style {:style/fill [:color/rgb 200 50 50]}}))
