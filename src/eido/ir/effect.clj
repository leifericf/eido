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

(defn lower-effects
  "Lowers a draw item with effects to concrete ops.
  Reconstructs a scene node, applies effect expansion, and compiles."
  [item]
  (let [geom    (:item/geometry item)
        scene-node (fill/geometry->scene-node
                     geom
                     (:item/fill item)
                     (:item/stroke item)
                     (:item/opacity item))
        ;; Merge all effect descriptors onto the scene node
        with-effects (reduce (fn [node effect]
                               (merge node (effect->scene-keys effect)))
                             scene-node
                             (:item/effects item))
        ;; Build the shadow/glow group structure
        shadow (:effect/shadow with-effects)
        glow   (:effect/glow with-effects)
        clean  (dissoc with-effects :effect/shadow :effect/glow)
        children (cond-> []
                   shadow (conj (compile/make-shadow-node clean shadow))
                   glow   (conj (compile/make-glow-node clean glow))
                   true   (conj clean))
        group  {:node/type      :group
                :group/children children}]
    (compile/compile-tree group compile/default-ctx)))
