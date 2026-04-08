(ns eido.ir.fill
  "Semantic fill descriptors and lowering to concrete ops.

  Fill types:
    :fill/solid    — solid color
    :fill/gradient — linear or radial gradient
    :fill/pattern  — tiled pattern
    :fill/hatch    — hatched line pattern
    :fill/stipple  — dot distribution pattern

  Hatch and stipple fills preserve their semantic identity in the IR
  and are expanded to concrete geometry during lowering."
  (:require
    [eido.compile :as compile]
    [eido.ir :as ir]))

;; --- fill constructors ---

(defn solid [color]
  {:fill/type :fill/solid
   :color     color})

(defn gradient [type stops & {:keys [from to center radius]}]
  (cond-> {:fill/type      :fill/gradient
           :gradient/type  type
           :gradient/stops stops}
    from   (assoc :gradient/from from)
    to     (assoc :gradient/to to)
    center (assoc :gradient/center center)
    radius (assoc :gradient/radius radius)))

(defn hatch [opts]
  (merge {:fill/type :hatch} opts))

(defn stipple [opts]
  (merge {:fill/type :stipple} opts))

;; --- geometry → scene node reconstruction ---

(defn geometry->scene-node
  "Reconstructs a scene-level node from a semantic geometry map.
  Used by fill and effect lowering to pass through the existing
  compile expansion pipeline."
  [geom fill stroke opacity]
  (let [base (case (:geometry/type geom)
               :rect
               {:node/type :shape/rect
                :rect/xy   (:rect/xy geom)
                :rect/size (:rect/size geom)}

               :circle
               {:node/type     :shape/circle
                :circle/center (:circle/center geom)
                :circle/radius (:circle/radius geom)}

               :ellipse
               {:node/type      :shape/ellipse
                :ellipse/center (:ellipse/center geom)
                :ellipse/rx     (:ellipse/rx geom)
                :ellipse/ry     (:ellipse/ry geom)}

               :path
               {:node/type     :shape/path
                :path/commands (:path/commands geom)}

               (throw (ex-info "Cannot reconstruct scene node for geometry type"
                               {:geometry/type (:geometry/type geom)})))]
    (cond-> base
      fill    (assoc :style/fill fill)
      stroke  (assoc :style/stroke stroke)
      opacity (assoc :node/opacity opacity))))

;; --- hatch/stipple lowering ---

(defn lower-hatch
  "Lowers a draw item with a hatch fill to concrete ops.
  Uses the existing compile expansion and compilation pipeline."
  [item]
  (let [geom   (:item/geometry item)
        fill   (:item/fill item)
        stroke (:item/stroke item)
        node   (geometry->scene-node geom fill stroke (:item/opacity item))
        group  (compile/expand-hatch-fill node)]
    (compile/compile-tree group compile/default-ctx)))

(defn lower-stipple
  "Lowers a draw item with a stipple fill to concrete ops.
  Uses the existing compile expansion and compilation pipeline."
  [item]
  (let [geom   (:item/geometry item)
        fill   (:item/fill item)
        stroke (:item/stroke item)
        node   (geometry->scene-node geom fill stroke (:item/opacity item))
        group  (compile/expand-stipple-fill node)]
    (compile/compile-tree group compile/default-ctx)))

;; --- fill type dispatch ---

(defn semantic-fill?
  "Returns true if a fill descriptor requires semantic lowering
  (as opposed to direct resolution to a color/gradient)."
  [fill]
  (and (map? fill)
       (#{:hatch :stipple :fill/hatch :fill/stipple} (:fill/type fill))))
