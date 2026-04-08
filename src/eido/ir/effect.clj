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
    [eido.compile :as compile]
    [eido.ir.fill :as fill]))

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
  "Returns true for effects that create additional geometry (shadow/glow)."
  [effect]
  (#{:effect/shadow :effect/glow} (:effect/type effect)))

(defn- filter-effect?
  "Returns true for effects that apply image-space filters."
  [effect]
  (#{:effect/blur :effect/grain :effect/posterize
     :effect/duotone :effect/halftone} (:effect/type effect)))

;; --- effect lowering ---

(defn- effect->scene-keys
  "Converts an effect descriptor to the scene-node keys the existing
  compile expansion expects."
  [effect]
  (case (:effect/type effect)
    :effect/shadow {:effect/shadow {:dx      (:effect/dx effect)
                                    :dy      (:effect/dy effect)
                                    :blur    (:effect/blur effect)
                                    :color   (:effect/color effect)
                                    :opacity (:effect/opacity effect)}}
    :effect/glow   {:effect/glow {:blur    (:effect/blur effect)
                                  :color   (:effect/color effect)
                                  :opacity (:effect/opacity effect)}}))

(defn- effect->filter-spec
  "Converts a filter effect descriptor to the filter spec vector
  that render.clj/apply-filter expects."
  [effect]
  (case (:effect/type effect)
    :effect/blur      [:blur (:effect/radius effect)]
    :effect/grain     [:grain (:effect/amount effect) (:effect/seed effect)]
    :effect/posterize [:posterize (:effect/levels effect)]
    :effect/duotone   [:duotone (:effect/color-a effect) (:effect/color-b effect)]
    :effect/halftone  [:halftone (:effect/dot-size effect) (:effect/angle effect)]))

(defn lower-effects
  "Lowers a draw item with effects to concrete ops.
  Handles both geometry effects (shadow/glow) and filter effects
  (blur/grain/posterize/duotone/halftone)."
  [item]
  (let [effects     (:item/effects item)
        geom-fx     (filter geometry-effect? effects)
        filter-fx   (filter filter-effect? effects)
        geom        (:item/geometry item)
        scene-node  (fill/geometry->scene-node
                      geom
                      (:item/fill item)
                      (:item/stroke item)
                      (:item/opacity item))
        ;; Build base node with geometry effects (shadow/glow)
        with-geom-fx (reduce (fn [node effect]
                               (merge node (effect->scene-keys effect)))
                             scene-node
                             geom-fx)
        shadow      (:effect/shadow with-geom-fx)
        glow        (:effect/glow with-geom-fx)
        clean       (dissoc with-geom-fx :effect/shadow :effect/glow)
        children    (cond-> []
                      shadow (conj (compile/make-shadow-node clean shadow))
                      glow   (conj (compile/make-glow-node clean glow))
                      true   (conj clean))
        ;; Wrap in group for geometry effects
        base-group  (if (seq geom-fx)
                      {:node/type      :group
                       :group/children children}
                      scene-node)
        ;; Wrap in filter groups for each filter effect
        with-filters (reduce (fn [node fx]
                               {:node/type      :group
                                :group/filter   (effect->filter-spec fx)
                                :group/children [node]})
                             base-group
                             filter-fx)]
    (compile/compile-tree with-filters compile/default-ctx)))
