(ns eido.ir.generator
  "Generator descriptors and lowering.

  A generator is a draw item that produces multiple concrete ops by
  calling existing feature module functions. Generators use
  :item/generator instead of :item/geometry.

  Generator types:
    :generator/flow-field — streamlines from noise field
    :generator/contour    — iso-contour lines from scalar field
    :generator/scatter    — distributed copies of a shape
    :generator/voronoi    — Voronoi tessellation from seed points
    :generator/delaunay   — Delaunay triangulation edges
    :generator/decorator  — shapes placed along a path"
  (:require
    [eido.gen.contour :as contour]
    [eido.gen.flow :as flow]
    [eido.gen.noise :as noise]
    [eido.gen.particle :as particle]
    [eido.gen.scatter :as scatter]
    [eido.gen.vary :as vary]
    [eido.gen.voronoi :as voronoi]
    [eido.ir :as ir]
    [eido.ir.lower :as lower]
    [eido.ir.vary :as ir-vary]
    [eido.path.decorate :as decorator]))

;; --- generator constructors ---

(defn flow-field
  "Creates a flow field generator descriptor."
  [bounds & {:keys [opts style overrides]}]
  {:item/generator {:generator/type :generator/flow-field
                    :generator/bounds bounds
                    :generator/opts (or opts {})
                    :generator/style style
                    :generator/overrides overrides}})

(defn contour
  "Creates a contour generator descriptor.
  field: a field descriptor (or nil for default Perlin noise)."
  [bounds & {:keys [field opts style]}]
  {:item/generator {:generator/type :generator/contour
                    :generator/bounds bounds
                    :generator/field field
                    :generator/opts (or opts {})
                    :generator/style style}})

(defn scatter-gen
  "Creates a scatter generator descriptor."
  [shape positions & {:keys [jitter overrides]}]
  {:item/generator {:generator/type :generator/scatter
                    :generator/shape shape
                    :generator/positions positions
                    :generator/jitter jitter
                    :generator/overrides overrides}})

(defn voronoi-gen
  "Creates a Voronoi generator descriptor."
  [points bounds & {:keys [style overrides]}]
  {:item/generator {:generator/type :generator/voronoi
                    :generator/points points
                    :generator/bounds bounds
                    :generator/style style
                    :generator/overrides overrides}})

(defn delaunay-gen
  "Creates a Delaunay edge generator descriptor."
  [points bounds & {:keys [style]}]
  {:item/generator {:generator/type :generator/delaunay
                    :generator/points points
                    :generator/bounds bounds
                    :generator/style style}})

(defn particle-gen
  "Creates a particle system generator descriptor.
  config: particle system config map (emitter, forces, lifetime, etc.)
  frame: which frame to render (0-indexed)
  n: total frames to simulate."
  [config frame n]
  {:item/generator {:generator/type :generator/particle
                    :particle/config config
                    :particle/frame  frame
                    :particle/n      n}})

(defn decorator-gen
  "Creates a decorator generator descriptor."
  [path-commands shape & {:keys [spacing rotate? overrides]
                          :or {spacing 20 rotate? true}}]
  {:item/generator {:generator/type :generator/decorator
                    :generator/path-commands path-commands
                    :generator/shape shape
                    :generator/spacing spacing
                    :generator/rotate? rotate?
                    :generator/overrides overrides}})

;; --- style application ---

(defn- apply-style
  "Applies style overrides to generated scene nodes."
  [nodes style]
  (if style
    (mapv (fn [node]
            (cond-> node
              (:fill style)   (assoc :style/fill (:fill style))
              (:stroke style) (assoc :style/stroke (:stroke style))))
          nodes)
    nodes))

(defn- apply-overrides
  "Applies vary overrides to generated nodes if present.
  Overrides can be a literal vector or a vary descriptor."
  [nodes overrides]
  (if (seq overrides)
    (let [resolved (if (map? overrides)
                     (ir-vary/resolve-overrides overrides)
                     overrides)]
      (vary/apply-overrides nodes resolved))
    nodes))

;; --- generator expansion ---

(defn expand-generator
  "Expands a generator descriptor to concrete ops by calling
  existing feature module functions and compiling the results."
  [gen-desc]
  (let [gen-type (:generator/type gen-desc)]
    (case gen-type
      :generator/flow-field
      (let [nodes (flow/flow-field (:generator/bounds gen-desc)
                    (:generator/opts gen-desc))
            styled (-> nodes
                       (apply-style (:generator/style gen-desc))
                       (apply-overrides (:generator/overrides gen-desc)))]
        (lower/lower-scene-nodes
              styled))

      :generator/contour
      (let [field-desc (:generator/field gen-desc)
            noise-fn   (if field-desc
                         (fn [x y opts]
                           (noise/perlin2d x y
                             (merge opts
                               (when (:field/seed field-desc)
                                 {:seed (:field/seed field-desc)}))))
                         noise/perlin2d)
            nodes (contour/contour-lines noise-fn (:generator/bounds gen-desc)
                    (:generator/opts gen-desc))
            styled (apply-style nodes (:generator/style gen-desc))]
        (lower/lower-scene-nodes
              styled))

      :generator/scatter
      (let [nodes (scatter/scatter->nodes
                    (:generator/shape gen-desc)
                    (:generator/positions gen-desc)
                    (:generator/jitter gen-desc))
            with-overrides (apply-overrides nodes (:generator/overrides gen-desc))]
        (lower/lower-scene-nodes
              with-overrides))

      :generator/voronoi
      (let [cells (voronoi/voronoi-cells
                    (:generator/points gen-desc) (:generator/bounds gen-desc))
            styled (-> cells
                       (apply-style (:generator/style gen-desc))
                       (apply-overrides (:generator/overrides gen-desc)))]
        (lower/lower-scene-nodes
              styled))

      :generator/delaunay
      (let [edges (voronoi/delaunay-edges
                    (:generator/points gen-desc) (:generator/bounds gen-desc))
            styled (apply-style edges (:generator/style gen-desc))]
        (lower/lower-scene-nodes
              styled))

      :generator/particle
      (let [config (:particle/config gen-desc)
            n      (:particle/n gen-desc)
            frame  (:particle/frame gen-desc)
            all-states (particle/states config n {})
            state  (nth (vec all-states) (min frame (dec n)))
            nodes  (particle/render-frame state config)]
        (lower/lower-scene-nodes
              nodes))

      :generator/decorator
      (let [nodes (decorator/decorate-path
                    (:generator/path-commands gen-desc)
                    (:generator/shape gen-desc)
                    {:spacing  (:generator/spacing gen-desc)
                     :rotate?  (:generator/rotate? gen-desc)})
            with-overrides (apply-overrides nodes (:generator/overrides gen-desc))]
        (lower/lower-scene-nodes
              with-overrides))

      :generator/paint-surface
      (let [paint   (requiring-resolve 'eido.paint/render-stroke!)
            compose (requiring-resolve 'eido.paint/compose)
            mk-surf (requiring-resolve 'eido.paint/make-surface)
            children (:paint/children gen-desc)
            size    (or (:paint/size gen-desc) [800 600])
            surface (mk-surf size)
            substrate-spec (:paint/surface gen-desc)
            default-color  (:paint/default-color gen-desc)]
        ;; Collect all paintable leaf nodes from the tree, applying
        ;; group transforms to path commands via affine matrix.
        ;; Matrix is [a b tx c d ty] for: x' = a*x + b*y + tx, y' = c*x + d*y + ty
        (letfn [(mat-identity [] [1.0 0.0 0.0 0.0 1.0 0.0])
                (mat-compose [[a1 b1 tx1 c1 d1 ty1] [a2 b2 tx2 c2 d2 ty2]]
                  [(+ (* a1 a2) (* b1 c2))
                   (+ (* a1 b2) (* b1 d2))
                   (+ (* a1 tx2) (* b1 ty2) tx1)
                   (+ (* c1 a2) (* d1 c2))
                   (+ (* c1 b2) (* d1 d2))
                   (+ (* c1 tx2) (* d1 ty2) ty1)])
                (mat-from-transforms [transforms]
                  (reduce (fn [m t]
                            (case (first t)
                              :transform/translate
                              (let [tx (double (nth t 1)) ty (double (nth t 2))]
                                (mat-compose m [1.0 0.0 tx 0.0 1.0 ty]))
                              :transform/rotate
                              (let [a (double (nth t 1))
                                    c (Math/cos a) s (Math/sin a)]
                                (mat-compose m [c (- s) 0.0 s c 0.0]))
                              :transform/scale
                              (let [sx (double (nth t 1)) sy (double (nth t 2))]
                                (mat-compose m [sx 0.0 0.0 0.0 sy 0.0]))
                              ;; Skip other transform types
                              m))
                          (mat-identity) transforms))
                (mat-identity? [[a b tx c d ty]]
                  (and (== a 1.0) (== b 0.0) (== tx 0.0)
                       (== c 0.0) (== d 1.0) (== ty 0.0)))
                (transform-point [[a b tx c d ty] [px py]]
                  [(+ (* a px) (* b py) tx)
                   (+ (* c px) (* d py) ty)])
                (transform-cmd [mat cmd]
                  (case (first cmd)
                    :move-to  [:move-to (transform-point mat (second cmd))]
                    :line-to  [:line-to (transform-point mat (second cmd))]
                    :curve-to (let [[_ c1 c2 p] cmd]
                                [:curve-to (transform-point mat c1)
                                           (transform-point mat c2)
                                           (transform-point mat p)])
                    :quad-to  (let [[_ cp p] cmd]
                                [:quad-to (transform-point mat cp)
                                          (transform-point mat p)])
                    :close cmd
                    cmd))
                (collect-painted [node mat]
                  (cond
                    ;; Leaf with paint params — render it
                    (and (#{:shape/path :shape/line} (:node/type node))
                         (:paint/brush node))
                    (let [shifted (if (and (not (mat-identity? mat))
                                          (:path/commands node))
                                   (update node :path/commands
                                     #(mapv (partial transform-cmd mat) %))
                                   node)
                          with-color (if (and default-color
                                             (not (:paint/color shifted))
                                             (not (:stroke/color shifted))
                                             (not (:style/fill shifted)))
                                       (assoc shifted :paint/color default-color)
                                       shifted)]
                      (paint surface with-color substrate-spec))

                    ;; Group — recurse with composed transform
                    (= :group (:node/type node))
                    (let [child-mat (if-let [ts (seq (:node/transform node))]
                                     (mat-compose mat (mat-from-transforms ts))
                                     mat)]
                      (doseq [c (:group/children node)]
                        (collect-painted c child-mat)))

                    ;; Ignore non-paintable nodes
                    :else nil))]
          (doseq [child children]
            (collect-painted child (mat-identity))))
        ;; Also handle explicit :paint/strokes on the descriptor
        (when-let [strokes (:paint/strokes gen-desc)]
          (doseq [s strokes]
            (paint surface s substrate-spec)))
        ;; Compose tiles to image and return as a rect op with procedural-image fill
        (let [img   (compose surface)
              [w h] size]
          [(ir/->RectOp :rect 0 0 w h nil
                        {:fill/type :procedural-image
                         :image     img
                         :offset    [0.0 0.0]}
                        nil nil 1.0
                        nil nil nil
                        nil nil)]))

      (throw (ex-info (str "Unknown generator type: " gen-type)
                      {:generator gen-desc})))))
