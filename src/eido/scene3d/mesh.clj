(ns eido.scene3d.mesh
  "Mesh constructors: primitives, platonic solids, and parametric generators."
  (:require
    [eido.ir.field :as field]
    [eido.math :as m]
    [eido.scene3d.util :as u]))

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
     [(u/make-face [v3 v2 v1 v0])   ;; front  (z=pz, normal -Z)
      (u/make-face [v4 v5 v6 v7])   ;; back   (z=pz+s, normal +Z)
      (u/make-face [v0 v1 v5 v4])   ;; bottom (y=py, normal -Y)
      (u/make-face [v2 v3 v7 v6])   ;; top    (y=py+s, normal +Y)
      (u/make-face [v0 v4 v7 v3])   ;; left   (x=px, normal -X)
      (u/make-face [v1 v2 v6 v5])]))) ;; right  (x=px+s, normal +X)

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
        bottom-face (u/make-face bottom)
        ;; Top cap (CW from above → normal points up)
        top-face    (u/make-face (vec (reverse top)))
        ;; Side faces (winding for outward-pointing normals)
        sides (for [i (range n)
                    :let [j (mod (inc i) n)]]
                (u/make-face [(nth bottom i) (nth top i)
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
          (u/make-face [v0 v2 v3])
          (if (= lat (dec rng))
            ;; Bottom cap: triangle
            (u/make-face [v0 v1 v2])
            ;; Regular quad
            (u/make-face [v0 v1 v2 v3])))))))

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
        base-face (u/make-face base)
        top-face  (u/make-face (vec (reverse top)))
        ;; Sides (winding for outward-pointing normals)
        sides (for [i (range n)
                    :let [j (mod (inc i) n)]]
                (u/make-face [(nth base i) (nth top i)
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
        (u/make-face [(pt (* i rstep) (* (inc j) tstep))
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
        base-face (u/make-face base-pts)
        ;; Side triangles (winding for outward-pointing normals)
        sides (for [i (range seg)
                    :let [j (mod (inc i) seg)]]
                (u/make-face [(nth base-pts j) (nth base-pts i) apex]))]
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
              (u/make-face vs)
              (u/make-face (vec (reverse vs))))))
        face-indices))

(defn- platonic-tetrahedron [r]
  (let [a (/ 1.0 3.0)
        b (/ (Math/sqrt 8.0) 3.0)
        c (/ (Math/sqrt 2.0) 3.0)
        d (/ (Math/sqrt 6.0) 3.0)]
    [(u/make-face [(m/v* [0.0 1.0 0.0] r) (m/v* [c (- a) d] r) (m/v* [(- b) (- a) 0.0] r)])
     (u/make-face [(m/v* [0.0 1.0 0.0] r) (m/v* [c (- a) (- d)] r) (m/v* [c (- a) d] r)])
     (u/make-face [(m/v* [0.0 1.0 0.0] r) (m/v* [(- b) (- a) 0.0] r) (m/v* [c (- a) (- d)] r)])
     (u/make-face [(m/v* [(- b) (- a) 0.0] r) (m/v* [c (- a) d] r) (m/v* [c (- a) (- d)] r)])]))

(defn- platonic-octahedron [r]
  (let [px [r 0.0 0.0]  nx [(- r) 0.0 0.0]
        py [0.0 r 0.0]  ny [0.0 (- r) 0.0]
        pz [0.0 0.0 r]  nz [0.0 0.0 (- r)]]
    [(u/make-face [py pz px]) (u/make-face [py px nz])
     (u/make-face [py nz nx]) (u/make-face [py nx pz])
     (u/make-face [ny px pz]) (u/make-face [ny nz px])
     (u/make-face [ny nx nz]) (u/make-face [ny pz nx])]))

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
        dx    (if (> cols 1) (/ (double bw) (dec (int cols))) 0.0)
        dz    (if (> rows 1) (/ (double bd) (dec (int rows))) 0.0)
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
        (u/make-face [(pts i) (pts i2) (pts i3) (pts i1)])))))

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
        (u/make-face [v0 v1 v2 v3])))))

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
        (u/make-face [v0 v1 v2 v3])))))
