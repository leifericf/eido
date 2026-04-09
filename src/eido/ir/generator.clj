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
    [eido.gen.voronoi :as voronoi]
    [eido.ir.lower :as lower]
    [eido.ir.vary :as ir-vary]
    [eido.path.decorate :as decorator]
    [eido.vary :as vary]))
  ;; NOTE: no dependency on eido.compile — generator lowering is self-sufficient

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
      (let [[bx by bw bh] (:generator/bounds gen-desc)
            nodes (flow/flow-field bx by bw bh (:generator/opts gen-desc))
            styled (-> nodes
                       (apply-style (:generator/style gen-desc))
                       (apply-overrides (:generator/overrides gen-desc)))]
        (lower/lower-scene-nodes
              styled))

      :generator/contour
      (let [[bx by bw bh] (:generator/bounds gen-desc)
            field-desc (:generator/field gen-desc)
            noise-fn   (if field-desc
                         (fn [x y opts]
                           (noise/perlin2d x y
                             (merge opts
                               (when (:field/seed field-desc)
                                 {:seed (:field/seed field-desc)}))))
                         noise/perlin2d)
            nodes (contour/contour-lines noise-fn bx by bw bh
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
      (let [[bx by bw bh] (:generator/bounds gen-desc)
            cells (voronoi/voronoi-cells
                    (:generator/points gen-desc) bx by bw bh)
            styled (-> cells
                       (apply-style (:generator/style gen-desc))
                       (apply-overrides (:generator/overrides gen-desc)))]
        (lower/lower-scene-nodes
              styled))

      :generator/delaunay
      (let [[bx by bw bh] (:generator/bounds gen-desc)
            edges (voronoi/delaunay-edges
                    (:generator/points gen-desc) bx by bw bh)
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
                    (:generator/spacing gen-desc)
                    (:generator/rotate? gen-desc))
            with-overrides (apply-overrides nodes (:generator/overrides gen-desc))]
        (lower/lower-scene-nodes
              with-overrides))

      (throw (ex-info (str "Unknown generator type: " gen-type)
                      {:generator gen-desc})))))
