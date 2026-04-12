(ns eido.engine.compile
  (:refer-clojure :exclude [compile])
  (:require
    [eido.color :as color]
    [eido.gen.contour :as contour]
    [eido.gen.flow :as flow]
    [eido.gen.lsystem :as lsystem]
    [eido.gen.scatter :as scatter]
    [eido.gen.voronoi :as voronoi]
    [eido.ir :as ir]
    [eido.path.decorate :as decorator]
    [eido.path.distort :as distort]
    [eido.path.stroke :as stroke]
    [eido.path.warp :as warp]
    [eido.text :as text]
    [eido.validate :as validate]
    [eido.gen.vary :as vary]))

;; Legacy compile-tree, compile-node, compile-style, and related helpers
;; have been removed. The compilation pipeline now routes through
;; compile-semantic → ir.lower/lower. See eido.ir.lower for lowering.

(defn- apply-distort-transforms
  "Applies :transform/distort entries to a path node's commands,
  removing them from the transform list."
  [node]
  (if-let [transforms (:node/transform node)]
    (let [distorts (filterv #(= :transform/distort (first %)) transforms)
          others   (filterv #(not= :transform/distort (first %)) transforms)]
      (if (seq distorts)
        (let [cmds (reduce (fn [cmds [_ opts]] (distort/distort-commands cmds opts))
                           (:path/commands node)
                           distorts)]
          (-> node
              (assoc :path/commands cmds)
              (assoc :node/transform (when (seq others) others))))
        node))
    node))

(defn- expand-stroke-profile
  "Expands a path node with :stroke/profile into a group containing
  the original (fill-only) plus a filled outline path for the stroke."
  [node]
  (let [profile    (:stroke/profile node)
        stroke     (:style/stroke node)
        max-width  (or (:width stroke) 1.0)
        stroke-clr (or (:color stroke) (:style/fill node))
        outline    (stroke/outline-commands (:path/commands node) profile max-width)]
    (if outline
      {:node/type :group
       :group/children
       [(-> node
            (dissoc :stroke/profile)
            (dissoc :style/stroke))
        {:node/type      :shape/path
         :path/commands  outline
         :style/fill     stroke-clr}]}
      (dissoc node :stroke/profile))))

(defn- hatch-fill?
  "Returns true if a fill spec is a hatch fill."
  [fill]
  (and (map? fill) (= :hatch (:fill/type fill))))

(defn- stipple-fill?
  "Returns true if a fill spec is a stipple fill."
  [fill]
  (and (map? fill) (= :stipple (:fill/type fill))))

;; pattern-fill?, shape-bounds, expand-hatch-fill, expand-stipple-fill,
;; make-shadow-node, make-glow-node have been removed.
;; Hatch/stipple fills are now handled by eido.ir.fill.
;; Shadow/glow effects are now handled by eido.ir.effect.

;; expand-effects removed — effects are now handled by eido.ir.effect
;; via scene-node->draw-item's effect extraction.

;; --- node expansion helpers ---

(defn- wrap-group
  "Wraps children in a group node, propagating opacity and transform
  from the source node."
  [children node]
  (cond-> {:node/type      :group
           :group/children children}
    (:node/opacity node)
    (assoc :node/opacity (:node/opacity node))
    (:node/transform node)
    (assoc :node/transform (:node/transform node))))

(defn- wrap-path
  "Wraps path commands in a path node, propagating style, opacity,
  and transform from the source node."
  [commands node]
  (cond-> {:node/type     :shape/path
           :path/commands commands}
    (:style/fill node)
    (assoc :style/fill (:style/fill node))
    (:style/stroke node)
    (assoc :style/stroke (:style/stroke node))
    (:node/opacity node)
    (assoc :node/opacity (:node/opacity node))
    (:node/transform node)
    (assoc :node/transform (:node/transform node))))

(defn- apply-style-overrides
  "Applies per-item style overrides from an indexed override list.
  Base style comes from the parent node."
  [items node overrides style-keys]
  (vec (map-indexed
         (fn [i item]
           (let [ovr (when (seq overrides)
                       (nth overrides (mod i (count overrides))))]
             (reduce (fn [m k]
                       (cond-> m
                         (k node) (assoc k (k node))
                         (and ovr (k ovr)) (assoc k (k ovr))))
                     item
                     style-keys)))
         items)))

(declare expand-node)

;; --- per-type expanders ---

(defn- expand-scatter [node]
  (let [children (-> (scatter/scatter->nodes
                       (:scatter/shape node)
                       (:scatter/positions node)
                       (:scatter/jitter node))
                     (vary/apply-overrides
                       (:scatter/overrides node)))]
    (wrap-group children node)))

(defn- expand-voronoi [node]
  (let [cells (voronoi/voronoi-cells
                (:voronoi/points node) (:voronoi/bounds node))
        styled (apply-style-overrides
                 cells node (:voronoi/overrides node)
                 [:style/fill :style/stroke :node/opacity])]
    (wrap-group styled node)))

(defn- expand-delaunay [node]
  (let [edges (voronoi/delaunay-edges
                (:delaunay/points node) (:delaunay/bounds node))
        styled (mapv #(cond-> %
                        (:style/stroke node)
                        (assoc :style/stroke (:style/stroke node)))
                     edges)]
    (wrap-group styled node)))

(defn- expand-lsystem [node]
  (let [cmds (lsystem/lsystem->path-cmds
               (:lsystem/axiom node)
               (:lsystem/rules node)
               {:iterations (get node :lsystem/iterations 3)
                :angle      (get node :lsystem/angle 25.0)
                :length     (get node :lsystem/length 5.0)
                :origin     (get node :lsystem/origin [0 0])
                :heading    (get node :lsystem/heading -90.0)})]
    (wrap-path cmds node)))

(defn- expand-contour [node]
  (let [noise-fn (case (get node :contour/fn :perlin)
                   :perlin eido.gen.noise/perlin2d
                   :fbm    (fn [x y opts]
                             (eido.gen.noise/fbm
                               eido.gen.noise/perlin2d x y
                               (merge opts (:contour/opts node)))))
        paths (contour/contour-lines noise-fn (:contour/bounds node)
                (or (:contour/opts node) {}))
        styled (mapv #(cond-> %
                        (:style/stroke node)
                        (assoc :style/stroke (:style/stroke node)))
                     paths)]
    (wrap-group styled node)))

(defn- expand-flow-field [node]
  (let [paths     (flow/flow-field (:flow/bounds node)
                    (or (:flow/opts node) {}))
        styled    (apply-style-overrides
                    paths node (:flow/overrides node)
                    [:style/fill :style/stroke :node/opacity])]
    (wrap-group styled node)))

(defn- expand-symmetry [node]
  (let [children  (mapv expand-node (:group/children node))
        sym-type  (:symmetry/type node)
        overrides (:symmetry/overrides node)
        sym-children
        (case sym-type
          :radial
          (let [n (:symmetry/n node)
                [cx cy] (:symmetry/center node [0 0])
                step (/ (* 2.0 Math/PI) n)]
            (mapv (fn [i]
                    {:node/type      :group
                     :node/transform [[:transform/translate cx cy]
                                      [:transform/rotate (* i step)]
                                      [:transform/translate (- cx) (- cy)]]
                     :group/children children})
                  (range n)))
          :bilateral
          (let [[cx cy] (:symmetry/center node [0 0])
                axis    (:symmetry/axis node :vertical)
                mirror  (case axis
                          :vertical
                          [[:transform/translate cx 0]
                           [:transform/scale -1 1]
                           [:transform/translate (- cx) 0]]
                          :horizontal
                          [[:transform/translate 0 cy]
                           [:transform/scale 1 -1]
                           [:transform/translate 0 (- cy)]])]
            [{:node/type :group :group/children children}
             {:node/type      :group
              :node/transform mirror
              :group/children children}])
          :grid
          (let [cols (:symmetry/cols node)
                rows (:symmetry/rows node)
                [dx dy] (:symmetry/spacing node [100 100])]
            (into []
                  (for [row (range rows)
                        col (range cols)]
                    {:node/type      :group
                     :node/transform [[:transform/translate
                                        (* col dx) (* row dy)]]
                     :group/children children}))))]
    (wrap-group (vary/apply-overrides sym-children overrides) node)))

(defn- expand-path-decorated [node]
  (let [children (-> (decorator/decorate-path
                       (:path/commands node)
                       (:decorator/shape node)
                       {:spacing  (get node :decorator/spacing 20)
                        :rotate?  (get node :decorator/rotate? true)})
                     (vary/apply-overrides
                       (:decorator/overrides node)))]
    (wrap-group children node)))

(defn- expand-group [node]
  (let [expanded (update node :group/children
                   #(mapv expand-node %))]
    (if-let [warp-spec (:group/warp expanded)]
      (-> (warp/warp-node expanded warp-spec)
          (dissoc :group/warp))
      expanded)))

;; --- node expansion dispatcher ---

(defn- expand-node
  "Expands high-level nodes into primitive nodes.
  Text nodes become groups of path nodes. Other nodes pass through."
  [node]
  (let [expanded (case (:node/type node)
                   :shape/text         (text/text-node->group node)
                   :shape/text-glyphs  (text/text-glyphs-node->group node)
                   :shape/text-on-path (text/text-on-path-node->group node)
                   :scatter            (expand-scatter node)
                   :voronoi            (expand-voronoi node)
                   :delaunay           (expand-delaunay node)
                   :lsystem            (expand-lsystem node)
                   :contour            (expand-contour node)
                   :flow-field         (expand-flow-field node)
                   :symmetry           (expand-symmetry node)
                   :path/decorated     (expand-path-decorated node)
                   :group              (expand-group node)
                   node)]
    (if (= :shape/path (:node/type expanded))
      (let [with-distort (apply-distort-transforms expanded)]
        (if (:stroke/profile with-distort)
          (expand-stroke-profile with-distort)
          with-distort))
      expanded)))

;; --- paint param propagation ---

(def ^:private paint-keys
  "Keys to propagate from paint-group parents to leaf path children."
  [:paint/brush :paint/color :paint/radius :paint/pressure :paint/speed :paint/seed])

(defn- propagate-paint-params
  "Walks an expanded node tree and propagates paint params from a source
  node to all leaf paths that don't already have :paint/brush set."
  [node source]
  (let [params (select-keys source paint-keys)]
    (if (empty? params)
      node
      (if (= :group (:node/type node))
        (update node :group/children
          #(mapv (fn [c] (propagate-paint-params c source)) %))
        ;; Leaf node — merge paint params if it's a path and doesn't have its own
        (if (and (#{:shape/path :shape/line} (:node/type node))
                 (not (:paint/brush node)))
          (merge node params)
          node)))))

(defn- flatten-paint-children
  "Expands children of a paint-surface group and propagates paint
  params from parent nodes to generated leaf paths."
  [children]
  (into []
    (mapcat
      (fn [child]
        (let [expanded (expand-node child)
              with-paint (propagate-paint-params expanded child)]
          (if (= :group (:node/type with-paint))
            (:group/children with-paint)
            [with-paint]))))
    children))

(defn validate-scene!
  "Validates a scene map; throws ex-info with :errors on failure."
  [scene]
  (when-let [errors (validate/validate scene)]
    (throw (ex-info (str "Invalid scene\n" (validate/format-errors errors))
                    {:errors errors}))))

(def ^:private generator-node-types
  "Node types that map to generator descriptors in the semantic IR."
  #{:flow-field :contour :scatter :voronoi :delaunay :path/decorated :lsystem
    :paint/surface})

(defn- normalize-node
  "Like expand-node but preserves semantic constructs as data instead of
  expanding them to geometry. Generators, hatch/stipple fills, and effects
  are kept as-is for semantic lowering.
  Other node types are expanded normally via expand-node.
  scene-size: [w h] from the scene, threaded for paint surface defaults."
  ([node] (normalize-node node nil))
  ([node scene-size]
  (let [fill      (:style/fill node)
        node-type (:node/type node)]
    (cond
      ;; Generator node types — preserve for semantic lowering (except L-systems)
      ;; For standalone :paint/surface, also expand any children and merge paint/size
      (and (= :paint/surface node-type))
      (let [children    (:paint/children node)
            flat        (if (seq children)
                          (flatten-paint-children children)
                          [])
            paint-size  (or (:paint/size node) scene-size)]
        (cond-> (assoc node :paint/size paint-size)
          (seq flat) (assoc :paint/children flat)))

      (and (generator-node-types node-type)
           (not= :lsystem node-type))
      node

      ;; L-systems — expand to path via legacy (complex parameter passing)
      (= :lsystem node-type)
      (expand-node node)

      ;; Hatch and stipple fills — preserve as-is
      (hatch-fill? fill)
      node

      (stipple-fill? fill)
      node

      ;; Symmetry — expand children with normalize-node
      (= :symmetry node-type)
      (expand-node (update node :group/children #(mapv (fn [c] (normalize-node c scene-size)) %)))

      ;; Groups with :paint/surface — convert to paint-surface generator
      (and (= :group node-type) (:paint/surface node))
      (let [flat-children (flatten-paint-children (:group/children node))
            paint-size    (or (:paint/size node) scene-size)]
        {:node/type :paint/surface
         :paint/surface (:paint/surface node)
         :paint/size paint-size
         :paint/children flat-children
         :node/opacity (:node/opacity node)
         :node/transform (:node/transform node)})

      ;; Groups — normalize children
      (= :group node-type)
      (let [expanded (update node :group/children #(mapv (fn [c] (normalize-node c scene-size)) %))]
        (if-let [warp-spec (:group/warp expanded)]
          (-> (warp/warp-node expanded warp-spec)
              (dissoc :group/warp))
          expanded))

      ;; Path/line with :paint/brush — wrap in implicit paint-surface
      (and (#{:shape/path :shape/line} node-type) (:paint/brush node))
      (let [expanded   (expand-node node)
            transforms (or (:node/transform expanded) [])
            ;; Extract translate offset and apply to path commands directly
            [tx ty] (reduce (fn [[ax ay] t]
                              (if (= :transform/translate (first t))
                                [(+ ax (double (nth t 1)))
                                 (+ ay (double (nth t 2)))]
                                [ax ay]))
                            [0.0 0.0] transforms)
            shifted  (if (and (not (and (zero? tx) (zero? ty)))
                              (:path/commands expanded))
                       (update expanded :path/commands
                         (fn [cmds]
                           (mapv (fn [cmd]
                                   (case (first cmd)
                                     :move-to [:move-to (mapv + (second cmd) [tx ty])]
                                     :line-to [:line-to (mapv + (second cmd) [tx ty])]
                                     :curve-to (let [[_ c1 c2 p] cmd]
                                                 [:curve-to (mapv + c1 [tx ty])
                                                            (mapv + c2 [tx ty])
                                                            (mapv + p [tx ty])])
                                     :quad-to (let [[_ cp p] cmd]
                                                [:quad-to (mapv + cp [tx ty])
                                                          (mapv + p [tx ty])])
                                     :close cmd
                                     cmd))
                                 cmds)))
                       expanded)]
        {:node/type :paint/surface
         :paint/surface {}
         :paint/size scene-size
         :paint/children [(dissoc shifted :node/transform)]
         :node/opacity (:node/opacity node)})

      ;; Everything else goes through the legacy expand-node
      :else
      (expand-node node)))))

(defn- node->fill-descriptor
  "Converts a scene-level fill to a semantic fill descriptor."
  [fill]
  (cond
    (nil? fill)                                          nil
    (and (map? fill) (#{:hatch :stipple} (:fill/type fill))) fill
    (and (map? fill) (= :pattern (:fill/type fill)))     fill
    (or (vector? fill) (keyword? fill))                    {:fill/type :fill/solid :color fill}
    (:gradient/type fill)                                (merge {:fill/type :fill/gradient} fill)
    (:color fill)                                        {:fill/type :fill/solid :color (:color fill)}
    :else                                                fill))

(defn- node->effects
  "Extracts effect descriptors from a scene node."
  [node]
  (cond-> []
    (:effect/shadow node)
    (conj (let [s (:effect/shadow node)]
            {:effect/type    :effect/shadow
             :effect/dx      (:dx s)
             :effect/dy      (:dy s)
             :effect/blur    (:blur s)
             :effect/color   (:color s)
             :effect/opacity (:opacity s)}))
    (:effect/glow node)
    (conj (let [g (:effect/glow node)]
            {:effect/type    :effect/glow
             :effect/blur    (:blur g)
             :effect/color   (:color g)
             :effect/opacity (:opacity g)}))))

(defn- node->generator
  "Converts a generator-type scene node to a generator descriptor."
  [node]
  (case (:node/type node)
    :flow-field
    {:generator/type   :generator/flow-field
     :generator/bounds (:flow/bounds node)
     :generator/opts   (or (:flow/opts node) {})
     :generator/style  (when (or (:style/fill node) (:style/stroke node))
                         (cond-> {}
                           (:style/fill node) (assoc :fill (:style/fill node))
                           (:style/stroke node) (assoc :stroke (:style/stroke node))))
     :generator/overrides (:flow/overrides node)}

    :contour
    {:generator/type   :generator/contour
     :generator/bounds (:contour/bounds node)
     :generator/opts   (or (:contour/opts node) {})
     :generator/style  (when (or (:style/fill node) (:style/stroke node))
                         (cond-> {}
                           (:style/fill node) (assoc :fill (:style/fill node))
                           (:style/stroke node) (assoc :stroke (:style/stroke node))))}

    :scatter
    {:generator/type      :generator/scatter
     :generator/shape     (:scatter/shape node)
     :generator/positions (:scatter/positions node)
     :generator/jitter    (:scatter/jitter node)
     :generator/overrides (:scatter/overrides node)}

    :voronoi
    {:generator/type      :generator/voronoi
     :generator/points    (:voronoi/points node)
     :generator/bounds    (:voronoi/bounds node)
     :generator/style     (when (or (:style/fill node) (:style/stroke node))
                            (cond-> {}
                              (:style/fill node) (assoc :fill (:style/fill node))
                              (:style/stroke node) (assoc :stroke (:style/stroke node))))
     :generator/overrides (:voronoi/overrides node)}

    :delaunay
    {:generator/type   :generator/delaunay
     :generator/points (:delaunay/points node)
     :generator/bounds (:delaunay/bounds node)
     :generator/style  (when (or (:style/fill node) (:style/stroke node))
                         (cond-> {}
                           (:style/fill node) (assoc :fill (:style/fill node))
                           (:style/stroke node) (assoc :stroke (:style/stroke node))))}

    :path/decorated
    {:generator/type          :generator/decorator
     :generator/path-commands (:path/commands node)
     :generator/shape         (:decorator/shape node)
     :generator/spacing       (get node :decorator/spacing 20)
     :generator/rotate?       (get node :decorator/rotate? true)
     :generator/overrides     (:decorator/overrides node)}

    :paint/surface
    {:generator/type    :generator/paint-surface
     :paint/surface     (:paint/surface node)
     :paint/size        (:paint/size node)
     :paint/children    (:paint/children node)
     :paint/strokes     (:paint/strokes node)
     :paint/default-color (:style/fill node)}

    :lsystem
    nil ;; L-systems still go through legacy (complex parameter passing)

    nil))

(defn- node->geometry
  "Extracts a geometry descriptor from a shape node. Returns nil for non-shapes."
  [node]
  (case (:node/type node)
    :shape/rect
    {:geometry/type       :rect
     :rect/xy             (:rect/xy node)
     :rect/size           (:rect/size node)
     :rect/corner-radius  (:rect/corner-radius node)}

    :shape/circle
    {:geometry/type   :circle
     :circle/center   (:circle/center node)
     :circle/radius   (:circle/radius node)}

    :shape/ellipse
    {:geometry/type    :ellipse
     :ellipse/center   (:ellipse/center node)
     :ellipse/rx       (:ellipse/rx node)
     :ellipse/ry       (:ellipse/ry node)}

    :shape/arc
    {:geometry/type :arc
     :arc/center    (:arc/center node)
     :arc/rx        (:arc/rx node)
     :arc/ry        (:arc/ry node)
     :arc/start     (:arc/start node)
     :arc/extent    (:arc/extent node)
     :arc/mode      (get node :arc/mode :open)}

    :shape/line
    {:geometry/type :line
     :line/from     (:line/from node)
     :line/to       (:line/to node)}

    :shape/path
    {:geometry/type  :path
     :path/commands  (:path/commands node)
     :path/fill-rule (:path/fill-rule node)}

    nil))

(defn- normalize-transforms
  "Strips :transform/distort entries and normalizes transform keywords."
  [raw-transforms]
  (if (seq raw-transforms)
    (mapv (fn [[k & args]]
            (into [(keyword (name k))] args))
          (remove #(= :transform/distort (first %))
                  raw-transforms))
    []))

(defn- scene-node->draw-item
  "Converts a normalized scene node to a semantic IR draw item.
  Handles shapes, generators, and the mapping from scene-level keys."
  [node]
  (let [node-type (:node/type node)]
    (if-let [gen (and (generator-node-types node-type)
                      (node->generator node))]
      (cond-> {:item/generator gen}
        (:node/opacity node) (assoc :item/opacity (:node/opacity node)))
      (let [geom       (node->geometry node)
            effects    (node->effects node)
            transforms (normalize-transforms (:node/transform node))]
        (if geom
          (cond-> {:item/geometry geom}
            (:style/fill node)   (assoc :item/fill (node->fill-descriptor (:style/fill node)))
            (:style/stroke node) (assoc :item/stroke (:style/stroke node))
            (:node/opacity node) (assoc :item/opacity (:node/opacity node))
            (seq effects)        (assoc :item/effects effects)
            true                 (assoc :item/transforms transforms))
          (if (= :group node-type)
            (let [children (keep scene-node->draw-item (:group/children node))
                  clip-node (:group/clip node)
                  clip-geom (when clip-node
                              (scene-node->draw-item
                                (-> clip-node
                                    (dissoc :style/fill :style/stroke
                                            :node/opacity :node/transform))))]
              (cond-> {:item/group (vec children)}
                (:group/composite node) (assoc :item/composite (:group/composite node))
                (:group/filter node)    (assoc :item/filter (:group/filter node))
                (:node/opacity node)    (assoc :item/opacity (:node/opacity node))
                clip-geom               (assoc :item/clip-geom (:item/geometry clip-geom))
                (:style/fill node)      (assoc :item/fill (node->fill-descriptor (:style/fill node)))
                (:style/stroke node)    (assoc :item/stroke (:style/stroke node))
                transforms              (assoc :item/transforms transforms)))
            (throw (ex-info (str "Unknown node type: " node-type)
                            {:node/type node-type}))))))))

(defn compile-semantic
  "Compiles a scene map into a semantic IR container.
  Preserves hatch/stipple fills, effects, and generators as semantic data.
  Groups are compiled via compile-tree and included as pre-lowered ops.
  Use eido.ir.lower/lower to convert to concrete ops."
  [scene]
  (let [bg    (color/resolve-color (:image/background scene))
        size  (:image/size scene)
        nodes (mapv #(normalize-node % size) (:image/nodes scene))
        items (vec (keep scene-node->draw-item nodes))]
    (ir/container size bg items)))

(defn compile
  "Compiles a scene map into concrete IR.
  Routes through the semantic IR layer: normalize → IR container → lower."
  [scene]
  ((requiring-resolve 'eido.ir.lower/lower) (compile-semantic scene)))

(comment
  (compile {:image/size [800 600]
            :image/background [:color/rgb 255 255 255]
            :image/nodes
            [{:node/type :shape/circle
              :circle/center [400 300]
              :circle/radius 100
              :style/fill {:color [:color/rgb 200 0 0]}}]})
  )
