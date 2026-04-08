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
    [eido.ir :as ir]
    [eido.ir.program :as program])
  (:import
    [java.awt.image BufferedImage]))

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

(defn procedural
  "Creates a procedural fill descriptor.
  program is a program map with :program/body and optional :program/inputs.
  The program is evaluated per-pixel with :uv bound to normalized [0-1] coords."
  [program]
  {:fill/type    :fill/procedural
   :fill/program program})

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

;; --- procedural fill rendering ---

(defn- clamp-byte ^long [^double v]
  (long (Math/max 0.0 (Math/min 255.0 v))))

(defn- color-result->argb
  "Converts a program result to an ARGB int.
  Accepts: {:r :g :b :a}, [r g b], [r g b a], or scalar (grayscale)."
  [result]
  (cond
    (map? result)
    (let [r (clamp-byte (double (:r result)))
          g (clamp-byte (double (:g result)))
          b (clamp-byte (double (:b result)))
          a (clamp-byte (* 255.0 (double (get result :a 1.0))))]
      (unchecked-int (bit-or (bit-shift-left a 24)
                             (bit-shift-left r 16)
                             (bit-shift-left g 8)
                             b)))

    (vector? result)
    (let [r (clamp-byte (double (nth result 0)))
          g (clamp-byte (double (nth result 1)))
          b (clamp-byte (double (nth result 2)))
          a (if (>= (count result) 4)
              (clamp-byte (* 255.0 (double (nth result 3))))
              255)]
      (unchecked-int (bit-or (bit-shift-left a 24)
                             (bit-shift-left r 16)
                             (bit-shift-left g 8)
                             b)))

    (number? result)
    (let [v (clamp-byte (* 255.0 (double result)))]
      (unchecked-int (bit-or (bit-shift-left 255 24)
                             (bit-shift-left v 16)
                             (bit-shift-left v 8)
                             v)))

    :else
    (unchecked-int 0xFF000000)))

(defn evaluate-procedural-fill
  "Evaluates a procedural fill over a bounding box, producing a BufferedImage.
  The program receives :uv as normalized [0..1, 0..1] coordinates."
  ^BufferedImage [fill-spec ^long w ^long h]
  (let [prog (:fill/program fill-spec)
        img  (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)]
    (dotimes [py h]
      (dotimes [px w]
        (let [u (/ (double px) (double w))
              v (/ (double py) (double h))
              env {:uv [u v] :px px :py py :size [w h]}
              result (program/run prog env)
              argb   (color-result->argb result)]
          (.setRGB img px py argb))))
    img))

;; --- procedural fill lowering ---

(defn lower-procedural
  "Lowers a draw item with a procedural fill to concrete ops.
  Evaluates the program over the item's bounds and creates an image fill."
  [item]
  (let [geom  (:item/geometry item)
        ;; Determine bounds for evaluation
        [bx by bw bh] (compile/shape-bounds
                         (geometry->scene-node geom nil nil nil))
        w     (max 1 (long (Math/ceil (double bw))))
        h     (max 1 (long (Math/ceil (double bh))))
        ;; Evaluate program over bounds → BufferedImage
        img   (evaluate-procedural-fill (:item/fill item) w h)
        ;; Create a fill that render.clj knows how to paint
        resolved-fill {:fill/type :procedural-image
                       :image     img
                       :offset    [(double bx) (double by)]}
        ;; Build the node with the resolved fill and compile it
        node  (geometry->scene-node
                geom resolved-fill
                (:item/stroke item)
                (:item/opacity item))]
    (compile/compile-tree node compile/default-ctx)))

;; --- fill type dispatch ---

(defn semantic-fill?
  "Returns true if a fill descriptor requires semantic lowering
  (as opposed to direct resolution to a color/gradient)."
  [fill]
  (and (map? fill)
       (#{:hatch :stipple :fill/hatch :fill/stipple
          :fill/procedural} (:fill/type fill))))
