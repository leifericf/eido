(ns eido.color-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.color :as color]))

(deftest resolve-color-rgb-test
  (testing "resolves :color/rgb to map with alpha 1.0"
    (is (= {:r 200 :g 0 :b 0 :a 1.0}
           (color/resolve-color [:color/rgb 200 0 0])))
    (is (= {:r 0 :g 128 :b 255 :a 1.0}
           (color/resolve-color [:color/rgb 0 128 255])))))

(deftest resolve-color-rgba-test
  (testing "resolves :color/rgba to map with specified alpha"
    (is (= {:r 200 :g 0 :b 0 :a 0.5}
           (color/resolve-color [:color/rgba 200 0 0 0.5])))
    (is (= {:r 0 :g 0 :b 0 :a 0.0}
           (color/resolve-color [:color/rgba 0 0 0 0.0])))))

;; --- v0.5 HSL tests ---

(deftest resolve-color-hsl-primaries-test
  (testing "resolves primary colors from HSL"
    (is (= {:r 255 :g 0 :b 0 :a 1.0}
           (color/resolve-color [:color/hsl 0 1.0 0.5])))
    (is (= {:r 0 :g 255 :b 0 :a 1.0}
           (color/resolve-color [:color/hsl 120 1.0 0.5])))
    (is (= {:r 0 :g 0 :b 255 :a 1.0}
           (color/resolve-color [:color/hsl 240 1.0 0.5])))))

(deftest resolve-color-hsl-achromatic-test
  (testing "achromatic HSL (s=0) produces grays"
    (is (= {:r 0 :g 0 :b 0 :a 1.0}
           (color/resolve-color [:color/hsl 0 0 0])))
    (is (= {:r 255 :g 255 :b 255 :a 1.0}
           (color/resolve-color [:color/hsl 0 0 1.0])))
    (is (= {:r 128 :g 128 :b 128 :a 1.0}
           (color/resolve-color [:color/hsl 0 0 0.5])))))

(deftest resolve-color-hsl-yellow-test
  (testing "HSL 60 degrees produces yellow"
    (is (= {:r 255 :g 255 :b 0 :a 1.0}
           (color/resolve-color [:color/hsl 60 1.0 0.5])))))

(deftest resolve-color-hsl-wrapping-test
  (testing "hue 360 wraps to 0"
    (is (= (color/resolve-color [:color/hsl 0 1.0 0.5])
           (color/resolve-color [:color/hsl 360 1.0 0.5])))))

(deftest resolve-color-hsla-test
  (testing "HSLA includes alpha"
    (is (= {:r 255 :g 0 :b 0 :a 0.5}
           (color/resolve-color [:color/hsla 0 1.0 0.5 0.5])))))

;; --- HSB tests ---

(deftest resolve-color-hsb-primaries-test
  (testing "resolves primary colors from HSB"
    (is (= {:r 255 :g 0 :b 0 :a 1.0}
           (color/resolve-color [:color/hsb 0 1.0 1.0])))
    (is (= {:r 0 :g 255 :b 0 :a 1.0}
           (color/resolve-color [:color/hsb 120 1.0 1.0])))
    (is (= {:r 0 :g 0 :b 255 :a 1.0}
           (color/resolve-color [:color/hsb 240 1.0 1.0])))))

(deftest resolve-color-hsb-black-white-test
  (testing "HSB with brightness=0 is black"
    (is (= {:r 0 :g 0 :b 0 :a 1.0}
           (color/resolve-color [:color/hsb 0 1.0 0]))))
  (testing "HSB with saturation=0 brightness=1 is white"
    (is (= {:r 255 :g 255 :b 255 :a 1.0}
           (color/resolve-color [:color/hsb 0 0 1.0])))))

(deftest resolve-color-hsba-test
  (testing "HSBA includes alpha"
    (is (= {:r 255 :g 0 :b 0 :a 0.5}
           (color/resolve-color [:color/hsba 0 1.0 1.0 0.5])))))

;; --- v0.5 hex tests ---

(deftest resolve-color-hex-6-test
  (testing "resolves 6-digit hex"
    (is (= {:r 255 :g 0 :b 0 :a 1.0}
           (color/resolve-color [:color/hex "#FF0000"])))
    (is (= {:r 0 :g 0 :b 0 :a 1.0}
           (color/resolve-color [:color/hex "#000000"])))
    (is (= {:r 255 :g 255 :b 255 :a 1.0}
           (color/resolve-color [:color/hex "#FFFFFF"])))))

(deftest resolve-color-hex-lowercase-test
  (testing "hex is case insensitive"
    (is (= {:r 255 :g 0 :b 0 :a 1.0}
           (color/resolve-color [:color/hex "#ff0000"])))))

(deftest resolve-color-hex-8-test
  (testing "resolves 8-digit hex with alpha"
    (let [c (color/resolve-color [:color/hex "#FF000080"])]
      (is (= 255 (:r c)))
      (is (= 0 (:g c)))
      (is (= 0 (:b c)))
      (is (< (Math/abs (- (/ 128 255.0) (:a c))) 0.01)))))

(deftest resolve-color-hex-3-test
  (testing "resolves 3-digit shorthand"
    (is (= {:r 255 :g 0 :b 0 :a 1.0}
           (color/resolve-color [:color/hex "#F00"])))))

(deftest resolve-color-hex-4-test
  (testing "resolves 4-digit shorthand with alpha"
    (let [c (color/resolve-color [:color/hex "#F008"])]
      (is (= 255 (:r c)))
      (is (= 0 (:g c)))
      (is (= 0 (:b c)))
      (is (< (Math/abs (- (/ 0x88 255.0) (:a c))) 0.01)))))

(deftest resolve-color-hex-no-hash-test
  (testing "hex works without # prefix"
    (is (= {:r 255 :g 0 :b 0 :a 1.0}
           (color/resolve-color [:color/hex "FF0000"])))))

(deftest resolve-color-hex-invalid-test
  (testing "invalid hex throws ex-info"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid hex"
          (color/resolve-color [:color/hex "#12345"])))))

;; --- v0.5 round-trip tests ---

(defn- approx-color=
  "Checks that two color maps are equal within tolerance per channel."
  [c1 c2 tolerance]
  (and (< (Math/abs (- (double (:r c1)) (double (:r c2)))) tolerance)
       (< (Math/abs (- (double (:g c1)) (double (:g c2)))) tolerance)
       (< (Math/abs (- (double (:b c1)) (double (:b c2)))) tolerance)))

(defn- vec-rgb
  "Extracts [r g b] from a color vector like [:color/rgb r g b]."
  [v]
  (let [[_ r g b] v] [r g b]))

(deftest rgb-hsl-roundtrip-test
  (testing "RGB -> HSL -> RGB round-trip preserves values within +/- 1"
    (doseq [[label color] [["red"    {:r 255 :g 0   :b 0   :a 1.0}]
                            ["green"  {:r 0   :g 255 :b 0   :a 1.0}]
                            ["blue"   {:r 0   :g 0   :b 255 :a 1.0}]
                            ["white"  {:r 255 :g 255 :b 255 :a 1.0}]
                            ["black"  {:r 0   :g 0   :b 0   :a 1.0}]
                            ["mid"    {:r 100 :g 150 :b 200 :a 1.0}]]]
      (let [[h s l] (color/rgb->hsl (:r color) (:g color) (:b color))
            roundtripped (color/resolve-color [:color/hsl h s l])]
        (is (approx-color= color roundtripped 1.5)
            (str label " round-trip failed: " color " -> " [h s l] " -> " roundtripped))))))

;; --- v0.5 manipulation tests ---

(deftest lighten-test
  (testing "lightens a color vector"
    (let [[_ r g b] (color/lighten [:color/rgb 255 0 0] 0.2)]
      (is (> r 0))
      (is (> g 0) "lightened red gains green")
      (is (> b 0) "lightened red gains blue")))
  (testing "lightening white stays white"
    (is (= [:color/rgb 255 255 255]
           (color/lighten [:color/rgb 255 255 255] 0.5)))))

(deftest darken-test
  (testing "darkens a color vector"
    (let [[_ r _g _b] (color/darken [:color/rgb 255 0 0] 0.2)]
      (is (< r 255))))
  (testing "darkening black stays black"
    (is (= [:color/rgb 0 0 0]
           (color/darken [:color/rgb 0 0 0] 0.5)))))

(deftest saturate-test
  (testing "saturating increases vividness"
    (let [[_ r1 _ _] [:color/rgb 150 100 100]
          [_ r2 _ _] (color/saturate [:color/rgb 150 100 100] 0.3)]
      (is (> r2 r1)))))

(deftest desaturate-test
  (testing "fully desaturating produces gray"
    (let [[_ r g _b] (color/desaturate [:color/rgb 255 0 0] 1.0)]
      (is (< (Math/abs (- (double r) (double g))) 2)
          "r and g should be approximately equal"))))

(deftest rotate-hue-test
  (testing "rotating red by 120 gives green"
    (let [[_ r g _b] (color/rotate-hue [:color/rgb 255 0 0] 120)]
      (is (< r 10))
      (is (> g 245))))
  (testing "rotating by 360 returns same color"
    (is (= (color/rotate-hue [:color/rgb 255 0 0] 0)
           (color/rotate-hue [:color/rgb 255 0 0] 360)))))

(deftest lerp-test
  (testing "t=0 returns color-a"
    (is (= [:color/rgb 255 0 0]
           (color/lerp [:color/rgb 255 0 0] [:color/rgb 0 0 255] 0))))
  (testing "t=1 returns color-b"
    (is (= [:color/rgb 0 0 255]
           (color/lerp [:color/rgb 255 0 0] [:color/rgb 0 0 255] 1))))
  (testing "t=0.5 of black and white gives gray"
    (let [[_ r _g _b] (color/lerp [:color/rgb 0 0 0] [:color/rgb 255 255 255] 0.5)]
      (is (< (Math/abs (- 128 (long r))) 2))))
  (testing "preserves alpha interpolation"
    (let [result (color/lerp [:color/rgba 0 0 0 0.0] [:color/rgba 0 0 0 1.0] 0.5)]
      (is (= :color/rgba (first result)))
      (is (= 0.5 (nth result 4))))))

;; --- named colors ---

(deftest resolve-color-name-test
  (testing "resolves CSS named colors"
    (is (= {:r 255 :g 0 :b 0 :a 1.0}
           (color/resolve-color [:color/name "red"])))
    (is (= {:r 255 :g 127 :b 80 :a 1.0}
           (color/resolve-color [:color/name "coral"])))
    (is (= {:r 100 :g 149 :b 237 :a 1.0}
           (color/resolve-color [:color/name "cornflowerblue"])))))

(deftest resolve-color-name-case-insensitive-test
  (testing "named colors are case insensitive"
    (is (= (color/resolve-color [:color/name "red"])
           (color/resolve-color [:color/name "Red"])))
    (is (= (color/resolve-color [:color/name "cornflowerblue"])
           (color/resolve-color [:color/name "CornflowerBlue"])))))

(deftest resolve-color-name-unknown-test
  (testing "unknown name throws ex-info"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unknown color name"
          (color/resolve-color [:color/name "notacolor"])))))

;; --- keyword color shorthand ---

(deftest resolve-color-keyword-test
  (testing "resolves bare keyword color names"
    (is (= {:r 255 :g 0 :b 0 :a 1.0}
           (color/resolve-color :red)))
    (is (= {:r 0 :g 0 :b 0 :a 1.0}
           (color/resolve-color :black)))
    (is (= {:r 255 :g 255 :b 255 :a 1.0}
           (color/resolve-color :white)))
    (is (= {:r 255 :g 127 :b 80 :a 1.0}
           (color/resolve-color :coral)))
    (is (= {:r 100 :g 149 :b 237 :a 1.0}
           (color/resolve-color :cornflowerblue)))))

(deftest resolve-color-keyword-unknown-test
  (testing "unknown keyword throws ex-info"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unknown color name"
          (color/resolve-color :notacolor)))))

(deftest keyword-color-manipulation-test
  (testing "keyword colors work with lighten"
    (let [[_ r g b] (color/lighten :red 0.2)]
      (is (> r 0))
      (is (> g 0))))
  (testing "keyword colors work with darken"
    (let [[_ r _g _b] (color/darken :white 0.3)]
      (is (< r 255))))
  (testing "keyword colors work with lerp"
    (is (= [:color/rgb 128 0 128]
           (color/lerp :red :blue 0.5))))
  (testing "keyword colors work with rotate-hue"
    (let [result (color/rotate-hue :red 120)]
      (is (vector? result)))))

;; --- convenience helper tests ---

(deftest rgb-test
  (testing "creates color vector"
    (is (= [:color/rgb 255 0 0] (color/rgb 255 0 0)))))

(deftest hsl-test
  (testing "creates color vector"
    (is (= [:color/hsl 200 0.8 0.5] (color/hsl 200 0.8 0.5))))
  (testing "wraps hue with mod 360"
    (is (= [:color/hsl 10 0.5 0.5] (color/hsl 370 0.5 0.5)))))
