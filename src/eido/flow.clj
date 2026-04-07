(ns eido.flow
  (:require
    [eido.noise :as noise]))

(defn- trace-streamline
  "Traces a single streamline from a starting point.
  Returns a vector of [x y] points, or nil if too short."
  [x0 y0 bx by bw bh {:keys [step-length steps noise-scale seed min-length]
                        :or   {step-length 2.0 steps 50 noise-scale 0.005
                               seed 0 min-length 3}}]
  (let [bx  (double bx)
        by  (double by)
        bw  (double bw)
        bh  (double bh)
        sl  (double step-length)
        ns  (double noise-scale)
        opts (when seed {:seed seed})]
    (loop [x   (double x0)
           y   (double y0)
           i   0
           pts [[x0 y0]]]
      (if (>= i steps)
        (when (>= (count pts) min-length) pts)
        (let [angle (* 2.0 Math/PI (noise/perlin2d (* x ns) (* y ns) opts))
              nx    (+ x (* sl (Math/cos angle)))
              ny    (+ y (* sl (Math/sin angle)))]
          (if (or (< nx bx) (> nx (+ bx bw))
                  (< ny by) (> ny (+ by bh)))
            (when (>= (count pts) min-length) pts)
            (recur nx ny (inc i) (conj pts [nx ny]))))))))

(defn flow-field
  "Generates path nodes from a noise-based flow field within bounds.
  Returns a vector of :shape/path nodes (streamlines).
  opts: :density (20), :step-length (2.0), :steps (50),
        :noise-scale (0.005), :seed (0), :min-length (3)."
  [bx by bw bh opts]
  (let [density (get opts :density 20)
        bx      (double bx)
        by      (double by)
        bw      (double bw)
        bh      (double bh)
        cols    (int (Math/ceil (/ bw density)))
        rows    (int (Math/ceil (/ bh density)))
        half-d  (/ (double density) 2.0)]
    (into []
          (keep (fn [[col row]]
                  (let [x (+ bx (* col (double density)) half-d)
                        y (+ by (* row (double density)) half-d)
                        pts (trace-streamline x y bx by bw bh opts)]
                    (when pts
                      {:node/type     :shape/path
                       :path/commands (into [[:move-to (first pts)]]
                                            (mapv (fn [p] [:line-to p])
                                                  (rest pts)))}))))
          (for [row (range rows) col (range cols)]
            [col row]))))

(comment
  (flow-field 0 0 200 200 {:density 20 :steps 40 :noise-scale 0.005 :seed 42})
  )
