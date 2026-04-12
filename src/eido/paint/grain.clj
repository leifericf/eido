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
  "Fractal Brownian motion grain."
  ^double [^double x ^double y grain-spec]
  (let [scale (double (get grain-spec :grain/scale 0.1))
        contrast (double (get grain-spec :grain/contrast 0.5))
        octaves (get grain-spec :grain/octaves 4)
        seed (get grain-spec :grain/seed 0)
        v (noise/fbm noise/perlin2d (* x scale) (* y scale)
            {:octaves octaves :seed seed})]
    (remap v contrast)))

(defn- turbulence-grain
  "Turbulence grain — absolute-value fBm for billowy textures."
  ^double [^double x ^double y grain-spec]
  (let [scale (double (get grain-spec :grain/scale 0.1))
        contrast (double (get grain-spec :grain/contrast 0.5))
        octaves (get grain-spec :grain/octaves 4)
        seed (get grain-spec :grain/seed 0)
        v (noise/turbulence noise/perlin2d (* x scale) (* y scale)
            {:octaves octaves :seed seed})]
    (Math/max 0.0 (Math/min 1.0 (* v contrast 2.0)))))

(defn- ridge-grain
  "Ridged multifractal grain — sharp-edged texture."
  ^double [^double x ^double y grain-spec]
  (let [scale (double (get grain-spec :grain/scale 0.1))
        contrast (double (get grain-spec :grain/contrast 0.5))
        seed (get grain-spec :grain/seed 0)
        v (noise/ridge noise/perlin2d (* x scale) (* y scale)
            {:octaves 4 :seed seed})]
    (Math/max 0.0 (Math/min 1.0 (* v contrast)))))

(defn- fiber-grain
  "Fiber/directional grain — anisotropic noise stretched along one axis."
  ^double [^double x ^double y grain-spec]
  (let [scale (double (get grain-spec :grain/scale 0.1))
        contrast (double (get grain-spec :grain/contrast 0.5))
        stretch (double (get grain-spec :grain/stretch 4.0))
        angle (double (get grain-spec :grain/angle 0.0))
        seed (get grain-spec :grain/seed 0)
        ;; Rotate and stretch
        cos-a (Math/cos angle)
        sin-a (Math/sin angle)
        rx (+ (* x cos-a) (* y sin-a))
        ry (+ (* (- x) sin-a) (* y cos-a))
        v (noise/fbm noise/perlin2d (* rx scale) (* ry scale (/ 1.0 stretch))
            {:octaves 3 :seed seed})]
    (remap v contrast)))

(defn- weave-grain
  "Canvas/weave grain — two perpendicular periodic patterns."
  ^double [^double x ^double y grain-spec]
  (let [scale (double (get grain-spec :grain/scale 0.05))
        contrast (double (get grain-spec :grain/contrast 0.5))
        ;; Two perpendicular sine-based patterns
        warp-x (* 0.3 (noise/perlin2d (* x scale 0.3) (* y scale 0.3) {}))
        warp-y (* 0.3 (noise/perlin2d (* y scale 0.3) (* x scale 0.3) {:seed 1}))
        thread-x (Math/sin (* (+ x warp-x) scale Math/PI 2.0))
        thread-y (Math/sin (* (+ y warp-y) scale Math/PI 2.0))
        ;; Combine: woven pattern from max of perpendicular threads
        v (* 0.5 (+ 1.0 (Math/max thread-x thread-y)))]
    (Math/max 0.0 (Math/min 1.0 (* v contrast)))))

(defn- canvas-grain
  "Canvas texture — like weave but with added fine noise."
  ^double [^double x ^double y grain-spec]
  (let [base (weave-grain x y grain-spec)
        fine-scale (* 3.0 (double (get grain-spec :grain/scale 0.05)))
        fine (noise/perlin2d (* x fine-scale) (* y fine-scale) {:seed 42})
        mix (+ base (* 0.2 fine))]
    (Math/max 0.0 (Math/min 1.0 mix))))

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
