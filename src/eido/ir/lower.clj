(ns eido.ir.lower
  "Lowers semantic IR containers to concrete ops.

  Input:  {:ir/version 1 :ir/size [w h] :ir/background bg
           :ir/passes [{:pass/items [...]}]}
  Output: {:ir/size [w h] :ir/background bg :ir/ops [RectOp ...]}"
  (:require
    [eido.color :as color]
    [eido.ir :as ir]
    [eido.ir.effect :as effect]
    [eido.ir.fill :as fill]))

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

(defn- lower-item
  "Lowers a semantic draw item to a vector of concrete ops.
  Dispatches to specialized lowering for semantic fills and effects."
  [item]
  (let [item-fill (:item/fill item)
        effects   (:item/effects item)]
    (cond
      ;; Semantic fills (hatch/stipple) expand to many ops
      (fill/semantic-fill? item-fill)
      (case (:fill/type item-fill)
        (:hatch :fill/hatch)       (fill/lower-hatch item)
        (:stipple :fill/stipple)   (fill/lower-stipple item)
        :fill/procedural           (fill/lower-procedural item))

      ;; Effects wrap the item in shadow/glow buffer groups
      (seq effects)
      (effect/lower-effects item)

      ;; Simple geometry with simple fill → single op
      :else
      [(lower-simple-item item)])))

;; --- container lowering ---

(defn lower
  "Lowers a semantic IR container to the concrete format
  consumed by eido.render and eido.svg."
  [ir-container]
  {:ir/size       (:ir/size ir-container)
   :ir/background (:ir/background ir-container)
   :ir/ops        (into []
                    (mapcat (fn [pass]
                              (into [] (mapcat lower-item) (:pass/items pass))))
                    (:ir/passes ir-container))})
