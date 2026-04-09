(ns eido.path.aesthetic
  "High-level path aesthetic helpers: smoothing, jitter, and dashing.
  Operates on path commands and returns path commands."
  (:require
    [eido.path.distort :as distort]
    [eido.path.morph :as morph]
    [eido.text :as text]))

;; --- Catmull-Rom to cubic bezier ---

(defn- catmull-rom->cubic
  "Converts four Catmull-Rom control points to a cubic bezier :curve-to command.
  The curve spans from p1 to p2."
  [[x0 y0] [x1 y1] [x2 y2] [x3 y3]]
  (let [cp1x (+ x1 (/ (- x2 x0) 6.0))
        cp1y (+ y1 (/ (- y2 y0) 6.0))
        cp2x (- x2 (/ (- x3 x1) 6.0))
        cp2y (- y2 (/ (- y3 y1) 6.0))]
    [:curve-to [cp1x cp1y] [cp2x cp2y] [x2 y2]]))

(defn- extract-points
  "Extracts [x y] points from path commands (skipping :close)."
  [commands]
  (into []
        (keep (fn [[cmd & args]]
                (when (#{:move-to :line-to} cmd)
                  (first args))))
        commands))

;; --- smooth-commands ---

(defn smooth-commands
  "Smooths path commands by resampling and fitting Catmull-Rom curves.
  opts: :samples (32) — resample count before fitting."
  [commands opts]
  (let [samples (get opts :samples 32)
        has-close (= :close (first (last commands)))
        cmds (if has-close (vec (butlast commands)) (vec commands))
        resampled (morph/resample cmds samples)]
    (if (nil? resampled)
      commands
      (let [pts (extract-points resampled)
            n   (count pts)]
        (cond
          (< n 2) commands
          (= n 2) [[:move-to (first pts)] [:line-to (second pts)]]
          :else
          (let [padded (vec (concat [(first pts)] pts [(peek pts)]))
                curves (for [i (range 1 (dec (count padded)))]
                         (when (< (inc i) (count padded))
                           (catmull-rom->cubic
                             (nth padded (dec i))
                             (nth padded i)
                             (nth padded (inc i))
                             (nth padded (min (+ i 2) (dec (count padded)))))))]
            (cond-> (into [[:move-to (first pts)]]
                          (remove nil? (take (dec n) curves)))
              has-close (conj [:close]))))))))

;; --- jittered-commands ---

(defn jittered-commands
  "Adds organic jitter to path commands.
  Resamples for uniform point density, then applies jitter distortion.
  opts: :amount (3.0), :density (1.0) — approximate spacing between points,
        :seed (0)"
  [commands opts]
  (let [amount  (get opts :amount 3.0)
        density (get opts :density 1.0)
        seed    (get opts :seed 0)
        flat    (text/flatten-commands commands 0.5)
        len     (text/path-length flat)
        n       (max 3 (int (/ len density)))
        has-close (= :close (first (last commands)))
        cmds    (if has-close (vec (butlast commands)) (vec commands))
        resampled (or (morph/resample cmds n) cmds)]
    (cond-> (distort/distort-commands resampled {:type :jitter :amount amount :seed seed})
      has-close (conj [:close]))))

;; --- dash-commands ---

(defn dash-commands
  "Breaks path commands into dashed segments.
  opts: :dash [on-length off-length] — required
        :offset (0.0) — shift the pattern start
  Returns a vector of path-command vectors, one per dash."
  [commands opts]
  (let [[dash-on dash-off] (:dash opts)
        offset  (get opts :offset 0.0)
        flat    (text/flatten-commands commands 0.5)
        total   (text/path-length flat)
        period  (+ (double dash-on) (double dash-off))]
    (when (and (pos? total) (pos? period))
      (loop [dist   (double offset)
             dashes []]
        (if (>= dist total)
          dashes
          ;; Calculate start of this dash within the period
          (let [phase    (mod dist period)
                on-start (if (< phase dash-on)
                           dist
                           (+ dist (- period phase)))
                on-end   (min total (+ on-start dash-on))]
            (if (>= on-start total)
              dashes
              (let [;; Sample points along the dash
                    step 1.0
                    pts  (loop [d on-start acc []]
                           (if (>= d on-end)
                             (let [end-pt (text/point-at flat on-end)]
                               (if end-pt
                                 (conj acc (:point end-pt))
                                 acc))
                             (let [pt (text/point-at flat d)]
                               (recur (+ d step)
                                      (if pt
                                        (conj acc (:point pt))
                                        acc)))))]
                (if (< (count pts) 2)
                  (recur (+ on-start period) dashes)
                  (let [dash-cmds (into [[:move-to (first pts)]]
                                        (mapv (fn [p] [:line-to p]) (rest pts)))]
                    (recur (+ on-start period)
                           (conj dashes dash-cmds))))))))))))

(comment
  (smooth-commands
    [[:move-to [0.0 0.0]] [:line-to [50.0 20.0]]
     [:line-to [100.0 0.0]] [:line-to [150.0 30.0]]]
    {:samples 32})
  (jittered-commands
    [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]]]
    {:amount 5.0 :seed 42})
  (dash-commands
    [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]]]
    {:dash [15.0 5.0]}))
