(ns ^{:stability :provisional} eido.paint.grain
  "Procedural grain field evaluators for the paint engine.

  Grain modulates paint deposition within the tip area, creating
  texture effects like chalk, canvas, pastel, and fiber patterns.
  All fields are purely procedural — no bitmaps."
  (:require
    [eido.gen.noise :as noise]))

;; --- grain evaluation ---

(defn- remap
  "Remaps a value from [-1,1] range to [0,1] with contrast adjustment.
  contrast 0.5 = neutral, >0.5 = more contrast, <0.5 = flatter."
  ^double [^double v ^double contrast]
  (let [;; Map [-1,1] to [0,1]
        normalized (Math/max 0.0 (Math/min 1.0 (* 0.5 (+ v 1.0))))
        ;; Apply contrast via power curve
        ;; contrast 0.5 -> power 1.0 (linear)
        ;; contrast 1.0 -> power 0.5 (more contrast)
        ;; contrast 0.0 -> power 2.0 (flatter)
        power (Math/max 0.1 (/ 1.0 (Math/max 0.01 (* 2.0 contrast))))]
    (Math/pow normalized power)))

(defn- fbm-grain
  "Fractal Brownian motion grain. Uses simplex noise to avoid
  grid-aligned artifacts visible in Perlin at medium scales."
  ^double [^double x ^double y grain-spec]
  (let [scale (double (get grain-spec :grain/scale 0.1))
        contrast (double (get grain-spec :grain/contrast 0.5))
        octaves (get grain-spec :grain/octaves 6)
        seed (get grain-spec :grain/seed 0)
        v (noise/fbm noise/simplex2d (* x scale) (* y scale)
            {:octaves octaves :seed seed})]
    (remap v contrast)))

(defn- turbulence-grain
  "Turbulence grain — high-frequency stippled texture.
  Uses fine-grained noise with high octaves for a sandy/granular feel."
  ^double [^double x ^double y grain-spec]
  (let [scale (double (get grain-spec :grain/scale 0.1))
        contrast (double (get grain-spec :grain/contrast 0.5))
        octaves (get grain-spec :grain/octaves 8)
        seed (get grain-spec :grain/seed 0)
        ;; High-octave fBm gives fine-grained stipple
        v (noise/fbm noise/simplex2d (* x scale 1.5) (* y scale 1.5)
            {:octaves octaves :lacunarity 2.2 :seed seed})]
    (remap v contrast)))

(defn- ridge-grain
  "Ridged multifractal grain — sharp-edged texture."
  ^double [^double x ^double y grain-spec]
  (let [scale (double (get grain-spec :grain/scale 0.1))
        contrast (double (get grain-spec :grain/contrast 0.5))
        seed (get grain-spec :grain/seed 0)
        v (noise/ridge noise/simplex2d (* x scale) (* y scale)
            {:octaves 5 :seed seed})]
    (Math/max 0.0 (Math/min 1.0 (* v contrast)))))

(defn- fiber-grain
  "Fiber/directional grain — anisotropic noise stretched along one axis.
  Uses layered simplex for richer texture."
  ^double [^double x ^double y grain-spec]
  (let [scale (double (get grain-spec :grain/scale 0.1))
        contrast (double (get grain-spec :grain/contrast 0.5))
        stretch (double (get grain-spec :grain/stretch 4.0))
        angle (double (get grain-spec :grain/angle 0.0))
        seed (get grain-spec :grain/seed 0)
        cos-a (Math/cos angle)
        sin-a (Math/sin angle)
        rx (+ (* x cos-a) (* y sin-a))
        ry (+ (* (- x) sin-a) (* y cos-a))
        ;; Primary fiber direction
        v1 (noise/fbm noise/simplex2d (* rx scale) (* ry scale (/ 1.0 stretch))
             {:octaves 4 :seed seed})
        ;; Fine cross-fiber detail
        v2 (noise/simplex2d (* rx scale 3.0) (* ry scale 3.0) {:seed (+ seed 31)})
        v (+ (* 0.8 v1) (* 0.2 v2))]
    (remap v contrast)))

(defn- weave-grain
  "Weave grain — rough, pitted surface texture like coarse linen.
  Uses ridged noise for sharp valleys where paint skips."
  ^double [^double x ^double y grain-spec]
  (let [scale (double (get grain-spec :grain/scale 0.05))
        contrast (double (get grain-spec :grain/contrast 0.5))
        seed (get grain-spec :grain/seed 0)
        ;; Use a higher internal scale so texture is fine relative to dabs
        v (noise/ridge noise/simplex2d (* x scale 4.3) (* y scale 4.3)
            {:octaves 5 :seed seed})]
    (Math/max 0.0 (Math/min 1.0 (* v contrast)))))

(defn- canvas-grain
  "Canvas grain — fine, irregular paper surface.
  Uses 3D noise sampled on a tilted plane to avoid 2D lattice artifacts."
  ^double [^double x ^double y grain-spec]
  (let [scale (double (get grain-spec :grain/scale 0.05))
        contrast (double (get grain-spec :grain/contrast 0.5))
        seed (get grain-spec :grain/seed 0)
        ;; Manual fBm over perlin3d on a tilted plane
        sx (* x scale 1.1)
        sy (* y scale 1.1)
        sz (* (+ x y) scale 0.37)
        opts {:seed seed}
        ;; Higher internal multiplier for finer texture
        m 2.5
        v (+ (* 0.5  (noise/perlin3d (* sx m) (* sy m) (* sz m) opts))
             (* 0.25 (noise/perlin3d (* sx m 2.0) (* sy m 2.0) (* sz m 2.0) opts))
             (* 0.125 (noise/perlin3d (* sx m 4.0) (* sy m 4.0) (* sz m 4.0) opts))
             (* 0.0625 (noise/perlin3d (* sx m 8.0) (* sy m 8.0) (* sz m 8.0) opts)))]
    (remap v contrast)))

;; --- public evaluator ---

(defn evaluate-grain
  "Evaluates a grain field at surface coordinates (sx, sy).
  Returns a value in [0, 1] representing deposition strength.
  1.0 = full deposition, 0.0 = grain blocks deposition.

  grain-spec: {:grain/type :fbm, :grain/scale 0.1, :grain/contrast 0.5,
               :grain/mode :world}
  mode: :world (canvas coordinates) or :dragged (stroke-local coords)

  For :world mode, pass surface coords directly.
  For :dragged mode, caller should pass stroke-local coords."
  ^double [grain-spec ^double sx ^double sy]
  (if (nil? grain-spec)
    1.0
    (case (get grain-spec :grain/type :fbm)
      :fbm        (fbm-grain sx sy grain-spec)
      :turbulence (turbulence-grain sx sy grain-spec)
      :ridge      (ridge-grain sx sy grain-spec)
      :fiber      (fiber-grain sx sy grain-spec)
      :weave      (weave-grain sx sy grain-spec)
      :canvas     (canvas-grain sx sy grain-spec)
      ;; Default: no grain modulation
      1.0)))

(comment
  (evaluate-grain {:grain/type :fbm :grain/scale 0.05 :grain/contrast 0.5}
                  100.0 50.0)
  (evaluate-grain {:grain/type :fiber :grain/scale 0.08 :grain/contrast 0.6
                   :grain/stretch 5.0}
                  100.0 50.0))
