(ns eido.ir.transform
  "Semantic transform descriptors and lowering.

  Transform types:
    :transform/distort — noise/wave/roughen/jitter displacement on paths
    :transform/warp    — wave/twist/fisheye/bulge/bend coordinate warp
    :transform/morph   — interpolation between two path command sets

  Transforms are stored on draw items as :item/pre-transforms and applied
  to geometry before coordinate transforms during lowering."
  (:require
    [eido.distort :as distort]
    [eido.morph :as morph]
    [eido.warp :as warp]))

;; --- transform constructors ---

(defn distortion
  "Creates a distort transform descriptor.
  method: :noise | :wave | :roughen | :jitter
  opts: method-specific params (see eido.distort)."
  [method opts]
  (merge {:transform/type   :transform/distort
          :distort/method   method}
         opts))

(defn warp-transform
  "Creates a warp transform descriptor.
  method: :wave | :twist | :fisheye | :bulge | :bend
  params: method-specific params (see eido.warp)."
  [method params]
  (merge {:transform/type :transform/warp
          :warp/method    method}
         params))

(defn morph-transform
  "Creates a morph transform descriptor.
  target: path commands to morph toward.
  t: interpolation parameter (0.0 = source, 1.0 = target)."
  [target t]
  {:transform/type :transform/morph
   :morph/target   target
   :morph/t        t})

;; --- geometry → path commands ---

(defn- geometry->path-commands
  "Converts an IR geometry map to path commands for transforms that need them.
  Uses warp/shape->path-commands for shape-to-path conversion."
  [geom]
  (case (:geometry/type geom)
    :path (:path/commands geom)
    ;; Convert shape geometry to a scene node, then to path commands
    (let [scene-node (case (:geometry/type geom)
                       :rect    {:node/type :shape/rect
                                 :rect/xy   (:rect/xy geom)
                                 :rect/size (:rect/size geom)}
                       :circle  {:node/type     :shape/circle
                                 :circle/center (:circle/center geom)
                                 :circle/radius (:circle/radius geom)}
                       :ellipse {:node/type      :shape/ellipse
                                 :ellipse/center (:ellipse/center geom)
                                 :ellipse/rx     (:ellipse/rx geom)
                                 :ellipse/ry     (:ellipse/ry geom)}
                       :line    {:node/type :shape/line
                                 :line/from (:line/from geom)
                                 :line/to   (:line/to geom)}
                       (throw (ex-info "Cannot convert geometry to path for transform"
                                       {:geometry/type (:geometry/type geom)})))]
      (warp/shape->path-commands scene-node))))

;; --- transform application ---

(defn- apply-distort
  "Applies distort transform to path commands."
  [commands transform]
  (let [opts (merge {:type (:distort/method transform)}
                    (dissoc transform :transform/type :distort/method))]
    (distort/distort-commands commands opts)))

(defn- apply-warp
  "Applies warp transform to path commands."
  [commands transform]
  (let [spec (merge {:type (:warp/method transform)}
                    (dissoc transform :transform/type :warp/method))]
    (warp/warp-commands commands spec)))

(defn- apply-morph
  "Applies morph transform — blends commands toward target at t."
  [commands transform]
  (morph/morph-auto commands (:morph/target transform) (:morph/t transform)))

(defn apply-pre-transform
  "Applies a single pre-transform to path commands."
  [commands transform]
  (case (:transform/type transform)
    :transform/distort (apply-distort commands transform)
    :transform/warp    (apply-warp commands transform)
    :transform/morph   (apply-morph commands transform)
    (throw (ex-info (str "Unknown pre-transform type: " (:transform/type transform))
                    {:transform transform}))))

(defn apply-pre-transforms
  "Applies a sequence of pre-transforms to a geometry map.
  Returns a modified geometry map (always :path type after transforms)."
  [geom pre-transforms]
  (let [commands (geometry->path-commands geom)
        result   (reduce apply-pre-transform commands pre-transforms)]
    {:geometry/type :path
     :path/commands result}))
