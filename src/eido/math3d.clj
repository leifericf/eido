(ns eido.math3d)

;; --- vector operations ---

(defn v+
  "Adds two 3D vectors."
  [[x1 y1 z1] [x2 y2 z2]]
  [(+ (double x1) x2) (+ (double y1) y2) (+ (double z1) z2)])

(defn v-
  "Subtracts second 3D vector from first."
  [[x1 y1 z1] [x2 y2 z2]]
  [(- (double x1) x2) (- (double y1) y2) (- (double z1) z2)])

(defn v*
  "Multiplies a 3D vector by a scalar."
  [[x y z] s]
  [(* (double x) s) (* (double y) s) (* (double z) s)])

(defn dot
  "Dot product of two 3D vectors."
  [[x1 y1 z1] [x2 y2 z2]]
  (+ (* (double x1) x2) (* (double y1) y2) (* (double z1) z2)))

(defn cross
  "Cross product of two 3D vectors."
  [[x1 y1 z1] [x2 y2 z2]]
  [(- (* (double y1) z2) (* (double z1) y2))
   (- (* (double z1) x2) (* (double x1) z2))
   (- (* (double x1) y2) (* (double y1) x2))])

(defn magnitude
  "Length of a 3D vector."
  [[x y z]]
  (Math/sqrt (+ (* (double x) x) (* (double y) y) (* (double z) z))))

(defn normalize
  "Returns a unit vector in the same direction, or [0 0 0] for zero vector."
  [v]
  (let [m (magnitude v)]
    (if (zero? m)
      [0.0 0.0 0.0]
      (v* v (/ 1.0 m)))))

;; --- rotations ---

(defn rotate-x
  "Rotates a point around the X axis by angle (radians)."
  [[x y z] angle]
  (let [c (Math/cos angle)
        s (Math/sin angle)]
    [(double x)
     (- (* (double y) c) (* (double z) s))
     (+ (* (double y) s) (* (double z) c))]))

(defn rotate-y
  "Rotates a point around the Y axis by angle (radians)."
  [[x y z] angle]
  (let [c (Math/cos angle)
        s (Math/sin angle)]
    [(+ (* (double x) c) (* (double z) s))
     (double y)
     (- (* (double z) c) (* (double x) s))]))

(defn rotate-z
  "Rotates a point around the Z axis by angle (radians)."
  [[x y z] angle]
  (let [c (Math/cos angle)
        s (Math/sin angle)]
    [(- (* (double x) c) (* (double y) s))
     (+ (* (double x) s) (* (double y) c))
     (double z)]))

;; --- projection ---

(def ^:private cos30 (/ (Math/sqrt 3.0) 2.0))
(def ^:private sin30 0.5)

(def ^:private iso-camera-dir
  "Isometric camera direction: looking from [1, 1, 1] toward origin."
  (normalize [1.0 1.0 1.0]))

(defn camera-direction
  "Returns the unit vector pointing from the scene toward the camera.
  Used for back-face culling and depth sorting.
  Convention: yaw=0 pitch=0 means camera looks along -Z (camera at +Z)."
  [projection]
  (if (= :isometric (:projection/type projection))
    iso-camera-dir
    (let [yaw   (get projection :projection/yaw 0.0)
          pitch (get projection :projection/pitch 0.0)
          cp    (Math/cos pitch)
          sp    (Math/sin pitch)
          cy    (Math/cos yaw)
          sy    (Math/sin yaw)]
      ;; Camera forward is [-sin(yaw)*cos(pitch), sin(pitch), -cos(yaw)*cos(pitch)]
      ;; Camera direction (toward camera) is the negation
      [(* cp sy) (- sp) (* cp cy)])))

(defn view-transform
  "Transforms a 3D point into view space (rotated by camera angles).
  Used for perspective projection depth calculation."
  [projection point]
  (let [yaw   (get projection :projection/yaw 0.0)
        pitch (get projection :projection/pitch 0.0)
        roll  (get projection :projection/roll 0.0)]
    (-> point
        (rotate-y (- yaw))
        (rotate-x (- pitch))
        (rotate-z (- roll)))))

(defn- project-isometric
  [{:projection/keys [scale origin]} [x y z]]
  (let [s   (double scale)
        [ox oy] origin
        sx  (+ ox (* s (- x z) cos30))
        sy  (+ oy (* s (- (* (+ x z) sin30) y)))]
    [sx sy]))

(defn- project-orthographic
  [{:projection/keys [scale origin] :as proj} point]
  (let [s      (double scale)
        [ox oy] origin
        [vx vy _vz] (view-transform proj point)]
    [(+ ox (* s vx))
     (- oy (* s vy))]))

(defn- project-perspective
  [{:projection/keys [scale origin distance] :as proj} point]
  (let [s      (double scale)
        [ox oy] origin
        d      (double distance)
        [vx vy vz] (view-transform proj point)
        factor (/ d (+ d vz))]
    [(+ ox (* s vx factor))
     (- oy (* s vy factor))]))

(defn project
  "Projects a 3D point to 2D screen coordinates using the given projection."
  [projection point]
  (case (:projection/type projection)
    :isometric    (project-isometric projection point)
    :orthographic (project-orthographic projection point)
    :perspective  (project-perspective projection point)))

;; --- face utilities ---

(defn face-normal
  "Computes the normal vector of a face from its first three vertices.
  The direction follows the right-hand rule (counter-clockwise winding)."
  [[v0 v1 v2 & _]]
  (let [e1 (v- v1 v0)
        e2 (v- v2 v0)]
    (cross e1 e2)))

(defn face-centroid
  "Returns the centroid (average position) of a set of 3D points."
  [vertices]
  (let [n (double (count vertices))]
    (v* (reduce v+ [0.0 0.0 0.0] vertices) (/ 1.0 n))))
