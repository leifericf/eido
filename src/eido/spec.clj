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

(s/def ::color
  (s/or :rgb :color/rgb
        :rgba :color/rgba
        :hsl :color/hsl
        :hsla :color/hsla
        :hsb :color/hsb
        :hsba :color/hsba
        :hex :color/hex))

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

(s/def ::transform
  (s/or :translate :transform/translate
        :rotate :transform/rotate
        :scale :transform/scale
        :shear-x :transform/shear-x
        :shear-y :transform/shear-y))

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

;; --- styles ---

(s/def ::style-color ::color)
(s/def :style/fill (s/or :color-vec ::color
                         :color-map (s/keys :req-un [::color])))
(s/def ::width ::pos-number)
(s/def ::cap #{:butt :round :square})
(s/def ::join #{:miter :round :bevel})
(s/def ::dash (s/and vector? (s/coll-of pos? :min-count 1)))
(s/def :style/stroke (s/keys :req-un [::color ::width]
                              :opt-un [::cap ::join ::dash]))
(s/def :node/opacity ::unit-val)

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
          :opt [:rect/corner-radius :style/fill :style/stroke :node/opacity :node/transform]))

(defmethod node-type :shape/circle [_]
  (s/keys :req [:node/type :circle/center :circle/radius]
          :opt [:style/fill :style/stroke :node/opacity :node/transform]))

(defmethod node-type :shape/arc [_]
  (s/keys :req [:node/type :arc/center :arc/rx :arc/ry :arc/start :arc/extent]
          :opt [:arc/mode :style/fill :style/stroke :node/opacity :node/transform]))

(defmethod node-type :shape/line [_]
  (s/keys :req [:node/type :line/from :line/to]
          :opt [:style/stroke :node/opacity :node/transform]))

(defmethod node-type :shape/ellipse [_]
  (s/keys :req [:node/type :ellipse/center :ellipse/rx :ellipse/ry]
          :opt [:style/fill :style/stroke :node/opacity :node/transform]))

(defmethod node-type :shape/path [_]
  (s/keys :req [:node/type :path/commands]
          :opt [:path/fill-rule :style/fill :style/stroke :node/opacity :node/transform]))

(defmethod node-type :group [_]
  (s/keys :req [:node/type :group/children]
          :opt [:style/fill :style/stroke :node/opacity :node/transform]))

(defmethod node-type :default [_]
  (s/with-gen
    (s/and (s/keys :req [:node/type])
           #(contains? #{:shape/rect :shape/circle :shape/ellipse :shape/arc :shape/line :shape/path :group}
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
