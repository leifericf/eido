(ns eido.scene3d.camera
  "Projection constructors and camera utilities."
  (:require
    [eido.math3d :as m]))

;; --- projection constructors ---

(defn isometric
  "Creates an isometric projection map.
  opts: :scale (default 1.0), :origin [x y] (default [0 0])."
  [opts]
  {:projection/type   :isometric
   :projection/scale  (double (get opts :scale 1.0))
   :projection/origin (get opts :origin [0 0])})

(defn orthographic
  "Creates an orthographic projection map.
  opts: :scale, :origin, :yaw (radians), :pitch (radians)."
  [opts]
  {:projection/type   :orthographic
   :projection/scale  (double (get opts :scale 1.0))
   :projection/origin (get opts :origin [0 0])
   :projection/yaw    (double (get opts :yaw 0.0))
   :projection/pitch  (double (get opts :pitch 0.0))
   :projection/roll   (double (get opts :roll 0.0))})

(defn perspective
  "Creates a perspective projection map.
  opts: :scale, :origin, :yaw, :pitch, :distance."
  [opts]
  {:projection/type    :perspective
   :projection/scale   (double (get opts :scale 1.0))
   :projection/origin  (get opts :origin [0 0])
   :projection/yaw     (double (get opts :yaw 0.0))
   :projection/pitch   (double (get opts :pitch 0.0))
   :projection/roll    (double (get opts :roll 0.0))
   :projection/distance (double (get opts :distance 5.0))})

;; --- camera utilities ---

(defn look-at
  "Returns a projection map oriented so the camera looks from eye toward target.
  Derives :yaw, :pitch, and :roll from the geometry. Preserves :type, :scale,
  :origin, and :distance from base-projection.
  up: world up vector (default [0 1 0])."
  ([base-projection eye target]
   (look-at base-projection eye target [0 1 0]))
  ([base-projection eye target up]
   (let [forward (m/normalize (m/v- target eye))
         [fx fy fz] forward
         fy-clamped (max -1.0 (min 1.0 (double fy)))
         pitch (Math/asin fy-clamped)]
     (if (> (abs fy-clamped) 0.999)
       ;; Gimbal lock: looking nearly straight up or down.
       ;; Yaw is degenerate; derive from up vector, roll is 0.
       (let [yaw (Math/atan2 (- (double (nth up 0)))
                             (- (double (nth up 2))))]
         (assoc base-projection
           :projection/yaw   yaw
           :projection/pitch pitch
           :projection/roll  0.0))
       (let [yaw   (Math/atan2 (- (double fx)) (- (double fz)))
             right (m/normalize (m/cross forward up))
             actual-up (m/cross right forward)
             ;; Expected camera axes without roll (from yaw/pitch rotation matrix)
             cp (Math/cos pitch)
             sp (Math/sin pitch)
             cy (Math/cos yaw)
             sy (Math/sin yaw)
             expected-up    [(* sy sp) cp (* cy sp)]
             expected-right [cy 0.0 (- sy)]
             roll  (Math/atan2 (m/dot actual-up expected-right)
                               (m/dot actual-up expected-up))]
         (assoc base-projection
           :projection/yaw   yaw
           :projection/pitch pitch
           :projection/roll  roll))))))

(defn orbit
  "Returns a projection with the camera orbiting target at the given radius.
  yaw: horizontal orbital angle (radians). pitch: vertical angle (radians).
  At yaw=0 pitch=0, camera is at +Z relative to target (consistent with
  the default camera convention)."
  [base-projection target radius yaw pitch]
  (let [r  (double radius)
        cp (Math/cos pitch)
        sp (Math/sin pitch)
        cy (Math/cos yaw)
        sy (Math/sin yaw)
        eye (m/v+ target [(* r cp sy)
                          (* r (- sp))
                          (* r cp cy)])]
    (look-at base-projection eye target)))

(defn fov->distance
  "Converts a horizontal field-of-view angle (radians) to a perspective
  :distance value. half-width is half the viewport width in world-space
  units (typically screen-half-width / scale)."
  [fov half-width]
  (/ (double half-width) (Math/tan (* 0.5 (double fov)))))
