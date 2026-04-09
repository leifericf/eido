(ns eido.ir.lower
  "Lowers semantic IR containers to concrete ops.

  Input:  {:ir/version 1 :ir/size [w h] :ir/background bg
           :ir/passes [{:pass/items [...]}]}
  Output: {:ir/size [w h] :ir/background bg :ir/ops [RectOp ...]}"
  (:require
    [eido.color :as color]
    [eido.ir :as ir]
    [eido.ir.effect :as effect]
    [eido.ir.fill :as fill]
    [eido.ir.transform :as transform]))

(declare lower-scene-nodes lower-item)

;; --- fill resolution ---

(defn- resolve-fill
  "Resolves a semantic fill descriptor to the format concrete ops expect."
  [f]
  (when f
    (case (:fill/type f)
      :fill/solid
      (color/resolve-color (:color f))

      :fill/gradient
      (cond-> {:gradient/type  (:gradient/type f)
               :gradient/stops (mapv (fn [[pos c]]
                                       [pos (color/resolve-color c)])
                                     (:gradient/stops f))}
        (:gradient/from f)   (assoc :gradient/from (:gradient/from f))
        (:gradient/to f)     (assoc :gradient/to (:gradient/to f))
        (:gradient/center f) (assoc :gradient/center (:gradient/center f))
        (:gradient/radius f) (assoc :gradient/radius (:gradient/radius f)))

      ;; Pattern fill — compile tile nodes to ops
      :fill/pattern
      (when-let [tile-nodes (:pattern/nodes f)]
        (let [[tw th] (:pattern/size f)]
          {:fill/type    :pattern
           :pattern/size [tw th]
           :pattern/ops  (lower-scene-nodes tile-nodes)}))

      ;; Pre-resolved fills (procedural-image) pass through
      :procedural-image f

      ;; Already-resolved color map passthrough
      (when (and (:r f) (:g f) (:b f))
        f))))

(defn- resolve-stroke
  "Resolves stroke map to the field names concrete ops expect."
  [stroke]
  (when stroke
    {:stroke-color (some-> (:color stroke) color/resolve-color)
     :stroke-width (:width stroke)
     :stroke-cap   (:cap stroke)
     :stroke-join  (:join stroke)
     :stroke-dash  (:dash stroke)}))

;; compile-command is now in eido.ir — use ir/compile-command

;; --- single geometry → concrete op ---

(defn- lower-simple-item
  "Converts a draw item with a non-semantic fill to a single concrete IR op."
  [item]
  (let [geom       (:item/geometry item)
        f          (resolve-fill (:item/fill item))
        stroke     (resolve-stroke (:item/stroke item))
        opacity    (or (:item/opacity item) 1.0)
        transforms (:item/transforms item)
        clip       (:item/clip item)]
    (case (:geometry/type geom)
      :rect
      (let [[x y] (:rect/xy geom)
            [w h] (:rect/size geom)]
        (ir/->RectOp :rect x y w h (:rect/corner-radius geom)
                      f
                      (:stroke-color stroke) (:stroke-width stroke)
                      opacity
                      (:stroke-cap stroke) (:stroke-join stroke)
                      (:stroke-dash stroke)
                      transforms clip))

      :circle
      (let [[cx cy] (:circle/center geom)]
        (ir/->CircleOp :circle cx cy (:circle/radius geom)
                        f
                        (:stroke-color stroke) (:stroke-width stroke)
                        opacity
                        (:stroke-cap stroke) (:stroke-join stroke)
                        (:stroke-dash stroke)
                        transforms clip))

      :ellipse
      (let [[cx cy] (:ellipse/center geom)]
        (ir/->EllipseOp :ellipse cx cy
                         (:ellipse/rx geom) (:ellipse/ry geom)
                         f
                         (:stroke-color stroke) (:stroke-width stroke)
                         opacity
                         (:stroke-cap stroke) (:stroke-join stroke)
                         (:stroke-dash stroke)
                         transforms clip))

      :arc
      (let [[cx cy] (:arc/center geom)]
        (ir/->ArcOp :arc cx cy
                     (:arc/rx geom) (:arc/ry geom)
                     (:arc/start geom) (:arc/extent geom)
                     (get geom :arc/mode :open)
                     f
                     (:stroke-color stroke) (:stroke-width stroke)
                     opacity
                     (:stroke-cap stroke) (:stroke-join stroke)
                     (:stroke-dash stroke)
                     transforms clip))

      :line
      (let [[x1 y1] (:line/from geom)
            [x2 y2] (:line/to geom)]
        (ir/->LineOp :line x1 y1 x2 y2
                      f
                      (:stroke-color stroke) (:stroke-width stroke)
                      opacity
                      (:stroke-cap stroke) (:stroke-join stroke)
                      (:stroke-dash stroke)
                      transforms clip))

      :path
      (ir/->PathOp :path
                    (mapv ir/compile-command (:path/commands geom))
                    (:path/fill-rule geom)
                    f
                    (:stroke-color stroke) (:stroke-width stroke)
                    opacity
                    (:stroke-cap stroke) (:stroke-join stroke)
                    (:stroke-dash stroke)
                    transforms clip)

      (throw (ex-info (str "Unknown geometry type: " (:geometry/type geom))
                      {:geometry geom})))))

;; --- scene node → concrete op ---

(defn- resolve-scene-fill
  "Resolves a scene-level fill (color vector, color map, gradient, or
  pre-resolved map) to the format concrete ops expect."
  [fill]
  (when fill
    (cond
      (vector? fill)         (color/resolve-color fill)
      (:gradient/type fill)  (cond-> {:gradient/type  (:gradient/type fill)
                                      :gradient/stops (mapv (fn [[pos c]]
                                                              [pos (color/resolve-color c)])
                                                            (:gradient/stops fill))}
                               (:gradient/from fill)   (assoc :gradient/from (:gradient/from fill))
                               (:gradient/to fill)     (assoc :gradient/to (:gradient/to fill))
                               (:gradient/center fill) (assoc :gradient/center (:gradient/center fill))
                               (:gradient/radius fill) (assoc :gradient/radius (:gradient/radius fill)))
      (:color fill)          (color/resolve-color (:color fill))
      ;; Pre-resolved fills (procedural-image, pattern, etc.) pass through
      (:fill/type fill)      fill
      ;; Already-resolved color map
      (and (:r fill) (:g fill) (:b fill)) fill)))

(defn lower-scene-node
  "Converts a simple scene node (path, circle, rect, etc.) to a concrete op.
  Handles scene-level fill/stroke resolution. For use by IR modules that
  receive scene nodes from feature modules (hatch, stipple, flow, etc.)."
  [node]
  (let [stroke (:style/stroke node)
        fill   (resolve-scene-fill (:style/fill node))
        opacity (get node :node/opacity 1.0)
        transforms (:node/transform node)
        clip    nil
        stroke-color (some-> stroke :color color/resolve-color)
        stroke-width (when stroke (:width stroke))
        stroke-cap   (:cap stroke)
        stroke-join  (:join stroke)
        stroke-dash  (:dash stroke)]
    (case (:node/type node)
      :shape/rect
      (let [[x y] (:rect/xy node)
            [w h] (:rect/size node)]
        (ir/->RectOp :rect x y w h (:rect/corner-radius node)
                      fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash
                      transforms clip))
      :shape/circle
      (let [[cx cy] (:circle/center node)]
        (ir/->CircleOp :circle cx cy (:circle/radius node)
                        fill stroke-color stroke-width opacity
                        stroke-cap stroke-join stroke-dash
                        transforms clip))
      :shape/ellipse
      (let [[cx cy] (:ellipse/center node)]
        (ir/->EllipseOp :ellipse cx cy
                         (:ellipse/rx node) (:ellipse/ry node)
                         fill stroke-color stroke-width opacity
                         stroke-cap stroke-join stroke-dash
                         transforms clip))
      :shape/arc
      (let [[cx cy] (:arc/center node)]
        (ir/->ArcOp :arc cx cy
                     (:arc/rx node) (:arc/ry node)
                     (:arc/start node) (:arc/extent node)
                     (get node :arc/mode :open)
                     fill stroke-color stroke-width opacity
                     stroke-cap stroke-join stroke-dash
                     transforms clip))
      :shape/line
      (let [[x1 y1] (:line/from node)
            [x2 y2] (:line/to node)]
        (ir/->LineOp :line x1 y1 x2 y2
                      fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash
                      transforms clip))
      :shape/path
      (ir/->PathOp :path
                    (mapv ir/compile-command (:path/commands node))
                    (:path/fill-rule node)
                    fill stroke-color stroke-width opacity
                    stroke-cap stroke-join stroke-dash
                    transforms clip)

      ;; Groups — lower children recursively, wrap in BufferOp if needed
      :group
      (let [child-ops (into [] (map lower-scene-node) (:group/children node))
            composite (:group/composite node)
            filt      (:group/filter node)]
        (if (or composite filt)
          (ir/->BufferOp :buffer (or composite :src-over) filt
                         opacity transforms clip child-ops)
          ;; Flat group — just return child ops (handled by caller)
          child-ops))

      (throw (ex-info (str "Cannot lower scene node type: " (:node/type node))
                      {:node/type (:node/type node)})))))

(defn lower-scene-nodes
  "Converts a vector of simple scene nodes to a flat vector of concrete ops."
  [nodes]
  (into [] (mapcat (fn [node]
                     (let [result (lower-scene-node node)]
                       (if (vector? result)
                         result
                         [result]))))
        nodes))

;; --- context for group lowering ---

(def ^:private default-ctx
  {:style {} :opacity 1.0 :transforms [] :clip nil})

(defn- inherit-fill
  "If item has no fill, inherit from context style."
  [item ctx-style]
  (if (and (:style/fill ctx-style) (not (:item/fill item)))
    (let [fill (:style/fill ctx-style)]
      (assoc item :item/fill
        (cond
          (vector? fill)         {:fill/type :fill/solid :color fill}
          (:gradient/type fill)  (merge {:fill/type :fill/gradient} fill)
          (:color fill)          {:fill/type :fill/solid :color (:color fill)}
          :else                  fill)))
    item))

(defn- inherit-stroke
  "If item has no stroke, inherit from context style."
  [item ctx-style]
  (if (and (:style/stroke ctx-style) (not (:item/stroke item)))
    (assoc item :item/stroke (:style/stroke ctx-style))
    item))

;; --- item lowering dispatch ---

(defn- apply-item-pre-transforms
  "If the item has :item/pre-transforms, applies them to the geometry.
  Returns the item with transformed geometry (always :path type)."
  [item]
  (if-let [pre-ts (:item/pre-transforms item)]
    (-> item
        (assoc :item/geometry
               (transform/apply-pre-transforms (:item/geometry item) pre-ts))
        (dissoc :item/pre-transforms))
    item))

(defn- lower-leaf-item
  "Lowers a geometry item with inherited context applied."
  [item ctx]
  (let [item      (-> item
                      (inherit-fill (:style ctx))
                      (inherit-stroke (:style ctx))
                      apply-item-pre-transforms)
        ;; Apply context: multiply opacity, concatenate transforms, set clip
        eff-opacity (* (:opacity ctx) (or (:item/opacity item) 1.0))
        eff-transforms (into (:transforms ctx) (or (:item/transforms item) []))
        item      (assoc item
                    :item/opacity eff-opacity
                    :item/transforms eff-transforms)
        item      (if (:clip ctx)
                    (assoc item :item/clip (:clip ctx))
                    item)
        item-fill (:item/fill item)
        effects   (:item/effects item)]
    (cond
      ;; Semantic fills
      (fill/semantic-fill? item-fill)
      (case (:fill/type item-fill)
        (:hatch :fill/hatch)       (fill/lower-hatch item)
        (:stipple :fill/stipple)   (fill/lower-stipple item)
        :fill/procedural           (fill/lower-procedural item))

      ;; Effects
      (seq effects)
      (effect/lower-effects item)

      ;; Simple geometry
      :else
      [(lower-simple-item item)])))

(defn- make-clip-op
  "Creates a clip op from an IR geometry map."
  [geom]
  (lower-simple-item {:item/geometry geom :item/opacity 1.0 :item/transforms []}))

(defn- lower-item
  "Lowers a semantic draw item to a vector of concrete ops.
  Accepts optional context for group inheritance."
  ([item] (lower-item item default-ctx))
  ([item ctx]
   (cond
     ;; Generator items
     (:item/generator item)
     ((requiring-resolve 'eido.ir.generator/expand-generator) (:item/generator item))

     ;; Group items — thread context to children
     (:item/group item)
     (let [group-opacity (or (:item/opacity item) 1.0)
           group-transforms (or (:item/transforms item) [])
           group-fill (:item/fill item)
           group-stroke (:item/stroke item)
           clip-geom (:item/clip-geom item)
           composite (:item/composite item)
           filt (:item/filter item)
           ;; Build child context
           child-style (cond-> (:style ctx)
                         group-fill   (assoc :style/fill
                                       (let [f group-fill]
                                         (if (map? f)
                                           (case (:fill/type f)
                                             :fill/solid (:color f)
                                             f)
                                           f)))
                         group-stroke (assoc :style/stroke group-stroke))
           child-clip  (if clip-geom
                         (make-clip-op clip-geom)
                         (:clip ctx))
           child-transforms (into (:transforms ctx) group-transforms)
           child-opacity (* (:opacity ctx) group-opacity)
           child-ctx {:style      child-style
                      :opacity    (if (or composite filt) 1.0 child-opacity)
                      :transforms (if (or composite filt) [] child-transforms)
                      :clip       child-clip}
           child-ops (into [] (mapcat #(lower-item % child-ctx))
                           (:item/group item))]
       (if (or composite filt)
         ;; BufferOp wrapping
         [(ir/->BufferOp :buffer (or composite :src-over) filt
                         child-opacity child-transforms (:clip ctx)
                         child-ops)]
         ;; Flat group
         child-ops))

     ;; Leaf geometry items
     :else
     (lower-leaf-item item ctx))))

;; --- pass lowering ---

(defn- effect->filter-spec
  "Converts an effect descriptor to a filter spec vector for BufferOp."
  [eff]
  (case (:effect/type eff)
    :effect/blur      [:blur (:effect/radius eff)]
    :effect/grain     [:grain (:effect/amount eff) (:effect/seed eff)]
    :effect/posterize [:posterize (:effect/levels eff)]
    :effect/duotone   [:duotone (:effect/color-a eff) (:effect/color-b eff)]
    :effect/halftone  [:halftone (:effect/dot-size eff) (:effect/angle eff)]))

(defn- lower-pass
  "Lowers a single pass to concrete ops."
  [pass preceding-ops]
  (case (:pass/type pass)
    :draw-geometry
    (into [] (mapcat lower-item) (:pass/items pass))

    :effect-pass
    ;; Wrap all preceding ops in a BufferOp with the filter
    (let [filter-spec (effect->filter-spec (:pass/effect pass))]
      [(ir/->BufferOp :buffer :src-over filter-spec 1.0 nil nil
                      (or preceding-ops []))])

    :program-pass
    ;; Program passes evaluate per-pixel — produce a procedural image
    ;; that covers the full canvas
    (let [prog   (:pass/program pass)
          size   (:resource/size (get (:ir/resources pass) (:pass/input pass)))
          [w h]  (or size [100 100])]
      ;; For now, program passes are a stretch — just pass through
      preceding-ops)

    ;; Unknown pass type — pass through
    preceding-ops))

;; --- container lowering ---

(defn lower
  "Lowers a semantic IR container to the concrete format
  consumed by eido.engine.render and eido.engine.svg."
  [ir-container]
  (let [passes (:ir/passes ir-container)
        ops    (reduce (fn [acc pass]
                         (let [pass-ops (lower-pass
                                          (assoc pass :ir/resources (:ir/resources ir-container))
                                          acc)]
                           (if (= :effect-pass (:pass/type pass))
                             ;; Effect passes wrap preceding ops
                             pass-ops
                             ;; Draw passes append
                             (into acc pass-ops))))
                       []
                       passes)]
    {:ir/size       (:ir/size ir-container)
     :ir/background (:ir/background ir-container)
     :ir/ops        ops}))
