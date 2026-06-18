(ns eido.phane.translate
  "Translate an Eido scene into Phane's frozen authoring grammar.

  Eido authoring (eido.scene, eido.gen.*, examples) produces Eido-grammar
  data; the native Phane backend renders Phane grammar. This namespace is the
  pure mapping between them, so `:renderer :phane` renders an ordinary Eido
  scene. The rules mirror the gallery parity harness: colours resolve to 0..1
  `[:color/rgba]`, path verbs shorten (`[:move-to [x y]]` to `[:move x y]`),
  semantic fills and gradients become tagged vectors, generators lower to
  `:item/generator`, symmetry and decorated paths take their Phane node types,
  group compositing/filter/clip move to node level, and Eido's radian rotations
  become Phane degrees. Paint surfaces become a `:paint/render` program.

  `translate` returns {:kind :canvas :scene …} or {:kind :paint :program …}."
  (:require
    [clojure.walk :as walk]
    [eido.color :as ecolor]))

(def ^:dynamic *font*
  "Font asset path Phane text nodes resolve against. Text scenes need this
  asset available at the render base directory."
  "examples/assets/Lato-Regular.ttf")

;; ---- colours ---------------------------------------------------------------

(defn- norm-color [c]
  (try
    (let [{:keys [r g b a]} (ecolor/resolve-color c)]
      [:color/rgba (/ r 255.0) (/ g 255.0) (/ b 255.0) (double (or a 1.0))])
    (catch Throwable _ c)))

(defn- color-vec? [x]
  (and (vector? x) (keyword? (first x))
       (= "color" (namespace (first x)))))

(defn- color-kw? [x]
  ;; a bare keyword resolve-color recognizes (named CSS colour)
  (and (keyword? x) (nil? (namespace x))
       (try (ecolor/resolve-color x) true (catch Throwable _ false))))

(defn- norm-fill
  "Normalize a fill/colour at node level. Postwalk runs bottom-up, so a colour
  VECTOR is already a 0..1 :color/rgba; only bare named-colour keywords remain."
  [v]
  (if (color-kw? v) (norm-color v) v))

;; ---- transforms ------------------------------------------------------------

(defn- affine-op
  "Translate an Eido [:transform/verb …] op to a Phane op vector, or nil to
  drop it. Eido rotate is radians; Phane rotate is degrees."
  [op]
  (when (and (vector? op) (keyword? (first op))
             (= "transform" (namespace (first op))))
    (let [verb (keyword (name (first op)))
          args (rest op)]
      (case verb
        :translate (when (= 2 (count args)) (into [:translate] args))
        :scale (cond (= 2 (count args)) (into [:scale] args)
                     (= 1 (count args)) [:scale (first args) (first args)])
        :rotate (when (>= (count args) 1)
                  [:rotate (* (double (first args)) (/ 180.0 Math/PI))])
        nil))))

(defn- norm-transform [t]
  (when (sequential? t)
    (let [ops (keep affine-op t)]
      (when (seq ops) (vec ops)))))

(defn- merge-transform [node extra-ops]
  (let [existing (or (norm-transform (:node/transform node)) [])
        ops (vec (concat extra-ops existing))]
    (if (seq ops) (assoc node :node/transform ops) (dissoc node :node/transform))))

;; ---- generators ------------------------------------------------------------

(defn- gen-node [base typ bounds opts]
  (merge (dissoc base :node/type)
         {:node/type :item/generator :gen/type typ :gen/bounds bounds}
         (into {} (for [[k v] opts] [(keyword "gen" (name k)) v]))))

(defn- ->gen
  "Map an Eido generator node to a Phane :item/generator (or a group, for
  scatter). bounds is the fallback scene bounds [x y w h]."
  [x bounds]
  (case (:node/type x)
    :contour    (gen-node x :contour (or (:contour/bounds x) bounds) (:contour/opts x))
    :flow-field (gen-node x :flow (or (:flow/bounds x) bounds) (:flow/opts x))
    :voronoi    (gen-node x :voronoi (or (:voronoi/bounds x) bounds) (:voronoi/opts x))
    :delaunay   (gen-node x :delaunay (or (:delaunay/bounds x) bounds) (:delaunay/opts x))
    :lsystem    (merge (dissoc x :node/type
                               :lsystem/axiom :lsystem/rules :lsystem/angle
                               :lsystem/iterations :lsystem/length :lsystem/heading
                               :lsystem/origin)
                       {:node/type :item/generator
                        :gen/type :lsystem
                        :gen/bounds (or (:lsystem/bounds x) bounds)
                        :gen/axiom (:lsystem/axiom x)
                        :gen/rules (:lsystem/rules x)
                        :gen/angle (:lsystem/angle x)
                        :gen/iterations (:lsystem/iterations x)
                        :gen/length (:lsystem/length x)
                        :gen/heading (:lsystem/heading x)})
    :scatter    (let [shape (:scatter/shape x)
                      overrides (:scatter/overrides x)
                      base-fill (:style/fill shape)]
                  {:node/type :group
                   :group/children
                   (vec (for [[i [px py]] (map-indexed vector (:scatter/positions x))
                              :let [ov (when overrides (nth overrides i nil))
                                    fill (or (:style/fill ov) base-fill
                                             [:color/rgba 0.1 0.1 0.1 1.0])]]
                          (-> shape
                              (assoc :style/fill fill)
                              (cond-> (:node/opacity ov) (assoc :node/opacity (:node/opacity ov)))
                              (merge-transform [[:translate px py]]))))})
    x))

(defn- gen-node? [x]
  (and (map? x)
       (#{:contour :flow-field :voronoi :delaunay :lsystem :scatter} (:node/type x))))

;; ---- group-level compositing/filter ----------------------------------------

(def ^:private composite-map
  {:multiply :multiply :screen :screen :darken :darken :lighten :lighten
   :overlay :overlay :add :add :normal :normal :source-over :normal})

(defn- ->effect
  "Translate an Eido :group/filter tagged-vector into a Phane :node/effects
  entry. Eido filters are positional. Returns nil to drop unknowns."
  [flt]
  (when (and (vector? flt) (keyword? (first flt)))
    (let [[tag & args] flt]
      (case tag
        :halftone [:effect/halftone {:dot-size (or (nth args 0 nil) 6)
                                     :angle (or (nth args 1 nil) 0)}]
        :duotone [:effect/duotone {:color-a (nth args 0 [:color/rgba 0 0 0 1])
                                   :color-b (nth args 1 [:color/rgba 1 1 1 1])}]
        :posterize [:effect/posterize {:levels (or (nth args 0 nil) 4)}]
        :blur [:effect/blur {:radius (or (nth args 0 nil) 5)}]
        :grain [:effect/grain {:amount (or (nth args 0 nil) 30)}]
        nil))))

;; ---- arcs ------------------------------------------------------------------

(defn- r3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn- arc->path
  "Sample an elliptical arc into Phane path commands. Phane has no arc
  primitive, so an Eido :shape/arc lowers to a :shape/path. Matches Java2D
  Arc2D: angles in degrees, the point at angle a is
  (cx + rx·cos a, cy − ry·sin a). :open leaves the arc unclosed (a fill
  chords it, as Java2D does), :chord closes it, :pie lines to the centre
  and closes."
  [cx cy rx ry start extent mode]
  (let [n (max 8 (int (/ (Math/abs (double extent)) 3.0)))
        pt (fn [i] (let [a (Math/toRadians (+ start (* extent (/ (double i) n))))]
                     [(r3 (+ cx (* rx (Math/cos a))))
                      (r3 (- cy (* ry (Math/sin a))))]))
        [x0 y0] (pt 0)
        cmds (into [[:move x0 y0]]
                   (for [i (range 1 (inc n))] (into [:line] (pt i))))]
    (case mode
      :pie   (conj cmds [:line (r3 cx) (r3 cy)] [:close])
      :chord (conj cmds [:close])
      cmds)))

;; ---- core postwalk translation ---------------------------------------------

(defn xlate
  "The bottom-up rewrite of one Eido value into Phane grammar. Used as a
  `clojure.walk/postwalk` step, so children are already translated."
  [x]
  (cond
    (ratio? x) (double x)
    (instance? clojure.lang.BigInt x) (long x)
    (instance? java.math.BigInteger x) (long x)
    (instance? java.math.BigDecimal x) (double x)
    ;; round floats to 3 decimals to keep huge explicit-geometry scenes small
    (and (double? x) (not (Double/isNaN x)) (not (Double/isInfinite x)))
    (/ (Math/round (* x 1000.0)) 1000.0)

    (color-vec? x) (norm-color x)

    (and (vector? x) (= :move-to (first x)))  (into [:move] (second x))
    (and (vector? x) (= :line-to (first x)))  (into [:line] (second x))
    (and (vector? x) (= :quad-to (first x)))  (into [:quad] (concat (second x) (nth x 2)))
    (and (vector? x) (= :curve-to (first x))) (into [:curve] (concat (second x) (nth x 2) (nth x 3)))

    (and (map? x) (:gradient/type x))
    (case (:gradient/type x)
      :radial [:gradient/radial {:center (:gradient/center x) :radius (:gradient/radius x) :stops (:gradient/stops x)}]
      :linear [:gradient/linear {:from (:gradient/from x) :to (:gradient/to x) :stops (:gradient/stops x)}]
      x)

    ;; Eido semantic fill maps {:fill/type …}
    (and (map? x) (:fill/type x))
    (case (:fill/type x)
      :hatch (let [l (first (:hatch/layers x))]
               [:fill/hatch {:color (or (:hatch/color x) [:color/rgba 0 0 0 1])
                             :angle (or (:hatch/angle x) (:angle l) 0)
                             :spacing (or (:hatch/spacing x) (:spacing l) 6)
                             :width (or (:hatch/stroke-width x) 1)
                             :cross (boolean (and (:hatch/layers x) (> (count (:hatch/layers x)) 1)))}])
      :stipple [:fill/stipple {:color (or (:stipple/color x) [:color/rgba 0 0 0 1])
                               :radius (or (:stipple/radius x) 2)
                               :seed (or (:stipple/seed x) 0)}]
      :pattern [:fill/pattern {:size (:pattern/size x) :nodes (:pattern/nodes x)}]
      x)

    ;; Eido stroke {:color C :width W :dash D} -> Phane {:stroke/paint …}
    (and (map? x) (contains? x :width) (or (contains? x :color) (contains? x :gray)))
    (let [paint (cond
                  (contains? x :color) (norm-fill (:color x))
                  :else (let [g (:gray x) gg (if (> g 1) (/ g 255.0) g)] [:color/rgba gg gg gg 1.0]))]
      (cond-> {:stroke/paint paint :stroke/width (:width x)}
        (:dash x) (assoc :stroke/dash (:dash x))))

    (and (map? x) (= :path/decorated (:node/type x)))
    {:node/type :item/decorate
     :path/commands (:path/commands x)
     :decorate/node (:decorator/shape x)
     :decorate/spacing (:decorator/spacing x)
     :decorate/offset (or (:decorator/offset x) 0)
     :decorate/rotate (boolean (:decorator/rotate? x))}

    (and (map? x) (= :symmetry (:node/type x)))
    (let [kind (:symmetry/type x)]
      (case kind
        :grid (let [[ox oy] (or (:symmetry/origin x) [0 0])]
                (cond-> {:node/type :item/symmetry :symmetry/type :grid
                         :symmetry/cols (:symmetry/cols x) :symmetry/rows (:symmetry/rows x)
                         :symmetry/spacing (:symmetry/spacing x)
                         :group/children (:group/children x)}
                  (or (not= 0 ox) (not= 0 oy)) (assoc :node/transform [[:translate ox oy]])))
        :radial {:node/type :item/symmetry :symmetry/type :radial
                 :symmetry/count (or (:symmetry/n x) (:symmetry/count x))
                 :symmetry/center (or (:symmetry/center x) [0 0])
                 :group/children (:group/children x)}
        (:mirror :bilateral)
        (let [[cx cy] (or (:symmetry/center x) [0 0])
              horiz (= :horizontal (:symmetry/axis x))]
          {:node/type :item/symmetry :symmetry/type :bilateral
           :symmetry/axis (if horiz :horizontal :vertical)
           :symmetry/at (if horiz cy cx)
           :group/children (:group/children x)})
        x))

    (and (map? x) (= :shape/text-on-path (:node/type x)))
    {:node/type :shape/text
     :text/string (:text/content x)
     :text/font *font*
     :text/size (or (get-in x [:text/font :font/size]) 16)
     :text/path (:text/path x)
     :style/fill (or (norm-fill (:style/fill x)) [:color/rgba 0.1 0.1 0.1 1.0])}

    (and (map? x) (= :shape/text-glyphs (:node/type x)))
    (let [g0 (first (:text/glyphs x))]
      {:node/type :shape/text
       :text/string (:text/content x)
       :text/font *font*
       :text/size (or (get-in x [:text/font :font/size]) 16)
       :text/origin (:text/origin x)
       :text/vary {:vary/target :hue :vary/expr [:* :i 320]}
       :style/fill (or (norm-fill (:style/fill g0)) [:color/rgba 0.8 0.2 0.2 1.0])})

    (and (map? x) (= :shape/text (:node/type x)) (contains? x :text/content))
    (cond-> {:node/type :shape/text
             :text/string (:text/content x)
             :text/font *font*
             :text/size (or (get-in x [:text/font :font/size])
                            (when (number? (:text/font x)) (:text/font x)) 16)
             :style/fill (or (norm-fill (:style/fill x)) [:color/rgba 0.1 0.1 0.1 1.0])}
      (:text/origin x) (assoc :text/origin (:text/origin x))
      (:text/path x) (assoc :text/path (:text/path x)))

    (and (map? x) (= :shape/ellipse (:node/type x)) (contains? x :ellipse/rx))
    (-> x (assoc :ellipse/radii [(:ellipse/rx x) (:ellipse/ry x)])
        (dissoc :ellipse/rx :ellipse/ry))

    (and (map? x) (= :shape/arc (:node/type x)) (contains? x :arc/center))
    (let [[cx cy] (:arc/center x)]
      (-> x
          (dissoc :arc/center :arc/rx :arc/ry :arc/start :arc/extent :arc/mode)
          (assoc :node/type :shape/path
                 :path/commands (arc->path cx cy (:arc/rx x) (:arc/ry x)
                                           (:arc/start x) (:arc/extent x)
                                           (get x :arc/mode :open)))))

    (map? x)
    (cond-> x
      (contains? x :rect/xy) (-> (assoc :rect/origin (:rect/xy x)) (dissoc :rect/xy))
      (color-kw? (:style/fill x)) (update :style/fill norm-color)
      (color-kw? (:image/background x)) (update :image/background norm-color)
      (contains? x :group/clip)
      (-> (assoc :node/clip (:group/clip x)) (dissoc :group/clip))
      (contains? x :group/composite)
      (-> (assoc :node/blend (get composite-map (:group/composite x) :normal))
          (dissoc :group/composite))
      (contains? x :group/filter)
      (as-> m (let [e (->effect (:group/filter m))]
                (cond-> (dissoc m :group/filter)
                  e (assoc :node/effects [e]))))
      (contains? x :node/transform)
      (as-> m (let [t (norm-transform (:node/transform m))]
                (if t (assoc m :node/transform t) (dissoc m :node/transform)))))

    :else x))

(defn- lower-generators
  "A second pass lowering generator nodes once scene bounds are known."
  [scene bounds]
  (walk/postwalk (fn [x] (if (gen-node? x) (->gen x bounds) x)) scene))

;; ---- paint program translation ---------------------------------------------

(def ^:private tip-map
  {:round :round :ellipse :ellipse :chisel :chisel :rect :rect
   :line :line :square :rect :circle :round})

(def ^:private blend-map
  {:source-over :normal :normal :normal :multiply :multiply
   :screen :screen :overlay :overlay :darken :darken
   :lighten :lighten :add :add})

(defn- brush-preset [kw]
  (case kw
    :pencil {:brush/tip :round :brush/hardness 0.7 :brush/flow 0.7 :brush/radius 3}
    :ink {:brush/tip :round :brush/hardness 0.9 :brush/flow 0.95 :brush/radius 4}
    :marker {:brush/tip :chisel :brush/hardness 0.8 :brush/flow 0.9 :brush/radius 8}
    :watercolor {:brush/tip :round :brush/hardness 0.2 :brush/flow 0.5 :brush/radius 30}
    :oil {:brush/tip :ellipse :brush/hardness 0.6 :brush/flow 0.8 :brush/radius 12}
    :chalk {:brush/tip :round :brush/hardness 0.5 :brush/flow 0.7 :brush/radius 8 :brush/grain 0.7}
    {:brush/tip :round :brush/hardness 0.5 :brush/flow 0.7 :brush/radius 8}))

(defn- paint-brush [eido-brush color radius seed]
  (let [base (if (keyword? eido-brush)
               (brush-preset eido-brush)
               (let [tip (get-in eido-brush [:brush/tip :tip/shape])
                     hard (get-in eido-brush [:brush/tip :tip/hardness])
                     flow (get-in eido-brush [:brush/paint :paint/flow])
                     blend (get-in eido-brush [:brush/paint :paint/blend])]
                 (cond-> {}
                   tip (assoc :brush/tip (get tip-map tip :round))
                   hard (assoc :brush/hardness hard)
                   flow (assoc :brush/flow flow)
                   blend (assoc :brush/blend (get blend-map blend :normal)))))]
    (cond-> (merge {:brush/tip :round :brush/hardness 0.5 :brush/flow 0.7 :brush/radius 6} base)
      color (assoc :brush/color (norm-fill color))
      radius (assoc :brush/radius radius)
      (and seed (>= seed 0)) (assoc :brush/seed seed))))

(defn- cmd->point [c]
  (case (first c)
    :move [(nth c 1) (nth c 2)]
    :line [(nth c 1) (nth c 2)]
    :quad [(nth c 3) (nth c 4)]
    :curve [(nth c 5) (nth c 6)]
    nil))

(defn- ->paint-program [scene]
  (let [[w h] (:paint/size scene)]
    {:paint/size [w h]
     :paint/background (or (norm-fill (:paint/bg scene)) [:color/rgba 1.0 1.0 1.0 1.0])
     :paint/strokes
     (vec (for [s (:paint/strokes scene)]
            {:stroke/points (vec (for [p (:paint/points s)] [(nth p 0) (nth p 1)]))
             :stroke/brush (paint-brush (:paint/brush s) (:paint/color s)
                                        (:paint/radius s) (:paint/seed s))}))}))

(defn- brush-paths->program
  "A group of brush-painted :shape/path nodes becomes a paint program: each
  path's commands give the stroke points."
  [size bg children]
  (let [[w h] size
        bgc (cond (color-kw? bg) (norm-color bg)
                  (color-vec? bg) (norm-color bg)
                  :else bg)]
    {:paint/size [w h]
     :paint/background (or bgc [:color/rgba 1.0 1.0 1.0 1.0])
     :paint/strokes
     (vec (for [c children :when (:path/commands c)]
            {:stroke/points (vec (keep cmd->point (:path/commands c)))
             :stroke/brush (paint-brush (:paint/brush c) (:paint/color c)
                                        (:paint/radius c) (:paint/seed c))}))}))

(defn- pure-paint-node
  "If a scene is a single :paint/surface node, return that node; else nil."
  [scene]
  (let [nodes (:image/nodes scene)]
    (cond
      (= :paint/surface (:node/type scene)) scene
      (and (= 1 (count nodes)) (= :paint/surface (:node/type (first nodes))))
      (assoc (first nodes) :paint/bg (:image/background scene))
      :else nil)))

(defn- brush-path-group
  "If the scene is a single group carrying :paint/surface whose children are
  brush-painted paths, return [size bg children]; else nil."
  [scene]
  (let [nodes (:image/nodes scene)
        g (first nodes)]
    (when (and (= 1 (count nodes)) (= :group (:node/type g))
               (:paint/surface g)
               (every? #(and (:paint/brush %) (:path/commands %)
                             (not (:style/fill %)) (not (:style/stroke %)))
                       (:group/children g)))
      [(or (get-in g [:paint/surface :paint/size]) (:image/size scene))
       (:image/background scene)
       (:group/children g)])))

;; ---- scene selection and entry ---------------------------------------------

(defn still-of
  "Collapse an animated or wrapped example result to a single still scene."
  [r]
  (cond
    (and (map? r) (:frames r)) (first (:frames r))
    (and (map? r) (:scene r)) (:scene r)
    (map? r) r
    (sequential? r) (first r)
    :else r))

(defn- scene-bounds [scene]
  (let [[w h] (or (:image/size scene) (:paint/size scene) [512 512])]
    [0 0 w h]))

(defn translate
  "Translate an Eido scene (or example result) into Phane grammar. Returns
  {:kind :canvas :scene phane-scene} or {:kind :paint :program paint-program}."
  [eido-scene]
  (let [raw    (still-of eido-scene)
        paint  (pure-paint-node raw)
        brushg (brush-path-group raw)]
    (cond
      paint  {:kind :paint :program (->paint-program (walk/postwalk xlate paint))}
      brushg (let [[size bg children] brushg]
               {:kind :paint
                :program (brush-paths->program size bg (walk/postwalk xlate children))})
      :else  (let [bounds     (scene-bounds raw)
                   translated (walk/postwalk xlate raw)]
               {:kind :canvas :scene (lower-generators translated bounds)}))))
