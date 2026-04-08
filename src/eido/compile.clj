(ns eido.compile
  (:refer-clojure :exclude [compile])
  (:require
    [eido.color :as color]
    [eido.decorator :as decorator]
    [eido.contour :as contour]
    [eido.distort :as distort]
    [eido.flow :as flow]
    [eido.hatch :as hatch]
    [eido.lsystem :as lsystem]
    [eido.scatter :as scatter]
    [eido.stipple :as stipple]
    [eido.stroke :as stroke]
    [eido.text :as text]
    [eido.ir :as ir]
    [eido.validate :as validate]
    [eido.warp :as warp]
    [eido.vary :as vary]
    [eido.voronoi :as voronoi]))

(declare pattern-fill?)

(defn- resolve-gradient
  "Resolves colors within gradient stops, passes through coordinates."
  [gradient]
  (-> gradient
      (update :gradient/stops
              (fn [stops]
                (mapv (fn [[pos color-vec]]
                        [pos (color/resolve-color color-vec)])
                      stops)))))

(declare compile-tile-ops)

(defn- resolve-fill
  "Resolves a fill value — color vector, map with :color, gradient, or pattern."
  [fill]
  (when fill
    (cond
      (vector? fill)              (color/resolve-color fill)
      (:gradient/type fill)       (resolve-gradient fill)
      (:color fill)               (color/resolve-color (:color fill))
      (pattern-fill? fill)        (let [[tw th] (:pattern/size fill)
                                        tile-nodes (:pattern/nodes fill)]
                                    {:fill/type    :pattern
                                     :pattern/size [tw th]
                                     :pattern/ops  (compile-tile-ops tile-nodes tw th)})
      ;; Pre-resolved fills (e.g. procedural-image) pass through as-is
      (= :procedural-image (:fill/type fill)) fill
      ;; hatch/stipple fills pass through — handled in expand-node
      (:fill/type fill)           nil)))

(defn- compile-style
  "Extracts fill and stroke from a node, resolving colors.
  Returns a map with all style keys (nil for absent optional keys)."
  [node]
  (let [stroke (:style/stroke node)]
    {:fill         (resolve-fill (:style/fill node))
     :stroke-color (some-> stroke :color color/resolve-color)
     :stroke-width (when stroke (:width stroke))
     :opacity      (get node :node/opacity 1.0)
     :stroke-cap   (:cap stroke)
     :stroke-join  (:join stroke)
     :stroke-dash  (:dash stroke)}))

;; compile-command is now in eido.ir — use ir/compile-command

(defn- compile-node
  "Compiles a scene node into an IR op record."
  [node]
  (let [{:keys [fill stroke-color stroke-width opacity
                stroke-cap stroke-join stroke-dash]} (compile-style node)]
    (case (:node/type node)
      :shape/rect
      (let [[x y] (:rect/xy node)
            [w h] (:rect/size node)]
        (ir/->RectOp :rect x y w h (:rect/corner-radius node)
                      fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash
                      nil nil))
      :shape/circle
      (let [[cx cy] (:circle/center node)]
        (ir/->CircleOp :circle cx cy (:circle/radius node)
                        fill stroke-color stroke-width opacity
                        stroke-cap stroke-join stroke-dash
                        nil nil))
      :shape/arc
      (let [[cx cy] (:arc/center node)]
        (ir/->ArcOp :arc cx cy
                     (:arc/rx node) (:arc/ry node)
                     (:arc/start node) (:arc/extent node)
                     (get node :arc/mode :open)
                     fill stroke-color stroke-width opacity
                     stroke-cap stroke-join stroke-dash
                     nil nil))
      :shape/line
      (let [[x1 y1] (:line/from node)
            [x2 y2] (:line/to node)]
        (ir/->LineOp :line x1 y1 x2 y2
                      fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash
                      nil nil))
      :shape/ellipse
      (let [[cx cy] (:ellipse/center node)]
        (ir/->EllipseOp :ellipse cx cy
                         (:ellipse/rx node) (:ellipse/ry node)
                         fill stroke-color stroke-width opacity
                         stroke-cap stroke-join stroke-dash
                         nil nil))
      :shape/path
      (ir/->PathOp :path
                    (mapv ir/compile-command (:path/commands node))
                    (:path/fill-rule node)
                    fill stroke-color stroke-width opacity
                    stroke-cap stroke-join stroke-dash
                    nil nil)
      (throw (ex-info (str "Unknown node type: " (:node/type node))
                      {:node/type (:node/type node)})))))

(def default-ctx
  {:style {} :transforms [] :opacity 1.0})

(defn- inherit-style
  "Merges inherited style onto a node. Child keys win."
  [node inherited]
  (cond-> node
    (and (:style/fill inherited) (not (:style/fill node)))
    (assoc :style/fill (:style/fill inherited))
    (and (:style/stroke inherited) (not (:style/stroke node)))
    (assoc :style/stroke (:style/stroke inherited))))

(defn- group-style
  "Extracts style keys from a group node, merging with inherited."
  [node inherited]
  (cond-> inherited
    (:style/fill node)   (assoc :style/fill (:style/fill node))
    (:style/stroke node) (assoc :style/stroke (:style/stroke node))))

(defn- simplify-transform
  "Strips namespace prefix from transform keyword.
  [:transform/translate x y] -> [:translate x y]"
  [[k & args]]
  (into [(keyword (name k))] args))

(defn- accumulate-transforms
  "Concatenates node's transforms onto inherited transforms.
  Filters out :transform/distort which is consumed during expansion."
  [inherited node]
  (into inherited (keep (fn [t]
                          (when (not= :transform/distort (first t))
                            (simplify-transform t)))
                        (or (:node/transform node) []))))

(defn compile-tree
  "Recursively compiles a node tree into a flat sequence of IR ops."
  [node ctx]
  (let [node-type (:node/type node)
        opacity   (get node :node/opacity 1.0)]
    (if (= :group node-type)
      (let [clip       (:group/clip node)
            composite  (:group/composite node)
            filt       (:group/filter node)
            children   (:group/children node)
            child-ctx  (-> ctx
                           (update :style (partial group-style node))
                           (update :opacity * opacity)
                           (update :transforms accumulate-transforms node)
                           (cond-> clip
                             (assoc :clip (compile-node clip))))]
        (if (or composite filt)
          (let [buffer-ctx (assoc child-ctx :opacity 1.0)
                child-ops  (into [] (mapcat #(compile-tree % buffer-ctx))
                                  children)]
            [(ir/->BufferOp :buffer
                            (or composite :src-over)
                            filt
                            (* (:opacity ctx) opacity)
                            (accumulate-transforms (:transforms ctx) node)
                            (:clip ctx)
                            child-ops)])
          (into [] (mapcat #(compile-tree % child-ctx))
                children)))
      (let [effective-opacity (* (:opacity ctx) opacity)
            transforms (accumulate-transforms (:transforms ctx) node)
            styled-node (-> (inherit-style node (:style ctx))
                            (assoc :node/opacity effective-opacity))]
        [(cond-> (assoc (compile-node styled-node) :transforms transforms)
           (:clip ctx) (assoc :clip (:clip ctx)))]))))

(defn- compile-tile-ops
  "Compiles pattern tile nodes into IR ops for rendering."
  [nodes tw th]
  (into []
        (mapcat #(compile-tree % default-ctx))
        nodes))

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

(defn- pattern-fill?
  "Returns true if a fill spec is a pattern fill."
  [fill]
  (and (map? fill) (= :pattern (:fill/type fill))))

(defn shape-bounds
  "Returns [x y w h] bounding box for common shape types.
  Delegates to ir/geometry-bounds by mapping scene node keys."
  [node]
  (let [geom (case (:node/type node)
               :shape/rect    {:geometry/type :rect
                                :rect/xy (:rect/xy node)
                                :rect/size (:rect/size node)}
               :shape/circle  {:geometry/type :circle
                                :circle/center (:circle/center node)
                                :circle/radius (:circle/radius node)}
               :shape/ellipse {:geometry/type :ellipse
                                :ellipse/center (:ellipse/center node)
                                :ellipse/rx (:ellipse/rx node)
                                :ellipse/ry (:ellipse/ry node)}
               :shape/path    {:geometry/type :path
                                :path/commands (:path/commands node)}
               nil)]
    (when geom (ir/geometry-bounds geom))))

(defn expand-hatch-fill
  "Expands a node with a hatch fill into a group with:
  - The shape as a clip mask with optional background fill
  - Hatch lines clipped inside."
  [node]
  (let [fill-spec (:style/fill node)
        [bx by bw bh] (shape-bounds node)
        bg-fill   (:hatch/background fill-spec)
        clip-node (-> node
                      (dissoc :style/fill)
                      (dissoc :style/stroke)
                      (dissoc :node/opacity)
                      (dissoc :node/transform))
        hatch-nodes (hatch/hatch-fill->nodes bx by bw bh fill-spec)]
    {:node/type      :group
     :node/opacity   (get node :node/opacity 1.0)
     :node/transform (:node/transform node)
     :group/clip     clip-node
     :group/children (cond-> (if bg-fill
                               (into [(-> node
                                          (assoc :style/fill bg-fill)
                                          (dissoc :style/stroke)
                                          (dissoc :node/opacity)
                                          (dissoc :node/transform))]
                                     hatch-nodes)
                               (vec hatch-nodes))
                       (:style/stroke node)
                       (conj (-> node
                                 (dissoc :style/fill)
                                 (dissoc :node/opacity)
                                 (dissoc :node/transform))))}))

(defn expand-stipple-fill
  "Expands a node with a stipple fill into a group with:
  - The shape as a clip mask with optional background fill
  - Stipple dots clipped inside."
  [node]
  (let [fill-spec (:style/fill node)
        [bx by bw bh] (shape-bounds node)
        bg-fill   (:stipple/background fill-spec)
        clip-node (-> node
                      (dissoc :style/fill)
                      (dissoc :style/stroke)
                      (dissoc :node/opacity)
                      (dissoc :node/transform))
        dot-nodes (stipple/stipple-fill->nodes bx by bw bh fill-spec)]
    {:node/type      :group
     :node/opacity   (get node :node/opacity 1.0)
     :node/transform (:node/transform node)
     :group/clip     clip-node
     :group/children (cond-> (if bg-fill
                               (into [(-> node
                                          (assoc :style/fill bg-fill)
                                          (dissoc :style/stroke)
                                          (dissoc :node/opacity)
                                          (dissoc :node/transform))]
                                     dot-nodes)
                               (vec dot-nodes))
                       (:style/stroke node)
                       (conj (-> node
                                 (dissoc :style/fill)
                                 (dissoc :node/opacity)
                                 (dissoc :node/transform))))}))

(defn make-shadow-node
  "Creates a shadow/glow copy of a node."
  [node {:keys [dx dy blur color opacity] :or {dx 0 dy 0 blur 5 opacity 0.5}}]
  (let [shadow-node (-> node
                        (dissoc :effect/shadow :effect/glow)
                        (assoc :style/fill color)
                        (dissoc :style/stroke))]
    {:node/type      :group
     :group/composite :src-over
     :group/filter    [:blur blur]
     :node/opacity   opacity
     :node/transform [[:transform/translate dx dy]]
     :group/children [shadow-node]}))

(defn make-glow-node
  "Creates a glow copy of a node (shadow with no offset)."
  [node {:keys [blur color opacity] :or {blur 8 opacity 0.7}}]
  (make-shadow-node node {:dx 0 :dy 0 :blur blur :color color :opacity opacity}))

(defn- expand-effects
  "Wraps a node with shadow/glow effect layers if present."
  [node]
  (let [shadow (:effect/shadow node)
        glow   (:effect/glow node)]
    (if (or shadow glow)
      (let [clean-node (-> node (dissoc :effect/shadow :effect/glow))
            children   (cond-> []
                         shadow (conj (make-shadow-node clean-node shadow))
                         glow   (conj (make-glow-node clean-node glow))
                         true   (conj clean-node))]
        {:node/type      :group
         :group/children children})
      node)))

(defn- expand-node
  "Expands high-level nodes into primitive nodes.
  Text nodes become groups of path nodes. Other nodes pass through."
  [node]
  (let [node (case (:node/type node)
               :shape/text         (text/text-node->group node)
               :shape/text-glyphs  (text/text-glyphs-node->group node)
               :shape/text-on-path (text/text-on-path-node->group node)
               :scatter            (let [children (-> (scatter/scatter->nodes
                                                         (:scatter/shape node)
                                                         (:scatter/positions node)
                                                         (:scatter/jitter node))
                                                       (vary/apply-overrides
                                                         (:scatter/overrides node)))]
                                     (cond-> {:node/type      :group
                                              :group/children children}
                                       (:node/opacity node)
                                       (assoc :node/opacity (:node/opacity node))
                                       (:node/transform node)
                                       (assoc :node/transform (:node/transform node))))
               :voronoi             (let [[bx by bw bh] (:voronoi/bounds node)
                                          cells (voronoi/voronoi-cells
                                                  (:voronoi/points node) bx by bw bh)
                                          overrides (:voronoi/overrides node)
                                          styled (vec (map-indexed
                                                        (fn [i cell]
                                                          (let [ovr (when overrides
                                                                      (nth overrides (mod i (count overrides))))]
                                                            (cond-> cell
                                                              (:style/fill node)
                                                              (assoc :style/fill (:style/fill node))
                                                              (:style/stroke node)
                                                              (assoc :style/stroke (:style/stroke node))
                                                              (:style/fill ovr)
                                                              (assoc :style/fill (:style/fill ovr))
                                                              (:style/stroke ovr)
                                                              (assoc :style/stroke (:style/stroke ovr))
                                                              (:node/opacity ovr)
                                                              (assoc :node/opacity (:node/opacity ovr)))))
                                                        cells))]
                                     (cond-> {:node/type :group
                                              :group/children styled}
                                       (:node/opacity node)
                                       (assoc :node/opacity (:node/opacity node))
                                       (:node/transform node)
                                       (assoc :node/transform (:node/transform node))))
               :delaunay            (let [[bx by bw bh] (:delaunay/bounds node)
                                          edges (voronoi/delaunay-edges
                                                  (:delaunay/points node) bx by bw bh)
                                          styled (mapv #(cond-> %
                                                          (:style/stroke node)
                                                          (assoc :style/stroke (:style/stroke node)))
                                                       edges)]
                                     (cond-> {:node/type :group
                                              :group/children styled}
                                       (:node/opacity node)
                                       (assoc :node/opacity (:node/opacity node))
                                       (:node/transform node)
                                       (assoc :node/transform (:node/transform node))))
               :lsystem             (let [cmds (lsystem/lsystem->path-cmds
                                                     (:lsystem/axiom node)
                                                     (:lsystem/rules node)
                                                     (get node :lsystem/iterations 3)
                                                     (get node :lsystem/angle 25.0)
                                                     (get node :lsystem/length 5.0)
                                                     (get node :lsystem/origin [0 0])
                                                     (get node :lsystem/heading -90.0))]
                                     (cond-> {:node/type      :shape/path
                                              :path/commands  cmds}
                                       (:style/fill node)
                                       (assoc :style/fill (:style/fill node))
                                       (:style/stroke node)
                                       (assoc :style/stroke (:style/stroke node))
                                       (:node/opacity node)
                                       (assoc :node/opacity (:node/opacity node))
                                       (:node/transform node)
                                       (assoc :node/transform (:node/transform node))))
               :contour             (let [[bx by bw bh] (:contour/bounds node)
                                          noise-fn (case (get node :contour/fn :perlin)
                                                     :perlin eido.noise/perlin2d
                                                     :fbm    (fn [x y opts]
                                                               (eido.noise/fbm eido.noise/perlin2d x y
                                                                 (merge opts (:contour/opts node)))))
                                          paths (contour/contour-lines noise-fn bx by bw bh
                                                  (or (:contour/opts node) {}))
                                          styled (mapv #(cond-> %
                                                          (:style/stroke node)
                                                          (assoc :style/stroke (:style/stroke node)))
                                                       paths)]
                                     (cond-> {:node/type :group
                                              :group/children styled}
                                       (:node/opacity node)
                                       (assoc :node/opacity (:node/opacity node))
                                       (:node/transform node)
                                       (assoc :node/transform (:node/transform node))))
               :flow-field          (let [[bx by bw bh] (:flow/bounds node)
                                          paths (flow/flow-field bx by bw bh
                                                  (or (:flow/opts node) {}))
                                          overrides (:flow/overrides node)
                                          styled (vec (map-indexed
                                                        (fn [i p]
                                                          (let [ovr (when overrides
                                                                      (nth overrides (mod i (count overrides))))]
                                                            (cond-> p
                                                              (:style/fill node)
                                                              (assoc :style/fill (:style/fill node))
                                                              (:style/stroke node)
                                                              (assoc :style/stroke (:style/stroke node))
                                                              (:style/stroke ovr)
                                                              (assoc :style/stroke (:style/stroke ovr))
                                                              (:node/opacity ovr)
                                                              (assoc :node/opacity (:node/opacity ovr)))))
                                                        paths))]
                                     (cond-> {:node/type :group
                                              :group/children styled}
                                       (:node/opacity node)
                                       (assoc :node/opacity (:node/opacity node))
                                       (:node/transform node)
                                       (assoc :node/transform (:node/transform node))))
               :symmetry           (let [children (mapv expand-node (:group/children node))
                                          sym-type (:symmetry/type node)
                                          overrides (:symmetry/overrides node)]
                                     (cond-> {:node/type :group
                                              :group/children
                                              (vary/apply-overrides
                                              (case sym-type
                                                :radial
                                                (let [n (:symmetry/n node)
                                                      [cx cy] (:symmetry/center node [0 0])
                                                      step (/ (* 2.0 Math/PI) n)]
                                                  (mapv (fn [i]
                                                          {:node/type :group
                                                           :node/transform
                                                           [[:transform/translate cx cy]
                                                            [:transform/rotate (* i step)]
                                                            [:transform/translate (- cx) (- cy)]]
                                                           :group/children children})
                                                        (range n)))
                                                :bilateral
                                                (let [[cx cy] (:symmetry/center node [0 0])
                                                      axis (:symmetry/axis node :vertical)
                                                      mirror (case axis
                                                               :vertical
                                                               [[:transform/translate cx 0]
                                                                [:transform/scale -1 1]
                                                                [:transform/translate (- cx) 0]]
                                                               :horizontal
                                                               [[:transform/translate 0 cy]
                                                                [:transform/scale 1 -1]
                                                                [:transform/translate 0 (- cy)]])]
                                                  [{:node/type :group :group/children children}
                                                   {:node/type :group
                                                    :node/transform mirror
                                                    :group/children children}])
                                                :grid
                                                (let [cols (:symmetry/cols node)
                                                      rows (:symmetry/rows node)
                                                      [dx dy] (:symmetry/spacing node [100 100])]
                                                  (into []
                                                        (for [row (range rows)
                                                              col (range cols)]
                                                          {:node/type :group
                                                           :node/transform
                                                           [[:transform/translate (* col dx) (* row dy)]]
                                                           :group/children children}))))
                                              overrides)}
                                       (:node/opacity node)
                                       (assoc :node/opacity (:node/opacity node))
                                       (:node/transform node)
                                       (assoc :node/transform (:node/transform node))))
               :path/decorated     (let [children (-> (decorator/decorate-path
                                                         (:path/commands node)
                                                         (:decorator/shape node)
                                                         (get node :decorator/spacing 20)
                                                         (get node :decorator/rotate? true))
                                                       (vary/apply-overrides
                                                         (:decorator/overrides node)))]
                                     (cond-> {:node/type      :group
                                              :group/children children}
                                       (:node/opacity node)
                                       (assoc :node/opacity (:node/opacity node))
                                       (:node/transform node)
                                       (assoc :node/transform (:node/transform node))))
               :group              (let [expanded (update node :group/children
                                                          #(mapv expand-node %))]
                                     (if-let [warp-spec (:group/warp expanded)]
                                       (-> (warp/warp-node expanded warp-spec)
                                           (dissoc :group/warp))
                                       expanded))
               node)]
    (let [node (cond
                 (hatch-fill? (:style/fill node))
                 (expand-hatch-fill node)

                 (stipple-fill? (:style/fill node))
                 (expand-stipple-fill node)

                 (= :shape/path (:node/type node))
                 (let [node (apply-distort-transforms node)]
                   (if (:stroke/profile node)
                     (expand-stroke-profile node)
                     node))

                 :else node)]
      (expand-effects node))))

(defn validate-scene!
  "Validates a scene map; throws ex-info with :errors on failure."
  [scene]
  (when-let [errors (validate/validate scene)]
    (throw (ex-info (str "Invalid scene\n" (validate/format-errors errors))
                    {:errors errors}))))

(defn compile
  "Compiles a scene map into concrete IR (legacy path).
  Assumes scene has already been validated (call validate-scene! first)."
  [scene]
  (let [expanded (update scene :image/nodes #(mapv expand-node %))]
    {:ir/size       (:image/size expanded)
     :ir/background (color/resolve-color (:image/background expanded))
     :ir/ops        (into [] (mapcat #(compile-tree % default-ctx))
                           (:image/nodes expanded))}))

(def ^:private generator-node-types
  "Node types that map to generator descriptors in the semantic IR."
  #{:flow-field :contour :scatter :voronoi :delaunay :path/decorated :lsystem})

(defn- normalize-node
  "Like expand-node but preserves semantic constructs as data instead of
  expanding them to geometry. Generators, hatch/stipple fills, and effects
  are kept as-is for semantic lowering.
  Other node types are expanded normally via expand-node."
  [node]
  (let [fill      (:style/fill node)
        node-type (:node/type node)]
    (cond
      ;; Generator node types — preserve for semantic lowering
      (generator-node-types node-type)
      node

      ;; Hatch and stipple fills — preserve as-is
      (hatch-fill? fill)
      node

      (stipple-fill? fill)
      node

      ;; Symmetry — expand children with normalize-node
      (= :symmetry node-type)
      (expand-node (update node :group/children #(mapv normalize-node %)))

      ;; Groups — normalize children
      (= :group node-type)
      (let [expanded (update node :group/children #(mapv normalize-node %))]
        (if-let [warp-spec (:group/warp expanded)]
          (-> (warp/warp-node expanded warp-spec)
              (dissoc :group/warp))
          expanded))

      ;; Everything else goes through the legacy expand-node
      :else
      (expand-node node))))

(defn- node->fill-descriptor
  "Converts a scene-level fill to a semantic fill descriptor."
  [fill]
  (cond
    (nil? fill)                                          nil
    (and (map? fill) (#{:hatch :stipple} (:fill/type fill))) fill
    (vector? fill)                                       {:fill/type :fill/solid :color fill}
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
     :generator/path-commands (:decorator/path node)
     :generator/shape         (:decorator/shape node)
     :generator/spacing       (get node :decorator/spacing 20)
     :generator/rotate?       (get node :decorator/rotate? true)
     :generator/overrides     (:decorator/overrides node)}

    :lsystem
    nil ;; L-systems still go through legacy (complex parameter passing)

    nil))

(defn- scene-node->draw-item
  "Converts a normalized scene node to a semantic IR draw item.
  Handles shapes, generators, and the mapping from scene-level keys."
  [node]
  (let [node-type (:node/type node)]
    ;; Generator nodes
    (if-let [gen (and (generator-node-types node-type)
                      (node->generator node))]
      {:item/generator gen}
      ;; Shape nodes
      (let [geom (case node-type
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

                   ;; Groups and other complex nodes → fall back to legacy
                   nil)
            effects    (node->effects node)
            transforms (:node/transform node)]
        (when geom
          (cond-> {:item/geometry geom}
            (:style/fill node)   (assoc :item/fill (node->fill-descriptor (:style/fill node)))
            (:style/stroke node) (assoc :item/stroke (:style/stroke node))
            (:node/opacity node) (assoc :item/opacity (:node/opacity node))
            (seq effects)        (assoc :item/effects effects)
            transforms           (assoc :item/transforms transforms)))))))

(defn compile-semantic
  "Compiles a scene map into a semantic IR container.
  Preserves hatch/stipple fills and effects as semantic data.
  Use eido.ir.lower/lower to convert to concrete ops."
  [scene]
  (let [bg    (color/resolve-color (:image/background scene))
        size  (:image/size scene)
        nodes (mapv normalize-node (:image/nodes scene))
        items (keep scene-node->draw-item nodes)
        ;; Nodes that couldn't be converted (groups, complex types)
        ;; fall through to the legacy compile path
        fallback-nodes (filter #(nil? (scene-node->draw-item %)) nodes)
        fallback-ops   (when (seq fallback-nodes)
                         (into [] (mapcat #(compile-tree % default-ctx))
                               fallback-nodes))]
    (ir/container size bg (vec items))))

(comment
  (compile {:image/size [800 600]
            :image/background [:color/rgb 255 255 255]
            :image/nodes
            [{:node/type :shape/circle
              :circle/center [400 300]
              :circle/radius 100
              :style/fill {:color [:color/rgb 200 0 0]}}]})
  )
