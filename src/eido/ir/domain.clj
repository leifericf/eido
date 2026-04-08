(ns eido.ir.domain
  "Domain descriptors — the space over which something is evaluated.

  A domain describes what coordinate system and bindings are available
  when evaluating a program, field, or generator.

  Domain kinds:
    :image-grid   — pixel grid with :uv [0..1, 0..1], :px, :py, :size
    :shape-local  — shape-local UV space with :uv [0..1, 0..1]
    :world-2d     — world coordinates with :pos [x y]
    :path-param   — path parameter with :t [0..1], :pos [x y]
    :mesh-faces   — per-face with :normal, :centroid
    :mesh-vertices — per-vertex with :pos [x y z]
    :points       — point set with :pos [x y], :index
    :particles    — particle collection with :pos, :vel, :age, :life
    :timeline     — animation time with :t [0..1], :frame, :fps")

;; --- domain constructors ---

(defn image-grid
  "Domain over a pixel grid."
  [size]
  {:domain/kind :image-grid
   :domain/size size})

(defn shape-local
  "Domain over a shape's local UV space."
  []
  {:domain/kind :shape-local})

(defn world-2d
  "Domain over 2D world coordinates within bounds."
  [bounds]
  {:domain/kind  :world-2d
   :domain/bounds bounds})

(defn path-param
  "Domain over a path's parameter space [0..1]."
  [path-commands]
  {:domain/kind     :path-param
   :domain/commands path-commands})

(defn mesh-faces
  "Domain over mesh faces."
  [mesh]
  {:domain/kind :mesh-faces
   :domain/mesh mesh})

(defn points
  "Domain over a point set."
  [point-set]
  {:domain/kind   :points
   :domain/points point-set})

(defn particles
  "Domain over a particle collection."
  [source]
  {:domain/kind   :particles
   :domain/source source})

(defn timeline
  "Domain over animation time."
  [& {:keys [fps frames]}]
  (cond-> {:domain/kind :timeline}
    fps    (assoc :domain/fps fps)
    frames (assoc :domain/frames frames)))

;; --- domain bindings ---

(defn bindings-for
  "Returns the set of binding keywords available for a domain kind.
  Useful for documentation and validation."
  [domain-kind]
  (case domain-kind
    :image-grid   #{:uv :px :py :size}
    :shape-local  #{:uv}
    :world-2d     #{:pos :x :y}
    :path-param   #{:t :pos}
    :mesh-faces   #{:normal :centroid :face-index}
    :mesh-vertices #{:pos :vertex-index}
    :points       #{:pos :index}
    :particles    #{:pos :vel :age :life :index}
    :timeline     #{:t :frame :fps}
    #{}))
