(ns eido.gen.contour
  "Marching squares contour extraction from scalar fields.
  Produces connected iso-lines at specified thresholds for topographic effects."
  (:require
    [eido.gen.noise :as noise]))

;; --- marching squares ---

(def ^:private edge-table
  "For each 4-bit case, pairs of edges that form line segments.
  Edges: 0=top, 1=right, 2=bottom, 3=left."
  {0  []
   1  [[3 0]]
   2  [[0 1]]
   3  [[3 1]]
   4  [[1 2]]
   5  [[3 0] [1 2]]
   6  [[0 2]]
   7  [[3 2]]
   8  [[2 3]]
   9  [[2 0]]
   10 [[0 1] [2 3]]
   11 [[2 1]]
   12 [[1 3]]
   13 [[1 0]]
   14 [[0 3]]
   15 []})

(defn- interp ^double [^double v1 ^double v2 ^double threshold]
  (if (== v1 v2)
    0.5
    (/ (- threshold v1) (- v2 v1))))

(defn- edge-point
  "Returns the interpolated [x y] point on a cell edge.
  corners: [tl tr br bl] corner values.
  ctx: {:keys [res threshold bx by]} constant across the march."
  [edge col row [v-tl v-tr v-br v-bl] {:keys [res threshold bx by]}]
  (let [x0 (+ bx (* col res))
        y0 (+ by (* row res))
        x1 (+ x0 res)
        y1 (+ y0 res)]
    (case (int edge)
      0 [(+ x0 (* (interp v-tl v-tr threshold) res)) y0]        ;; top
      1 [x1 (+ y0 (* (interp v-tr v-br threshold) res))]        ;; right
      2 [(+ x0 (* (interp v-bl v-br threshold) res)) y1]        ;; bottom
      3 [x0 (+ y0 (* (interp v-tl v-bl threshold) res))])))

(defn- sample-grid
  "Samples noise function on a grid, returns a flat double-array
  with (inc rows) * (inc cols) entries, row-major."
  [noise-fn cols rows bx by res noise-scale seed]
  (let [opts   (when seed {:seed seed})
        w      (inc (int cols))
        h      (inc (int rows))
        ^doubles arr (double-array (* w h))
        bx     (double bx)
        by     (double by)
        res    (double res)
        ns     (double noise-scale)]
    (dotimes [row h]
      (let [row-off (* row w)]
        (dotimes [col w]
          (let [x (* (+ bx (* col res)) ns)
                y (* (+ by (* row res)) ns)]
            (aset arr (+ row-off col) (double (noise-fn x y opts)))))))
    arr))

(defn- march-threshold
  "Runs marching squares for a single threshold. Returns line segments."
  [^doubles grid cols rows res threshold bx by]
  (let [w   (inc (int cols))
        ctx {:res (double res) :threshold threshold
             :bx  (double bx)  :by (double by)}]
    (into []
          (mapcat
            (fn [row]
              (mapcat
                (fn [col]
                  (let [v-tl    (aget grid (+ (* row w) col))
                        v-tr    (aget grid (+ (* row w) (inc col)))
                        v-br    (aget grid (+ (* (inc row) w) (inc col)))
                        v-bl    (aget grid (+ (* (inc row) w) col))
                        corners [v-tl v-tr v-br v-bl]
                        case-idx (bit-or
                                   (if (>= v-tl threshold) 1 0)
                                   (if (>= v-tr threshold) 2 0)
                                   (if (>= v-br threshold) 4 0)
                                   (if (>= v-bl threshold) 8 0))
                        edges (get edge-table case-idx)]
                    (mapv (fn [[e1 e2]]
                            [(edge-point e1 col row corners ctx)
                             (edge-point e2 col row corners ctx)])
                          edges)))
                (range cols)))
            (range rows)))))

;; --- segment connection ---

(defn- quantize-point
  "Quantizes a point's coordinates for use as a hash key.
  Uses fixed precision to match the eps=0.01 tolerance."
  [[x y]]
  [(Math/round (* (double x) 100.0)) (Math/round (* (double y) 100.0))])

(defn- build-adjacency
  "Builds a map from quantized endpoint -> set of segment indices,
  and a vector of segment data for O(1) lookup."
  [segments]
  (let [seg-vec (vec segments)]
    (reduce-kv
      (fn [adj i [a b]]
        (let [ka (quantize-point a)
              kb (quantize-point b)]
          (-> adj
              (update ka (fnil conj #{}) i)
              (update kb (fnil conj #{}) i))))
      {}
      seg-vec)))

(defn- connect-segments
  "Connects line segments into polylines by matching endpoints.
  Uses spatial hashing for O(n) total instead of O(n²)."
  [segments]
  (if-not (seq segments)
    []
    (let [seg-vec  (vec segments)
          n        (count seg-vec)
          adj      (build-adjacency seg-vec)
          ;; Mutable state for tracking which segments are consumed
          used?    (boolean-array n)]
      (loop [seed 0 chains (transient [])]
        (if (>= seed n)
          (persistent! chains)
          (if (aget used? seed)
            (recur (inc seed) chains)
            (do
              (aset used? seed true)
              (let [[p1 p2] (seg-vec seed)
                    ;; Grow chain forward from p2
                    chain
                    (loop [chain (transient [p1 p2])
                           tail  p2]
                      (let [k     (quantize-point tail)
                            nbrs  (get adj k)
                            ;; Find an unused neighbor segment at this endpoint
                            match (when nbrs
                                    (reduce
                                      (fn [_ i]
                                        (when-not (aget used? i)
                                          (reduced i)))
                                      nil nbrs))]
                        (if (nil? match)
                          (persistent! chain)
                          (do
                            (aset used? match true)
                            (let [[a b] (seg-vec match)
                                  ka (quantize-point a)
                                  ;; Follow the other endpoint
                                  next-pt (if (= ka k) b a)]
                              (recur (conj! chain next-pt)
                                     next-pt))))))]
                (recur (inc seed) (conj! chains chain))))))))))

;; --- public API ---

(defn contour-lines
  "Generates contour paths at iso-thresholds of a scalar field.
  noise-fn: (fn [x y opts] -> double). bounds: [x y w h].
  Returns a vector of :shape/path nodes.
  opts: :thresholds ([0.0]), :resolution (5), :noise-scale (0.01), :seed (0)."
  [noise-fn bounds opts]
  (let [[bx by bw bh] bounds
        res         (get opts :resolution 5)]
    (if-not (and (pos? bw) (pos? bh) (pos? res))
      []
      (let [
        noise-scale (get opts :noise-scale 0.01)
        seed        (get opts :seed 0)
        thresholds  (get opts :thresholds [0.0])
        cols        (int (Math/ceil (/ (double bw) res)))
        rows        (int (Math/ceil (/ (double bh) res)))
        grid        (sample-grid noise-fn cols rows bx by res noise-scale seed)]
    (into []
          (mapcat
            (fn [threshold]
              (let [segments (march-threshold grid cols rows res threshold bx by)
                    chains   (connect-segments segments)]
                (keep (fn [chain]
                        (when (>= (count chain) 2)
                          {:node/type     :shape/path
                           :path/commands (into [[:move-to (first chain)]]
                                                (mapv (fn [p] [:line-to p])
                                                      (rest chain)))}))
                      chains))))
          thresholds)))))

(comment
  (contour-lines noise/perlin2d [0 0 200 200]
    {:thresholds [-0.2 0.0 0.2] :resolution 4 :noise-scale 0.02 :seed 42})
  )
