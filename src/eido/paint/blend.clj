(ns ^{:stability :provisional} eido.paint.blend
  "Paint-specific blend modes for the paint engine.

  Standard source-over is the default. Additional modes:
    :glazed      — max blend prevents over-saturation (markers)
    :opaque      — strong coverage (oil, acrylic)
    :erase       — removes paint from the surface
    :subtractive — pigment mixing (blue+yellow=green)"
  (:require
    [eido.color :as color]))

;; --- blend operations ---
;; All work in premultiplied RGBA float space.

(defn blend-source-over
  "Standard source-over compositing. Returns [r g b a]."
  [[^double sr ^double sg ^double sb ^double sa]
   [^double dr ^double dg ^double db ^double da]]
  (let [inv-sa (- 1.0 sa)]
    [(+ sr (* dr inv-sa))
     (+ sg (* dg inv-sa))
     (+ sb (* db inv-sa))
     (+ sa (* da inv-sa))]))

(defn blend-multiply
  "Multiply blend — darkens based on both colors."
  [[^double sr ^double sg ^double sb ^double sa]
   [^double dr ^double dg ^double db ^double da]]
  (let [inv-sa (- 1.0 sa)]
    [(+ (* sr dr) (* dr inv-sa))
     (+ (* sg dg) (* dg inv-sa))
     (+ (* sb db) (* db inv-sa))
     (+ sa (* da inv-sa))]))

(defn blend-erase
  "Erase mode — reduces destination alpha by source alpha."
  [[^double _sr ^double _sg ^double _sb ^double sa]
   [^double dr ^double dg ^double db ^double da]]
  (let [remaining (Math/max 0.0 (- da sa))]
    (if (> remaining 0.0)
      (let [scale (/ remaining da)]
        [(* dr scale) (* dg scale) (* db scale) remaining])
      [0.0 0.0 0.0 0.0])))

(defn blend-glazed
  "Glazed blend — uses max of source and destination per channel.
  Prevents over-saturation within a single stroke while allowing
  multiple strokes to layer. Characteristic of marker and ink wash."
  [[^double sr ^double sg ^double sb ^double sa]
   [^double dr ^double dg ^double db ^double da]]
  [(Math/max sr dr)
   (Math/max sg dg)
   (Math/max sb db)
   (Math/max sa da)])

(defn blend-opaque
  "Opaque blend — source replaces destination weighted by source alpha.
  Creates strong coverage that obscures underlying paint."
  [[^double sr ^double sg ^double sb ^double sa]
   [^double dr ^double dg ^double db ^double da]]
  (if (> sa 0.99)
    ;; Fully opaque — just replace
    [sr sg sb sa]
    ;; Weighted replace with higher alpha contribution
    (let [weight (Math/min 1.0 (* sa 1.5))
          inv-w  (- 1.0 weight)]
      [(+ (* sr weight) (* dr inv-w))
       (+ (* sg weight) (* dg inv-w))
       (+ (* sb weight) (* db inv-w))
       (Math/min 1.0 (+ (* sa weight) (* da inv-w)))])))

(defn blend-subtractive
  "Subtractive pigment blend — simulates how real paint colors mix.
  When blue paint meets yellow paint, the result is green.

  Blends between source-over (for transparency) and reflectance-space
  mixing (for color interaction), weighted by destination paint thickness.
  This avoids the muddiness of pure geometric-mean mixing at low alpha."
  [[^double sr ^double sg ^double sb ^double sa]
   [^double dr ^double dg ^double db ^double da]]
  (if (< da 0.005)
    ;; No existing paint — standard deposit
    [sr sg sb sa]
    (if (< sa 0.005)
      ;; No source paint — keep destination
      [dr dg db da]
      (let [;; Un-premultiply both (with safe floor)
            inv-sa (/ 1.0 (Math/max 0.01 sa))
            inv-da (/ 1.0 (Math/max 0.01 da))
            src-r (Math/min 1.0 (* sr inv-sa))
            src-g (Math/min 1.0 (* sg inv-sa))
            src-b (Math/min 1.0 (* sb inv-sa))
            dst-r (Math/min 1.0 (* dr inv-da))
            dst-g (Math/min 1.0 (* dg inv-da))
            dst-b (Math/min 1.0 (* db inv-da))
            ;; How much subtractive mixing to apply:
            ;; Even thin paint should mix subtractively — that's the point
            ;; of choosing this blend mode. Scale aggressively.
            sub-weight (Math/min 1.0 (* da 5.0))
            ;; Mix ratio for the subtractive component
            ;; Based on relative paint amounts
            mix-t (/ sa (+ sa da))
            ;; Subtractive result (reflectance multiplication)
            ^doubles sub-mixed (color/mix-subtractive
                                 [dst-r dst-g dst-b]
                                 [src-r src-g src-b]
                                 mix-t)
            sub-r (aget sub-mixed 0)
            sub-g (aget sub-mixed 1)
            sub-b (aget sub-mixed 2)
            ;; Additive result (standard source-over, un-premultiplied)
            inv-src-a (- 1.0 sa)
            add-a (+ sa (* da inv-src-a))
            add-r (if (> add-a 0.001)
                    (/ (+ sr (* dr inv-src-a)) add-a)
                    src-r)
            add-g (if (> add-a 0.001)
                    (/ (+ sg (* dg inv-src-a)) add-a)
                    src-g)
            add-b (if (> add-a 0.001)
                    (/ (+ sb (* db inv-src-a)) add-a)
                    src-b)
            ;; Blend between additive and subtractive by paint thickness
            w sub-weight
            inv-w (- 1.0 w)
            mr (+ (* sub-r w) (* add-r inv-w))
            mg (+ (* sub-g w) (* add-g inv-w))
            mb (+ (* sub-b w) (* add-b inv-w))
            ;; Combined alpha
            out-a (Math/min 1.0 (+ sa (* da inv-src-a)))
            ;; Re-premultiply
            out-r (* (Math/max 0.0 (Math/min 1.0 mr)) out-a)
            out-g (* (Math/max 0.0 (Math/min 1.0 mg)) out-a)
            out-b (* (Math/max 0.0 (Math/min 1.0 mb)) out-a)]
        [out-r out-g out-b out-a]))))

;; --- dispatcher ---

(defn blend
  "Blends source onto destination using the specified mode.
  Returns [r g b a] in premultiplied float space."
  [mode src dst]
  (case mode
    :source-over (blend-source-over src dst)
    :multiply    (blend-multiply src dst)
    :erase       (blend-erase src dst)
    :glazed      (blend-glazed src dst)
    :opaque      (blend-opaque src dst)
    :subtractive (blend-subtractive src dst)
    ;; Default: source-over
    (blend-source-over src dst)))
