(ns eido.morph
  (:require
    [eido.text :as text]))

;; --- point interpolation ---

(defn- lerp-pt [[x1 y1] [x2 y2] t]
  (let [t (double t) inv (- 1.0 t)]
    [(+ (* inv (double x1)) (* t (double x2)))
     (+ (* inv (double y1)) (* t (double y2)))]))

;; --- resample ---

(defn- ensure-doubles
  "Coerces path command coordinates to doubles."
  [[cmd & args :as c]]
  (case cmd
    :move-to  [:move-to [(double ((first args) 0)) (double ((first args) 1))]]
    :line-to  [:line-to [(double ((first args) 0)) (double ((first args) 1))]]
    :curve-to (let [[c1 c2 pt] args]
                [:curve-to [(double (c1 0)) (double (c1 1))]
                           [(double (c2 0)) (double (c2 1))]
                           [(double (pt 0)) (double (pt 1))]])
    :quad-to  (let [[cp pt] args]
                [:quad-to [(double (cp 0)) (double (cp 1))]
                          [(double (pt 0)) (double (pt 1))]])
    :close    [:close]))

(defn- extract-points
  "Extracts [x y] points from path commands (skipping :close)."
  [commands]
  (into []
        (keep (fn [[cmd & args]]
                (when (#{:move-to :line-to} cmd)
                  (first args))))
        commands))

(defn- cumulative-dists [points]
  (loop [i 1 dists [0.0]]
    (if (>= i (count points))
      dists
      (let [[x0 y0] (nth points (dec i))
            [x1 y1] (nth points i)
            d (Math/sqrt (+ (* (- x1 x0) (- x1 x0))
                            (* (- y1 y0) (- y1 y0))))]
        (recur (inc i) (conj dists (+ (peek dists) d)))))))

(defn resample
  "Resamples path commands to exactly n evenly-spaced points.
  Flattens curves to line segments first."
  [commands n]
  (let [flat (text/flatten-commands (mapv ensure-doubles commands) 0.5)
        points (extract-points flat)]
    (when (>= (count points) 2)
      (let [dists (cumulative-dists points)
            total (peek dists)
            step  (/ total (dec n))]
        (into [[:move-to (first points)]]
              (for [i (range 1 n)]
                (let [target (* i step)]
                  ;; Find segment containing target distance
                  (loop [j 1]
                    (if (or (>= j (count points))
                            (>= (nth dists j) target))
                      (let [j (min j (dec (count points)))
                            d0 (nth dists (dec j))
                            d1 (nth dists j)
                            seg-t (if (== d0 d1) 0.0
                                      (/ (- target d0) (- d1 d0)))]
                        [:line-to (lerp-pt (nth points (dec j))
                                           (nth points j)
                                           seg-t)])
                      (recur (inc j)))))))))))

;; --- morph ---

(defn- morph-command
  "Interpolates between two matching path commands at parameter t."
  [[cmd-a & args-a] [cmd-b & args-b] t]
  (case cmd-a
    :move-to  [:move-to (lerp-pt (first args-a) (first args-b) t)]
    :line-to  [:line-to (lerp-pt (first args-a) (first args-b) t)]
    :curve-to [:curve-to (lerp-pt (first args-a) (first args-b) t)
                         (lerp-pt (second args-a) (second args-b) t)
                         (lerp-pt (nth args-a 2) (nth args-b 2) t)]
    :quad-to  [:quad-to (lerp-pt (first args-a) (first args-b) t)
                        (lerp-pt (second args-a) (second args-b) t)]
    :close    [:close]))

(defn morph
  "Interpolates between two path command sequences at parameter t (0-1).
  Both must have the same number of commands and matching types."
  [commands-a commands-b t]
  (cond
    (<= t 0.0) commands-a
    (>= t 1.0) commands-b
    :else (mapv #(morph-command %1 %2 t) commands-a commands-b)))

(defn morph-auto
  "Resamples both paths to matching point counts, then morphs at t.
  Works with any two closed or open paths."
  [commands-a commands-b t]
  (let [;; Filter out :close for counting, then add back
        has-close-a (= :close (first (last commands-a)))
        has-close-b (= :close (first (last commands-b)))
        cmds-a (if has-close-a (butlast commands-a) commands-a)
        cmds-b (if has-close-b (butlast commands-b) commands-b)
        n (max (count cmds-a) (count cmds-b))
        ra (or (resample (vec cmds-a) n) cmds-a)
        rb (or (resample (vec cmds-b) n) cmds-b)
        result (morph (vec ra) (vec rb) t)]
    (if (or has-close-a has-close-b)
      (conj (vec result) [:close])
      result)))

(comment
  (resample [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]]
             [:line-to [100.0 100.0]] [:line-to [0.0 100.0]]] 8)
  (morph-auto
    [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]]
     [:line-to [100.0 100.0]] [:line-to [0.0 100.0]] [:close]]
    [[:move-to [50.0 0.0]] [:line-to [100.0 100.0]]
     [:line-to [0.0 100.0]] [:close]]
    0.5)
  )
