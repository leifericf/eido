(ns eido.gen.vary
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
  Calls (f noise-value) where noise-value is in [-1, 1]."
  [positions noise-scale seed f]
  (let [opts (when seed {:seed seed})]
    (mapv (fn [[x y]]
            (f (noise/perlin2d (* (double x) (double noise-scale))
                               (* (double y) (double noise-scale))
                               opts)))
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
  (if (or (nil? overrides) (empty? overrides))
    children
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
            children))))

(comment
  (by-index 5 (fn [i] {:node/opacity (/ (double i) 4.0)}))
  (by-gradient 5 [[0.0 [:color/rgb 255 0 0]] [1.0 [:color/rgb 0 0 255]]])
  )
