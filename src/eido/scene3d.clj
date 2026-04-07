(ns eido.scene3d
  (:require
    [eido.math3d :as m]
    [eido.scene :as scene]
    [eido.text :as text]))

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
  "Applies lighting to a face's style based on its normal and a light."
  [style normal light]
  (if (and light (:style/fill style))
    (let [light-dir (m/normalize (:light/direction light))
          ambient   (double (get light :light/ambient 0.3))
          intensity (double (get light :light/intensity 0.7))
          cos-angle (m/dot (m/normalize normal) light-dir)
          brightness (min 1.0 (+ ambient (* intensity (max 0.0 cos-angle))))]
      (cond-> (assoc style :style/fill (shade-color (:style/fill style) brightness))
        (:style/stroke style)
        (assoc-in [:style/stroke :color]
                  (shade-color (get-in style [:style/stroke :color]) brightness))))
    style))

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
                                   (m/dot (m/v* (m/v+ a b) 0.5) cam-dir))))]
    {:node/type :group
     :group/children
     (mapv (fn [[a b]]
             (let [pa (m/project projection a)
                   pb (m/project projection b)]
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
    :light     - light map for diffuse shading (optional)
    :cull-back - if true, omit back-facing polygons (default true)
    :wireframe - if true, render edges only (no fill, no shading)"
  [projection mesh opts]
  (if (:wireframe opts)
    (render-wireframe projection mesh opts)
    (let [light      (:light opts)
          cull?      (get opts :cull-back true)
          base-style (:style opts)
          cam-dir    (m/camera-direction projection)
          ;; Compute culling and depth in world space using camera direction
          processed  (->> mesh
                          (map (fn [face]
                                 (let [normal  (:face/normal face)
                                       verts   (:face/vertices face)
                                       facing  (m/dot (m/normalize normal) cam-dir)
                                       depth   (m/dot (m/face-centroid verts) cam-dir)]
                                   {:face face :facing facing :depth depth})))
                          ;; Keep front-facing (normal points toward camera)
                          (filter (fn [{:keys [facing]}]
                                    (if cull? (> facing 0.0) true)))
                          ;; Sort farthest first (smallest depth = farthest)
                          (sort-by :depth))]
      {:node/type :group
       :group/children
       (mapv (fn [{:keys [face facing depth]}]
               (let [face-style (or (:face/style face) base-style)
                     ;; For back-facing faces (when cull-back is false),
                     ;; flip normal so shading uses the camera-facing side
                     normal     (if (neg? facing)
                                  (m/v* (:face/normal face) -1)
                                  (:face/normal face))
                     shaded     (shade-face-style face-style normal light)
                     projected  (mapv #(m/project projection %) (:face/vertices face))
                     expanded   (expand-polygon projected)]
                 (assoc (merge (scene/polygon expanded) shaded)
                   :node/depth depth)))
             processed)})))

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
  [projection commands y-offset center]
  (mapv (fn [[cmd & args]]
          (case cmd
            :move-to  (let [[x z] (first args)
                            p3d (m/v- [(double x) y-offset (double z)] center)]
                        [:move-to (m/project projection p3d)])
            :line-to  (let [[x z] (first args)
                            p3d (m/v- [(double x) y-offset (double z)] center)]
                        [:line-to (m/project projection p3d)])
            :quad-to  (let [[cx cz] (first args)
                            [x z]   (second args)
                            cp3d (m/v- [(double cx) y-offset (double cz)] center)
                            p3d  (m/v- [(double x) y-offset (double z)] center)]
                        [:quad-to (m/project projection cp3d)
                                  (m/project projection p3d)])
            :curve-to (let [[c1x c1z] (first args)
                            [c2x c2z] (second args)
                            [x z]     (nth args 2)
                            cp1 (m/v- [(double c1x) y-offset (double c1z)] center)
                            cp2 (m/v- [(double c2x) y-offset (double c2z)] center)
                            p3d (m/v- [(double x) y-offset (double z)] center)]
                        [:curve-to (m/project projection cp1)
                                   (m/project projection cp2)
                                   (m/project projection p3d)])
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
        ;; Project cap paths and create nodes
        caps (cond-> []
               front-facing
               (conj (merge {:node/type      :shape/path
                             :path/commands  (project-path-commands
                                               projection cap-cmds 0.0 center)
                             :path/fill-rule :even-odd
                             :node/depth     front-depth}
                            (shade-face-style base-style [0.0 -1.0 0.0] light)))
               back-facing
               (conj (merge {:node/type      :shape/path
                             :path/commands  (project-path-commands
                                               projection cap-cmds
                                               (double depth) center)
                             :path/fill-rule :even-odd
                             :node/depth     back-depth}
                            (shade-face-style base-style [0.0 1.0 0.0] light))))
        ;; Merge caps into wall children and re-sort by depth
        all-nodes (into (:group/children walls) caps)
        sorted    (vec (sort-by #(get % :node/depth Double/NEGATIVE_INFINITY)
                                all-nodes))]
    (assoc walls :group/children sorted)))
