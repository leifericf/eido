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
    [eido.ir.generator :as generator]
    [eido.ir.transform :as transform]))

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

;; --- path command compilation ---

(defn- compile-command
  "Flattens scene path command to concrete IR format.
  [:move-to [x y]] → [:move-to x y]"
  [command]
  (let [cmd (nth command 0)]
    (case cmd
      :move-to  (let [[x y] (nth command 1)] [:move-to x y])
      :line-to  (let [[x y] (nth command 1)] [:line-to x y])
      :curve-to (let [[cx1 cy1] (nth command 1)
                      [cx2 cy2] (nth command 2)
                      [x y]     (nth command 3)]
                  [:curve-to cx1 cy1 cx2 cy2 x y])
      :quad-to  (let [[cpx cpy] (nth command 1)
                      [x y]     (nth command 2)]
                  [:quad-to cpx cpy x y])
      :close    [:close])))

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
                    (mapv compile-command (:path/commands geom))
                    (:path/fill-rule geom)
                    f
                    (:stroke-color stroke) (:stroke-width stroke)
                    opacity
                    (:stroke-cap stroke) (:stroke-join stroke)
                    (:stroke-dash stroke)
                    transforms clip)

      (throw (ex-info (str "Unknown geometry type: " (:geometry/type geom))
                      {:geometry geom})))))

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

(defn- lower-item
  "Lowers a semantic draw item to a vector of concrete ops.
  Dispatches to specialized lowering for generators, fills, and effects."
  [item]
  (if-let [gen (:item/generator item)]
    ;; Generator items expand to many ops via feature module functions
    (generator/expand-generator gen)
    ;; Geometry items go through transforms, fills, effects
    (let [item      (apply-item-pre-transforms item)
          item-fill (:item/fill item)
          effects   (:item/effects item)]
      (cond
        ;; Semantic fills (hatch/stipple/procedural) expand to many ops
        (fill/semantic-fill? item-fill)
        (case (:fill/type item-fill)
          (:hatch :fill/hatch)       (fill/lower-hatch item)
          (:stipple :fill/stipple)   (fill/lower-stipple item)
          :fill/procedural           (fill/lower-procedural item))

        ;; Effects wrap the item in shadow/glow/filter buffer groups
        (seq effects)
        (effect/lower-effects item)

        ;; Simple geometry with simple fill → single op
        :else
        [(lower-simple-item item)]))))

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
  consumed by eido.render and eido.svg."
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
