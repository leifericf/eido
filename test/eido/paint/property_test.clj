(ns eido.paint.property-test
  "Property-based tests for the paint engine.
  Uses test.check to verify invariants over many random inputs."
  (:require
    [clojure.test :refer [deftest is]]
    [clojure.test.check.clojure-test :refer [defspec]]
    [clojure.test.check.generators :as tc-gen]
    [clojure.test.check.properties :as prop]
    [eido.paint.tip :as tip]
    [eido.paint.grain :as grain]
    [eido.paint.substrate :as substrate]
    [eido.paint.stroke :as stroke]
    [eido.paint.surface :as surface]
    [eido.paint.kernel :as kernel]
    [eido.paint.blend :as blend]
    [eido.paint :as paint]
    [eido.spec]
    [clojure.spec.alpha :as s]))

;; --- spec conformance ---

(defspec all-presets-conform-to-spec 1
  (prop/for-all [_ (tc-gen/return nil)]
    (every? (fn [[k v]]
              (let [valid? (clojure.spec.alpha/valid? :eido.spec/brush-spec v)]
                (when-not valid?
                  (println "Preset" k "fails spec:"
                    (clojure.spec.alpha/explain-str :eido.spec/brush-spec v)))
                valid?))
            (deref (resolve 'eido.paint/presets)))))

;; --- generators ---

(def gen-unit
  "Value in [0, 1]."
  (tc-gen/double* {:min 0.0 :max 1.0 :NaN? false :infinite? false}))

(def gen-coord
  "Coordinate value."
  (tc-gen/double* {:min -200.0 :max 200.0 :NaN? false :infinite? false}))

(def gen-positive
  "Positive value > 0."
  (tc-gen/double* {:min 0.01 :max 100.0 :NaN? false :infinite? false}))

(def gen-angle
  "Angle in radians."
  (tc-gen/double* {:min (- Math/PI) :max Math/PI :NaN? false :infinite? false}))

;; --- tip properties ---

(defspec tip-coverage-in-unit-range 200
  (prop/for-all [px gen-coord
                 py gen-coord
                 hardness gen-unit
                 angle gen-angle]
    (let [v (tip/evaluate-tip {:tip/shape :ellipse :tip/hardness hardness :tip/aspect 1.0}
              px py angle)]
      (and (>= v 0.0)
           (<= v 1.0)
           (not (Double/isNaN v))
           (not (Double/isInfinite v))))))

(defspec tip-center-is-full-coverage 100
  (prop/for-all [hardness gen-unit
                 angle gen-angle]
    (let [v (tip/evaluate-tip {:tip/shape :ellipse :tip/hardness hardness}
              0.0 0.0 angle)]
      (= v 1.0))))

(defspec tip-outside-is-zero 100
  (prop/for-all [angle gen-angle]
    (let [v (tip/evaluate-tip {:tip/shape :circle :tip/hardness 0.7}
              2.0 0.0 angle)]
      (= v 0.0))))

;; --- grain properties ---

(defspec grain-in-unit-range 200
  (prop/for-all [x gen-coord
                 y gen-coord
                 grain-type (tc-gen/elements [:fbm :turbulence :ridge :fiber :weave :canvas])]
    (let [v (grain/evaluate-grain {:grain/type grain-type :grain/scale 0.1 :grain/contrast 0.5}
              x y)]
      (and (>= v 0.0)
           (<= v 1.0)
           (not (Double/isNaN v))
           (not (Double/isInfinite v))))))

;; --- substrate properties ---

(defspec substrate-tooth-in-unit-range 100
  (prop/for-all [x gen-coord
                 y gen-coord
                 tooth gen-unit]
    (let [v (substrate/evaluate-tooth {:substrate/tooth tooth :substrate/scale 0.1} x y)]
      (and (>= v 0.0)
           (<= v 1.0)
           (not (Double/isNaN v))
           (not (Double/isInfinite v))))))

;; --- stroke resampling properties ---

(defspec resample-produces-nonempty-for-valid-input 100
  (prop/for-all [x0 gen-coord y0 gen-coord
                 x1 gen-coord y1 gen-coord
                 spacing gen-positive]
    (let [;; Only test when points are actually separated
          dist (Math/sqrt (+ (* (- x1 x0) (- x1 x0)) (* (- y1 y0) (- y1 y0))))
          dabs (stroke/resample-stroke [[x0 y0] [x1 y1]] spacing
                  {:color {:r 100 :g 50 :b 25 :a 1.0} :radius 5.0})]
      (if (> dist 0.001)
        (and (some? dabs) (pos? (count dabs)))
        true)))) ;; zero-length strokes can return nil

(defspec resample-never-nan 100
  (prop/for-all [x0 gen-coord y0 gen-coord
                 x1 gen-coord y1 gen-coord
                 spacing gen-positive]
    (let [dabs (stroke/resample-stroke [[x0 y0] [x1 y1]] spacing
                {:color {:r 100 :g 50 :b 25 :a 1.0} :radius 5.0})]
      (every? (fn [d]
                (and (not (Double/isNaN (:dab/cx d)))
                     (not (Double/isNaN (:dab/cy d)))
                     (not (Double/isNaN (:dab/radius d)))
                     (not (Double/isNaN (:dab/opacity d)))))
              (or dabs [])))))

(defspec jittered-dabs-stay-bounded 100
  (prop/for-all [x0 gen-coord y0 gen-coord
                 x1 gen-coord y1 gen-coord
                 spacing gen-positive
                 pos-jitter gen-unit
                 opa-jitter gen-unit
                 size-jitter gen-unit]
    (let [dabs (stroke/resample-stroke [[x0 y0] [x1 y1]] spacing
                {:color {:r 100 :g 50 :b 25 :a 1.0} :radius 5.0 :opacity 0.5
                 :jitter {:jitter/position pos-jitter
                          :jitter/opacity opa-jitter
                          :jitter/size size-jitter}
                 :seed 42})]
      (every? (fn [d]
                (and (> (:dab/radius d) 0.0)
                     (> (:dab/opacity d) 0.0)
                     (<= (:dab/opacity d) 1.0)
                     (not (Double/isNaN (:dab/cx d)))
                     (not (Double/isNaN (:dab/cy d)))
                     (not (Double/isNaN (:dab/radius d)))
                     (not (Double/isNaN (:dab/opacity d)))))
              (or dabs [])))))

;; --- blend properties ---

(defspec blend-glazed-never-exceeds-max 100
  (prop/for-all [sr gen-unit sg gen-unit sb gen-unit sa gen-unit
                 dr gen-unit dg gen-unit db gen-unit da gen-unit]
    (let [[r g b a] (blend/blend :glazed [sr sg sb sa] [dr dg db da])]
      (and (<= r (+ (max sr dr) 0.001))
           (<= g (+ (max sg dg) 0.001))
           (<= b (+ (max sb db) 0.001))
           (<= a (+ (max sa da) 0.001))))))

(defspec blend-source-over-no-nan 100
  (prop/for-all [sr gen-unit sg gen-unit sb gen-unit sa gen-unit
                 dr gen-unit dg gen-unit db gen-unit da gen-unit]
    (let [[r g b a] (blend/blend :source-over [sr sg sb sa] [dr dg db da])]
      (and (not (Double/isNaN r)) (not (Double/isNaN g))
           (not (Double/isNaN b)) (not (Double/isNaN a))))))

;; --- dab rasterization properties ---

(defspec dab-never-crashes 50
  (prop/for-all [cx gen-coord cy gen-coord
                 radius gen-positive
                 hardness gen-unit
                 opacity gen-unit]
    (let [s (surface/create-surface 100 100)]
      (kernel/rasterize-dab! s {:dab/cx cx :dab/cy cy
                                :dab/radius radius
                                :dab/hardness hardness
                                :dab/opacity opacity
                                :dab/color {:r 128 :g 64 :b 32 :a 1.0}})
      true)))

(defspec dab-pixels-in-valid-range 50
  (prop/for-all [cx (tc-gen/double* {:min 10.0 :max 90.0 :NaN? false :infinite? false})
                 cy (tc-gen/double* {:min 10.0 :max 90.0 :NaN? false :infinite? false})
                 radius (tc-gen/double* {:min 1.0 :max 30.0 :NaN? false :infinite? false})]
    (let [s (surface/create-surface 100 100)]
      (kernel/rasterize-dab! s {:dab/cx cx :dab/cy cy
                                :dab/radius radius
                                :dab/hardness 0.7
                                :dab/opacity 0.8
                                :dab/color {:r 200 :g 100 :b 50 :a 1.0}})
      ;; Check center pixel is in valid premultiplied range
      (let [[r g b a] (surface/get-pixel s (long cx) (long cy))]
        (and (>= r 0.0) (<= r 1.0)
             (>= g 0.0) (<= g 1.0)
             (>= b 0.0) (<= b 1.0)
             (>= a 0.0) (<= a 1.0)
             ;; Premultiplied: RGB <= A
             (<= r (+ a 0.001))
             (<= g (+ a 0.001))
             (<= b (+ a 0.001)))))))
