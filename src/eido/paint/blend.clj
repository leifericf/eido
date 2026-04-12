(ns ^{:stability :provisional} eido.paint.blend
  "Paint-specific blend modes for the paint engine.

  Standard source-over is the default. Additional modes:
    :buildup — opacity accumulates linearly (linearized)
    :glaze   — transparent layering that preserves underlying detail
    :erase   — removes paint from the surface")

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
    ;; Default: source-over
    (blend-source-over src dst)))
