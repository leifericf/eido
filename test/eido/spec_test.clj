(ns eido.spec-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest is testing]]
    [eido.spec]))

;; --- primitive specs ---

(deftest rgb-val-test
  (testing "accepts valid RGB values"
    (is (s/valid? :eido.spec/rgb-val 0))
    (is (s/valid? :eido.spec/rgb-val 127))
    (is (s/valid? :eido.spec/rgb-val 255)))
  (testing "rejects out-of-range values"
    (is (not (s/valid? :eido.spec/rgb-val -1)))
    (is (not (s/valid? :eido.spec/rgb-val 256)))
    (is (not (s/valid? :eido.spec/rgb-val 1.5)))
    (is (not (s/valid? :eido.spec/rgb-val "red")))))

(deftest unit-val-test
  (testing "accepts 0-1 range"
    (is (s/valid? :eido.spec/unit-val 0))
    (is (s/valid? :eido.spec/unit-val 0.5))
    (is (s/valid? :eido.spec/unit-val 1.0)))
  (testing "rejects outside range"
    (is (not (s/valid? :eido.spec/unit-val -0.1)))
    (is (not (s/valid? :eido.spec/unit-val 1.1)))))

(deftest hue-val-test
  (testing "accepts 0-360"
    (is (s/valid? :eido.spec/hue-val 0))
    (is (s/valid? :eido.spec/hue-val 180))
    (is (s/valid? :eido.spec/hue-val 360)))
  (testing "rejects outside range"
    (is (not (s/valid? :eido.spec/hue-val -1)))
    (is (not (s/valid? :eido.spec/hue-val 361)))))

(deftest point-test
  (testing "accepts [x y] vector"
    (is (s/valid? :eido.spec/point [10 20]))
    (is (s/valid? :eido.spec/point [0.5 -3.7])))
  (testing "rejects wrong shape"
    (is (not (s/valid? :eido.spec/point [1])))
    (is (not (s/valid? :eido.spec/point [1 2 3])))
    (is (not (s/valid? :eido.spec/point "10,20")))))

(deftest pos-size-test
  (testing "accepts positive dimensions"
    (is (s/valid? :eido.spec/pos-size [100 200]))
    (is (s/valid? :eido.spec/pos-size [0.5 0.5])))
  (testing "rejects zero or negative"
    (is (not (s/valid? :eido.spec/pos-size [0 100])))
    (is (not (s/valid? :eido.spec/pos-size [-1 100])))))

;; --- color specs ---

(deftest color-rgb-test
  (testing "accepts valid rgb"
    (is (s/valid? :eido.spec/color [:color/rgb 255 0 0]))
    (is (s/valid? :eido.spec/color [:color/rgb 0 128 255])))
  (testing "rejects out-of-range"
    (is (not (s/valid? :eido.spec/color [:color/rgb 256 0 0])))
    (is (not (s/valid? :eido.spec/color [:color/rgb -1 0 0])))))

(deftest color-rgba-test
  (testing "accepts valid rgba"
    (is (s/valid? :eido.spec/color [:color/rgba 255 0 0 0.5])))
  (testing "rejects bad alpha"
    (is (not (s/valid? :eido.spec/color [:color/rgba 255 0 0 1.5])))))

(deftest color-hsl-test
  (testing "accepts valid hsl"
    (is (s/valid? :eido.spec/color [:color/hsl 0 1.0 0.5]))
    (is (s/valid? :eido.spec/color [:color/hsl 360 0 0])))
  (testing "rejects bad values"
    (is (not (s/valid? :eido.spec/color [:color/hsl 400 0 0])))))

(deftest color-hsla-test
  (testing "accepts valid hsla"
    (is (s/valid? :eido.spec/color [:color/hsla 120 0.8 0.5 0.7]))))

(deftest color-hex-test
  (testing "accepts valid hex strings"
    (is (s/valid? :eido.spec/color [:color/hex "#FF0000"]))
    (is (s/valid? :eido.spec/color [:color/hex "#F00"]))
    (is (s/valid? :eido.spec/color [:color/hex "#FF000080"]))
    (is (s/valid? :eido.spec/color [:color/hex "#F008"])))
  (testing "rejects invalid hex"
    (is (not (s/valid? :eido.spec/color [:color/hex "#GG0000"])))
    (is (not (s/valid? :eido.spec/color [:color/hex "#12345"])))))

(deftest color-dispatch-test
  (testing "unknown color tag is invalid"
    (is (not (s/valid? :eido.spec/color [:color/cmyk 0 0 0 0])))))

;; --- transform specs ---

(deftest transform-translate-test
  (testing "accepts valid translate"
    (is (s/valid? :eido.spec/transform [:transform/translate 10 20])))
  (testing "rejects wrong arity"
    (is (not (s/valid? :eido.spec/transform [:transform/translate 10])))))

(deftest transform-rotate-test
  (testing "accepts valid rotate"
    (is (s/valid? :eido.spec/transform [:transform/rotate 3.14])))
  (testing "rejects non-numeric"
    (is (not (s/valid? :eido.spec/transform [:transform/rotate "90deg"])))))

(deftest transform-scale-test
  (testing "accepts valid scale"
    (is (s/valid? :eido.spec/transform [:transform/scale 2 3]))))

(deftest transforms-collection-test
  (testing "accepts vector of transforms"
    (is (s/valid? :node/transform [[:transform/translate 10 20]
                                    [:transform/rotate 1.57]])))
  (testing "rejects unknown transform"
    (is (not (s/valid? :node/transform [[:transform/skew 10 20]])))))

;; --- path command specs ---

(deftest path-command-move-to-test
  (testing "accepts move-to with point"
    (is (s/valid? :eido.spec/path-command [:move-to [10 20]])))
  (testing "rejects move-to without point vector"
    (is (not (s/valid? :eido.spec/path-command [:move-to 10 20])))))

(deftest path-command-line-to-test
  (testing "accepts line-to"
    (is (s/valid? :eido.spec/path-command [:line-to [50 60]]))))

(deftest path-command-curve-to-test
  (testing "accepts curve-to with 3 points"
    (is (s/valid? :eido.spec/path-command [:curve-to [10 20] [30 40] [50 60]]))))

(deftest path-command-close-test
  (testing "accepts close"
    (is (s/valid? :eido.spec/path-command [:close]))))

;; --- style specs ---

(deftest style-fill-test
  (testing "accepts fill with color"
    (is (s/valid? :style/fill {:color [:color/rgb 255 0 0]})))
  (testing "rejects fill without color"
    (is (not (s/valid? :style/fill {})))))

(deftest style-stroke-test
  (testing "accepts stroke with color and width"
    (is (s/valid? :style/stroke {:color [:color/rgb 0 0 0] :width 2})))
  (testing "rejects stroke without width"
    (is (not (s/valid? :style/stroke {:color [:color/rgb 0 0 0]})))))

(deftest opacity-test
  (testing "accepts valid opacity"
    (is (s/valid? :node/opacity 0.5)))
  (testing "rejects out of range"
    (is (not (s/valid? :node/opacity 1.5)))))
