(ns eido.ir.effect
  "Semantic effect descriptors and lowering to concrete ops.

  Effect types:
    :effect/shadow  — drop shadow with offset, blur, color
    :effect/glow    — glow (shadow with no offset)
    :effect/blur    — gaussian blur
    :effect/grain   — film grain
    :effect/posterize — color quantization
    :effect/duotone  — two-color mapping
    :effect/halftone — dot-screen halftone

  Effects are stored as descriptors in draw items and expanded
  to BufferOp wrappers during lowering."
  (:require
    [eido.color :as color]
    [eido.ir :as ir]))
  ;; NOTE: no dependency on eido.engine.compile — effect lowering is self-sufficient

;; --- effect constructors ---

(defn shadow [& {:keys [dx dy blur color opacity]
                 :or {dx 3 dy 3 blur 5 opacity 0.5}}]
  {:effect/type    :effect/shadow
   :effect/dx      dx
   :effect/dy      dy
   :effect/blur    blur
   :effect/color   color
   :effect/opacity opacity})

(defn glow [& {:keys [blur color opacity]
               :or {blur 8 opacity 0.7}}]
  {:effect/type    :effect/glow
   :effect/blur    blur
   :effect/color   color
   :effect/opacity opacity})

(defn blur [& {:keys [radius] :or {radius 5}}]
  {:effect/type   :effect/blur
   :effect/radius radius})

(defn grain [& {:keys [amount seed] :or {amount 30}}]
  {:effect/type   :effect/grain
   :effect/amount amount
   :effect/seed   seed})

(defn posterize [& {:keys [levels] :or {levels 4}}]
  {:effect/type   :effect/posterize
   :effect/levels levels})

(defn duotone [& {:keys [color-a color-b]}]
  {:effect/type    :effect/duotone
   :effect/color-a color-a
   :effect/color-b color-b})

(defn halftone [& {:keys [dot-size angle] :or {dot-size 6 angle 45}}]
  {:effect/type     :effect/halftone
   :effect/dot-size dot-size
   :effect/angle    angle})

;; --- effect classification ---

(defn- geometry-effect?
  [effect]
  (#{:effect/shadow :effect/glow} (:effect/type effect)))

(defn- filter-effect?
  [effect]
  (#{:effect/blur :effect/grain :effect/posterize
     :effect/duotone :effect/halftone} (:effect/type effect)))

;; --- concrete op construction ---

(defn- item->base-op
  "Converts a draw item to a concrete op (simplified — for shadow/glow copies).
  Handles basic geometry types with fill, stroke, opacity."
  [geom fill stroke opacity]
  (let [resolve-fn   (requiring-resolve 'eido.ir.lower/resolve-fill)
        resolved-fill (when fill
                        (cond
                          (vector? fill)                          (color/resolve-color fill)
                          (:fill/type fill)                       (resolve-fn fill)
                          (:color fill)                           (color/resolve-color (:color fill))
                          (and (:r fill) (:g fill) (:b fill))     fill
                          :else                                   nil))
        stroke-color  (some-> stroke :color color/resolve-color)
        stroke-width  (when stroke (:width stroke))
        stroke-cap    (:cap stroke)
        stroke-join   (:join stroke)
        stroke-dash   (:dash stroke)]
    (case (:geometry/type geom)
      :rect
      (let [[x y] (:rect/xy geom)
            [w h] (:rect/size geom)]
        (ir/->RectOp :rect x y w h (:rect/corner-radius geom)
                      resolved-fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash nil nil))
      :circle
      (let [[cx cy] (:circle/center geom)]
        (ir/->CircleOp :circle cx cy (:circle/radius geom)
                        resolved-fill stroke-color stroke-width opacity
                        stroke-cap stroke-join stroke-dash nil nil))
      :ellipse
      (let [[cx cy] (:ellipse/center geom)]
        (ir/->EllipseOp :ellipse cx cy
                         (:ellipse/rx geom) (:ellipse/ry geom)
                         resolved-fill stroke-color stroke-width opacity
                         stroke-cap stroke-join stroke-dash nil nil))
      :path
      (ir/->PathOp :path
                    (mapv ir/compile-command (:path/commands geom))
                    (:path/fill-rule geom)
                    resolved-fill stroke-color stroke-width opacity
                    stroke-cap stroke-join stroke-dash nil nil)
      :line
      (let [[x1 y1] (:line/from geom)
            [x2 y2] (:line/to geom)]
        (ir/->LineOp :line x1 y1 x2 y2
                      resolved-fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash nil nil))
      :arc
      (let [[cx cy] (:arc/center geom)]
        (ir/->ArcOp :arc cx cy
                     (:arc/rx geom) (:arc/ry geom)
                     (:arc/start geom) (:arc/extent geom)
                     (get geom :arc/mode :open)
                     resolved-fill stroke-color stroke-width opacity
                     stroke-cap stroke-join stroke-dash nil nil)))))

(defn- effect->filter-spec
  "Converts a filter effect descriptor to a filter spec vector."
  [effect]
  (case (:effect/type effect)
    :effect/blur      [:blur (:effect/radius effect)]
    :effect/grain     [:grain (:effect/amount effect) (:effect/seed effect)]
    :effect/posterize [:posterize (:effect/levels effect)]
    :effect/duotone   [:duotone (:effect/color-a effect) (:effect/color-b effect)]
    :effect/halftone  [:halftone (:effect/dot-size effect) (:effect/angle effect)]))

;; --- effect lowering ---

(defn lower-effects
  "Lowers a draw item with effects to concrete ops.
  Builds BufferOp wrappers directly for shadow/glow and filter effects."
  [item]
  (let [effects    (:item/effects item)
        geom-fx    (filter geometry-effect? effects)
        filter-fx  (filter filter-effect? effects)
        geom       (:item/geometry item)
        fill       (:item/fill item)
        stroke     (:item/stroke item)
        opacity    (or (:item/opacity item) 1.0)
        ;; Base op for the item itself
        base-op    (item->base-op geom fill stroke opacity)
        ;; Build shadow/glow ops
        shadow-ops (mapcat
                     (fn [fx]
                       (let [shadow-fill (:effect/color fx)
                             shadow-op   (item->base-op geom shadow-fill nil 1.0)
                             dx          (get fx :effect/dx 0)
                             dy          (get fx :effect/dy 0)
                             blur-r      (:effect/blur fx)
                             fx-opacity  (get fx :effect/opacity 0.5)
                             translate   (when (or (not= 0 dx) (not= 0 dy))
                                           [[:translate dx dy]])]
                         [(ir/->BufferOp :buffer :src-over
                                         [:blur blur-r]
                                         fx-opacity
                                         translate nil
                                         [shadow-op])]))
                     geom-fx)
        ;; Combine: shadow ops first, then the base item
        all-ops    (into (vec shadow-ops) [base-op])
        ;; Wrap in filter BufferOps for each filter effect
        result     (reduce (fn [ops fx]
                             [(ir/->BufferOp :buffer :src-over
                                             (effect->filter-spec fx)
                                             1.0 nil nil
                                             (vec ops))])
                           all-ops
                           filter-fx)]
    (if (vector? result) result [result])))
