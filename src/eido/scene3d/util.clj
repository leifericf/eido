(ns eido.scene3d.util
  "Shared utilities for scene3d sub-modules."
  (:require
    [eido.ir.field :as field]
    [eido.math :as m]))

;; --- face construction ---

(defn make-face
  "Creates a face map from a sequence of 3D vertices.
  Computes the normal automatically from the first three vertices."
  ([vertices]
   (make-face vertices nil))
  ([vertices style]
   (let [vs (vec vertices)]
     (cond-> {:face/vertices vs
              :face/normal   (m/face-normal vs)}
       style (assoc :face/style style)))))

;; --- mesh utilities ---

(defn merge-meshes
  "Combines multiple meshes into a single mesh (vector of faces).
  Each argument is either a bare mesh (vector of faces) or a
  [mesh style] pair, where style is applied to every face in that mesh."
  [& meshes]
  (into []
    (mapcat (fn [entry]
              (if (and (vector? entry) (= 2 (count entry)) (map? (second entry)))
                (let [[mesh style] entry]
                  (mapv #(assoc % :face/style style) mesh))
                entry))
            meshes)))

(defn mesh-bounds
  "Returns the axis-aligned bounding box of a mesh as {:min [x y z] :max [x y z]}."
  [mesh]
  (let [all-verts (seq (mapcat :face/vertices mesh))]
    (if all-verts
      (let [xs (map #(nth % 0) all-verts)
            ys (map #(nth % 1) all-verts)
            zs (map #(nth % 2) all-verts)]
        {:min [(apply min xs) (apply min ys) (apply min zs)]
         :max [(apply max xs) (apply max ys) (apply max zs)]})
      {:min [0.0 0.0 0.0] :max [0.0 0.0 0.0]})))

(defn mesh-center
  "Returns the center point of a mesh's bounding box."
  [mesh]
  (let [{[x0 y0 z0] :min [x1 y1 z1] :max} (mesh-bounds mesh)]
    [(* 0.5 (+ (double x0) x1))
     (* 0.5 (+ (double y0) y1))
     (* 0.5 (+ (double z0) z1))]))

;; --- shared internal helpers (^:no-doc) ---

(defn ^:no-doc axis-component
  "Extracts the component of a 3D vector along the given axis (:x, :y, or :z)."
  ^double [axis [x y z]]
  (case axis :x (double x) :y (double y) :z (double z)))

(defn ^:no-doc axis-range
  "Returns [min max] of the given axis across all vertices in a mesh."
  [axis mesh]
  (let [vals (seq (map #(axis-component axis %)
                    (mapcat :face/vertices mesh)))]
    (if vals
      [(apply min vals) (apply max vals)]
      [0.0 0.0])))

(defn ^:no-doc edge-key
  "Returns a canonical key for an edge between two 3D points.
  Sorts the endpoints so edge [a b] and [b a] produce the same key."
  [a b]
  (if (neg? (compare (vec a) (vec b))) [a b] [b a]))

(defn ^:no-doc lerp-color
  "Linear interpolation between two RGB colors."
  [[_t1 r1 g1 b1] [_t2 r2 g2 b2] t]
  (let [t (double t)
        s (- 1.0 t)]
    [:color/rgb
     (int (Math/round (+ (* s (double r1)) (* t (double r2)))))
     (int (Math/round (+ (* s (double g1)) (* t (double g2)))))
     (int (Math/round (+ (* s (double b1)) (* t (double b2)))))]))

(defn ^:no-doc palette-color
  "Maps a [0,1] value to a color from a palette via linear interpolation."
  [palette t]
  (case (count palette)
    0 [:color/rgb 0 0 0]
    1 (first palette)
    (let [t  (Math/max 0.0 (Math/min 1.0 (double t)))
          n  (dec (count palette))
          fi (* t n)
          lo (int (Math/floor fi))
          lo (min lo (dec n))
          hi (min (inc lo) n)
          f  (- fi lo)]
      (lerp-color (nth palette lo) (nth palette hi) f))))

(defn ^:no-doc make-face-selector
  "Builds a predicate fn from a selector descriptor.
  Returns (fn [face centroid normal] -> boolean)."
  [opts]
  (case (:select/type opts)
    :all (fn [_face _centroid _normal] true)

    :normal
    (let [dir   (m/normalize (:select/direction opts))
          tol   (double (get opts :select/tolerance 0.3))]
      (fn [_face _centroid normal]
        (> (m/dot (m/normalize normal) dir) (- 1.0 tol))))

    :field
    (let [f     (:select/field opts)
          thresh (double (get opts :select/threshold 0.0))]
      (fn [_face centroid _normal]
        (let [[cx _cy cz] centroid]
          (> (field/evaluate f (double cx) (double cz)) thresh))))

    :axis
    (let [axis (get opts :select/axis :y)
          lo   (double (get opts :select/min Double/NEGATIVE_INFINITY))
          hi   (double (get opts :select/max Double/POSITIVE_INFINITY))]
      (fn [_face centroid _normal]
        (let [v (axis-component axis centroid)]
          (and (>= v lo) (<= v hi)))))))
