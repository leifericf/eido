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

;; --- chaikin ---

(defn- chaikin-step
  "One iteration of Chaikin corner-cutting on a point sequence."
  [points retain-ends?]
  (let [n (count points)]
    (if (<= n 1)
      points
      (let [pairs (map vector points (rest points))
            inner (mapcat (fn [[[x0 y0] [x1 y1]]]
                            [[(+ (* 0.75 x0) (* 0.25 x1))
                              (+ (* 0.75 y0) (* 0.25 y1))]
                             [(+ (* 0.25 x0) (* 0.75 x1))
                              (+ (* 0.25 y0) (* 0.75 y1))]])
                          pairs)]
        (if retain-ends?
          (vec (concat [(first points)] inner [(last points)]))
          (vec inner))))))

(defn chaikin-commands
  "Chaikin corner-cutting smoothing on path commands.
  Produces rounder, more uniform curves than Catmull-Rom.
  opts: :iterations (3), :retain-ends (true)."
  [commands opts]
  (let [iterations (get opts :iterations 3)
        retain?    (get opts :retain-ends true)
        points     (extract-points commands)
        has-close? (= :close (first (last commands)))
        smoothed   (reduce (fn [pts _] (chaikin-step pts retain?))
                           points
                           (range iterations))]
    (if (seq smoothed)
      (let [cmds (into [[:move-to (first smoothed)]]
                       (mapv (fn [p] [:line-to p]) (rest smoothed)))]
        (if has-close? (conj cmds [:close]) cmds))
      commands)))

;; --- convenience helpers ---

(defn ^{:convenience true :convenience-for 'eido.path.aesthetic/smooth-commands}
  stylize
  "Applies path aesthetic transforms described as data.
  Wraps smooth-commands, jittered-commands, dash-commands, chaikin-commands.
  steps: vector of {:op :smooth/:jitter/:dash/:chaikin, ...opts}.
  Example: (stylize cmds [{:op :chaikin :iterations 3} {:op :dash :dash [10 5]}])"
  [commands steps]
  (reduce (fn [cmds {:keys [op] :as step}]
            (let [opts (dissoc step :op)]
              (case op
                :smooth  (smooth-commands cmds opts)
                :jitter  (jittered-commands cmds opts)
                :dash    (dash-commands cmds opts)
                :chaikin (chaikin-commands cmds opts))))
          commands steps))

;; --- media presets ---
;; Each preset returns a stylize step vector. They are data, not opaque
;; functions — users can inspect, modify, and compose them.

(defn ink-preset
  "Returns stylize steps for ink-stroke aesthetics.
  Moderate Chaikin smoothing with organic jitter."
  [seed]
  [{:op :chaikin :iterations 2}
   {:op :jitter :amount 1.2 :density 1.5 :seed seed}])

(defn pencil-preset
  "Returns stylize steps for pencil-line aesthetics.
  Light smoothing, fine jitter, short dashes for sketch feel."
  [seed]
  [{:op :smooth :samples 24}
   {:op :jitter :amount 0.6 :density 0.8 :seed seed}
   {:op :dash :dash [8.0 1.5]}])

(defn watercolor-preset
  "Returns stylize steps for watercolor-edge aesthetics.
  Heavy Chaikin smoothing with pronounced jitter for bleeding edges."
  [seed]
  [{:op :chaikin :iterations 3}
   {:op :jitter :amount 3.0 :density 2.0 :seed seed}])

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
    {:dash [15.0 5.0]})
  (stylize [[:move-to [0.0 0.0]] [:line-to [50.0 0.0]] [:line-to [100.0 50.0]]]
    (ink-preset 42)))
