(ns eido.scene3d.render
  "3D rendering: shading, NPR, mesh projection, depth sorting,
  convenience wrappers, and text mesh."
  (:require
    [eido.hatch :as hatch]
    [eido.math3d :as m]
    [eido.scene :as scene]
    [eido.scene3d.camera :as camera]
    [eido.scene3d.mesh :as mesh]
    [eido.scene3d.topology :as topology]
    [eido.scene3d.transform :as xform]
    [eido.scene3d.util :as u]
    [eido.stipple :as stipple]
    [eido.text :as text]))

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

;; --- NPR rendering helpers ---

(defn- point-in-polygon?
  "Tests if a 2D point is inside a polygon using the ray casting algorithm."
  [[px py] polygon]
  (let [n (count polygon)]
    (loop [i 0 j (dec n) inside false]
      (if (>= i n)
        inside
        (let [[xi yi] (nth polygon i)
              [xj yj] (nth polygon j)
              intersect (and (not= (> yi py) (> yj py))
                             (< px (+ xi (* (/ (- xj xi) (- yj yi))
                                            (- py yi)))))]
          (recur (inc i) i (if intersect (not inside) inside)))))))

(defn- clip-line-to-polygon
  "Clips a line segment to a convex-ish polygon. Returns [x1 y1 x2 y2] or nil."
  [[x1 y1 x2 y2] polygon]
  (let [n   (count polygon)
        dx  (- (double x2) (double x1))
        dy  (- (double y2) (double y1))
        ;; Find all intersection parameters t along the line
        ts  (for [i (range n)
                  :let [j  (mod (inc i) n)
                        [ex1 ey1] (nth polygon i)
                        [ex2 ey2] (nth polygon j)
                        edx (- (double ex2) (double ex1))
                        edy (- (double ey2) (double ey1))
                        denom (- (* dx edy) (* dy edx))]
                  :when (not (zero? denom))
                  :let [t (/ (- (* (- (double ex1) (double x1)) edy)
                                (* (- (double ey1) (double y1)) edx))
                             denom)
                        u (/ (- (* (- (double ex1) (double x1)) dy)
                                (* (- (double ey1) (double y1)) dx))
                             denom)]
                  :when (and (>= u 0.0) (<= u 1.0)
                             (>= t 0.0) (<= t 1.0))]
              t)
        ts  (sort ts)]
    (when (>= (count ts) 2)
      (let [t0 (first ts)
            t1 (last ts)]
        [(+ (double x1) (* t0 dx)) (+ (double y1) (* t0 dy))
         (+ (double x1) (* t1 dx)) (+ (double y1) (* t1 dy))]))))

(defn- npr-face-nodes
  "Generates hatch or stipple nodes for a projected face polygon.
  Hatch lines are clipped to the face polygon boundary.
  Returns a vector of scene nodes (lines or circles) within the face."
  [projected face-style brightness depth]
  (let [xs (map first projected)
        ys (map second projected)
        bx (apply min xs) by (apply min ys)
        bw (- (apply max xs) bx) bh (- (apply max ys) by)
        fill-type (:render/mode face-style)]
    (case fill-type
      :hatch
      (let [angle    (get face-style :hatch/angle 45)
            spacing  (max 1.0 (* (double (get face-style :hatch/spacing 4)) brightness))
            color    (get face-style :hatch/color [:color/rgb 30 30 30])
            stroke-w (get face-style :hatch/stroke-width 0.5)
            lines    (hatch/hatch-lines bx by bw bh {:angle angle :spacing spacing})
            poly     (vec projected)]
        (into []
          (keep (fn [line]
                  (when-let [[cx1 cy1 cx2 cy2] (clip-line-to-polygon line poly)]
                    (assoc {:node/type :shape/path
                            :path/commands [[:move-to [cx1 cy1]] [:line-to [cx2 cy2]]]
                            :style/stroke {:color color :width stroke-w}}
                      :node/depth depth))))
          lines))

      :stipple
      (let [density (min 1.0 (* brightness (double (get face-style :stipple/density 0.5))))
            radius  (get face-style :stipple/radius 1.0)
            seed    (get face-style :stipple/seed (hash projected))
            color   (get face-style :stipple/color [:color/rgb 30 30 30])
            poly    (vec projected)
            nodes   (stipple/stipple-fill->nodes bx by bw bh
                      {:stipple/density density :stipple/radius radius
                       :stipple/seed seed :stipple/color color})]
        (into []
          (keep (fn [node]
                  (when (point-in-polygon? (:circle/center node) poly)
                    (assoc node :node/depth depth))))
          nodes))

      [])))

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
                         (let [{:keys [face-data vert-faces]} (topology/build-face-adjacency mesh)]
                           (topology/compute-vertex-normals face-data vert-faces)))
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
                                                         avg (u/lerp-color
                                                               (u/lerp-color c0 c1 0.5)
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
                 (let [projected (mapv proj-fn verts)
                       expanded  (expand-polygon projected)]
                   (if (:render/mode face-style)
                     ;; NPR rendering: hatch or stipple
                     (let [has-light (or light (seq lights))
                           brightness (if has-light
                                        (let [l (or light (first lights))
                                              amb (double (get l :light/ambient 0.3))
                                              int (double (or (:light/multiplier l)
                                                              (:light/intensity l) 0.7))
                                              cos (m/dot shade-normal
                                                    (m/normalize (or (:light/direction l) [0 1 0])))]
                                          (min 1.0 (+ amb (* int (max 0.0 cos)))))
                                        1.0)
                           bg-node (assoc (scene/polygon expanded)
                                     :style/fill (get face-style :style/fill [:color/rgb 255 255 255])
                                     :node/depth depth)
                           npr-nodes (npr-face-nodes projected face-style brightness depth)]
                       (into [bg-node] npr-nodes))
                     ;; Solid fill
                     (let [shaded (shade-face-style face-style shade-normal
                                                    norm-light-dir light cam-dir
                                                    lights centroid)]
                       [(assoc (merge (scene/polygon expanded) shaded)
                          :node/depth depth)]))))))
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
  (render-mesh projection (mesh/cube-mesh position size) opts))

(defn prism
  "Creates a 3D prism projected into 2D."
  [projection base-points height opts]
  (render-mesh projection (mesh/prism-mesh base-points height) opts))

(defn cylinder
  "Creates a 3D cylinder projected into 2D."
  [projection position radius height opts]
  (let [m (-> (mesh/cylinder-mesh radius height (get opts :segments 16))
              (xform/translate-mesh position))]
    (render-mesh projection m opts)))

(defn sphere
  "Creates a 3D sphere projected into 2D."
  [projection position radius opts]
  (let [seg  (get opts :segments 16)
        rng  (get opts :rings 8)
        m    (-> (mesh/sphere-mesh radius seg rng)
                 (xform/translate-mesh position))]
    (render-mesh projection m opts)))

(defn torus
  "Creates a 3D torus projected into 2D."
  [projection position R r opts]
  (let [rseg (get opts :ring-segments 24)
        tseg (get opts :tube-segments 12)
        m    (-> (mesh/torus-mesh R r rseg tseg)
                 (xform/translate-mesh position))]
    (render-mesh projection m opts)))

(defn cone
  "Creates a 3D cone projected into 2D."
  [projection position radius height opts]
  (let [m (-> (mesh/cone-mesh radius height (get opts :segments 16))
              (xform/translate-mesh position))]
    (render-mesh projection m opts)))

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
      (u/make-face [(nth base i) (nth top i)
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
  (let [text-m   (text-mesh content font-spec depth opts)
        center   (u/mesh-center text-m)
        centered (xform/translate-mesh text-m (mapv - center))
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
