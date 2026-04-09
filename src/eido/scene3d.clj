(ns eido.scene3d
  (:require
    [eido.ir.field :as field]
    [eido.math3d :as m]
    [eido.noise :as noise]
    [eido.scene :as scene]
    [eido.text :as text]))

(declare mesh-bounds)

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

;; --- mesh constructors ---

(defn cube-mesh
  "Returns a mesh of 6 quad faces for a unit cube at the origin,
  or a cube at position [x y z] with given size."
  ([]
   (cube-mesh [0 0 0] 1))
  ([position size]
   (let [[px py pz] (mapv double position)
         s (double size)
         ;; 8 corners
         v0 [px py pz]
         v1 [(+ px s) py pz]
         v2 [(+ px s) (+ py s) pz]
         v3 [px (+ py s) pz]
         v4 [px py (+ pz s)]
         v5 [(+ px s) py (+ pz s)]
         v6 [(+ px s) (+ py s) (+ pz s)]
         v7 [px (+ py s) (+ pz s)]]
     ;; Faces with outward-facing normals (counter-clockwise from outside)
     [(make-face [v3 v2 v1 v0])   ;; front  (z=pz, normal -Z)
      (make-face [v4 v5 v6 v7])   ;; back   (z=pz+s, normal +Z)
      (make-face [v0 v1 v5 v4])   ;; bottom (y=py, normal -Y)
      (make-face [v2 v3 v7 v6])   ;; top    (y=py+s, normal +Y)
      (make-face [v0 v4 v7 v3])   ;; left   (x=px, normal -X)
      (make-face [v1 v2 v6 v5])]))) ;; right  (x=px+s, normal +X)

(defn prism-mesh
  "Returns a mesh for a prism: a 2D polygon base extruded along the Y axis.
  base-points: seq of [x y] for the base (in the XZ plane).
  height: extrusion distance along the Y axis."
  [base-points height]
  (let [h   (double height)
        pts (vec base-points)
        n   (count pts)
        ;; Bottom vertices: [x, 0, z] from [x, z] input
        bottom (mapv (fn [[x z]] [(double x) 0.0 (double z)]) pts)
        ;; Top vertices: [x, h, z]
        top    (mapv (fn [[x z]] [(double x) h (double z)]) pts)
        ;; Bottom cap (CCW from above → normal points down)
        bottom-face (make-face bottom)
        ;; Top cap (CW from above → normal points up)
        top-face    (make-face (vec (reverse top)))
        ;; Side faces (winding for outward-pointing normals)
        sides (for [i (range n)
                    :let [j (mod (inc i) n)]]
                (make-face [(nth bottom i) (nth top i)
                            (nth top j) (nth bottom j)]))]
    (into [bottom-face top-face] sides)))

(defn cylinder-mesh
  "Returns a mesh approximating a cylinder centered at the origin.
  segments: number of sides for the circular cross-section."
  [radius height segments]
  (let [r   (double radius)
        h   (double height)
        seg (int segments)
        step (/ (* 2.0 Math/PI) seg)
        ;; Generate circle points
        circle-pts (mapv (fn [i]
                           (let [a (* i step)]
                             [(* r (Math/cos a)) (* r (Math/sin a))]))
                         (range seg))]
    (prism-mesh circle-pts h)))

(defn sphere-mesh
  "Returns a mesh approximating a sphere at the origin.
  segments: longitude slices, rings: latitude bands."
  [radius segments rings]
  (let [r    (double radius)
        seg  (int segments)
        rng  (int rings)
        ;; Generate vertices as a grid of lat/lon
        vert (fn [lat lon]
               (let [phi   (* Math/PI (/ (double lat) rng))
                     theta (* 2.0 Math/PI (/ (double lon) seg))
                     sp    (Math/sin phi)]
                 [(* r sp (Math/cos theta))
                  (* r (Math/cos phi))
                  (* r sp (Math/sin theta))]))]
    (into []
      (for [lat (range rng)
            lon (range seg)
            :let [v0 (vert lat lon)
                  v1 (vert lat (inc lon))
                  v2 (vert (inc lat) (inc lon))
                  v3 (vert (inc lat) lon)]]
        (if (zero? lat)
          ;; Top cap: triangle (winding for outward normal)
          (make-face [v0 v2 v3])
          (if (= lat (dec rng))
            ;; Bottom cap: triangle
            (make-face [v0 v1 v2])
            ;; Regular quad
            (make-face [v0 v1 v2 v3])))))))

(defn extrude-mesh
  "Extrudes a 2D polygon along a 3D vector.
  path-points: seq of [x y] (treated as [x, 0, y] in 3D space).
  direction: [dx dy dz] extrusion vector."
  [path-points direction]
  (let [pts (vec path-points)
        n   (count pts)
        dir (mapv double direction)
        ;; Base vertices in XZ plane
        base (mapv (fn [[x z]] [(double x) 0.0 (double z)]) pts)
        ;; Extruded vertices
        top  (mapv #(m/v+ % dir) base)
        ;; Caps
        base-face (make-face base)
        top-face  (make-face (vec (reverse top)))
        ;; Sides (winding for outward-pointing normals)
        sides (for [i (range n)
                    :let [j (mod (inc i) n)]]
                (make-face [(nth base i) (nth top i)
                            (nth top j) (nth base j)]))]
    (into [base-face top-face] sides)))

(defn torus-mesh
  "Returns a mesh approximating a torus at the origin.
  R: major radius (center of tube to center of torus).
  r: minor radius (tube cross-section).
  ring-segments: divisions around the ring. tube-segments: divisions around the tube."
  [R r ring-segments tube-segments]
  (let [R    (double R)
        r    (double r)
        rseg (int ring-segments)
        tseg (int tube-segments)
        rstep (/ (* 2.0 Math/PI) rseg)
        tstep (/ (* 2.0 Math/PI) tseg)
        pt (fn [theta phi]
             (let [ct (Math/cos theta) st (Math/sin theta)
                   cp (Math/cos phi)   sp (Math/sin phi)]
               [(* (+ R (* r cp)) ct)
                (* r sp)
                (* (+ R (* r cp)) st)]))]
    (into []
      (for [i (range rseg) j (range tseg)]
        (make-face [(pt (* i rstep) (* (inc j) tstep))
                    (pt (* (inc i) rstep) (* (inc j) tstep))
                    (pt (* (inc i) rstep) (* j tstep))
                    (pt (* i rstep) (* j tstep))])))))

(defn cone-mesh
  "Returns a mesh approximating a cone at the origin.
  Base circle in the XZ plane at y=0, apex at [0 height 0].
  radius: base radius. height: cone height. segments: number of sides."
  [radius height segments]
  (let [r    (double radius)
        h    (double height)
        seg  (int segments)
        step (/ (* 2.0 Math/PI) seg)
        apex [0.0 h 0.0]
        base-pts (mapv (fn [i]
                         (let [a (* i step)]
                           [(* r (Math/cos a)) 0.0 (* r (Math/sin a))]))
                       (range seg))
        ;; Base cap (CCW from above → normal points down)
        base-face (make-face base-pts)
        ;; Side triangles (winding for outward-pointing normals)
        sides (for [i (range seg)
                    :let [j (mod (inc i) seg)]]
                (make-face [(nth base-pts j) (nth base-pts i) apex]))]
    (into [base-face] sides)))

;; --- platonic solids ---

(defn- make-indexed-faces
  "Creates faces from indexed vertex data, fixing winding for outward normals."
  [verts face-indices]
  (mapv (fn [idxs]
          (let [vs (mapv verts idxs)
                centroid (m/face-centroid vs)
                n (m/face-normal vs)]
            (if (pos? (m/dot n centroid))
              (make-face vs)
              (make-face (vec (reverse vs))))))
        face-indices))

(defn- platonic-tetrahedron [r]
  (let [a (/ 1.0 3.0)
        b (/ (Math/sqrt 8.0) 3.0)
        c (/ (Math/sqrt 2.0) 3.0)
        d (/ (Math/sqrt 6.0) 3.0)]
    [(make-face [(m/v* [0.0 1.0 0.0] r) (m/v* [c (- a) d] r) (m/v* [(- b) (- a) 0.0] r)])
     (make-face [(m/v* [0.0 1.0 0.0] r) (m/v* [c (- a) (- d)] r) (m/v* [c (- a) d] r)])
     (make-face [(m/v* [0.0 1.0 0.0] r) (m/v* [(- b) (- a) 0.0] r) (m/v* [c (- a) (- d)] r)])
     (make-face [(m/v* [(- b) (- a) 0.0] r) (m/v* [c (- a) d] r) (m/v* [c (- a) (- d)] r)])]))

(defn- platonic-octahedron [r]
  (let [px [r 0.0 0.0]  nx [(- r) 0.0 0.0]
        py [0.0 r 0.0]  ny [0.0 (- r) 0.0]
        pz [0.0 0.0 r]  nz [0.0 0.0 (- r)]]
    [(make-face [py pz px]) (make-face [py px nz])
     (make-face [py nz nx]) (make-face [py nx pz])
     (make-face [ny px pz]) (make-face [ny nz px])
     (make-face [ny nx nz]) (make-face [ny pz nx])]))

(defn- platonic-dodecahedron [r]
  (let [phi (/ (+ 1.0 (Math/sqrt 5.0)) 2.0)
        ip  (/ 1.0 phi)
        raw [[1 1 1] [1 1 -1] [1 -1 1] [1 -1 -1]
             [-1 1 1] [-1 1 -1] [-1 -1 1] [-1 -1 -1]
             [0 ip phi] [0 ip (- phi)] [0 (- ip) phi] [0 (- ip) (- phi)]
             [ip phi 0] [ip (- phi) 0] [(- ip) phi 0] [(- ip) (- phi) 0]
             [phi 0 ip] [phi 0 (- ip)] [(- phi) 0 ip] [(- phi) 0 (- ip)]]
        verts (mapv (fn [v] (m/v* (m/normalize (mapv double v)) r)) raw)]
    (make-indexed-faces verts
      [[0 16 2 10 8] [0 8 4 14 12] [16 17 1 9 3] [1 12 14 5 9]
       [2 16 17 3 13] [4 18 6 15 14] [0 12 1 17 16] [5 19 18 4 8]
       [6 10 2 13 15] [3 9 5 19 7] [7 11 3 13 15] [7 19 18 6 15]])))

(defn- platonic-icosahedron [r]
  (let [phi (/ (+ 1.0 (Math/sqrt 5.0)) 2.0)
        raw [[0 1 phi] [0 1 (- phi)] [0 -1 phi] [0 -1 (- phi)]
             [1 phi 0] [1 (- phi) 0] [-1 phi 0] [-1 (- phi) 0]
             [phi 0 1] [phi 0 -1] [(- phi) 0 1] [(- phi) 0 -1]]
        verts (mapv (fn [v] (m/v* (m/normalize (mapv double v)) r)) raw)]
    (make-indexed-faces verts
      [[0 2 8]  [0 8 4]  [0 4 6]  [0 6 10] [0 10 2]
       [2 10 7] [2 7 5]  [2 5 8]  [8 5 9]  [8 9 4]
       [4 9 1]  [4 1 6]  [6 1 11] [6 11 10] [10 11 7]
       [3 5 7]  [3 9 5]  [3 1 9]  [3 11 1] [3 7 11]])))

(defn platonic-mesh
  "Returns a platonic solid inscribed in a sphere of the given radius.
  type: :tetrahedron, :octahedron, :dodecahedron, or :icosahedron."
  [type radius]
  (let [r (double radius)]
    (case type
      :tetrahedron  (platonic-tetrahedron r)
      :octahedron   (platonic-octahedron r)
      :dodecahedron (platonic-dodecahedron r)
      :icosahedron  (platonic-icosahedron r))))

;; --- heightfield ---

(defn heightfield-mesh
  "Creates a mesh from a 2D field sampled on a grid.
  opts:
    :field  - field descriptor (noise, constant, distance)
    :bounds - [x z width depth] defining the sampling area
    :grid   - [cols rows] number of sample points
    :height - maximum Y displacement"
  [{:keys [field bounds grid height]}]
  (let [[bx bz bw bd] bounds
        [cols rows] grid
        h     (double (or height 1.0))
        dx    (/ (double bw) (dec (int cols)))
        dz    (/ (double bd) (dec (int rows)))
        ;; Sample grid of Y values
        pts   (vec (for [r (range rows)
                         c (range cols)]
                     (let [x (+ (double bx) (* c dx))
                           z (+ (double bz) (* r dz))
                           y (* h (field/evaluate field x z))]
                       [x y z])))]
    (into []
      (for [r (range (dec rows))
            c (range (dec cols))
            :let [i  (+ (* r cols) c)
                  i1 (+ i 1)
                  i2 (+ i cols)
                  i3 (+ i cols 1)]]
        (make-face [(pts i) (pts i1) (pts i3) (pts i2)])))))

;; --- surface of revolution ---

(defn revolve-mesh
  "Creates a mesh by revolving a 2D profile around the Y axis.
  opts:
    :profile  - vector of [radius height] pairs defining the cross-section
    :segments - number of rotation steps around the axis"
  [{:keys [profile segments]}]
  (let [seg   (int segments)
        step  (/ (* 2.0 Math/PI) seg)
        pts   (vec profile)
        n     (count pts)
        ;; Generate rings of 3D points
        rings (vec (for [s (range seg)]
                     (let [a (* s step)
                           ca (Math/cos a)
                           sa (Math/sin a)]
                       (mapv (fn [[r h]]
                               (let [r (double r)]
                                 [(* r ca) (double h) (* r sa)]))
                             pts))))]
    (into []
      (for [s  (range seg)
            p  (range (dec n))
            :let [s1 (mod (inc s) seg)
                  v0 (get-in rings [s p])
                  v1 (get-in rings [s (inc p)])
                  v2 (get-in rings [s1 (inc p)])
                  v3 (get-in rings [s1 p])]]
        (make-face [v0 v1 v2 v3])))))

;; --- sweep mesh ---

(defn- interpolate-path
  "Linearly interpolates a path of 3D waypoints into n evenly spaced points."
  [waypoints n]
  (let [segs (dec (count waypoints))
        seg-lengths (mapv (fn [i]
                            (m/magnitude (m/v- (nth waypoints (inc i))
                                              (nth waypoints i))))
                          (range segs))
        ;; Cumulative distances at each waypoint
        cum-dists (reductions + 0.0 seg-lengths)
        total     (last cum-dists)]
    (vec
      (for [i (range n)]
        (let [t    (if (= 1 n) 0.0 (/ (double i) (dec n)))
              dist (* t total)]
          ;; Find which segment this distance falls in
          (loop [seg 0]
            (if (>= seg segs)
              (last waypoints)
              (let [d0 (nth cum-dists seg)
                    d1 (nth cum-dists (inc seg))
                    seg-len (- d1 d0)]
                (if (or (<= dist d1) (= seg (dec segs)))
                  (let [local-t (if (zero? seg-len) 0.0
                                  (/ (- dist d0) seg-len))]
                    (m/lerp (nth waypoints seg)
                            (nth waypoints (inc seg))
                            (min 1.0 local-t)))
                  (recur (inc seg)))))))))))

(defn sweep-mesh
  "Creates a mesh by sweeping a 2D profile along a 3D path.
  opts:
    :profile  - vector of [x y] pairs defining the cross-section
    :path     - vector of [x y z] waypoints defining the sweep path
    :segments - number of steps along the path
    :closed   - if true, connect last ring back to first (default false)"
  [{:keys [profile path segments closed]}]
  (let [seg     (int segments)
        pts     (interpolate-path path (if closed (inc seg) seg))
        n-prof  (count profile)
        ;; Compute tangent and local frame at each point
        rings   (vec
                  (for [i (range (count pts))]
                    (let [;; Tangent from finite differences
                          tangent (m/normalize
                                    (if (< i (dec (count pts)))
                                      (m/v- (nth pts (inc i)) (nth pts i))
                                      (m/v- (nth pts i) (nth pts (dec i)))))
                          ;; Find a perpendicular axis
                          up (if (> (abs (m/dot tangent [0 1 0])) 0.99)
                               [1 0 0]
                               [0 1 0])
                          right (m/normalize (m/cross tangent up))
                          actual-up (m/cross right tangent)
                          pos (nth pts i)]
                      (mapv (fn [[px py]]
                              (m/v+ pos (m/v+ (m/v* right (double px))
                                              (m/v* actual-up (double py)))))
                            profile))))
        n-rings (if closed seg (count rings))]
    (into []
      (for [s (range (if closed seg (dec n-rings)))
            p (range n-prof)
            :let [s1 (mod (inc s) n-rings)
                  p1 (mod (inc p) n-prof)
                  v0 (get-in rings [s p])
                  v1 (get-in rings [s p1])
                  v2 (get-in rings [s1 p1])
                  v3 (get-in rings [s1 p])]]
        (make-face [v0 v1 v2 v3])))))

;; --- mesh transforms ---

(defn translate-mesh
  "Translates all vertices in a mesh by offset [dx dy dz]."
  [mesh offset]
  (mapv (fn [face]
          (let [new-verts (mapv #(m/v+ % offset) (:face/vertices face))]
            (assoc face :face/vertices new-verts)))
        mesh))

(defn- rotate-fn [axis]
  (case axis
    :x m/rotate-x
    :y m/rotate-y
    :z m/rotate-z))

(defn rotate-mesh
  "Rotates all vertices and normals in a mesh around the given axis.
  axis: :x, :y, or :z. angle: radians."
  [mesh axis angle]
  (let [rot (rotate-fn axis)]
    (mapv (fn [face]
            (let [new-verts  (mapv #(rot % angle) (:face/vertices face))
                  new-normal (rot (:face/normal face) angle)]
              (assoc face
                :face/vertices new-verts
                :face/normal new-normal)))
          mesh)))

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
            (let [new-verts (mapv scale-fn (:face/vertices face))]
              (assoc face
                :face/vertices new-verts
                :face/normal (m/face-normal new-verts))))
          mesh)))

;; --- mesh deformations ---

(defn- axis-component
  "Extracts the component of a 3D vector along the given axis (:x, :y, or :z)."
  ^double [axis [x y z]]
  (case axis :x (double x) :y (double y) :z (double z)))

(defn- axis-range
  "Returns [min max] of the given axis across all vertices in a mesh."
  [axis mesh]
  (let [vals (map #(axis-component axis %)
               (mapcat :face/vertices mesh))]
    [(apply min vals) (apply max vals)]))

(defn- deform-twist
  "Rotates each vertex around axis proportional to its position along it."
  [vertex _normal {:keys [deform/axis deform/amount]} [ax-min ax-max]]
  (let [pos (axis-component axis vertex)
        range (- (double ax-max) (double ax-min))
        t (if (zero? range) 0.0 (/ (- pos (double ax-min)) range))
        angle (* (double amount) t)
        rot (case axis :x m/rotate-x :y m/rotate-y :z m/rotate-z)]
    (rot vertex angle)))

(defn- deform-taper
  "Scales the cross-section perpendicular to axis by position along it."
  [vertex _normal {:keys [deform/axis deform/amount]} [ax-min ax-max]]
  (let [pos (axis-component axis vertex)
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
  (let [pos (axis-component axis vertex)
        range (- (double ax-max) (double ax-min))
        t (if (zero? range) 0.0 (/ (- pos (double ax-min)) range))
        angle (* (double amount) t)
        ;; Bend around perpendicular axis: Y bends around Z, X around Y, Z around X
        rot (case axis :x m/rotate-y :y m/rotate-z :z m/rotate-x)]
    (rot vertex angle)))

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
                    (axis-range axis mesh))]
    (mapv (fn [face]
            (let [normal (:face/normal face)
                  new-verts (mapv #(deform-fn % normal descriptor ar)
                              (:face/vertices face))]
              (assoc face
                :face/vertices new-verts
                :face/normal (m/face-normal new-verts))))
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
                                                         :z [x y (- (double z))]))
                                                     verts)
                                ;; Reverse winding to fix normals
                                reversed (vec (reverse mirrored-verts))]
                            (make-face reversed (:face/style face))))
                        mesh)]
    (if merge?
      (into mesh reflected)
      reflected)))

;; --- mesh adjacency (shared by subdivision, smooth shading, auto-smooth) ---

(defn- edge-key
  "Returns a canonical key for an edge between two 3D points.
  Sorts the endpoints so edge [a b] and [b a] produce the same key."
  [a b]
  (if (neg? (compare (vec a) (vec b))) [a b] [b a]))

(defn- build-face-adjacency
  "Builds adjacency maps from a mesh.
  Returns {:edge-faces {edge-key → [face-index ...]}
           :vert-faces {vertex → [face-index ...]}
           :face-data  [{:vertices [...] :style ... :normal ... :point ...} ...]}"
  [mesh]
  (let [face-data (mapv (fn [face]
                          (let [verts (:face/vertices face)]
                            {:vertices verts
                             :style    (:face/style face)
                             :normal   (:face/normal face)
                             :point    (m/face-centroid verts)}))
                        mesh)
        edge-faces (reduce
                     (fn [acc [fi fd]]
                       (let [verts (:vertices fd)
                             n     (count verts)]
                         (reduce (fn [acc i]
                                   (let [j  (mod (inc i) n)
                                         ek (edge-key (nth verts i) (nth verts j))]
                                     (update acc ek (fnil conj []) fi)))
                                 acc (range n))))
                     {} (map-indexed vector face-data))
        vert-faces (reduce
                     (fn [acc [fi fd]]
                       (reduce (fn [acc v]
                                 (update acc v (fnil conj []) fi))
                               acc (:vertices fd)))
                     {} (map-indexed vector face-data))]
    {:edge-faces edge-faces
     :vert-faces vert-faces
     :face-data  face-data}))

(defn- compute-vertex-normals
  "Computes smooth vertex normals by averaging adjacent face normals.
  Returns {vertex → normalized-average-normal}."
  [face-data vert-faces]
  (reduce-kv
    (fn [acc v fi-list]
      (let [sum (reduce (fn [s fi]
                          (m/v+ s (m/normalize (:normal (nth face-data fi)))))
                        [0.0 0.0 0.0] fi-list)]
        (assoc acc v (m/normalize sum))))
    {} vert-faces))

;; --- subdivision ---

(defn- subdivide-once
  "One iteration of Catmull-Clark subdivision."
  ([mesh] (subdivide-once mesh nil))
  ([mesh hard-edges]
   (let [{:keys [edge-faces vert-faces face-data]} (build-face-adjacency mesh)

         vert-edges (reduce-kv
                      (fn [acc ek _]
                        (let [[a b] ek]
                          (-> acc
                              (update a (fnil conj #{}) ek)
                              (update b (fnil conj #{}) ek))))
                      {} edge-faces)

         edge-points (reduce-kv
                       (fn [acc ek face-indices]
                         (let [[a b] ek
                               mid   (m/lerp a b 0.5)]
                           (if (and hard-edges (contains? hard-edges ek))
                             ;; Hard edge: use midpoint (no averaging)
                             (assoc acc ek mid)
                             (if (= 2 (count face-indices))
                               (let [fp0 (:point (nth face-data (first face-indices)))
                                     fp1 (:point (nth face-data (second face-indices)))]
                                 (assoc acc ek (m/v* (reduce m/v+ [mid fp0 fp1]) (/ 1.0 3.0))))
                               (assoc acc ek mid)))))
                       {} edge-faces)

         new-verts  (reduce-kv
                      (fn [acc v fi-list]
                        (let [edges (get vert-edges v)
                              n-hard (if hard-edges
                                       (count (filter #(contains? hard-edges %) edges))
                                       0)]
                          (if (>= n-hard 2)
                            ;; Vertex on hard edge crease: use boundary rule
                            (let [hard-mids (->> edges
                                                 (filter #(contains? hard-edges %))
                                                 (mapv (fn [[a b]] (m/lerp a b 0.5))))
                                  avg-mid   (m/v* (reduce m/v+ hard-mids)
                                                  (/ 1.0 (count hard-mids)))]
                              (assoc acc v (m/v* (m/v+ (m/v* v 6.0)
                                                       (m/v* avg-mid 2.0))
                                                 (/ 1.0 8.0))))
                            ;; Normal Catmull-Clark vertex rule
                            (let [n  (count fi-list)
                                  fp (m/v* (reduce m/v+ (mapv #(:point (nth face-data %)) fi-list))
                                           (/ 1.0 n))
                                  ep (if (seq edges)
                                       (m/v* (reduce m/v+ (mapv (fn [[a b]] (m/lerp a b 0.5)) edges))
                                             (/ 1.0 (count edges)))
                                       v)
                                  new-pos (m/v* (m/v+ (m/v+ fp (m/v* ep 2.0))
                                                      (m/v* v (- n 3.0)))
                                                (/ 1.0 n))]
                              (assoc acc v new-pos)))))
                      {} vert-faces)]

     ;; Generate new quad faces
     (into []
       (mapcat
         (fn [{:keys [vertices style point]}]
           (let [n (count vertices)]
             (for [i (range n)
                   :let [j  (mod (inc i) n)
                         v0 (get new-verts (nth vertices i))
                         e0 (get edge-points (edge-key (nth vertices i) (nth vertices j)))
                         fp point
                         e1 (get edge-points (edge-key (nth vertices (mod (dec i) n))
                                                       (nth vertices i)))]]
               (make-face [v0 e0 fp e1] style))))
         face-data)))))

(defn subdivide
  "Applies Catmull-Clark subdivision to a mesh.
  opts:
    :iterations  - number of subdivision passes (each 4× face count)
    :hard-edges  - optional set of edge keys to preserve as creases
  Returns a new mesh of quad faces."
  [mesh opts]
  (let [iterations (get opts :iterations 1)
        hard       (:hard-edges opts)]
    (if (<= iterations 0)
      mesh
      (recur (subdivide-once mesh hard) (assoc opts :iterations (dec iterations))))))

;; --- auto-smooth ---

(defn auto-smooth-edges
  "Returns a set of hard edge keys where adjacent faces meet at more than
  the given angle. Use with subdivide's :hard-edges option.
  opts:
    :angle - threshold angle in radians (edges sharper than this are hard)"
  [mesh opts]
  (let [angle     (double (:angle opts))
        cos-limit (Math/cos angle)
        {:keys [edge-faces face-data]} (build-face-adjacency mesh)]
    (into #{}
      (keep (fn [[ek fi-list]]
              (when (= 2 (count fi-list))
                (let [n0 (m/normalize (:normal (nth face-data (first fi-list))))
                      n1 (m/normalize (:normal (nth face-data (second fi-list))))
                      cos-angle (m/dot n0 n1)]
                  (when (< cos-angle cos-limit)
                    ek)))))
      edge-faces)))

;; --- face selection ---

(defn- make-face-selector
  "Builds a predicate fn from a selector descriptor.
  Returns (fn [face centroid normal] -> boolean)."
  [opts]
  (case (:select/by opts)
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
    :uv/method  - :box, :spherical, :cylindrical, or :planar
    :uv/axis    - axis for :cylindrical/:planar (default :y)
    :uv/scale   - UV scale factor (default 1.0)
    :uv/offset  - [u-offset v-offset] (default [0 0])
    :select/*   - optional face selector"
  [mesh opts]
  (let [method (get opts :uv/method :box)
        axis   (get opts :uv/axis :y)
        scale  (double (get opts :uv/scale 1.0))
        [ou ov] (get opts :uv/offset [0 0])
        sel    (when (:select/by opts) (make-face-selector opts))
        bounds (mesh-bounds mesh)]
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

(defn- lerp-color
  "Linear interpolation between two RGB colors."
  [[_t1 r1 g1 b1] [_t2 r2 g2 b2] t]
  (let [t (double t)
        s (- 1.0 t)]
    [:color/rgb
     (int (Math/round (+ (* s (double r1)) (* t (double r2)))))
     (int (Math/round (+ (* s (double g1)) (* t (double g2)))))
     (int (Math/round (+ (* s (double b1)) (* t (double b2)))))]))

(defn- palette-color
  "Maps a [0,1] value to a color from a palette via linear interpolation."
  [palette t]
  (let [t  (Math/max 0.0 (Math/min 1.0 (double t)))
        n  (dec (count palette))
        fi (* t n)
        lo (int (Math/floor fi))
        lo (min lo (dec n))
        hi (min (inc lo) n)
        f  (- fi lo)]
    (lerp-color (nth palette lo) (nth palette hi) f)))

(defn color-mesh
  "Colors each face based on a descriptor.
  When :select/by is present, only selected faces are colored; others pass through.
  opts:
    :color/type    - :field, :axis-gradient, or :normal-map
    :color/palette - vector of [:color/rgb r g b] colors
    :color/field   - field descriptor (for :field type)
    :color/axis    - :x, :y, or :z (for :axis-gradient type)
    :select/*      - optional face selector (defaults to all faces)"
  [mesh opts]
  (let [palette (:color/palette opts)
        sel     (when (:select/by opts) (make-face-selector opts))
        bounds  (when (= :axis-gradient (:color/type opts))
                  (let [axis (get opts :color/axis :y)]
                    (axis-range axis mesh)))
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
                (/ (- (axis-component axis centroid) (double lo)) range))))

          :normal-map
          (fn [_face _centroid normal]
            (let [[nx ny nz] (m/normalize normal)
                  ax (abs (double nx))
                  ay (abs (double ny))
                  az (abs (double nz))
                  mx (max ax ay az)]
              (cond
                (== mx ax) (/ ax (+ ax ay az))
                (== mx ay) (/ (+ ax ay) (+ ax ay az))
                :else      (/ (+ ax ay az -0.01) (+ ax ay az))))))]
    (mapv (fn [face]
            (let [verts    (:face/vertices face)
                  centroid (m/face-centroid verts)
                  normal   (:face/normal face)]
              (if (or (nil? sel) (sel face centroid normal))
                (let [t     (color-fn face centroid normal)
                      color (palette-color palette t)
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
        sel       (when (:select/by opts) (make-face-selector opts))
        bounds    (when (= :axis-gradient (:color/type opts))
                    (axis-range (get opts :color/axis :y) mesh))
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
                  (/ (- (axis-component axis vertex) (double lo)) range)))))

          :normal-map
          (fn [_vertex normal _uv]
            (let [[nx ny nz] (m/normalize normal)
                  ax (abs (double nx))
                  ay (abs (double ny))
                  az (abs (double nz))
                  mx (max ax ay az)]
              (cond
                (== mx ax) (/ ax (+ ax ay az))
                (== mx ay) (/ (+ ax ay) (+ ax ay az))
                :else      (/ (+ ax ay az -0.01) (+ ax ay az))))))]
    (mapv (fn [face]
            (let [verts    (:face/vertices face)
                  centroid (m/face-centroid verts)
                  normal   (:face/normal face)
                  uvs      (:face/texture-coords face)]
              (if (or (nil? sel) (sel face centroid normal))
                (let [colors (mapv (fn [v i]
                                     (let [uv (when uvs (nth uvs i nil))]
                                       (palette-color palette (vert-t-fn v normal uv))))
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
        sel      (when (:select/by opts) (make-face-selector opts))]
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
        sel   (when (:select/by opts) (make-face-selector opts))]
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

;; --- polygonal modeling ---

(defn extrude-faces
  "Extrudes selected faces along their normals.
  opts:
    :select/*       - face selector (see make-face-selector)
    :extrude/amount - distance to push faces
    :extrude/scale  - optional scale factor toward centroid (default 1.0)"
  [mesh opts]
  (let [sel    (make-face-selector opts)
        amount (double (get opts :extrude/amount 1.0))
        scale  (double (get opts :extrude/scale 1.0))]
    (into []
      (mapcat
        (fn [face]
          (let [verts    (:face/vertices face)
                centroid (m/face-centroid verts)
                normal   (m/normalize (:face/normal face))]
            (if (sel face centroid (:face/normal face))
              (let [n     (count verts)
                    ;; Scale vertices toward centroid then push along normal
                    cap-verts (mapv (fn [v]
                                     (let [scaled (m/lerp centroid v scale)]
                                       (m/v+ scaled (m/v* normal amount))))
                                   verts)
                    cap   (make-face cap-verts (:face/style face))
                    ;; Side-wall quads connecting original edge to extruded edge
                    walls (for [i (range n)
                                :let [j  (mod (inc i) n)
                                      v0 (nth verts i)
                                      v1 (nth verts j)
                                      v2 (nth cap-verts j)
                                      v3 (nth cap-verts i)]]
                            (make-face [v0 v1 v2 v3] (:face/style face)))]
                (into [cap] walls))
              [face])))
        mesh))))

(defn inset-faces
  "Insets selected faces, creating a smaller inner face and border quads.
  opts:
    :select/*      - face selector
    :inset/amount  - how far to shrink inward (0-1 fraction of distance to centroid)"
  [mesh opts]
  (let [sel (make-face-selector opts)
        amt (double (get opts :inset/amount 0.2))]
    (into []
      (mapcat
        (fn [face]
          (let [verts    (:face/vertices face)
                centroid (m/face-centroid verts)
                normal   (:face/normal face)]
            (if (sel face centroid normal)
              (let [n          (count verts)
                    inner-verts (mapv #(m/lerp % centroid amt) verts)
                    inner-face (make-face inner-verts (:face/style face))
                    ;; Border quads between outer and inner edges
                    borders    (for [i (range n)
                                     :let [j  (mod (inc i) n)
                                           o0 (nth verts i)
                                           o1 (nth verts j)
                                           i0 (nth inner-verts i)
                                           i1 (nth inner-verts j)]]
                                 (make-face [o0 o1 i1 i0] (:face/style face)))]
                (into [inner-face] borders))
              [face])))
        mesh))))

;; --- convenience helpers ---

(defn bevel-faces
  "Insets selected faces then extrudes the inner faces, creating a beveled edge.
  Composes inset-faces and extrude-faces in one step.
  opts:
    :select/*     - face selector (defaults to :all)
    :bevel/inset  - how far to shrink inward (0-1)
    :bevel/depth  - extrusion distance (positive = outward, negative = inward)"
  [mesh opts]
  (let [inset-amt (get opts :bevel/inset 0.1)
        depth     (get opts :bevel/depth 0.05)
        sel-opts  (if (:select/by opts) opts {:select/by :all})
        ;; First inset: creates inner faces + border quads
        inset-mesh (inset-faces mesh (merge sel-opts {:inset/amount inset-amt}))]
    ;; Then extrude the smaller inner faces (they have the same selection criteria
    ;; but are now smaller). We use :all since inset already created the structure.
    (extrude-faces inset-mesh (merge sel-opts {:extrude/amount depth}))))

(defn greeble-faces
  "Adds procedural surface detail by insetting then noise-extruding faces.
  Creates mechanical/sci-fi panel detail. Composes inset + field-driven extrude.
  opts:
    :select/*            - face selector (defaults to :all)
    :greeble/field       - noise field for per-face extrusion depth
    :greeble/inset       - inset amount (default 0.1)
    :greeble/depth-range - [min-depth max-depth] range for extrusion"
  [mesh opts]
  (let [inset-amt   (get opts :greeble/inset 0.1)
        [d-min d-max] (get opts :greeble/depth-range [0.02 0.15])
        greeble-field (:greeble/field opts)
        sel-opts    (if (:select/by opts) opts {:select/by :all})
        ;; Inset all selected faces
        inset-mesh  (inset-faces mesh (merge sel-opts {:inset/amount inset-amt}))]
    ;; Extrude each face by a noise-sampled depth
    (mapv (fn [face]
            (let [verts    (:face/vertices face)
                  centroid (m/face-centroid verts)
                  [cx _cy cz] centroid
                  ;; Sample noise to get extrusion depth for this face
                  noise-val (if greeble-field
                              (* 0.5 (+ 1.0 (field/evaluate greeble-field
                                              (double cx) (double cz))))
                              (rand))
                  depth     (+ (double d-min) (* noise-val (- (double d-max) (double d-min))))
                  normal    (m/normalize (:face/normal face))
                  new-verts (mapv #(m/v+ % (m/v* normal depth)) verts)]
              (assoc face
                :face/vertices new-verts
                :face/normal (m/face-normal new-verts))))
          inset-mesh)))

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
  (let [all-verts (mapcat :face/vertices mesh)
        xs (map #(nth % 0) all-verts)
        ys (map #(nth % 1) all-verts)
        zs (map #(nth % 2) all-verts)]
    {:min [(apply min xs) (apply min ys) (apply min zs)]
     :max [(apply max xs) (apply max ys) (apply max zs)]}))

(defn mesh-center
  "Returns the center point of a mesh's bounding box."
  [mesh]
  (let [{[x0 y0 z0] :min [x1 y1 z1] :max} (mesh-bounds mesh)]
    [(* 0.5 (+ (double x0) x1))
     (* 0.5 (+ (double y0) y1))
     (* 0.5 (+ (double z0) z1))]))

;; --- shading ---

(defn- shade-color
  "Adjusts an RGB fill color by a brightness factor [0, 1]."
  [fill brightness]
  (let [[type r g b] fill]
    [type
     (int (Math/round (* (double r) brightness)))
     (int (Math/round (* (double g) brightness)))
     (int (Math/round (* (double b) brightness)))]))

(defn- shade-face-style
  "Applies lighting to a face's style based on its normal and lights.
  norm-normal and norm-light-dir should be pre-normalized to avoid
  redundant computation in hot loops.
  If the style has a :material key, delegates to eido.ir.material/shade-face
  for Blinn-Phong multi-light shading."
  ([style norm-normal norm-light-dir light]
   (shade-face-style style norm-normal norm-light-dir light nil nil nil))
  ([style norm-normal norm-light-dir light cam-dir]
   (shade-face-style style norm-normal norm-light-dir light cam-dir nil nil))
  ([style norm-normal norm-light-dir light cam-dir lights face-centroid]
   (let [has-light (or light (seq lights))]
     (if (and has-light (:style/fill style))
       (if-let [material (:material style)]
         ;; Material-based shading (supports all light types)
         (let [mat-fn (requiring-resolve 'eido.ir.material/shade-face)]
           (mat-fn style norm-normal norm-light-dir cam-dir light material
                   lights face-centroid))
         ;; Legacy diffuse-only shading (directional light only)
         (let [light    (or light (first lights))
               ambient   (double (get light :light/ambient 0.3))
               intensity (double (or (:light/multiplier light)
                                     (:light/intensity light) 0.7))
               cos-angle (m/dot norm-normal
                           (or norm-light-dir
                               (m/normalize (:light/direction light))))
               brightness (min 1.0 (+ ambient (* intensity (max 0.0 cos-angle))))]
           (cond-> (assoc style :style/fill (shade-color (:style/fill style) brightness))
             (:style/stroke style)
             (assoc-in [:style/stroke :color]
                       (shade-color (get-in style [:style/stroke :color]) brightness)))))
       style))))

(defn- expand-polygon
  "Expands projected 2D polygon slightly outward from its centroid
  to seal anti-aliasing gaps between adjacent faces."
  [projected]
  (let [n  (count projected)
        cx (/ (reduce + (map first projected)) n)
        cy (/ (reduce + (map second projected)) n)
        max-d (reduce max 0.001
                (map (fn [[x y]]
                       (Math/sqrt (+ (let [dx (- x cx)] (* dx dx))
                                     (let [dy (- y cy)] (* dy dy)))))
                     projected))
        expand (min 0.4 (* 0.15 max-d))]
    (mapv (fn [[x y]]
            (let [dx (- x cx)
                  dy (- y cy)
                  d  (Math/sqrt (+ (* dx dx) (* dy dy)))]
              (if (< d 0.001)
                [x y]
                (let [s (/ (+ d expand) d)]
                  [(+ cx (* dx s))
                   (+ cy (* dy s))]))))
          projected)))

;; --- render-mesh ---

(defn- render-wireframe
  "Renders a mesh as wireframe edges projected to 2D :shape/line nodes."
  [projection mesh opts]
  (let [cam-dir    (m/camera-direction projection)
        base-style (:style opts)
        stroke     (or (:style/stroke base-style)
                       {:color [:color/rgb 0 0 0] :width 1})
        ;; Extract unique edges as sets of vertex pairs
        edges      (into #{}
                     (mapcat (fn [face]
                               (let [verts (:face/vertices face)
                                     n     (count verts)]
                                 (for [i (range n)
                                       :let [j (mod (inc i) n)
                                             a (nth verts i)
                                             b (nth verts j)]]
                                   (if (neg? (compare (vec a) (vec b)))
                                     [a b]
                                     [b a]))))
                            mesh))
        ;; Sort edges by depth (farthest first)
        sorted     (->> edges
                        (sort-by (fn [[a b]]
                                   (m/dot (m/v* (m/v+ a b) 0.5) cam-dir))))
        proj-fn    (m/make-projector projection)]
    {:node/type :group
     :group/children
     (mapv (fn [[a b]]
             (let [pa (proj-fn a)
                   pb (proj-fn b)]
               {:node/type :shape/line
                :line/from pa
                :line/to   pb
                :style/stroke stroke}))
           sorted)}))

(defn render-mesh
  "Projects a mesh and returns a :group node with faces sorted back-to-front.
  Each face becomes a :shape/path node.

  opts:
    :style     - default style {:style/fill [...] :style/stroke {...}}
    :light     - single light map (backward compatible)
    :lights    - vector of light maps (multi-light)
    :cull-back - if true, omit back-facing polygons (default true)
    :wireframe - if true, render edges only (no fill, no shading)
    :shading   - :flat (default) or :smooth (vertex normal averaging)"
  [projection mesh opts]
  (if (:wireframe opts)
    (render-wireframe projection mesh opts)
    (let [light      (:light opts)
          lights     (:lights opts)
          cull?      (get opts :cull-back true)
          base-style (:style opts)
          cam-dir    (m/camera-direction projection)
          proj-fn    (m/make-projector projection)
          smooth?    (= :smooth (:shading opts))
          ;; Compute smooth vertex normals if needed
          vert-normals (when smooth?
                         (let [{:keys [face-data vert-faces]} (build-face-adjacency mesh)]
                           (compute-vertex-normals face-data vert-faces)))
          ;; Pre-normalize directional light direction for the common case
          norm-light-dir (when (and light (:light/direction light))
                           (m/normalize (:light/direction light)))
          ;; Compute culling and depth in world space using camera direction
          processed  (->> mesh
                          (map (fn [face]
                                 (let [normal      (:face/normal face)
                                       verts       (:face/vertices face)
                                       norm-normal (m/normalize normal)
                                       facing      (m/dot norm-normal cam-dir)
                                       centroid    (m/face-centroid verts)
                                       depth       (m/dot centroid cam-dir)
                                       ;; For smooth shading, average the vertex normals
                                       shade-normal (if (and smooth? vert-normals)
                                                      (let [vn (mapv vert-normals verts)]
                                                        (m/normalize
                                                          (reduce m/v+ [0.0 0.0 0.0] (filter some? vn))))
                                                      norm-normal)]
                                   {:face face :facing facing :depth depth
                                    :norm-normal norm-normal :shade-normal shade-normal
                                    :centroid centroid})))
                          ;; Keep front-facing (normal points toward camera)
                          (filter (fn [{:keys [facing]}]
                                    (if cull? (> facing 0.0) true)))
                          ;; Sort farthest first (smallest depth = farthest)
                          (sort-by :depth))]
      {:node/type :group
       :group/children
       (into []
         (mapcat
           (fn [{:keys [face facing depth shade-normal centroid]}]
             (let [face-style    (or (:face/style face) base-style)
                   shade-normal  (if (neg? facing)
                                   (m/v* shade-normal -1)
                                   shade-normal)
                   vert-colors   (:face/vertex-colors face)
                   vert-normals  (:face/vertex-normals face)
                   vert-specular (:face/vertex-specular face)
                   verts         (:face/vertices face)]
               (if (or vert-colors vert-normals vert-specular)
                 ;; Fan-triangulate for per-vertex interpolation
                 (let [n          (count verts)
                       center-2d  (let [ps (mapv proj-fn verts)
                                        cx (/ (reduce + (map first ps)) n)
                                        cy (/ (reduce + (map second ps)) n)]
                                    [cx cy])
                       center-color (when vert-colors
                                      (let [rs (map #(nth % 1) vert-colors)
                                            gs (map #(nth % 2) vert-colors)
                                            bs (map #(nth % 3) vert-colors)]
                                        [:color/rgb
                                         (int (/ (reduce + rs) n))
                                         (int (/ (reduce + gs) n))
                                         (int (/ (reduce + bs) n))]))
                       center-normal (when vert-normals
                                       (m/normalize
                                         (reduce m/v+ [0.0 0.0 0.0] vert-normals)))
                       center-specular (when vert-specular
                                         (/ (reduce + vert-specular) n))]
                   (for [i (range n)
                         :let [j  (mod (inc i) n)
                               p0 (proj-fn (nth verts i))
                               p1 (proj-fn (nth verts j))
                               ;; Per-sub-triangle shading normal
                               tri-shade-normal
                               (if vert-normals
                                 (let [n0 (nth vert-normals i)
                                       n1 (nth vert-normals j)]
                                   (m/normalize
                                     (reduce m/v+ [0.0 0.0 0.0]
                                       [n0 n1 center-normal])))
                                 shade-normal)
                               tri-shade-normal (if (neg? facing)
                                                  (m/v* tri-shade-normal -1)
                                                  tri-shade-normal)
                               ;; Per-sub-triangle specular override
                               tri-style (let [s (if vert-colors
                                                   (let [c0 (nth vert-colors i)
                                                         c1 (nth vert-colors j)
                                                         avg (lerp-color
                                                               (lerp-color c0 c1 0.5)
                                                               center-color 0.333)]
                                                     (assoc (or face-style {}) :style/fill avg))
                                                   face-style)]
                                           (if vert-specular
                                             (let [avg-spec (/ (+ (nth vert-specular i)
                                                                  (nth vert-specular j)
                                                                  (double center-specular))
                                                               3.0)]
                                               (if (:material s)
                                                 (assoc-in s [:material :material/specular] avg-spec)
                                                 s))
                                             s))
                               shaded    (shade-face-style tri-style tri-shade-normal
                                           norm-light-dir light cam-dir
                                           lights centroid)
                               expanded  (expand-polygon [p0 p1 center-2d])]]
                     (assoc (merge (scene/polygon expanded) shaded)
                       :node/depth depth)))
                 ;; Standard single-color face
                 (let [shaded    (shade-face-style face-style shade-normal
                                                   norm-light-dir light cam-dir
                                                   lights centroid)
                       projected (mapv proj-fn verts)
                       expanded  (expand-polygon projected)]
                   [(assoc (merge (scene/polygon expanded) shaded)
                      :node/depth depth)]))))
           processed))})))

;; --- depth sorting ---

(defn- flatten-node
  "Flattens a node for depth sorting. Groups are expanded to their children;
  leaf nodes are returned as single-element vectors."
  [node]
  (if (= :group (:node/type node))
    (:group/children node)
    [node]))

(defn depth-sort
  "Sorts a mixed collection of nodes by :node/depth for correct 3D occlusion.
  Accepts any number of node collections — vectors of nodes, individual group
  nodes (from render-mesh), or single nodes.

  Groups are flattened into their children before sorting. All nodes are
  sorted smallest-depth-first (farthest from camera drawn first — painter's
  algorithm). Nodes without :node/depth are placed at the back.

  Returns a vector of sorted nodes ready for :image/nodes."
  [& node-colls]
  (let [nodes (->> node-colls
                   (mapcat (fn [coll]
                             (if (map? coll)
                               (flatten-node coll)
                               (mapcat flatten-node coll))))
                   (vec))]
    (vec (sort-by #(get % :node/depth Double/NEGATIVE_INFINITY) nodes))))

;; --- convenience functions ---

(defn cube
  "Creates a 3D cube projected into 2D, returned as a :group node.
  projection: projection map. position: [x y z]. size: number.
  opts: :style, :light, :cull-back."
  [projection position size opts]
  (render-mesh projection (cube-mesh position size) opts))

(defn prism
  "Creates a 3D prism projected into 2D."
  [projection base-points height opts]
  (render-mesh projection (prism-mesh base-points height) opts))

(defn cylinder
  "Creates a 3D cylinder projected into 2D."
  [projection position radius height opts]
  (let [mesh (-> (cylinder-mesh radius height (get opts :segments 16))
                 (translate-mesh position))]
    (render-mesh projection mesh opts)))

(defn sphere
  "Creates a 3D sphere projected into 2D."
  [projection position radius opts]
  (let [seg  (get opts :segments 16)
        rng  (get opts :rings 8)
        mesh (-> (sphere-mesh radius seg rng)
                 (translate-mesh position))]
    (render-mesh projection mesh opts)))

(defn torus
  "Creates a 3D torus projected into 2D."
  [projection position R r opts]
  (let [rseg (get opts :ring-segments 24)
        tseg (get opts :tube-segments 12)
        mesh (-> (torus-mesh R r rseg tseg)
                 (translate-mesh position))]
    (render-mesh projection mesh opts)))

(defn cone
  "Creates a 3D cone projected into 2D."
  [projection position radius height opts]
  (let [mesh (-> (cone-mesh radius height (get opts :segments 16))
                 (translate-mesh position))]
    (render-mesh projection mesh opts)))

;; --- text mesh ---

(defn- contour->points
  "Extracts [x y] points from a contour (flattened path commands)."
  [contour]
  (into []
    (keep (fn [[cmd & args]]
            (case cmd
              :move-to (first args)
              :line-to (first args)
              nil)))
    contour))

(defn- extrude-walls
  "Creates only side-wall faces (no caps) for a contour extrusion."
  [path-points direction]
  (let [pts (vec path-points)
        n   (count pts)
        dir (mapv double direction)
        base (mapv (fn [[x z]] [(double x) 0.0 (double z)]) pts)
        top  (mapv #(m/v+ % dir) base)]
    (for [i (range n)
          :let [j (mod (inc i) n)]]
      (make-face [(nth base i) (nth top i)
                  (nth top j) (nth base j)]))))

(defn- offset-path-commands
  "Offsets all coordinates in path commands by [dx dy]."
  [commands dx dy]
  (mapv (fn [[cmd & args]]
          (case cmd
            :move-to  [:move-to [(+ dx ((first args) 0))
                                  (+ dy ((first args) 1))]]
            :line-to  [:line-to [(+ dx ((first args) 0))
                                  (+ dy ((first args) 1))]]
            :quad-to  [:quad-to
                        [(+ dx ((first args) 0))
                         (+ dy ((first args) 1))]
                        [(+ dx ((second args) 0))
                         (+ dy ((second args) 1))]]
            :curve-to [:curve-to
                        [(+ dx ((first args) 0))
                         (+ dy ((first args) 1))]
                        [(+ dx ((second args) 0))
                         (+ dy ((second args) 1))]
                        [(+ dx ((nth args 2) 0))
                         (+ dy ((nth args 2) 1))]]
            :close [:close]))
        commands))

(defn text-mesh
  "Creates side-wall faces for extruded text (no caps).
  content: string. font-spec: font map. depth: extrusion depth along Y axis.
  opts: :flatness (curve approximation, default 1.0)."
  [content font-spec depth opts]
  (let [flatness   (get opts :flatness 1.0)
        glyph-data (text/text->glyph-paths content font-spec)
        all-faces
        (mapcat
          (fn [{:keys [commands position]}]
            (let [[gx gy] position
                  offset-cmds (offset-path-commands commands gx gy)
                  flat (text/flatten-commands offset-cmds flatness)
                  contours (text/glyph-contours flat)]
              (mapcat (fn [contour]
                        (let [pts (contour->points contour)]
                          (when (>= (count pts) 3)
                            (extrude-walls pts [0 depth 0]))))
                      contours)))
          glyph-data)]
    (vec all-faces)))

(defn- project-path-commands
  "Projects 2D path commands (in XZ plane) through a 3D projection.
  The commands are first placed in 3D as [x, y-offset, z] then projected."
  [proj-fn commands y-offset center]
  (mapv (fn [[cmd & args]]
          (case cmd
            :move-to  (let [[x z] (first args)
                            p3d (m/v- [(double x) y-offset (double z)] center)]
                        [:move-to (proj-fn p3d)])
            :line-to  (let [[x z] (first args)
                            p3d (m/v- [(double x) y-offset (double z)] center)]
                        [:line-to (proj-fn p3d)])
            :quad-to  (let [[cx cz] (first args)
                            [x z]   (second args)
                            cp3d (m/v- [(double cx) y-offset (double cz)] center)
                            p3d  (m/v- [(double x) y-offset (double z)] center)]
                        [:quad-to (proj-fn cp3d)
                                  (proj-fn p3d)])
            :curve-to (let [[c1x c1z] (first args)
                            [c2x c2z] (second args)
                            [x z]     (nth args 2)
                            cp1 (m/v- [(double c1x) y-offset (double c1z)] center)
                            cp2 (m/v- [(double c2x) y-offset (double c2z)] center)
                            p3d (m/v- [(double x) y-offset (double z)] center)]
                        [:curve-to (proj-fn cp1)
                                   (proj-fn cp2)
                                   (proj-fn p3d)])
            :close [:close]))
        commands))

(defn text-3d
  "Creates 3D extruded text projected into 2D, returned as a :group node.
  projection: projection map. content: string. font-spec: font map.
  depth: extrusion depth. opts: same as render-mesh plus :flatness.
  The mesh is centered at the origin for natural rotation.
  Front/back caps use even-odd fill to correctly render letter holes."
  [projection content font-spec depth opts]
  (let [mesh     (text-mesh content font-spec depth opts)
        center   (mesh-center mesh)
        centered (translate-mesh mesh (mapv - center))
        ;; Render side walls
        walls    (render-mesh projection centered opts)
        ;; Build cap path commands from all glyphs combined
        glyph-data (text/text->glyph-paths content font-spec)
        cap-cmds (into []
                   (mapcat (fn [{:keys [commands position]}]
                             (let [[gx gy] position]
                               (offset-path-commands commands gx gy))))
                   glyph-data)
        ;; Compute cap depth for sorting
        cam-dir  (m/camera-direction projection)
        front-center (m/v- [0.0 0.0 0.0] center)
        back-center  (m/v- [0.0 (double depth) 0.0] center)
        front-depth (m/dot front-center cam-dir)
        back-depth  (m/dot back-center cam-dir)
        ;; Determine which cap faces the camera
        front-facing (> (m/dot [0.0 -1.0 0.0] cam-dir) 0)
        back-facing  (> (m/dot [0.0 1.0 0.0] cam-dir) 0)
        ;; Shade cap faces
        light    (:light opts)
        base-style (:style opts)
        norm-light-dir (when light (m/normalize (:light/direction light)))
        ;; Project cap paths and create nodes
        proj-fn (m/make-projector projection)
        caps (cond-> []
               front-facing
               (conj (merge {:node/type      :shape/path
                             :path/commands  (project-path-commands
                                               proj-fn cap-cmds 0.0 center)
                             :path/fill-rule :even-odd
                             :node/depth     front-depth}
                            (shade-face-style base-style [0.0 -1.0 0.0]
                                              norm-light-dir light)))
               back-facing
               (conj (merge {:node/type      :shape/path
                             :path/commands  (project-path-commands
                                               proj-fn cap-cmds
                                               (double depth) center)
                             :path/fill-rule :even-odd
                             :node/depth     back-depth}
                            (shade-face-style base-style [0.0 1.0 0.0]
                                              norm-light-dir light))))
        ;; Merge caps into wall children and re-sort by depth
        all-nodes (into (:group/children walls) caps)
        sorted    (vec (sort-by #(get % :node/depth Double/NEGATIVE_INFINITY)
                                all-nodes))]
    (assoc walls :group/children sorted)))
