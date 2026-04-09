(ns eido.scene3d.modeling
  "Polygonal modeling operations: extrude, inset, bevel, detail, L-system, instancing."
  (:require
    [eido.ir.field :as field]
    [eido.gen.lsystem :as lsystem]
    [eido.gen.noise :as noise]
    [eido.math :as m]
    [eido.scene3d.mesh :as mesh]
    [eido.scene3d.transform :as xform]
    [eido.scene3d.util :as u]))

;; --- polygonal modeling ---

(defn extrude-faces
  "Extrudes selected faces along their normals.
  opts:
    :select/*       - face selector (see make-face-selector)
    :extrude/amount - distance to push faces
    :extrude/scale  - optional scale factor toward centroid (default 1.0)"
  [mesh opts]
  (let [sel    (u/make-face-selector opts)
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
                    cap   (u/make-face cap-verts (:face/style face))
                    ;; Side-wall quads connecting original edge to extruded edge
                    walls (for [i (range n)
                                :let [j  (mod (inc i) n)
                                      v0 (nth verts i)
                                      v1 (nth verts j)
                                      v2 (nth cap-verts j)
                                      v3 (nth cap-verts i)]]
                            (u/make-face [v0 v1 v2 v3] (:face/style face)))]
                (into [cap] walls))
              [face])))
        mesh))))

(defn inset-faces
  "Insets selected faces, creating a smaller inner face and border quads.
  opts:
    :select/*      - face selector
    :inset/amount  - how far to shrink inward (0-1 fraction of distance to centroid)"
  [mesh opts]
  (let [sel (u/make-face-selector opts)
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
                    inner-face (u/make-face inner-verts (:face/style face))
                    ;; Border quads between outer and inner edges
                    borders    (for [i (range n)
                                     :let [j  (mod (inc i) n)
                                           o0 (nth verts i)
                                           o1 (nth verts j)
                                           i0 (nth inner-verts i)
                                           i1 (nth inner-verts j)]]
                                 (u/make-face [o0 o1 i1 i0] (:face/style face)))]
                (into [inner-face] borders))
              [face])))
        mesh))))

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
        sel-opts  (if (:select/type opts) opts {:select/type :all})
        ;; First inset: creates inner faces + border quads
        inset-mesh (inset-faces mesh (merge sel-opts {:inset/amount inset-amt}))]
    ;; Then extrude the smaller inner faces (they have the same selection criteria
    ;; but are now smaller). We use :all since inset already created the structure.
    (extrude-faces inset-mesh (merge sel-opts {:extrude/amount depth}))))

(defn detail-faces
  "Adds procedural surface detail by insetting then noise-extruding faces.
  Creates mechanical/sci-fi panel detail. Composes inset + field-driven extrude.
  opts:
    :select/*            - face selector (defaults to :all)
    :detail/field       - noise field for per-face extrusion depth
    :detail/inset       - inset amount (default 0.1)
    :detail/depth-range - [min-depth max-depth] range for extrusion"
  [mesh opts]
  (let [inset-amt   (get opts :detail/inset 0.1)
        [d-min d-max] (get opts :detail/depth-range [0.02 0.15])
        detail-field (:detail/field opts)
        sel-opts    (if (:select/type opts) opts {:select/type :all})
        ;; Inset all selected faces
        inset-mesh  (inset-faces mesh (merge sel-opts {:inset/amount inset-amt}))]
    ;; Extrude each face by a noise-sampled depth
    (mapv (fn [face]
            (let [verts    (:face/vertices face)
                  centroid (m/face-centroid verts)
                  [cx _cy cz] centroid
                  ;; Sample noise to get extrusion depth for this face
                  noise-val (if detail-field
                              (* 0.5 (+ 1.0 (field/evaluate detail-field
                                              (double cx) (double cz))))
                              ;; Deterministic fallback: derive from centroid position
                              (* 0.5 (+ 1.0 (noise/perlin3d (+ (double cx) 0.37)
                                                             (double _cy)
                                                             (+ (double cz) 0.71)
                                                             nil))))
                  depth     (+ (double d-min) (* noise-val (- (double d-max) (double d-min))))
                  normal    (m/normalize (:face/normal face))
                  new-verts (mapv #(m/v+ % (m/v* normal depth)) verts)]
              (assoc face
                :face/vertices new-verts
                :face/normal (m/face-normal new-verts))))
          inset-mesh)))

;; --- 3D L-system ---

(defn- interpret-3d
  "Interprets an L-system string as a 3D turtle, returning branch paths.
  Symbols: F=draw, +=yaw right, -=yaw left, ^=pitch up, &=pitch down,
           \\=roll left, /=roll right, [=push, ]=pop.
  Returns a vector of branch paths, each a vector of [x y z] points."
  [expanded angle-deg length]
  (let [angle-rad (* (double angle-deg) (/ Math/PI 180.0))
        length    (double length)]
    (loop [chars     (seq expanded)
           pos       [0.0 0.0 0.0]
           ;; Direction as yaw/pitch (simplified — no full quaternion)
           yaw       0.0
           pitch     (/ Math/PI 2.0)     ;; start pointing up (+Y)
           stack     []
           branches  []
           current   [pos]
           depth     0]
      (if (empty? chars)
        (if (> (count current) 1)
          (conj branches current)
          branches)
        (let [c (first chars)
              r (rest chars)]
          (case c
            \F (let [;; Direction from yaw/pitch
                     dx (* length (Math/cos pitch) (Math/sin yaw))
                     dy (* length (Math/sin pitch))
                     dz (* length (Math/cos pitch) (Math/cos yaw))
                     new-pos (m/v+ pos [dx dy dz])]
                 (recur r new-pos yaw pitch stack branches
                        (conj current new-pos) depth))
            \+ (recur r pos (+ yaw angle-rad) pitch stack branches current depth)
            \- (recur r pos (- yaw angle-rad) pitch stack branches current depth)
            \^ (recur r pos yaw (+ pitch angle-rad) stack branches current depth)
            \& (recur r pos yaw (- pitch angle-rad) stack branches current depth)
            \\ (recur r pos yaw pitch stack branches current depth) ;; roll (no-op in simplified model)
            \/ (recur r pos yaw pitch stack branches current depth) ;; roll (no-op in simplified model)
            \[ (recur r pos yaw pitch
                      (conj stack [pos yaw pitch current depth])
                      branches [pos] (inc depth))
            \] (let [[spos syaw spitch scurrent sdepth] (peek stack)
                     branches (if (> (count current) 1)
                                (conj branches current)
                                branches)]
                 (recur r spos syaw spitch
                        (pop stack)
                        branches scurrent sdepth))
            ;; Unknown symbol — skip
            (recur r pos yaw pitch stack branches current depth)))))))

(defn lsystem-mesh
  "Generates a 3D mesh from an L-system by sweeping a profile along branches.
  Composes L-system string expansion + 3D turtle interpretation + sweep-mesh.
  opts:
    :axiom      - starting string
    :rules      - rewrite rules {\"F\" \"FF[+F][-F]\"}
    :iterations - number of rewriting iterations
    :angle      - turn angle in degrees
    :length     - distance per F step
    :profile    - 2D cross-section [[x y] ...] for sweep
    :segments   - sweep segments per branch (default 4)
    :taper      - profile scale factor per branch depth (default 1.0, < 1 = thinner)"
  [{:keys [axiom rules iterations angle length profile segments taper]}]
  (let [expanded (lsystem/expand-string axiom rules iterations)
        branches (interpret-3d expanded angle length)
        seg      (or segments 4)
        taper    (double (or taper 1.0))]
    (into []
      (mapcat
        (fn [branch]
          (when (>= (count branch) 2)
            (mesh/sweep-mesh {:profile profile
                              :path    branch
                              :segments seg})))
        branches))))

;; --- instancing ---

(defn instance-mesh
  "Places copies of a mesh at multiple positions.
  Bridges 2D scatter distributions to 3D mesh placement.
  opts:
    :positions - vector of [x y z] positions
    :jitter    - {:amount n :seed s} random positional jitter
    :rotate-y  - {:range [min max] :seed s} random Y-axis rotation per instance"
  [mesh opts]
  (let [positions (:positions opts)
        jitter    (:jitter opts)
        rot-y     (:rotate-y opts)
        rng       (when (or jitter rot-y)
                    (java.util.Random.
                      (long (or (:seed jitter) (:seed rot-y) 0))))]
    (into []
      (mapcat
        (fn [pos]
          (let [;; Optional jitter
                offset (if jitter
                         (let [a (double (:amount jitter))]
                           [(* a (- (* 2.0 (.nextDouble ^java.util.Random rng)) 1.0))
                            0.0
                            (* a (- (* 2.0 (.nextDouble ^java.util.Random rng)) 1.0))])
                         [0 0 0])
                final-pos (m/v+ pos offset)
                ;; Optional Y rotation
                angle (when rot-y
                        (let [[lo hi] (:range rot-y)]
                          (+ (double lo) (* (.nextDouble ^java.util.Random rng)
                                            (- (double hi) (double lo))))))
                placed (-> mesh
                           (cond-> angle (xform/rotate-mesh :y angle))
                           (xform/translate-mesh final-pos))]
            placed))
        positions))))
