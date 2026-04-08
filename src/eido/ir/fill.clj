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
    [eido.color :as color]
    [eido.hatch :as hatch]
    [eido.ir :as ir]
    [eido.ir.program :as program]
    [eido.stipple :as stipple])
  ;; NOTE: no dependency on eido.compile — fill lowering is self-sufficient
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

(defn- geometry->scene-node
  "Reconstructs a scene-level node from a semantic geometry map.
  Used internally for converting IR geometry to scene nodes for
  hatch/stipple generators and procedural fill lowering."
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

;; --- scene node → concrete op (inline, avoids circular dep with lower) ---

(defn- scene-node->op
  "Converts a simple scene node to a concrete op.
  Used by hatch/stipple lowering for the generated line/circle nodes."
  [node & {:keys [clip]}]
  (let [stroke (:style/stroke node)
        fill   (when-let [f (:style/fill node)]
                 (cond
                   (vector? f)                          (color/resolve-color f)
                   (:color f)                           (color/resolve-color (:color f))
                   (and (:r f) (:g f) (:b f))           f
                   (:fill/type f)                       f
                   :else                                nil))
        opacity    (get node :node/opacity 1.0)
        stroke-clr (some-> stroke :color color/resolve-color)
        stroke-w   (when stroke (:width stroke))
        stroke-cap (:cap stroke)
        stroke-join (:join stroke)
        stroke-dash (:dash stroke)]
    (case (:node/type node)
      :shape/path
      (ir/->PathOp :path
                    (mapv ir/compile-command (:path/commands node))
                    (:path/fill-rule node)
                    fill stroke-clr stroke-w opacity
                    stroke-cap stroke-join stroke-dash
                    nil clip)
      :shape/circle
      (let [[cx cy] (:circle/center node)]
        (ir/->CircleOp :circle cx cy (:circle/radius node)
                        fill stroke-clr stroke-w opacity
                        stroke-cap stroke-join stroke-dash
                        nil clip))
      :shape/rect
      (let [[x y] (:rect/xy node)
            [w h] (:rect/size node)]
        (ir/->RectOp :rect x y w h (:rect/corner-radius node)
                      fill stroke-clr stroke-w opacity
                      stroke-cap stroke-join stroke-dash
                      nil clip))
      ;; Fallback: skip unknown types
      nil)))

(defn- geometry->clip-op
  "Creates a clip op from an IR geometry map (shape boundary without fill/stroke)."
  [geom]
  (let [node (geometry->scene-node geom nil nil nil)]
    (scene-node->op node)))

;; --- hatch/stipple lowering ---

(defn lower-hatch
  "Lowers a draw item with a hatch fill to concrete ops.
  Calls hatch/hatch-fill->nodes directly and converts to concrete ops."
  [item]
  (let [geom      (:item/geometry item)
        fill-spec (:item/fill item)
        stroke    (:item/stroke item)
        [bx by bw bh] (ir/geometry-bounds geom)
        bg-fill   (:hatch/background fill-spec)
        clip-op   (geometry->clip-op geom)
        ;; Generate hatch line nodes
        hatch-nodes (hatch/hatch-fill->nodes bx by bw bh fill-spec)
        ;; Convert each hatch line to a concrete op with clip
        hatch-ops (into [] (keep #(scene-node->op % :clip clip-op)) hatch-nodes)
        ;; Background fill (if specified)
        bg-ops    (when bg-fill
                    (let [bg-node (geometry->scene-node geom bg-fill nil nil)]
                      [(scene-node->op bg-node :clip clip-op)]))
        ;; Stroke outline (if specified)
        stroke-ops (when stroke
                     (let [stroke-node (geometry->scene-node geom nil stroke nil)]
                       [(scene-node->op stroke-node)]))]
    (into [] (concat bg-ops hatch-ops stroke-ops))))

(defn lower-stipple
  "Lowers a draw item with a stipple fill to concrete ops.
  Calls stipple/stipple-fill->nodes directly and converts to concrete ops."
  [item]
  (let [geom      (:item/geometry item)
        fill-spec (:item/fill item)
        stroke    (:item/stroke item)
        [bx by bw bh] (ir/geometry-bounds geom)
        bg-fill   (:stipple/background fill-spec)
        clip-op   (geometry->clip-op geom)
        ;; Generate stipple dot nodes
        dot-nodes (stipple/stipple-fill->nodes bx by bw bh fill-spec)
        ;; Convert each dot to a concrete op with clip
        dot-ops   (into [] (keep #(scene-node->op % :clip clip-op)) dot-nodes)
        ;; Background fill (if specified)
        bg-ops    (when bg-fill
                    (let [bg-node (geometry->scene-node geom bg-fill nil nil)]
                      [(scene-node->op bg-node :clip clip-op)]))
        ;; Stroke outline (if specified)
        stroke-ops (when stroke
                     (let [stroke-node (geometry->scene-node geom nil stroke nil)]
                       [(scene-node->op stroke-node)]))]
    (into [] (concat bg-ops dot-ops stroke-ops))))

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
        [bx by bw bh] (ir/geometry-bounds geom)
        w     (max 1 (long (Math/ceil (double bw))))
        h     (max 1 (long (Math/ceil (double bh))))
        img   (evaluate-procedural-fill (:item/fill item) w h)
        resolved-fill {:fill/type :procedural-image
                       :image     img
                       :offset    [(double bx) (double by)]}
        node  (geometry->scene-node geom resolved-fill
                (:item/stroke item) (:item/opacity item))]
    [(scene-node->op node)]))

;; --- fill type dispatch ---

(defn semantic-fill?
  "Returns true if a fill descriptor requires semantic lowering
  (as opposed to direct resolution to a color/gradient)."
  [fill]
  (and (map? fill)
       (#{:hatch :stipple :fill/hatch :fill/stipple
          :fill/procedural} (:fill/type fill))))
