(ns ^{:stability :provisional} eido.paint.stroke
  "Stroke interpretation for the paint engine.

  Converts stroke input (a sequence of points with pressure/speed/tilt)
  into a sequence of dab maps at evenly-spaced intervals along the path.
  Spacing is by arc length, not by raw input point indices."
  (:require
    [eido.color :as color]
    [eido.text :as text])
  (:import
    [java.util Random]))

;; --- path commands to points ---

(defn path-commands->points
  "Converts path commands to a sequence of [x y] points via curve flattening.
  Flatness controls approximation quality (lower = smoother, more points)."
  [commands ^double flatness]
  (let [flat (text/flatten-commands commands flatness)]
    (into []
          (keep (fn [cmd]
                  (let [op (nth cmd 0)]
                    (when (or (= :move-to op) (= :line-to op))
                      (nth cmd 1)))))
          flat)))

;; --- arc-length resampling ---

(defn- segment-length
  "Euclidean distance between two 2D points."
  ^double [[^double x0 ^double y0] [^double x1 ^double y1]]
  (let [dx (- x1 x0)
        dy (- y1 y0)]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(defn- lerp-point
  "Linearly interpolates between two points at parameter t."
  [[^double x0 ^double y0] [^double x1 ^double y1] ^double t]
  [(+ x0 (* t (- x1 x0)))
   (+ y0 (* t (- y1 y0)))])

(defn- lerp-value
  "Linearly interpolates between two scalar values."
  ^double [^double a ^double b ^double t]
  (+ a (* t (- b a))))

(defn- curve-lookup
  "Looks up a value from a [[t value] ...] curve at parameter t.
  Returns interpolated value. Curve must be sorted by t."
  ^double [curve ^double t]
  (if (nil? curve)
    1.0
    (let [t  (Math/max 0.0 (Math/min 1.0 t))
          n  (count curve)]
      (if (<= n 1)
        (if (pos? n) (double (second (first curve))) 1.0)
        (loop [i 0]
          (if (>= i (dec n))
            (double (second (last curve)))
            (let [[t0 v0] (nth curve i)
                  [t1 v1] (nth curve (inc i))]
              (if (<= (double t0) t (double t1))
                (let [seg-t (if (== (double t0) (double t1))
                              0.0
                              (/ (- t (double t0)) (- (double t1) (double t0))))]
                  (lerp-value (double v0) (double v1) seg-t))
                (recur (inc i))))))))))

;; --- per-dab jitter ---

(defn- jitter-rng
  "Creates a deterministic Random from stroke seed + dab index."
  ^Random [^long seed ^long dab-idx]
  (Random. (unchecked-add (unchecked-multiply seed 31) dab-idx)))

(defn- apply-jitter
  "Applies per-dab jitter to a dab map. jitter-spec is a map of
  :jitter/position, :jitter/opacity, :jitter/size, :jitter/angle keys.
  Returns the modified dab."
  [dab jitter-spec ^long seed ^long dab-idx]
  (if (nil? jitter-spec)
    dab
    (let [^Random rng (jitter-rng seed dab-idx)
          pos-j   (double (get jitter-spec :jitter/position 0.0))
          opa-j   (double (get jitter-spec :jitter/opacity 0.0))
          size-j  (double (get jitter-spec :jitter/size 0.0))
          angle-j (double (get jitter-spec :jitter/angle 0.0))
          radius  (double (:dab/radius dab))]
      (cond-> dab
        (> pos-j 0.0)
        (-> (update :dab/cx + (* pos-j radius (.nextGaussian rng) 0.5))
            (update :dab/cy + (* pos-j radius (.nextGaussian rng) 0.5)))

        (> opa-j 0.0)
        (update :dab/opacity (fn [^double o]
                               (Math/max 0.01 (Math/min 1.0
                                 (+ o (* opa-j o (.nextGaussian rng) 0.5))))))

        (> size-j 0.0)
        (update :dab/radius (fn [^double r]
                              (Math/max 0.5 (+ r (* size-j r (.nextGaussian rng) 0.5)))))

        (> angle-j 0.0)
        (update :dab/angle (fn [^double a]
                             (+ a (* angle-j (.nextGaussian rng) 0.5))))))))

(defn resample-stroke
  "Resamples a stroke into evenly-spaced dab positions along the path.

  points: vector of [x y] positions (from path flattening or explicit input)
  spacing-px: distance in pixels between dab centers
  opts:
    :color    — resolved color map {:r :g :b :a}
    :radius   — base brush radius
    :hardness — base hardness [0..1]
    :opacity  — base opacity [0..1]
    :pressure — [[t pressure] ...] curve, or nil for constant 1.0
    :seed     — deterministic seed for jitter
    :jitter   — jitter spec map (see apply-jitter)

  Returns a vector of dab maps suitable for kernel/rasterize-dab!."
  [points ^double spacing-px opts]
  (if (< (count points) 2)
    ;; Single point — emit one dab at that position
    (if (seq points)
      (let [[x y] (first points)
            c     (:color opts)]
        [{:dab/cx (double x) :dab/cy (double y)
          :dab/radius (get opts :radius 8.0)
          :dab/hardness (get opts :hardness 0.7)
          :dab/opacity (get opts :opacity 1.0)
          :dab/aspect (get opts :aspect 1.0)
          :dab/angle 0.0
          :dab/tip (:tip opts)
          :dab/color c}])
      [])
    ;; Multiple points — walk the polyline at spacing intervals
    (let [spacing-px (if (pos? spacing-px) spacing-px 1.0) ;; guard against zero/negative
          ;; Compute cumulative arc lengths
          segments (mapv (fn [i]
                          (segment-length (nth points i) (nth points (inc i))))
                        (range (dec (count points))))
          total-len (reduce + 0.0 segments)
          base-radius  (double (get opts :radius 8.0))
          base-hard    (double (get opts :hardness 0.7))
          base-opacity (double (get opts :opacity 1.0))
          base-aspect  (double (get opts :aspect 1.0))
          pressure-curve (:pressure opts)
          tip-spec     (:tip opts)
          jitter-spec  (:jitter opts)
          seed         (long (get opts :seed 0))
          c            (:color opts)
          ;; Compute max segment length for speed normalization
          max-seg-len  (double (apply max 0.001 segments))]
      (when (> total-len 0.0)
        (loop [dist       0.0      ;; distance along path for next dab
               seg-idx    0        ;; current segment index
               seg-offset 0.0      ;; distance consumed within current segment
               dab-idx    0        ;; monotonic dab counter for jitter seed
               dabs       (transient [])]
          (if (> dist total-len)
            (persistent! dabs)
            ;; Find the segment containing this distance
            (let [seg-len (double (nth segments seg-idx))]
              (if (and (> (- dist seg-offset) seg-len)
                       (< seg-idx (dec (count segments))))
                ;; Move to next segment
                (recur dist (inc seg-idx) (+ seg-offset seg-len) dab-idx dabs)
                ;; Interpolate within this segment
                (let [local-t (if (> seg-len 0.0)
                                (/ (- dist seg-offset) seg-len)
                                0.0)
                      local-t (Math/max 0.0 (Math/min 1.0 local-t))
                      p0      (nth points seg-idx)
                      p1      (nth points (inc seg-idx))
                      [px py] (lerp-point p0 p1 local-t)
                      ;; Compute stroke direction angle
                      [x0 y0] p0
                      [x1 y1] p1
                      seg-angle (Math/atan2 (- (double y1) (double y0))
                                            (- (double x1) (double x0)))
                      stroke-t (/ dist total-len)
                      pressure (curve-lookup pressure-curve stroke-t)
                      ;; Per-dab speed: normalized so longest segment = 1.0
                      speed    (/ seg-len max-seg-len)
                      r (* base-radius pressure)
                      o (* base-opacity pressure)
                      raw-dab {:dab/cx       px
                               :dab/cy       py
                               :dab/radius   r
                               :dab/hardness base-hard
                               :dab/opacity  o
                               :dab/aspect   base-aspect
                               :dab/angle    seg-angle
                               :dab/speed    speed
                               :dab/tip      tip-spec
                               :dab/color    c}
                      dab (apply-jitter raw-dab jitter-spec seed dab-idx)]
                  (recur (+ dist spacing-px)
                         seg-idx
                         seg-offset
                         (inc dab-idx)
                         (conj! dabs dab)))))))))))

;; --- stroke points with explicit data ---

(defn explicit-points->stroke-points
  "Converts explicit stroke points [x y pressure speed tilt-x tilt-y]
  to a simpler [x y] point sequence plus a pressure curve.
  Returns {:points [[x y] ...] :pressure [[t pressure] ...]}."
  [explicit-points]
  (if (empty? explicit-points)
    {:points [] :pressure nil}
    (let [pts  (mapv (fn [p] [(double (nth p 0)) (double (nth p 1))]) explicit-points)
          ;; Compute cumulative distances for pressure curve t values
          dists (loop [i 1 acc [0.0]]
                  (if (>= i (count pts))
                    acc
                    (recur (inc i)
                           (conj acc (+ (peek acc) (segment-length (nth pts (dec i)) (nth pts i)))))))
          total (double (peek dists))
          pressure (if (> total 0.0)
                     (mapv (fn [p d]
                             [(/ (double d) total) (double (nth p 2 1.0))])
                           explicit-points dists)
                     (mapv (fn [p i] [(double i) (double (nth p 2 1.0))])
                           explicit-points (range)))]
      {:points pts :pressure pressure})))

(comment
  (resample-stroke [[0 0] [100 0]] 10.0
    {:color {:r 200 :g 50 :b 30 :a 1.0} :radius 8.0 :opacity 0.5})
  (resample-stroke [[0 0] [100 0]] 10.0
    {:color {:r 200 :g 50 :b 30 :a 1.0} :radius 8.0 :opacity 0.5
     :jitter {:jitter/position 0.15 :jitter/opacity 0.2 :jitter/size 0.1}
     :seed 42})
  (explicit-points->stroke-points
    [[100 100 0.8 0.0 0 0] [200 150 0.6 1.2 0 0] [350 120 0.3 0.8 0 0]]))
