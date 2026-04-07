(ns eido.contour
  (:require
    [eido.noise :as noise]))

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
  Cell corners: tl(col,row), tr(col+1,row), br(col+1,row+1), bl(col,row+1)."
  [edge col row res v-tl v-tr v-br v-bl threshold bx by]
  (let [x0 (+ bx (* col (double res)))
        y0 (+ by (* row (double res)))
        x1 (+ x0 (double res))
        y1 (+ y0 (double res))]
    (case (int edge)
      0 [(+ x0 (* (interp v-tl v-tr threshold) (double res))) y0]        ;; top
      1 [x1 (+ y0 (* (interp v-tr v-br threshold) (double res)))]        ;; right
      2 [(+ x0 (* (interp v-bl v-br threshold) (double res))) y1]        ;; bottom
      3 [x0 (+ y0 (* (interp v-tl v-bl threshold) (double res)))])))     ;; left

(defn- sample-grid
  "Samples noise function on a grid, returns a 2D vector of values."
  [noise-fn cols rows bx by res noise-scale seed]
  (let [opts (when seed {:seed seed})]
    (mapv (fn [row]
            (mapv (fn [col]
                    (let [x (* (+ bx (* col (double res))) (double noise-scale))
                          y (* (+ by (* row (double res))) (double noise-scale))]
                      (double (noise-fn x y opts))))
                  (range (inc cols))))
          (range (inc rows)))))

(defn- march-threshold
  "Runs marching squares for a single threshold. Returns line segments."
  [grid cols rows res threshold bx by]
  (into []
        (mapcat
          (fn [row]
            (mapcat
              (fn [col]
                (let [v-tl (double (get-in grid [row col]))
                      v-tr (double (get-in grid [row (inc col)]))
                      v-br (double (get-in grid [(inc row) (inc col)]))
                      v-bl (double (get-in grid [(inc row) col]))
                      case-idx (bit-or
                                 (if (>= v-tl threshold) 1 0)
                                 (if (>= v-tr threshold) 2 0)
                                 (if (>= v-br threshold) 4 0)
                                 (if (>= v-bl threshold) 8 0))
                      edges (get edge-table case-idx)]
                  (mapv (fn [[e1 e2]]
                          [(edge-point e1 col row res v-tl v-tr v-br v-bl threshold bx by)
                           (edge-point e2 col row res v-tl v-tr v-br v-bl threshold bx by)])
                        edges)))
              (range cols)))
          (range rows))))

;; --- segment connection ---

(defn- connect-segments
  "Connects line segments into polylines by matching endpoints."
  [segments]
  (if (empty? segments)
    []
    (let [eps 0.01
          close? (fn [p1 p2]
                   (and (< (Math/abs (- (double (p1 0)) (double (p2 0)))) eps)
                        (< (Math/abs (- (double (p1 1)) (double (p2 1)))) eps)))]
      (loop [remaining (vec segments)
             chains []]
        (if (empty? remaining)
          chains
          (let [[seg & rest-segs] remaining
                [p1 p2] seg
                ;; Grow chain from this segment
                [chain leftover]
                (loop [chain [p1 p2]
                       avail (vec rest-segs)]
                  (let [tail (last chain)
                        match (first (keep-indexed
                                       (fn [i [a b]]
                                         (cond
                                           (close? tail a) [i b]
                                           (close? tail b) [i a]
                                           :else nil))
                                       avail))]
                    (if match
                      (let [[idx pt] match]
                        (recur (conj chain pt)
                               (into (subvec avail 0 idx)
                                     (subvec avail (inc idx)))))
                      [chain avail])))]
            (recur leftover (conj chains chain))))))))

;; --- public API ---

(defn contour-lines
  "Generates contour paths at iso-thresholds of a scalar field.
  noise-fn: (fn [x y opts] -> double).
  Returns a vector of :shape/path nodes.
  opts: :thresholds ([0.0]), :resolution (5), :noise-scale (0.01), :seed (0)."
  [noise-fn bx by bw bh opts]
  (let [res         (get opts :resolution 5)
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
          thresholds)))

(comment
  (contour-lines noise/perlin2d 0 0 200 200
    {:thresholds [-0.2 0.0 0.2] :resolution 4 :noise-scale 0.02 :seed 42})
  )
