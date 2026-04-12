(ns eido.scene3d.transform
  "Mesh transforms, deformations, and mirror operations."
  (:require
    [eido.ir.field :as field]
    [eido.math :as m]
    [eido.gen.noise :as noise]
    [eido.scene3d.util :as u]))

;; --- mesh transforms ---

(defn translate-mesh
  "Translates all vertices in a mesh by offset [dx dy dz]."
  [mesh offset]
  (mapv (fn [face]
          (let [new-verts (mapv #(m/v+ % offset) (:face/vertices face))]
            (assoc face :face/vertices new-verts)))
        mesh))

(defn rotate-mesh
  "Rotates all vertices and normals in a mesh around the given axis.
  axis: :x, :y, or :z. angle: radians."
  [mesh axis angle]
  (mapv (fn [face]
          (let [new-verts  (mapv #(m/rotate % axis angle) (:face/vertices face))
                new-normal (m/rotate (:face/normal face) axis angle)]
            (cond-> (assoc face
                      :face/vertices new-verts
                      :face/normal new-normal)
              (:face/vertex-normals face)
              (assoc :face/vertex-normals
                (mapv #(m/rotate % axis angle) (:face/vertex-normals face))))))
        mesh))

(defn scale-mesh
  "Scales all vertices in a mesh. factor is a number (uniform) or [sx sy sz]."
  [mesh factor]
  (let [scale-fn (if (number? factor)
                   (fn [[x y z]]
                     [(* (double x) factor)
                      (* (double y) factor)
                      (* (double z) factor)])
                   (let [[sx sy sz] factor]
                     (fn [[x y z]]
                       [(* (double x) sx)
                        (* (double y) sy)
                        (* (double z) sz)])))]
    (mapv (fn [face]
            (let [new-verts (mapv scale-fn (:face/vertices face))
                  new-normal (m/face-normal new-verts)]
              (cond-> (assoc face
                        :face/vertices new-verts
                        :face/normal new-normal)
                ;; Recompute vertex normals after non-uniform scale
                (:face/vertex-normals face)
                (dissoc :face/vertex-normals))))
          mesh)))

;; --- mesh deformations ---

(defn- deform-twist
  "Rotates each vertex around axis proportional to its position along it."
  [vertex _normal {:keys [deform/axis deform/amount]} [ax-min ax-max]]
  (let [pos (u/axis-component axis vertex)
        range (- (double ax-max) (double ax-min))
        t (if (zero? range) 0.0 (/ (- pos (double ax-min)) range))
        angle (* (double amount) t)]
    (m/rotate vertex axis angle)))

(defn- deform-taper
  "Scales the cross-section perpendicular to axis by position along it."
  [vertex _normal {:keys [deform/axis deform/amount]} [ax-min ax-max]]
  (let [pos (u/axis-component axis vertex)
        range (- (double ax-max) (double ax-min))
        t (if (zero? range) 0.0 (/ (- pos (double ax-min)) range))
        scale (- 1.0 (* (double amount) t))
        [x y z] vertex]
    (case axis
      :x [x (* (double y) scale) (* (double z) scale)]
      :y [(* (double x) scale) y (* (double z) scale)]
      :z [(* (double x) scale) (* (double y) scale) z])))

(defn- deform-bend
  "Bends the mesh along an axis by rotating vertices around a perpendicular axis."
  [vertex _normal {:keys [deform/axis deform/amount]} [ax-min ax-max]]
  (let [pos (u/axis-component axis vertex)
        range (- (double ax-max) (double ax-min))
        t (if (zero? range) 0.0 (/ (- pos (double ax-min)) range))
        angle (* (double amount) t)
        ;; Bend around perpendicular axis: Y bends around Z, X around Y, Z around X
        bend-axis (case axis :x :y :y :z :z :x)]
    (m/rotate vertex bend-axis angle)))

(defn- deform-inflate
  "Pushes each vertex along its face normal by a fixed amount."
  [vertex normal {:keys [deform/amount]} _axis-range]
  (let [n (m/normalize normal)]
    (m/v+ vertex (m/v* n (double amount)))))

(defn- deform-crumple
  "Randomly perturbs each vertex position. Seeded for determinism."
  [vertex _normal {:keys [deform/amplitude deform/seed]} _axis-range]
  (let [amp   (double (or amplitude 0.1))
        s     (or seed 0)
        [x y z] vertex
        ;; Use vertex position + fractional offsets to avoid Perlin zero-at-integers
        dx (* amp (noise/perlin3d (+ (double x) 100.37) (+ (double y) 0.71) (+ (double z) 0.13) {:seed s}))
        dy (* amp (noise/perlin3d (+ (double x) 0.53) (+ (double y) 100.91) (+ (double z) 0.29) {:seed s}))
        dz (* amp (noise/perlin3d (+ (double x) 0.17) (+ (double y) 0.43) (+ (double z) 100.67) {:seed s}))]
    [(+ (double x) dx) (+ (double y) dy) (+ (double z) dz)]))

(defn- deform-displace
  "Pushes each vertex along its face normal by a field value * amplitude."
  [vertex normal {:keys [deform/field deform/amplitude]} _axis-range]
  (let [amp (double (or amplitude 1.0))
        [x y z] vertex
        val (field/evaluate-3d field (double x) (double y) (double z))
        n   (m/normalize normal)]
    (m/v+ vertex (m/v* n (* amp val)))))

(defn deform-mesh
  "Applies a deformation to a mesh, returning a new mesh.
  descriptor: map with :deform/type and type-specific options.
  Types: :twist, :taper, :bend, :inflate, :crumple, :displace."
  [mesh descriptor]
  (let [deform-fn (case (:deform/type descriptor)
                    :twist    deform-twist
                    :taper    deform-taper
                    :bend     deform-bend
                    :inflate  deform-inflate
                    :crumple  deform-crumple
                    :displace deform-displace)
        axis      (get descriptor :deform/axis :y)
        ar        (when (#{:twist :taper :bend} (:deform/type descriptor))
                    (u/axis-range axis mesh))]
    (mapv (fn [face]
            (let [normal (:face/normal face)
                  new-verts (mapv #(deform-fn % normal descriptor ar)
                              (:face/vertices face))]
              (cond-> (assoc face
                        :face/vertices new-verts
                        :face/normal (m/face-normal new-verts))
                ;; Invalidate per-vertex normals — must be recomputed after deformation
                (:face/vertex-normals face) (dissoc :face/vertex-normals))))
          mesh)))

;; --- mirror ---

(defn mirror-mesh
  "Reflects a mesh across an axis plane through the origin.
  opts:
    :mirror/axis  - :x, :y, or :z (which axis plane to reflect across)
    :mirror/merge - if true, return original + reflection combined (default false)"
  [mesh opts]
  (let [axis  (:mirror/axis opts)
        merge? (get opts :mirror/merge false)
        reflected (mapv (fn [face]
                          (let [verts (:face/vertices face)
                                ;; Negate the axis component
                                mirrored-verts (mapv (fn [[x y z]]
                                                       (case axis
                                                         :x [(- (double x)) y z]
                                                         :y [x (- (double y)) z]
                                                         :z [x y (- (double z))]
                                                         (throw (ex-info "Invalid mirror axis"
                                                                         {:axis axis
                                                                          :valid #{:x :y :z}}))))
                                                     verts)
                                ;; Reverse winding to fix normals
                                reversed (vec (reverse mirrored-verts))
                                new-face (u/make-face reversed (:face/style face))]
                            ;; Preserve all face attributes
                            (merge (dissoc face :face/vertices :face/normal) new-face)))
                        mesh)]
    (if merge?
      (into mesh reflected)
      reflected)))
