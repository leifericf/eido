(ns eido.ir.material
  "Material descriptors and multi-light shading for 3D rendering.

  Materials describe how surfaces respond to light using Blinn-Phong.

  Light types:
    :directional — parallel rays from a direction (default, existing)
    :omni        — radiates in all directions from a position
    :spot        — cone of light from a position with hotspot/falloff
    :hemisphere  — sky/ground ambient blend by normal direction

  Standard light types for 3D scene illumination."
  (:require
    [eido.color :as color]
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

;; --- light constructors ---

(defn directional
  "Creates a directional light (parallel rays)."
  [direction & {:keys [color multiplier ambient]
                :or {multiplier 1.0 ambient 0.0}}]
  (cond-> {:light/type      :directional
           :light/direction direction
           :light/multiplier multiplier}
    color   (assoc :light/color color)
    (pos? ambient) (assoc :light/ambient ambient)))

(defn omni
  "Creates an omni (point) light that radiates in all directions."
  [position & {:keys [color multiplier decay decay-start]
               :or {multiplier 1.0 decay :none decay-start 0.0}}]
  (cond-> {:light/type       :omni
           :light/position   position
           :light/multiplier multiplier
           :light/decay      decay
           :light/decay-start decay-start}
    color (assoc :light/color color)))

(defn spot
  "Creates a spot light with a cone defined by hotspot and falloff angles."
  [position direction & {:keys [color multiplier hotspot falloff decay decay-start]
                         :or {multiplier 1.0 hotspot 43.0 falloff 45.0
                              decay :none decay-start 0.0}}]
  (cond-> {:light/type       :spot
           :light/position   position
           :light/direction  direction
           :light/multiplier multiplier
           :light/hotspot    hotspot
           :light/falloff    falloff
           :light/decay      decay
           :light/decay-start decay-start}
    color (assoc :light/color color)))

(defn hemisphere
  "Creates a hemisphere (sky) light with sky and ground colors."
  [sky-color ground-color & {:keys [up multiplier]
                              :or {up [0 1 0] multiplier 0.3}}]
  {:light/type         :hemisphere
   :light/sky-color    sky-color
   :light/ground-color ground-color
   :light/up           up
   :light/multiplier   multiplier})

;; --- decay ---

(defn- apply-decay
  "Applies distance decay to light intensity."
  ^double [^double intensity light ^double distance]
  (let [decay      (get light :light/decay :none)
        decay-start (double (get light :light/decay-start 0.0))
        effective-dist (max 0.0 (- distance decay-start))]
    (if (or (= :none decay) (<= effective-dist 0.0))
      intensity
      (let [factor (case decay
                     :inverse        (/ 1.0 (+ 1.0 effective-dist))
                     :inverse-square (/ 1.0 (+ 1.0 (* effective-dist effective-dist)))
                     1.0)]
        (* intensity factor)))))

;; --- per-light-type contribution ---

(defn- clamp-byte ^long [^double v]
  (long (Math/max 0.0 (Math/min 255.0 v))))

(defn- get-multiplier ^double [light]
  (double (or (:light/multiplier light) 1.0)))

(defn- directional-contribution
  "Computes diffuse + specular contribution from a directional light."
  [normal cam-dir material light]
  (let [light-dir   (m/normalize (:light/direction light))
        multiplier  (get-multiplier light)
        mat-diffuse (double (:material/diffuse material))
        mat-spec    (double (:material/specular material))
        shininess   (double (:material/shininess material))
        cos-angle   (m/dot normal light-dir)
        diffuse     (* mat-diffuse multiplier (max 0.0 cos-angle))
        half-vec    (m/normalize (m/v+ light-dir cam-dir))
        n-dot-h     (max 0.0 (m/dot normal half-vec))
        specular    (if (pos? cos-angle)
                      (* mat-spec multiplier (Math/pow n-dot-h shininess))
                      0.0)]
    {:diffuse diffuse :specular specular}))

(defn- omni-contribution
  "Computes diffuse + specular contribution from an omni (point) light."
  [normal cam-dir material light face-centroid]
  (let [light-pos   (:light/position light)
        to-light    (m/v- light-pos face-centroid)
        distance    (m/magnitude to-light)
        light-dir   (if (pos? distance) (m/v* to-light (/ 1.0 distance)) [0 0 0])
        multiplier  (apply-decay (get-multiplier light) light distance)
        mat-diffuse (double (:material/diffuse material))
        mat-spec    (double (:material/specular material))
        shininess   (double (:material/shininess material))
        cos-angle   (m/dot normal light-dir)
        diffuse     (* mat-diffuse multiplier (max 0.0 cos-angle))
        half-vec    (m/normalize (m/v+ light-dir cam-dir))
        n-dot-h     (max 0.0 (m/dot normal half-vec))
        specular    (if (pos? cos-angle)
                      (* mat-spec multiplier (Math/pow n-dot-h shininess))
                      0.0)]
    {:diffuse diffuse :specular specular}))

(defn- spot-contribution
  "Computes diffuse + specular contribution from a spot light."
  [normal cam-dir material light face-centroid]
  (let [light-pos   (:light/position light)
        to-light    (m/v- light-pos face-centroid)
        distance    (m/magnitude to-light)
        light-dir   (if (pos? distance) (m/v* to-light (/ 1.0 distance)) [0 0 0])
        ;; Spot cone factor
        spot-dir    (m/normalize (:light/direction light))
        cos-theta   (m/dot (m/v* light-dir -1.0) spot-dir)
        hotspot-rad (* (/ (double (:light/hotspot light)) 2.0) (/ Math/PI 180.0))
        falloff-rad (* (/ (double (:light/falloff light)) 2.0) (/ Math/PI 180.0))
        cos-hotspot (Math/cos hotspot-rad)
        cos-falloff (Math/cos falloff-rad)
        spot-factor (m/smoothstep cos-falloff cos-hotspot cos-theta)]
    (if (zero? spot-factor)
      {:diffuse 0.0 :specular 0.0}
      (let [multiplier  (* (apply-decay (get-multiplier light) light distance) spot-factor)
            mat-diffuse (double (:material/diffuse material))
            mat-spec    (double (:material/specular material))
            shininess   (double (:material/shininess material))
            cos-angle   (m/dot normal light-dir)
            diffuse     (* mat-diffuse multiplier (max 0.0 cos-angle))
            half-vec    (m/normalize (m/v+ light-dir cam-dir))
            n-dot-h     (max 0.0 (m/dot normal half-vec))
            specular    (if (pos? cos-angle)
                          (* mat-spec multiplier (Math/pow n-dot-h shininess))
                          0.0)]
        {:diffuse diffuse :specular specular}))))

(defn- hemisphere-contribution
  "Computes ambient contribution from a hemisphere light.
  Returns color contribution as [r g b] scaled 0-1."
  [normal light]
  (let [up          (m/normalize (or (:light/up light) [0 1 0]))
        multiplier  (get-multiplier light)
        factor      (+ 0.5 (* 0.5 (m/dot normal up)))
        sky         (color/resolve-color (:light/sky-color light))
        ground      (color/resolve-color (:light/ground-color light))
        r (/ (+ (* factor (:r sky)) (* (- 1.0 factor) (:r ground))) 255.0)
        g (/ (+ (* factor (:g sky)) (* (- 1.0 factor) (:g ground))) 255.0)
        b (/ (+ (* factor (:b sky)) (* (- 1.0 factor) (:b ground))) 255.0)]
    {:ambient-r (* r multiplier)
     :ambient-g (* g multiplier)
     :ambient-b (* b multiplier)}))

;; --- multi-light shading ---

(defn- resolve-lights
  "Normalizes light input to a vector of light maps.
  Supports single :light map or :lights vector."
  [light lights]
  (cond
    (seq lights) lights
    light        [light]
    :else        []))

(defn shade-multi-light
  "Shades a face using multiple lights and a Blinn-Phong material.
  Returns [:color/rgb r g b]."
  [normal cam-dir face-centroid material lights]
  (let [base-color  (or (:material/color material) [:color/rgb 128 128 128])
        [_ br bg bb] base-color
        mat-ambient (double (:material/ambient material))
        ;; Accumulate contributions from all lights
        ;; Resolve light color to [0-1] RGB scale factors
        light-rgb (fn [light]
                    (if-let [c (:light/color light)]
                      (let [resolved (color/resolve-color c)]
                        [(/ (double (:r resolved)) 255.0)
                         (/ (double (:g resolved)) 255.0)
                         (/ (double (:b resolved)) 255.0)])
                      [1.0 1.0 1.0]))
        result (reduce
                 (fn [{:keys [diff-r diff-g diff-b spec-r spec-g spec-b
                              amb-r amb-g amb-b]} light]
                   (let [light-type (get light :light/type :directional)
                         [lr lg lb] (light-rgb light)]
                     (case light-type
                       :directional
                       (let [{:keys [diffuse specular]} (directional-contribution normal cam-dir material light)]
                         {:diff-r (+ diff-r (* diffuse lr)) :diff-g (+ diff-g (* diffuse lg)) :diff-b (+ diff-b (* diffuse lb))
                          :spec-r (+ spec-r (* specular lr)) :spec-g (+ spec-g (* specular lg)) :spec-b (+ spec-b (* specular lb))
                          :amb-r amb-r :amb-g amb-g :amb-b amb-b})

                       :omni
                       (let [{:keys [diffuse specular]} (omni-contribution normal cam-dir material light face-centroid)]
                         {:diff-r (+ diff-r (* diffuse lr)) :diff-g (+ diff-g (* diffuse lg)) :diff-b (+ diff-b (* diffuse lb))
                          :spec-r (+ spec-r (* specular lr)) :spec-g (+ spec-g (* specular lg)) :spec-b (+ spec-b (* specular lb))
                          :amb-r amb-r :amb-g amb-g :amb-b amb-b})

                       :spot
                       (let [{:keys [diffuse specular]} (spot-contribution normal cam-dir material light face-centroid)]
                         {:diff-r (+ diff-r (* diffuse lr)) :diff-g (+ diff-g (* diffuse lg)) :diff-b (+ diff-b (* diffuse lb))
                          :spec-r (+ spec-r (* specular lr)) :spec-g (+ spec-g (* specular lg)) :spec-b (+ spec-b (* specular lb))
                          :amb-r amb-r :amb-g amb-g :amb-b amb-b})

                       :hemisphere
                       (let [{:keys [ambient-r ambient-g ambient-b]} (hemisphere-contribution normal light)]
                         {:diff-r diff-r :diff-g diff-g :diff-b diff-b
                          :spec-r spec-r :spec-g spec-g :spec-b spec-b
                          :amb-r (+ amb-r ambient-r) :amb-g (+ amb-g ambient-g) :amb-b (+ amb-b ambient-b)})

                       ;; Unknown light type — skip
                       {:diff-r diff-r :diff-g diff-g :diff-b diff-b
                        :spec-r spec-r :spec-g spec-g :spec-b spec-b
                        :amb-r amb-r :amb-g amb-g :amb-b amb-b})))
                 {:diff-r 0.0 :diff-g 0.0 :diff-b 0.0
                  :spec-r 0.0 :spec-g 0.0 :spec-b 0.0
                  :amb-r mat-ambient :amb-g mat-ambient :amb-b mat-ambient}
                 lights)
        {:keys [diff-r diff-g diff-b spec-r spec-g spec-b amb-r amb-g amb-b]} result
        r (clamp-byte (+ (* (double br) (min 1.0 (+ amb-r diff-r))) (* 255.0 spec-r)))
        g (clamp-byte (+ (* (double bg) (min 1.0 (+ amb-g diff-g))) (* 255.0 spec-g)))
        b (clamp-byte (+ (* (double bb) (min 1.0 (+ amb-b diff-b))) (* 255.0 spec-b)))]
    [:color/rgb r g b]))

;; --- backward-compatible API ---

(defn shade-phong
  "Computes Blinn-Phong shading for a face with a single directional light."
  [normal light-dir cam-dir light material]
  (shade-multi-light normal cam-dir [0 0 0] material
    [(assoc light :light/type :directional :light/direction light-dir)]))

(defn shade-face
  "Shades a face style using a material and lights.
  Supports single light or lights vector."
  ([style normal light-dir cam-dir light material]
   (shade-face style normal light-dir cam-dir light material nil nil))
  ([style normal light-dir cam-dir light material lights face-centroid]
   (let [mat-color    (or (:material/color material) (:style/fill style))
         material     (assoc material :material/color mat-color)
         all-lights   (resolve-lights light lights)
         centroid     (or face-centroid [0 0 0])
         shaded-color (shade-multi-light normal cam-dir centroid material all-lights)]
     (cond-> (assoc style :style/fill shaded-color)
       (:style/stroke style)
       (assoc-in [:style/stroke :color] shaded-color)))))
