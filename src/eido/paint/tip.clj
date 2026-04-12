(ns ^{:stability :provisional} eido.paint.tip
  "Procedural tip field evaluators for the paint engine.

  A tip field computes coverage [0..1] at a point relative to a dab center.
  Supports circle, ellipse, rounded rectangle, and line/nib shapes.
  All tips are defined as signed distance functions (SDFs) combined
  with a hardness-controlled falloff curve.")

;; --- hardness falloff ---

(defn hardness-falloff
  "Converts a normalized distance [0..1] to a coverage value using
  a hardness-controlled falloff. hardness=1 is a hard edge,
  hardness=0 is fully soft (linear falloff from center)."
  ^double [^double dist ^double hardness]
  (cond
    (>= dist 1.0) 0.0
    (<= dist 0.0) 1.0
    :else
    (if (<= dist hardness)
      1.0
      ;; Smooth quadratic falloff from hardness to 1.0
      (let [t (/ (- dist hardness) (- 1.0 hardness))]
        (- 1.0 (* t t))))))

;; --- SDF primitives ---

(defn- circle-sdf
  "Distance from point (px, py) to a circle at origin with radius 1."
  ^double [^double px ^double py]
  (Math/sqrt (+ (* px px) (* py py))))

(defn- ellipse-sdf
  "Distance from point (px, py) to an ellipse with aspect ratio.
  Aspect > 1 makes the ellipse wider horizontally."
  ^double [^double px ^double py ^double aspect]
  (let [ex (/ px aspect)]
    (Math/sqrt (+ (* ex ex) (* py py)))))

(defn- rounded-rect-sdf
  "Distance from point (px, py) to a rounded rectangle with given
  aspect ratio and corner radius [0..1]."
  ^double [^double px ^double py ^double aspect ^double corner-r]
  (let [hx aspect
        hy 1.0
        ;; Closest point on rounded rect boundary
        dx (- (Math/abs px) (- hx corner-r))
        dy (- (Math/abs py) (- hy corner-r))
        outer (Math/sqrt (+ (* (Math/max dx 0.0) (Math/max dx 0.0))
                            (* (Math/max dy 0.0) (Math/max dy 0.0))))
        inner (Math/min (Math/max dx dy) 0.0)]
    (+ outer inner corner-r)))

(defn- line-sdf
  "Distance from point to a line segment from (-aspect, 0) to (aspect, 0)."
  ^double [^double px ^double py ^double aspect]
  (let [;; Project point onto line segment
        t (/ (+ px aspect) (* 2.0 aspect))
        t (Math/max 0.0 (Math/min 1.0 t))
        lx (+ (- aspect) (* t 2.0 aspect))
        ly 0.0
        dx (- px lx)
        dy (- py ly)]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

;; --- tip evaluation ---

(defn evaluate-tip
  "Evaluates a tip field at a point relative to the dab center.
  Returns coverage [0..1].

  tip-spec: {:tip/shape :ellipse, :tip/hardness 0.7, :tip/aspect 1.0,
             :tip/corner-radius 0.3}
  px, py: offset from dab center, already normalized by radius
          (so the tip boundary is at distance 1.0)
  angle: dab rotation in radians (0 = no rotation)"
  ^double [tip-spec ^double px ^double py ^double angle]
  (let [;; Rotate point by negative angle to align with tip
        cos-a (Math/cos (- angle))
        sin-a (Math/sin (- angle))
        rx (+ (* px cos-a) (* py sin-a))
        ry (+ (* (- px) sin-a) (* py cos-a))
        ;; Tip parameters
        shape    (get tip-spec :tip/shape :ellipse)
        hardness (double (get tip-spec :tip/hardness 0.7))
        aspect   (double (get tip-spec :tip/aspect 1.0))
        ;; Compute SDF
        dist (case shape
               :circle  (circle-sdf rx ry)
               :ellipse (ellipse-sdf rx ry aspect)
               :rect    (rounded-rect-sdf rx ry aspect
                          (double (get tip-spec :tip/corner-radius 0.2)))
               :line    (line-sdf rx ry aspect)
               ;; Default to circle
               (circle-sdf rx ry))]
    (hardness-falloff dist hardness)))

;; --- bristle/multi-tip ---

(defn bristle-offsets
  "Computes bristle sub-tip offsets for a multi-tip brush.
  Returns a vector of {:offset [dx dy] :opacity-scale f :size-scale f}
  maps, one per bristle lane.

  bristle-spec:
    :bristle/count    — number of bristles (default 5)
    :bristle/spread   — spread width relative to radius (default 0.6)
    :bristle/shear    — shear amount for fan effect (default 0.0)
    :bristle/randomize — per-bristle random offset (default 0.1)"
  [bristle-spec ^double angle ^long seed]
  (let [n        (long (get bristle-spec :bristle/count 5))
        spread   (double (get bristle-spec :bristle/spread 0.6))
        shear    (double (get bristle-spec :bristle/shear 0.0))
        randomize (double (get bristle-spec :bristle/randomize 0.1))
        cos-a    (Math/cos angle)
        sin-a    (Math/sin angle)
        ;; Bristles are arranged perpendicular to stroke direction
        perp-x   (- sin-a)
        perp-y   cos-a]
    (mapv (fn [i]
            (let [t     (if (> n 1) (/ (double i) (dec n)) 0.5)
                  ;; Position along the perpendicular axis [-0.5, 0.5]
                  pos   (- t 0.5)
                  ;; Apply shear: offset along stroke direction
                  shear-offset (* shear pos)
                  ;; Pseudo-random jitter per bristle
                  hash  (Math/abs (double (rem (* (+ seed i 7) 2654435761) 1000)))
                  jx    (* randomize (- (/ hash 500.0) 1.0))
                  jy    (* randomize (- (/ (rem (* hash 37) 1000) 500.0) 1.0))
                  ;; Final offset
                  dx    (+ (* perp-x pos spread) (* cos-a shear-offset) jx)
                  dy    (+ (* perp-y pos spread) (* sin-a shear-offset) jy)
                  ;; Opacity variation: edges slightly lighter
                  edge-fade (- 1.0 (* 0.3 (Math/abs (* 2.0 pos))))
                  ;; Size variation: edges slightly smaller
                  size-fade (- 1.0 (* 0.2 (Math/abs (* 2.0 pos))))]
              {:offset [dx dy]
               :opacity-scale edge-fade
               :size-scale    size-fade}))
          (range n))))

(comment
  ;; Test: circle tip at center
  (evaluate-tip {:tip/shape :circle :tip/hardness 0.7} 0.0 0.0 0.0)
  ;; => 1.0

  ;; Test: circle tip at edge
  (evaluate-tip {:tip/shape :circle :tip/hardness 0.7} 0.9 0.0 0.0)
  ;; => ~0.1 (in falloff zone)

  ;; Test: ellipse wider
  (evaluate-tip {:tip/shape :ellipse :tip/hardness 0.7 :tip/aspect 2.0}
                1.5 0.0 0.0)
  ;; Should be in falloff zone (1.5/2.0 = 0.75)
  )
