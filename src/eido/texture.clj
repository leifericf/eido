(ns ^{:stability :provisional} eido.texture
  "Texture and material helpers for organic rendering effects.

  These helpers produce scene nodes (groups with deformed, layered copies)
  for simulating translucent media like watercolor, ink wash, and gouache."
  (:require
    [eido.path.distort :as distort]))

(defn layered
  "Returns a group node with n deformed copies of shape at low opacity.
  Simulates watercolor, ink wash, and other translucent media.
  opts: :layers (required), :opacity (required), :deform-fn (required),
        :seed (default 0).
  deform-fn: (fn [shape-node layer-index seed] -> shape-node).
  Each layer gets a unique sub-seed derived from the base seed."
  [shape-node opts]
  (let [n         (:layers opts)
        opacity   (:opacity opts)
        deform-fn (:deform-fn opts)
        seed      (get opts :seed 0)]
    {:node/type :group
     :group/children
     (mapv (fn [i]
             (-> (deform-fn shape-node i (+ (long seed) i))
                 (assoc :node/opacity opacity)))
           (range n))}))

(defn watercolor
  "Convenience: layered rendering with jitter deformation.
  Returns a group node simulating watercolor wash.
  opts: :layers (30), :opacity (0.04), :amount (3.0), :seed (0)."
  [path-node opts]
  (let [amount (double (get opts :amount 3.0))]
    (layered path-node
      {:layers    (get opts :layers 30)
       :opacity   (get opts :opacity 0.04)
       :seed      (get opts :seed 0)
       :deform-fn (fn [node _i s]
                    (update node :path/commands
                      (fn [cmds]
                        (distort/distort-commands cmds {:type :jitter :amount amount :seed s}))))})))

(comment
  (layered
    {:node/type :shape/path
     :path/commands [[:move-to [50 50]] [:line-to [150 50]]
                     [:line-to [150 150]] [:line-to [50 150]] [:close]]
     :style/fill [:color/rgba 200 50 50 1.0]}
    {:layers 30 :opacity 0.04 :seed 42
     :deform-fn (fn [node _i s]
                  (update node :path/commands
                    distort/distort-commands {:type :jitter :amount 3 :seed s}))})
  (watercolor
    {:node/type :shape/path
     :path/commands [[:move-to [50 50]] [:line-to [150 50]]
                     [:line-to [150 150]] [:line-to [50 150]] [:close]]
     :style/fill [:color/rgba 200 50 50 1.0]}
    {:layers 20 :opacity 0.05 :amount 4.0 :seed 42}))
