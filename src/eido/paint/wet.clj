(ns ^{:stability :provisional} eido.paint.wet
  "Wet media simulation for the paint engine.

  Provides tile-local wetness and pigment diffusion for watercolor-like
  effects: edge darkening, blooms, and soft pigment spread.

  Wetness and pigment are stored as auxiliary planes on surface tiles.
  Diffusion uses a local Laplacian kernel following the pattern
  established by eido.gen.ca/rd-step."
  (:require
    [eido.paint.surface :as surface]))

;; --- auxiliary tile planes ---

(defn ensure-wet-planes!
  "Ensures that a surface has wetness plane allocated for the given tile.
  Returns the wetness float-array."
  [surface ^long tx ^long ty]
  (let [ts   (long surface/tile-size)
        n    (* ts ts)
        cols (long (:surface/cols surface))
        idx  (+ tx (* ty cols))
        ^objects wet-planes (:surface/wet-planes surface)
        ^floats wet (aget wet-planes idx)]
    (if wet
      wet
      (let [new-wet (float-array n)]
        (aset wet-planes idx new-wet)
        new-wet))))

(defn deposit-wetness!
  "Deposits wetness at surface coordinates (px, py).
  amount: wetness to add [0..1]."
  [surface ^long px ^long py ^double amount]
  (let [ts  (long surface/tile-size)
        tx  (quot px ts)
        ty  (quot py ts)
        lx  (rem px ts)
        ly  (rem py ts)
        ^floats wet (ensure-wet-planes! surface tx ty)
        idx (+ (* ly ts) lx)
        old (double (aget wet idx))]
    (aset wet idx (float (Math/min 1.0 (+ old amount))))))

;; --- diffusion ---

(defn diffuse-tile!
  "Runs one iteration of Laplacian diffusion on a wetness tile.
  Spreads wetness from wet to dry areas using a 3x3 kernel.
  strength: diffusion rate [0..1]."
  [^floats wet ^long ts ^double strength]
  (let [n     (* ts ts)
        buf   (float-array n)]
    ;; Copy current state
    (System/arraycopy wet 0 buf 0 n)
    ;; Apply Laplacian
    (dotimes [y ts]
      (dotimes [x ts]
        (let [idx (+ (* y ts) x)
              center (double (aget buf idx))
              ;; Cardinal neighbors (clamped to tile edges)
              left  (if (> x 0) (double (aget buf (+ (* y ts) (dec x)))) center)
              right (if (< x (dec ts)) (double (aget buf (+ (* y ts) (inc x)))) center)
              up    (if (> y 0) (double (aget buf (+ (* (dec y) ts) x))) center)
              down  (if (< y (dec ts)) (double (aget buf (+ (* (inc y) ts) x))) center)
              ;; Laplacian: average of neighbors minus center
              laplacian (- (* 0.25 (+ left right up down)) center)
              ;; Apply diffusion
              new-val (+ center (* strength laplacian))]
          (aset wet idx (float (Math/max 0.0 (Math/min 1.0 new-val)))))))))

(defn darken-edges!
  "Increases pigment concentration at wetness boundaries.
  Creates the characteristic dark edges of watercolor."
  [^floats wet ^floats color ^long ts ^double darken-amount]
  (dotimes [y ts]
    (dotimes [x ts]
      (let [idx  (+ (* y ts) x)
            w    (double (aget wet idx))]
        (when (> w 0.01)
          ;; Check if this is an edge: wet pixel next to dry
          (let [ci (* idx 4)
                left-wet  (if (> x 0) (double (aget wet (+ (* y ts) (dec x)))) 0.0)
                right-wet (if (< x (dec ts)) (double (aget wet (+ (* y ts) (inc x)))) 0.0)
                up-wet    (if (> y 0) (double (aget wet (+ (* (dec y) ts) x))) 0.0)
                down-wet  (if (< y (dec ts)) (double (aget wet (+ (* (inc y) ts) x))) 0.0)
                min-neighbor (Math/min (Math/min left-wet right-wet) (Math/min up-wet down-wet))
                ;; Edge strength: high when this pixel is wetter than neighbors
                edge-strength (* darken-amount (- w min-neighbor))]
            (when (> edge-strength 0.01)
              ;; Darken the color tile — reduce RGB, increase alpha
              (let [scale (- 1.0 (* edge-strength 0.3))]
                (aset color ci (float (* (aget color ci) scale)))
                (aset color (+ ci 1) (float (* (aget color (+ ci 1)) scale)))
                (aset color (+ ci 2) (float (* (aget color (+ ci 2)) scale)))))))))))

(defn apply-wet-pass!
  "Runs diffusion and edge darkening on all dirty tiles.
  iterations: number of diffusion steps."
  [surface ^long iterations ^double diffusion-strength ^double darken-amount]
  (let [cols  (long (:surface/cols surface))
        rows  (long (:surface/rows surface))
        ts    (long surface/tile-size)
        wet-planes (:surface/wet-planes surface)
        tiles (:surface/tiles surface)]
    (when wet-planes
      (dotimes [ty rows]
        (dotimes [tx cols]
          (let [idx (+ tx (* ty cols))
                ^floats wet (when wet-planes (aget ^objects wet-planes idx))
                ^floats color (when tiles (aget ^objects tiles idx))]
            (when (and wet color)
              ;; Run diffusion iterations
              (dotimes [_ iterations]
                (diffuse-tile! wet ts diffusion-strength))
              ;; Apply edge darkening
              (when (> darken-amount 0.0)
                (darken-edges! wet color ts darken-amount)))))))))

(comment
  (let [wet (float-array (* 8 8))]
    ;; Put a wet spot in the center
    (aset wet (+ (* 4 8) 4) (float 1.0))
    (println "Before:" (vec wet))
    (diffuse-tile! wet 8 0.5)
    (println "After:" (vec wet))))
