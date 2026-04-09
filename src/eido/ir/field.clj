(ns eido.ir.field
  "Field descriptors and evaluation.

  A field is a function over a 2D domain that yields a scalar or vector value.
  Fields are reusable descriptors that can be consumed by fills, generators,
  transforms, and programs.

  Field types:
    :field/noise      — Perlin noise (raw, fbm, turbulence, ridge)
    :field/constant   — uniform value everywhere
    :field/distance   — distance from a point"
  (:require
    [eido.noise :as noise]))

;; --- field constructors ---

(defn noise-field
  "Creates a noise field descriptor.
  Options: :noise-type (:perlin), :variant (:raw | :fbm | :turbulence | :ridge),
           :scale (1.0), :octaves (4), :lacunarity (2.0), :gain (0.5),
           :seed (nil), :offset (1.0, ridge only)."
  [& {:keys [scale variant octaves lacunarity gain seed offset]
      :or {scale 1.0 variant :fbm}}]
  (cond-> {:field/type    :field/noise
           :field/scale   scale
           :field/variant variant}
    octaves    (assoc :field/octaves octaves)
    lacunarity (assoc :field/lacunarity lacunarity)
    gain       (assoc :field/gain gain)
    seed       (assoc :field/seed seed)
    offset     (assoc :field/offset offset)))

(defn constant-field
  "Creates a field that returns the same value everywhere."
  [value]
  {:field/type  :field/constant
   :field/value value})

(defn distance-field
  "Creates a field that returns the distance from a point."
  [center]
  {:field/type   :field/distance
   :field/center center})

;; --- field evaluation ---

(defn- noise-opts
  "Extracts noise options from a field descriptor."
  [field]
  (cond-> {}
    (:field/octaves field)    (assoc :octaves (:field/octaves field))
    (:field/lacunarity field) (assoc :lacunarity (:field/lacunarity field))
    (:field/gain field)       (assoc :gain (:field/gain field))
    (:field/seed field)       (assoc :seed (:field/seed field))
    (:field/offset field)     (assoc :offset (:field/offset field))))

(defn- evaluate-noise
  "Evaluates a noise field at (x, y)."
  ^double [field ^double x ^double y]
  (let [scale   (double (get field :field/scale 1.0))
        sx      (* x scale)
        sy      (* y scale)
        variant (get field :field/variant :fbm)
        opts    (noise-opts field)]
    (case variant
      :raw        (noise/perlin2d sx sy opts)
      :fbm        (noise/fbm noise/perlin2d sx sy opts)
      :turbulence (noise/turbulence noise/perlin2d sx sy opts)
      :ridge      (noise/ridge noise/perlin2d sx sy opts)
      (noise/fbm noise/perlin2d sx sy opts))))

(defn- evaluate-noise-3d
  "Evaluates a noise field at (x, y, z) using 3D Perlin noise."
  ^double [field ^double x ^double y ^double z]
  (let [scale   (double (get field :field/scale 1.0))
        sx      (* x scale)
        sy      (* y scale)
        sz      (* z scale)
        variant (get field :field/variant :fbm)
        opts    (noise-opts field)
        ;; Wrap perlin3d as a 2-arg + opts fn for fbm/turbulence/ridge
        ;; by currying the z coordinate
        noise2  (fn
                  ([a b] (noise/perlin3d a b sz nil))
                  ([a b o] (noise/perlin3d a b sz o)))]
    (case variant
      :raw        (noise/perlin3d sx sy sz opts)
      :fbm        (noise/fbm noise2 sx sy opts)
      :turbulence (noise/turbulence noise2 sx sy opts)
      :ridge      (noise/ridge noise2 sx sy opts)
      (noise/fbm noise2 sx sy opts))))

(defn evaluate
  "Evaluates a field descriptor at position (x, y).
  Returns a double."
  ^double [field ^double x ^double y]
  (case (:field/type field)
    :field/noise
    (evaluate-noise field x y)

    :field/constant
    (double (:field/value field))

    :field/distance
    (let [[cx cy] (:field/center field)
          dx      (- x (double cx))
          dy      (- y (double cy))]
      (Math/sqrt (+ (* dx dx) (* dy dy))))

    (throw (ex-info (str "Unknown field type: " (:field/type field))
                    {:field field}))))

(defn evaluate-3d
  "Evaluates a field descriptor at 3D position (x, y, z).
  Noise fields use 3D Perlin noise. Distance fields use x and y only.
  Returns a double."
  ^double [field ^double x ^double y ^double z]
  (case (:field/type field)
    :field/noise
    (evaluate-noise-3d field x y z)

    :field/constant
    (double (:field/value field))

    :field/distance
    (let [[cx cy] (:field/center field)
          dx      (- x (double cx))
          dy      (- y (double cy))]
      (Math/sqrt (+ (* dx dx) (* dy dy))))

    (throw (ex-info (str "Unknown field type: " (:field/type field))
                    {:field field}))))
