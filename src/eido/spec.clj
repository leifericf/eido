(ns eido.spec
  (:require
    [clojure.spec.alpha :as s]))

;; --- primitives ---

(s/def ::rgb-val (s/and int? #(<= 0 % 255)))
(s/def ::unit-val (s/and number? #(<= 0 % 1)))
(s/def ::hue-val (s/and number? #(<= 0 % 360)))
(s/def ::pos-number (s/and number? pos?))
(s/def ::point (s/tuple number? number?))
(s/def ::pos-size (s/tuple ::pos-number ::pos-number))

;; --- colors ---

(s/def :color/rgb
  (s/and vector?
         (s/cat :tag #{:color/rgb} :r ::rgb-val :g ::rgb-val :b ::rgb-val)))

(s/def :color/rgba
  (s/and vector?
         (s/cat :tag #{:color/rgba} :r ::rgb-val :g ::rgb-val :b ::rgb-val :a ::unit-val)))

(s/def :color/hsl
  (s/and vector?
         (s/cat :tag #{:color/hsl} :h ::hue-val :s ::unit-val :l ::unit-val)))

(s/def :color/hsla
  (s/and vector?
         (s/cat :tag #{:color/hsla} :h ::hue-val :s ::unit-val :l ::unit-val :a ::unit-val)))

(s/def :color/hsb
  (s/and vector?
         (s/cat :tag #{:color/hsb} :h ::hue-val :s ::unit-val :b ::unit-val)))

(s/def :color/hsba
  (s/and vector?
         (s/cat :tag #{:color/hsba} :h ::hue-val :s ::unit-val :b ::unit-val :a ::unit-val)))

(s/def :color/hex
  (s/and vector?
         (s/cat :tag #{:color/hex}
                :hex (s/and string?
                            #(re-matches #"#?([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})" %)))))

(s/def :color/name
  (s/and vector?
         (s/cat :tag #{:color/name} :name string?)))

(s/def ::color
  (s/or :rgb :color/rgb
        :rgba :color/rgba
        :hsl :color/hsl
        :hsla :color/hsla
        :hsb :color/hsb
        :hsba :color/hsba
        :hex :color/hex
        :name :color/name))

;; --- transforms ---

(s/def :transform/translate
  (s/and vector? (s/cat :tag #{:transform/translate} :x number? :y number?)))

(s/def :transform/rotate
  (s/and vector? (s/cat :tag #{:transform/rotate} :rad number?)))

(s/def :transform/scale
  (s/and vector? (s/cat :tag #{:transform/scale} :sx number? :sy number?)))

(s/def :transform/shear-x
  (s/and vector? (s/cat :tag #{:transform/shear-x} :sx number?)))

(s/def :transform/shear-y
  (s/and vector? (s/cat :tag #{:transform/shear-y} :sy number?)))

(s/def :transform/distort
  (s/and vector? (s/cat :tag #{:transform/distort} :opts map?)))

(s/def ::transform
  (s/or :translate :transform/translate
        :rotate :transform/rotate
        :scale :transform/scale
        :shear-x :transform/shear-x
        :shear-y :transform/shear-y
        :distort :transform/distort))

(s/def :node/transform (s/coll-of ::transform :kind vector?))

;; --- path commands ---

(s/def :cmd/move-to
  (s/and vector? (s/cat :tag #{:move-to} :pt ::point)))

(s/def :cmd/line-to
  (s/and vector? (s/cat :tag #{:line-to} :pt ::point)))

(s/def :cmd/curve-to
  (s/and vector? (s/cat :tag #{:curve-to} :cp1 ::point :cp2 ::point :pt ::point)))

(s/def :cmd/quad-to
  (s/and vector? (s/cat :tag #{:quad-to} :cp ::point :pt ::point)))

(s/def :cmd/close
  (s/and vector? (s/cat :tag #{:close})))

(s/def ::path-command
  (s/or :move-to :cmd/move-to
        :line-to :cmd/line-to
        :curve-to :cmd/curve-to
        :quad-to :cmd/quad-to
        :close :cmd/close))

(s/def :path/commands (s/coll-of ::path-command :kind vector?))
(s/def :path/fill-rule #{:even-odd :non-zero})

;; --- gradients ---

(s/def :gradient/type #{:linear :radial})
(s/def :gradient/from ::point)
(s/def :gradient/to ::point)
(s/def :gradient/center ::point)
(s/def :gradient/radius ::pos-number)
(s/def ::stop-position (s/and number? #(<= 0 % 1)))
(s/def ::gradient-stop (s/tuple ::stop-position ::color))
(s/def :gradient/stops (s/and (s/coll-of ::gradient-stop :kind vector? :min-count 2)))

(defmulti gradient-type :gradient/type)

(defmethod gradient-type :linear [_]
  (s/keys :req [:gradient/type :gradient/from :gradient/to :gradient/stops]))

(defmethod gradient-type :radial [_]
  (s/keys :req [:gradient/type :gradient/center :gradient/radius :gradient/stops]))

(s/def ::gradient (s/multi-spec gradient-type :gradient/type))

;; --- styles ---

(s/def ::style-color ::color)
(s/def ::hatch-fill
  (s/and map? #(= :hatch (:fill/type %))))

(s/def ::stipple-fill
  (s/and map? #(= :stipple (:fill/type %))))

(s/def ::pattern-fill
  (s/and map? #(= :pattern (:fill/type %))))

(s/def :style/fill (s/or :color-vec ::color
                         :color-map (s/keys :req-un [::color])
                         :gradient ::gradient
                         :hatch ::hatch-fill
                         :stipple ::stipple-fill
                         :pattern ::pattern-fill))
(s/def ::width ::pos-number)
(s/def ::cap #{:butt :round :square})
(s/def ::join #{:miter :round :bevel})
(s/def ::dash (s/and vector? (s/coll-of pos? :min-count 1)))
(s/def :style/stroke (s/keys :req-un [::color ::width]
                              :opt-un [::cap ::join ::dash]))
(s/def :node/opacity ::unit-val)

;; --- fonts ---

(s/def :font/family string?)
(s/def :font/size ::pos-number)
(s/def :font/weight #{:normal :bold})
(s/def :font/style #{:normal :italic})
(s/def :font/file string?)
(s/def ::font-spec (s/keys :req [:font/family :font/size]
                           :opt [:font/weight :font/style :font/file]))

;; --- text ---

(s/def :text/content string?)
(s/def :text/font ::font-spec)
(s/def :text/origin ::point)
(s/def :text/spacing number?)
(s/def :text/align #{:left :center :right})
(s/def :text/offset number?)
(s/def :text/path :path/commands)

(s/def :glyph/index (s/and int? #(>= % 0)))
(s/def ::glyph-override
  (s/keys :req [:glyph/index]
          :opt [:style/fill :style/stroke :node/opacity :node/transform]))
(s/def :text/glyphs (s/coll-of ::glyph-override :kind vector?))

;; --- nodes ---

(s/def :rect/xy ::point)
(s/def :rect/size ::pos-size)
(s/def :rect/corner-radius (s/and number? #(>= % 0)))
(s/def :circle/center ::point)
(s/def :circle/radius ::pos-number)
(s/def :arc/center ::point)
(s/def :arc/rx ::pos-number)
(s/def :arc/ry ::pos-number)
(s/def :arc/start number?)
(s/def :arc/extent number?)
(s/def :arc/mode #{:open :chord :pie})
(s/def :line/from ::point)
(s/def :line/to ::point)
(s/def :ellipse/center ::point)
(s/def :ellipse/rx ::pos-number)
(s/def :ellipse/ry ::pos-number)
;; group/clip is validated in compile, not here, to avoid circular spec
;; (clip shape doesn't need to be a full recursive node validation)
(s/def :group/children (s/coll-of ::node :kind vector?))

(defmulti node-type :node/type)

(defmethod node-type :shape/rect [_]
  (s/keys :req [:node/type :rect/xy :rect/size]
          :opt [:rect/corner-radius :style/fill :style/stroke :node/opacity :node/transform
                :effect/shadow :effect/glow]))

(defmethod node-type :shape/circle [_]
  (s/keys :req [:node/type :circle/center :circle/radius]
          :opt [:style/fill :style/stroke :node/opacity :node/transform
                :effect/shadow :effect/glow]))

(defmethod node-type :shape/arc [_]
  (s/keys :req [:node/type :arc/center :arc/rx :arc/ry :arc/start :arc/extent]
          :opt [:arc/mode :style/fill :style/stroke :node/opacity :node/transform
                :effect/shadow :effect/glow]))

(defmethod node-type :shape/line [_]
  (s/keys :req [:node/type :line/from :line/to]
          :opt [:style/stroke :node/opacity :node/transform
                :effect/shadow :effect/glow]))

(defmethod node-type :shape/ellipse [_]
  (s/keys :req [:node/type :ellipse/center :ellipse/rx :ellipse/ry]
          :opt [:style/fill :style/stroke :node/opacity :node/transform
                :effect/shadow :effect/glow]))

(defmethod node-type :shape/path [_]
  (s/keys :req [:node/type :path/commands]
          :opt [:path/fill-rule :style/fill :style/stroke :node/opacity :node/transform
                :stroke/profile :effect/shadow :effect/glow]))

(defmethod node-type :group [_]
  (s/keys :req [:node/type :group/children]
          :opt [:style/fill :style/stroke :node/opacity :node/transform
                :group/composite :group/filter]))

(defmethod node-type :shape/text [_]
  (s/keys :req [:node/type :text/content :text/font :text/origin]
          :opt [:text/spacing :text/align
                :style/fill :style/stroke :node/opacity :node/transform
                :group/composite :group/filter]))

(defmethod node-type :shape/text-glyphs [_]
  (s/keys :req [:node/type :text/content :text/font :text/origin]
          :opt [:text/glyphs :text/spacing :text/align
                :style/fill :style/stroke :node/opacity :node/transform
                :group/composite :group/filter]))

(defmethod node-type :shape/text-on-path [_]
  (s/keys :req [:node/type :text/content :text/font :text/path]
          :opt [:text/offset :text/spacing
                :style/fill :style/stroke :node/opacity :node/transform
                :group/composite :group/filter]))

(defmethod node-type :lsystem [_]
  (s/keys :req [:node/type :lsystem/axiom :lsystem/rules]
          :opt [:lsystem/iterations :lsystem/angle :lsystem/length
                :lsystem/origin :lsystem/heading
                :style/fill :style/stroke :node/opacity :node/transform]))

(defmethod node-type :voronoi [_]
  (s/keys :req [:node/type :voronoi/points :voronoi/bounds]
          :opt [:style/fill :style/stroke :node/opacity :node/transform]))

(defmethod node-type :delaunay [_]
  (s/keys :req [:node/type :delaunay/points :delaunay/bounds]
          :opt [:style/stroke :node/opacity :node/transform]))

(defmethod node-type :contour [_]
  (s/keys :req [:node/type :contour/bounds]
          :opt [:contour/fn :contour/opts :style/stroke :node/opacity :node/transform]))

(defmethod node-type :flow-field [_]
  (s/keys :req [:node/type :flow/bounds]
          :opt [:flow/opts :style/fill :style/stroke :node/opacity :node/transform]))

(defmethod node-type :symmetry [_]
  (s/keys :req [:node/type :symmetry/type :group/children]
          :opt [:symmetry/n :symmetry/center :symmetry/axis
                :symmetry/cols :symmetry/rows :symmetry/spacing
                :node/opacity :node/transform]))

(defmethod node-type :scatter [_]
  (s/keys :req [:node/type :scatter/shape :scatter/positions]
          :opt [:scatter/jitter :node/opacity :node/transform]))

(defmethod node-type :path/decorated [_]
  (s/keys :req [:node/type :path/commands :decorator/shape :decorator/spacing]
          :opt [:decorator/rotate? :node/opacity :node/transform]))

(defmethod node-type :default [_]
  (s/with-gen
    (s/and (s/keys :req [:node/type])
           #(contains? #{:shape/rect :shape/circle :shape/ellipse :shape/arc
                         :shape/line :shape/path :group
                         :shape/text :shape/text-glyphs :shape/text-on-path
                         :lsystem :voronoi :delaunay :contour
                         :flow-field :symmetry :scatter :path/decorated}
                       (:node/type %)))
    #(s/gen #{:shape/rect})))

(s/def ::node (s/multi-spec node-type :node/type))

;; --- scene ---

(s/def :image/size ::pos-size)
(s/def :image/background ::color)
(s/def :image/nodes (s/coll-of ::node :kind vector?))

(s/def :eido/version (s/and string? #(re-matches #"\d+\.\d+" %)))

(s/def ::scene
  (s/keys :req [:image/size :image/background :image/nodes]
          :opt [:eido/version]))

(comment
  ;; Check if a scene conforms
  (s/valid? ::scene
    {:image/size [800 600]
     :image/background [:color/rgb 255 255 255]
     :image/nodes []})

  ;; Explain failures
  (s/explain ::scene
    {:image/size [800 600]
     :image/background [:color/rgb 255 255 255]
     :image/nodes [{:node/type :shape/rect}]})

  ;; Check a color
  (s/valid? ::color [:color/hsl 180 0.5 0.5])
  )
