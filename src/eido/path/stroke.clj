(ns eido.path.stroke
  "Stroke profiles and variable-width path expansion. Apply named or
  custom width profiles to convert open paths into filled outlines."
  (:require
    [eido.text :as text]))

;; --- built-in profiles ---

(def ^:private named-profiles
  {:pointed [[0.0 0.0] [0.15 0.8] [0.5 1.0] [0.85 0.8] [1.0 0.0]]
   :chisel  [[0.0 0.7] [0.1 1.0] [0.9 1.0] [1.0 0.7]]
   :brush   [[0.0 0.1] [0.1 0.6] [0.3 1.0] [0.7 0.9] [0.9 0.5] [1.0 0.0]]})

(defn resolve-profile
  "Resolves a profile keyword to a vector of [t width-factor] pairs,
  or passes through a vector."
  [profile]
  (if (keyword? profile)
    (get named-profiles profile (:pointed named-profiles))
    profile))

(defn width-at
  "Linearly interpolates width from a profile at parameter t (0..1).
  Profile is a vector of [t width] pairs, sorted by t."
  ^double [profile ^double t]
  (let [t (max 0.0 (min 1.0 t))]
    (loop [i 0]
      (if (>= i (dec (count profile)))
        (double (second (last profile)))
        (let [[t0 w0] (nth profile i)
              [t1 w1] (nth profile (inc i))]
          (if (<= t0 t t1)
            (let [seg-t (if (== t0 t1) 0.0 (/ (- t t0) (- t1 t0)))]
              (+ (* (- 1.0 seg-t) (double w0))
                 (* seg-t (double w1))))
            (recur (inc i))))))))

;; --- path to points ---

(defn- flatten-to-points
  "Converts path commands to a vector of [x y] points via flattening."
  [commands]
  (let [flat (text/flatten-commands commands 0.5)]
    (into []
          (keep (fn [command]
                  (let [cmd (nth command 0)]
                    (when (or (= :move-to cmd) (= :line-to cmd))
                      (nth command 1)))))
          flat)))

;; --- normal computation ---

(defn- vec-normalize [[x y]]
  (let [len (Math/sqrt (+ (* x x) (* y y)))]
    (if (< len 1e-10)
      [0.0 0.0]
      [(/ x len) (/ y len)])))

(defn- point-normal
  "Computes the averaged normal at point i in a polyline.
  Normal points to the left of the travel direction."
  [points i]
  (let [n (count points)]
    (cond
      (< n 2) [0.0 -1.0]
      (zero? i)
      (let [[x0 y0] (nth points 0)
            [x1 y1] (nth points 1)
            [dx dy] (vec-normalize [(- x1 x0) (- y1 y0)])]
        [(- dy) dx])
      (= i (dec n))
      (let [[x0 y0] (nth points (- n 2))
            [x1 y1] (nth points (dec n))
            [dx dy] (vec-normalize [(- x1 x0) (- y1 y0)])]
        [(- dy) dx])
      :else
      (let [[x0 y0] (nth points (dec i))
            [x1 y1] (nth points i)
            [x2 y2] (nth points (inc i))
            [dx1 dy1] (vec-normalize [(- x1 x0) (- y1 y0)])
            [dx2 dy2] (vec-normalize [(- x2 x1) (- y2 y1)])
            nx (/ (+ (- dy1) (- dy2)) 2.0)
            ny (/ (+ dx1 dx2) 2.0)
            [nx ny] (vec-normalize [nx ny])]
        [nx ny]))))

;; --- cumulative distance ---

(defn- cumulative-distances
  "Returns a double-array of cumulative arc-length distances for each point."
  ^doubles [points]
  (let [n (count points)
        ^doubles dists (double-array n)]
    (aset dists 0 0.0)
    (loop [i 1]
      (when (< i n)
        (let [[x0 y0] (nth points (dec i))
              [x1 y1] (nth points i)
              dx (- (double x1) (double x0))
              dy (- (double y1) (double y0))
              d (Math/sqrt (+ (* dx dx) (* dy dy)))]
          (aset dists i (+ (aget dists (dec i)) d))
          (recur (inc i)))))
    dists))

;; --- outline generation ---

(defn outline-commands
  "Generates path commands for a variable-width stroke outline.
  path-cmds: scene-format path commands ([:move-to [x y]] ...)
  profile: keyword or [[t width-factor] ...] pairs
  max-width: the maximum stroke width (profile scales this)"
  [path-cmds profile max-width]
  (let [profile   (resolve-profile profile)
        points    (flatten-to-points path-cmds)
        n         (count points)]
    (when (>= n 2)
      (let [^doubles dists (cumulative-distances points)
            total-len (aget dists (dec n))
            half-w    (/ (double max-width) 2.0)
            ;; Pre-compute normals once for all points
            normals   (let [arr (object-array n)]
                        (dotimes [i n]
                          (aset arr i (point-normal points i)))
                        arr)
            ;; Build left and right offset points in a single pass
            left      (object-array n)
            right     (object-array n)]
        (dotimes [i n]
          (let [[px py] (nth points i)
                [nx ny] (aget normals i)
                t (if (pos? total-len)
                    (/ (aget dists i) total-len)
                    0.0)
                w (* (width-at profile t) half-w)]
            (aset left i [(+ px (* nx w)) (+ py (* ny w))])
            (aset right i [(- px (* nx w)) (- py (* ny w))])))
        ;; Build outline: left forward, then right backward, then close
        (let [cmds (transient [])]
          (conj! cmds [:move-to (aget left 0)])
          (dotimes [j (dec n)]
            (conj! cmds [:line-to (aget left (inc j))]))
          (loop [i (dec n)]
            (when (>= i 0)
              (conj! cmds [:line-to (aget right i)])
              (recur (dec i))))
          (conj! cmds [:close])
          (persistent! cmds))))))

(comment
  (outline-commands [[:move-to [10 50]] [:line-to [190 50]]] :pointed 20)
  (outline-commands [[:move-to [10 50]]
                     [:curve-to [60 10] [140 90] [190 50]]]
                    :brush 15)
  )
