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
