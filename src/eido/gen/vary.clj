(ns eido.gen.vary
  "Per-item style variation: color, opacity, and transform overrides
  driven by index, position, noise, or gradient interpolation."
  (:require
    [eido.color.palette :as palette]
    [eido.gen.noise :as noise]))

;; --- override generators ---

(defn by-index
  "Generates n overrides by calling (f i) for each index 0..n-1.
  f should return a map of node attribute overrides."
  [n f]
  (mapv f (range n)))

(defn by-position
  "Generates overrides by calling (f x y) for each [x y] position.
  f should return a map of node attribute overrides."
  [positions f]
  (mapv (fn [[x y]] (f x y)) positions))

(defn by-noise
  "Generates overrides from Perlin noise sampled at each position.
  Calls (f noise-value) where noise-value is in [-1, 1].
  opts: :noise-scale (required), :seed (default 0)."
  [positions f opts]
  (let [noise-scale (double (:noise-scale opts))
        noise-opts  (when-let [s (:seed opts)] {:seed s})]
    (mapv (fn [[x y]]
            (f (noise/perlin2d (* (double x) noise-scale)
                               (* (double y) noise-scale)
                               noise-opts)))
          positions)))

(defn by-gradient
  "Generates n fill overrides interpolated through gradient stops.
  stops: [[pos color] ...] for palette/gradient-map."
  [n stops]
  (mapv (fn [i]
          {:style/fill (palette/gradient-map stops (/ (double i) (max 1 (dec n))))})
        (range n)))

;; --- applying overrides ---

(defn apply-overrides
  "Merges overrides onto a vector of child group nodes.
  Each override map is merged onto the first child of the group at index i.
  Wraps via mod when overrides is shorter than children."
  [children overrides]
  (if (seq overrides)
    (let [n (count overrides)]
      (mapv (fn [i child]
              (let [ovr (nth overrides (mod i n))]
                (if (and (:group/children child) (seq (:group/children child)))
                  (update-in child [:group/children 0]
                             (fn [shape]
                               (cond-> shape
                                 (:style/fill ovr)
                                 (assoc :style/fill (:style/fill ovr))
                                 (:style/stroke ovr)
                                 (assoc :style/stroke (:style/stroke ovr))
                                 (:node/opacity ovr)
                                 (assoc :node/opacity (:node/opacity ovr))
                                 (:node/transform ovr)
                                 (update :node/transform
                                         (fnil into []) (:node/transform ovr)))))
                  (merge child ovr))))
            (range (count children))
            children))
    children))

;; --- convenience helpers ---

(defn ^{:convenience true :convenience-for 'eido.gen.vary/by-index}
  by-palette
  "Generates n fill overrides from a palette with optional weights.
  opts: :seed (default 0), :weights (optional weight vector).
  Wraps (by-index n (fn [i] {:style/fill ...}))."
  ([n palette] (by-palette n palette {}))
  ([n palette opts]
   (let [weights (:weights opts)
         seed    (get opts :seed 0)]
     (if weights
       (let [colors (palette/weighted-sample palette weights n seed)]
         (mapv (fn [c] {:style/fill c}) colors))
       (mapv (fn [i] {:style/fill (nth palette (mod i (count palette)))})
             (range n))))))

(defn ^{:convenience true :convenience-for 'eido.gen.vary/by-noise}
  by-noise-palette
  "Generates fill overrides by mapping noise to palette colors.
  opts: :noise-scale (required), :seed (default 0).
  Wraps (by-noise positions f opts)."
  [positions palette opts]
  (let [stops (mapv (fn [i c] [(/ (double i) (max 1 (dec (count palette)))) c])
                    (range) palette)]
    (by-noise positions
      (fn [v] {:style/fill (palette/gradient-map stops (+ 0.5 (* 0.5 v)))})
      opts)))

(comment
  (by-index 5 (fn [i] {:node/opacity (/ (double i) 4.0)}))
  (by-gradient 5 [[0.0 [:color/rgb 255 0 0]] [1.0 [:color/rgb 0 0 255]]])
  (by-palette 5 [[:color/rgb 255 0 0] [:color/rgb 0 255 0]] {:seed 42})
  )
