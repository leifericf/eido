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
        :hex :color/hex))

;; --- transforms ---

(s/def :transform/translate
  (s/and vector? (s/cat :tag #{:transform/translate} :x number? :y number?)))

(s/def :transform/rotate
  (s/and vector? (s/cat :tag #{:transform/rotate} :rad number?)))

(s/def :transform/scale
  (s/and vector? (s/cat :tag #{:transform/scale} :sx number? :sy number?)))

(s/def ::transform
  (s/or :translate :transform/translate
        :rotate :transform/rotate
        :scale :transform/scale))

(s/def :node/transform (s/coll-of ::transform :kind vector?))

;; --- path commands ---

(s/def :cmd/move-to
  (s/and vector? (s/cat :tag #{:move-to} :pt ::point)))

(s/def :cmd/line-to
  (s/and vector? (s/cat :tag #{:line-to} :pt ::point)))

(s/def :cmd/curve-to
  (s/and vector? (s/cat :tag #{:curve-to} :cp1 ::point :cp2 ::point :pt ::point)))

(s/def :cmd/close
  (s/and vector? (s/cat :tag #{:close})))

(s/def ::path-command
  (s/or :move-to :cmd/move-to
        :line-to :cmd/line-to
        :curve-to :cmd/curve-to
        :close :cmd/close))

(s/def :path/commands (s/coll-of ::path-command :kind vector?))

;; --- styles ---

(s/def ::style-color ::color)
(s/def :style/fill (s/keys :req-un [::color]))
(s/def ::width ::pos-number)
(s/def :style/stroke (s/keys :req-un [::color ::width]))
(s/def :node/opacity ::unit-val)
