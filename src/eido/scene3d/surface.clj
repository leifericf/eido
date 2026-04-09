(ns eido.scene3d.surface
  "UV projection, face coloring, vertex painting, and material maps."
  (:require
    [eido.ir.field :as field]
    [eido.math :as m]
    [eido.scene3d.util :as u]))

;; --- UV projection ---

(defn- uv-box-project
  "Projects vertices onto the two axes perpendicular to the face's dominant axis."
  [face bounds]
  (let [normal (:face/normal face)
        [nx ny nz] (m/normalize normal)
        ax (abs (double nx))
        ay (abs (double ny))
        az (abs (double nz))
        ;; Choose projection plane by dominant normal axis
        [axis-a axis-b min-a min-b range-a range-b]
        (cond
          (and (>= ax ay) (>= ax az))
          [1 2 (second (:min bounds)) (nth (:min bounds) 2)
           (- (second (:max bounds)) (second (:min bounds)))
           (- (nth (:max bounds) 2) (nth (:min bounds) 2))]
          (>= ay az)
          [0 2 (first (:min bounds)) (nth (:min bounds) 2)
           (- (first (:max bounds)) (first (:min bounds)))
           (- (nth (:max bounds) 2) (nth (:min bounds) 2))]
          :else
          [0 1 (first (:min bounds)) (second (:min bounds))
           (- (first (:max bounds)) (first (:min bounds)))
           (- (second (:max bounds)) (second (:min bounds)))])]
    (mapv (fn [v]
            (let [a (nth v axis-a)
                  b (nth v axis-b)
                  u (if (zero? range-a) 0.5 (/ (- (double a) min-a) range-a))
                  vv (if (zero? range-b) 0.5 (/ (- (double b) min-b) range-b))]
              [(max 0.0 (min 1.0 u)) (max 0.0 (min 1.0 vv))]))
          (:face/vertices face))))

(defn- uv-spherical-project [face]
  (mapv (fn [[x y z]]
          (let [r (m/magnitude [x y z])
                r (if (zero? r) 1.0 r)
                u (+ 0.5 (/ (Math/atan2 (double z) (double x)) (* 2.0 Math/PI)))
                v (+ 0.5 (/ (Math/asin (max -1.0 (min 1.0 (/ (double y) r)))) Math/PI))]
            [u v]))
        (:face/vertices face)))

(defn- uv-cylindrical-project [face bounds axis]
  (let [[y-min y-max] (case axis
                        :x [(first (:min bounds)) (first (:max bounds))]
                        :y [(second (:min bounds)) (second (:max bounds))]
                        :z [(nth (:min bounds) 2) (nth (:max bounds) 2)])
        y-range (- (double y-max) (double y-min))]
    (mapv (fn [[x y z]]
            (let [;; Angle around axis
                  [a b h] (case axis
                            :x [y z x] :y [x z y] :z [x y z])
                  u (+ 0.5 (/ (Math/atan2 (double b) (double a)) (* 2.0 Math/PI)))
                  v (if (zero? y-range) 0.5
                      (/ (- (double h) (double y-min)) y-range))]
              [u (max 0.0 (min 1.0 v))]))
          (:face/vertices face))))

(defn- uv-planar-project [face bounds axis]
  (let [[a-idx b-idx] (case axis :x [1 2] :y [0 2] :z [0 1])
        a-min (nth (:min bounds) a-idx)
        b-min (nth (:min bounds) b-idx)
        a-range (- (double (nth (:max bounds) a-idx)) a-min)
        b-range (- (double (nth (:max bounds) b-idx)) b-min)]
    (mapv (fn [v]
            (let [a (nth v a-idx)
                  b (nth v b-idx)
                  u (if (zero? a-range) 0.5 (/ (- (double a) a-min) a-range))
                  vv (if (zero? b-range) 0.5 (/ (- (double b) b-min) b-range))]
              [(max 0.0 (min 1.0 u)) (max 0.0 (min 1.0 vv))]))
          (:face/vertices face))))

(defn uv-project
  "Assigns texture coordinates to mesh faces via projection.
  opts:
    :uv/type  - :box, :spherical, :cylindrical, or :planar
    :uv/axis    - axis for :cylindrical/:planar (default :y)
    :uv/scale   - UV scale factor (default 1.0)
    :uv/offset  - [u-offset v-offset] (default [0 0])
    :select/*   - optional face selector"
  [mesh opts]
  (let [method (get opts :uv/type :box)
        axis   (get opts :uv/axis :y)
        scale  (double (get opts :uv/scale 1.0))
        [ou ov] (get opts :uv/offset [0 0])
        sel    (when (:select/type opts) (u/make-face-selector opts))
        bounds (u/mesh-bounds mesh)]
    (mapv (fn [face]
            (let [centroid (m/face-centroid (:face/vertices face))
                  normal   (:face/normal face)]
              (if (or (nil? sel) (sel face centroid normal))
                (let [raw-uvs (case method
                                :box         (uv-box-project face bounds)
                                :spherical   (uv-spherical-project face)
                                :cylindrical (uv-cylindrical-project face bounds axis)
                                :planar      (uv-planar-project face bounds axis))
                      scaled  (if (and (== scale 1.0) (zero? (double ou)) (zero? (double ov)))
                                raw-uvs
                                (mapv (fn [[u v]]
                                        [(+ (* u scale) (double ou))
                                         (+ (* v scale) (double ov))])
                                      raw-uvs))]
                  (assoc face :face/texture-coords scaled))
                face)))
          mesh)))

;; --- per-face color ---

(defn color-mesh
  "Colors each face based on a descriptor.
  When :select/type is present, only selected faces are colored; others pass through.
  opts:
    :color/type    - :field, :axis-gradient, or :normal-map
    :color/palette - vector of [:color/rgb r g b] colors
    :color/field   - field descriptor (for :field type)
    :color/axis    - :x, :y, or :z (for :axis-gradient type)
    :select/*      - optional face selector (defaults to all faces)"
  [mesh opts]
  (let [palette (:color/palette opts)
        sel     (when (:select/type opts) (u/make-face-selector opts))
        bounds  (when (= :axis-gradient (:color/type opts))
                  (let [axis (get opts :color/axis :y)]
                    (u/axis-range axis mesh)))
        color-fn
        (case (:color/type opts)
          :field
          (let [f (:color/field opts)]
            (fn [_face centroid _normal]
              (let [[cx _cy cz] centroid
                    v (field/evaluate f (double cx) (double cz))]
                ;; Map noise [-1,1] to [0,1]
                (* 0.5 (+ 1.0 v)))))

          :axis-gradient
          (let [axis    (get opts :color/axis :y)
                [lo hi] bounds
                range   (- (double hi) (double lo))]
            (fn [_face centroid _normal]
              (if (zero? range)
                0.5
                (/ (- (u/axis-component axis centroid) (double lo)) range))))

          :normal-map
          (fn [_face _centroid normal]
            (let [[nx ny nz] (m/normalize normal)
                  ax (abs (double nx))
                  ay (abs (double ny))
                  az (abs (double nz))
                  total (+ ax ay az)]
              (if (zero? total)
                0.5
                (let [mx (max ax ay az)]
                  (cond
                    (== mx ax) (/ ax total)
                    (== mx ay) (/ (+ ax ay) total)
                    :else      1.0))))))]
    (mapv (fn [face]
            (let [verts    (:face/vertices face)
                  centroid (m/face-centroid verts)
                  normal   (:face/normal face)]
              (if (or (nil? sel) (sel face centroid normal))
                (let [t     (color-fn face centroid normal)
                      color (u/palette-color palette t)
                      style (merge (:face/style face) {:style/fill color})]
                  (assoc face :face/style style))
                face)))
          mesh)))

;; --- vertex color ---

(defn paint-mesh
  "Assigns per-vertex colors by sampling a field at vertex positions.
  When :color/source is :uv, samples at vertex UV coordinates instead of
  3D positions — this is the bridge from Eido's 2D procedural system to 3D.
  opts:
    :color/type    - :field, :axis-gradient, or :normal-map
    :color/source  - :position (default) or :uv (requires :face/texture-coords)
    :color/palette - vector of [:color/rgb r g b] colors
    :color/field   - field descriptor (for :field type)
    :color/axis    - :x, :y, or :z (for :axis-gradient type)
    :select/*      - optional face selector"
  [mesh opts]
  (let [palette   (:color/palette opts)
        uv-source? (= :uv (:color/source opts))
        sel       (when (:select/type opts) (u/make-face-selector opts))
        bounds    (when (= :axis-gradient (:color/type opts))
                    (u/axis-range (get opts :color/axis :y) mesh))
        vert-t-fn
        (case (:color/type opts)
          :field
          (let [f (:color/field opts)]
            (if uv-source?
              (fn [_vertex _normal uv]
                (let [[u v] uv]
                  (* 0.5 (+ 1.0 (field/evaluate f (double u) (double v))))))
              (fn [vertex _normal _uv]
                (let [[x _y z] vertex]
                  (* 0.5 (+ 1.0 (field/evaluate f (double x) (double z))))))))

          :axis-gradient
          (let [axis    (get opts :color/axis :y)
                [lo hi] bounds
                range   (- (double hi) (double lo))]
            (if uv-source?
              ;; For UV source, axis-gradient maps V coordinate
              (fn [_vertex _normal uv]
                (second uv))
              (fn [vertex _normal _uv]
                (if (zero? range)
                  0.5
                  (/ (- (u/axis-component axis vertex) (double lo)) range)))))

          :normal-map
          (fn [_vertex normal _uv]
            (let [[nx ny nz] (m/normalize normal)
                  ax (abs (double nx))
                  ay (abs (double ny))
                  az (abs (double nz))
                  total (+ ax ay az)]
              (if (zero? total)
                0.5
                (let [mx (max ax ay az)]
                  (cond
                    (== mx ax) (/ ax total)
                    (== mx ay) (/ (+ ax ay) total)
                    :else      1.0))))))]
    (mapv (fn [face]
            (let [verts    (:face/vertices face)
                  centroid (m/face-centroid verts)
                  normal   (:face/normal face)
                  uvs      (:face/texture-coords face)]
              (if (and (or (nil? sel) (sel face centroid normal))
                       (or (not uv-source?) uvs))
                (let [colors (mapv (fn [v i]
                                     (let [uv (when uvs (nth uvs i nil))]
                                       (u/palette-color palette (vert-t-fn v normal uv))))
                                   verts (range))]
                  (assoc face :face/vertex-colors colors))
                face)))
          mesh)))

;; --- normal/bump maps ---

(defn- perturb-normal-from-field
  "Perturbs a surface normal using the gradient of a scalar field sampled at UV.
  Uses finite differences and the TBN frame."
  [field strength face-verts face-uvs vertex-idx face-normal]
  (let [eps 0.001
        uv  (nth face-uvs vertex-idx)
        [u v] uv
        ;; Sample field and compute gradient via finite differences
        f0  (field/evaluate field (double u) (double v))
        fu  (field/evaluate field (+ (double u) eps) (double v))
        fv  (field/evaluate field (double u) (+ (double v) eps))
        du  (/ (- fu f0) eps)
        dv  (/ (- fv f0) eps)
        ;; Compute TBN frame from face geometry + UVs
        v0  (nth face-verts 0)
        v1  (nth face-verts (min 1 (dec (count face-verts))))
        v2  (nth face-verts (min 2 (dec (count face-verts))))
        uv0 (nth face-uvs 0)
        uv1 (nth face-uvs (min 1 (dec (count face-uvs))))
        uv2 (nth face-uvs (min 2 (dec (count face-uvs))))
        tb  (m/face-tangent-bitangent [v0 v1 v2] [uv0 uv1 uv2])
        n   (m/normalize face-normal)]
    (if tb
      (let [[tangent bitangent] tb
            ;; Perturb normal in tangent space
            perturbation (m/v+ (m/v* tangent (* (double strength) du))
                               (m/v* bitangent (* (double strength) dv)))]
        (m/normalize (m/v+ n perturbation)))
      n)))

(defn normal-map-mesh
  "Perturbs vertex normals using a field sampled at UV coordinates.
  Creates visible surface detail under lighting without adding geometry.
  Requires :face/texture-coords on faces (from uv-project or OBJ import).
  opts:
    :normal-map/field    - field descriptor
    :normal-map/strength - perturbation strength (default 1.0)
    :select/*            - optional face selector"
  [mesh opts]
  (let [f        (:normal-map/field opts)
        strength (double (get opts :normal-map/strength 1.0))
        sel      (when (:select/type opts) (u/make-face-selector opts))]
    (mapv (fn [face]
            (let [verts    (:face/vertices face)
                  centroid (m/face-centroid verts)
                  normal   (:face/normal face)
                  uvs      (:face/texture-coords face)]
              (if (and uvs (or (nil? sel) (sel face centroid normal)))
                (let [vert-normals (mapv (fn [i]
                                           (perturb-normal-from-field
                                             f strength verts uvs i normal))
                                         (range (count verts)))]
                  (assoc face :face/vertex-normals vert-normals))
                face)))
          mesh)))

;; --- specular maps ---

(defn specular-map-mesh
  "Varies specular intensity per vertex using a field sampled at UV coordinates.
  Requires :face/texture-coords on faces. The material's specular value is
  overridden per sub-triangle during rendering.
  opts:
    :specular-map/field - field descriptor
    :specular-map/range - [min-spec max-spec] (default [0.0 1.0])
    :select/*           - optional face selector"
  [mesh opts]
  (let [f     (:specular-map/field opts)
        [lo hi] (get opts :specular-map/range [0.0 1.0])
        lo    (double lo)
        range (- (double hi) lo)
        sel   (when (:select/type opts) (u/make-face-selector opts))]
    (mapv (fn [face]
            (let [verts    (:face/vertices face)
                  centroid (m/face-centroid verts)
                  normal   (:face/normal face)
                  uvs      (:face/texture-coords face)]
              (if (and uvs (or (nil? sel) (sel face centroid normal)))
                (let [specs (mapv (fn [uv]
                                    (let [[u v] uv
                                          t (* 0.5 (+ 1.0 (field/evaluate f (double u) (double v))))]
                                      (+ lo (* t range))))
                                  uvs)]
                  (assoc face :face/vertex-specular specs))
                face)))
          mesh)))
