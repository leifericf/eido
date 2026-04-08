(ns eido.ir.material
  "Material descriptors for 3D rendering.

  Materials describe how surfaces respond to light — going beyond
  the existing diffuse-only shading in scene3d.

  Material types:
    :material/phong — Blinn-Phong shading with ambient, diffuse, and specular"
  (:require
    [eido.math3d :as m]))

;; --- material constructors ---

(defn phong
  "Creates a Blinn-Phong material descriptor."
  [& {:keys [ambient diffuse specular shininess color]
      :or {ambient 0.3 diffuse 0.7 specular 0.0 shininess 32.0}}]
  {:material/type      :material/phong
   :material/ambient   ambient
   :material/diffuse   diffuse
   :material/specular  specular
   :material/shininess shininess
   :material/color     color})

;; --- shading ---

(defn- clamp-byte ^long [^double v]
  (long (Math/max 0.0 (Math/min 255.0 v))))

(defn shade-phong
  "Computes Blinn-Phong shading for a face.
  Returns a shaded color vector [:color/rgb r g b].

  normal:    normalized face normal (toward camera)
  light-dir: normalized light direction (toward light)
  cam-dir:   normalized camera direction (toward camera)
  light:     light map with :light/ambient, :light/intensity
  material:  material descriptor with :material/* keys"
  [normal light-dir cam-dir light material]
  (let [base-color  (:material/color material)
        [_ br bg bb] base-color
        ambient     (double (:material/ambient material))
        mat-diffuse (double (:material/diffuse material))
        mat-spec    (double (:material/specular material))
        shininess   (double (:material/shininess material))
        intensity   (double (get light :light/intensity 0.7))
        ;; Diffuse (Lambert)
        cos-angle   (m/dot normal light-dir)
        diffuse     (* mat-diffuse intensity (max 0.0 cos-angle))
        ;; Specular (Blinn-Phong)
        half-vec    (m/normalize (m/v+ light-dir cam-dir))
        n-dot-h     (max 0.0 (m/dot normal half-vec))
        specular    (if (pos? cos-angle)
                      (* mat-spec intensity (Math/pow n-dot-h shininess))
                      0.0)
        ;; Combined brightness
        brightness  (min 1.0 (+ ambient diffuse))
        ;; Apply to color channels, add specular as white highlight
        r (clamp-byte (+ (* (double br) brightness) (* 255.0 specular)))
        g (clamp-byte (+ (* (double bg) brightness) (* 255.0 specular)))
        b (clamp-byte (+ (* (double bb) brightness) (* 255.0 specular)))]
    [:color/rgb r g b]))

(defn shade-face
  "Shades a face style using a material descriptor.
  Falls back to the material's color if the face has no fill.
  Returns updated style map with shaded fill and stroke."
  [style normal light-dir cam-dir light material]
  (let [mat-color    (or (:material/color material)
                         (:style/fill style))
        material     (assoc material :material/color mat-color)
        shaded-color (shade-phong normal light-dir cam-dir light material)]
    (cond-> (assoc style :style/fill shaded-color)
      (:style/stroke style)
      (assoc-in [:style/stroke :color] shaded-color))))
