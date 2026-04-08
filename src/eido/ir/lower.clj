(ns eido.ir.lower
  "Lowers semantic IR containers to concrete ops.

  Input:  {:ir/version 1 :ir/size [w h] :ir/background bg
           :ir/passes [{:pass/items [...]}]}
  Output: {:ir/size [w h] :ir/background bg :ir/ops [RectOp ...]}"
  (:require
    [eido.color :as color]
    [eido.ir :as ir]))

;; --- fill resolution ---

(defn- resolve-fill
  "Resolves a semantic fill descriptor to the format concrete ops expect."
  [fill]
  (when fill
    (case (:fill/type fill)
      :fill/solid
      (color/resolve-color (:color fill))

      :fill/gradient
      (let [g fill]
        (cond-> {:gradient/type  (:gradient/type g)
                 :gradient/stops (mapv (fn [[pos c]]
                                         [pos (color/resolve-color c)])
                                       (:gradient/stops g))}
          (:gradient/from g)   (assoc :gradient/from (:gradient/from g))
          (:gradient/to g)     (assoc :gradient/to (:gradient/to g))
          (:gradient/center g) (assoc :gradient/center (:gradient/center g))
          (:gradient/radius g) (assoc :gradient/radius (:gradient/radius g))))

      ;; Already-resolved color map passthrough
      (when (and (:r fill) (:g fill) (:b fill))
        fill))))

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

;; --- geometry → concrete op ---

(defn- lower-item
  "Converts a semantic draw item to a concrete IR op."
  [item]
  (let [geom       (:item/geometry item)
        fill       (resolve-fill (:item/fill item))
        stroke     (resolve-stroke (:item/stroke item))
        opacity    (or (:item/opacity item) 1.0)
        transforms (:item/transforms item)
        clip       (:item/clip item)]
    (case (:geometry/type geom)
      :rect
      (let [[x y] (:rect/xy geom)
            [w h] (:rect/size geom)]
        (ir/->RectOp :rect x y w h (:rect/corner-radius geom)
                      fill
                      (:stroke-color stroke) (:stroke-width stroke)
                      opacity
                      (:stroke-cap stroke) (:stroke-join stroke)
                      (:stroke-dash stroke)
                      transforms clip))

      :circle
      (let [[cx cy] (:circle/center geom)]
        (ir/->CircleOp :circle cx cy (:circle/radius geom)
                        fill
                        (:stroke-color stroke) (:stroke-width stroke)
                        opacity
                        (:stroke-cap stroke) (:stroke-join stroke)
                        (:stroke-dash stroke)
                        transforms clip))

      :ellipse
      (let [[cx cy] (:ellipse/center geom)]
        (ir/->EllipseOp :ellipse cx cy
                         (:ellipse/rx geom) (:ellipse/ry geom)
                         fill
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
                     fill
                     (:stroke-color stroke) (:stroke-width stroke)
                     opacity
                     (:stroke-cap stroke) (:stroke-join stroke)
                     (:stroke-dash stroke)
                     transforms clip))

      :line
      (let [[x1 y1] (:line/from geom)
            [x2 y2] (:line/to geom)]
        (ir/->LineOp :line x1 y1 x2 y2
                      fill
                      (:stroke-color stroke) (:stroke-width stroke)
                      opacity
                      (:stroke-cap stroke) (:stroke-join stroke)
                      (:stroke-dash stroke)
                      transforms clip))

      :path
      (ir/->PathOp :path
                    (mapv compile-command (:path/commands geom))
                    (:path/fill-rule geom)
                    fill
                    (:stroke-color stroke) (:stroke-width stroke)
                    opacity
                    (:stroke-cap stroke) (:stroke-join stroke)
                    (:stroke-dash stroke)
                    transforms clip)

      (throw (ex-info (str "Unknown geometry type: " (:geometry/type geom))
                      {:geometry geom})))))

;; --- container lowering ---

(defn lower
  "Lowers a semantic IR container to the concrete format
  consumed by eido.render and eido.svg."
  [ir-container]
  {:ir/size       (:ir/size ir-container)
   :ir/background (:ir/background ir-container)
   :ir/ops        (into []
                    (mapcat (fn [pass]
                              (mapv lower-item (:pass/items pass))))
                    (:ir/passes ir-container))})
