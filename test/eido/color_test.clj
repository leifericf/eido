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

(deftest ->awt-color-test
  (testing "converts color map to java.awt.Color"
    (let [c (color/->awt-color {:r 200 :g 100 :b 50 :a 1.0})]
      (is (instance? java.awt.Color c))
      (is (= 200 (.getRed c)))
      (is (= 100 (.getGreen c)))
      (is (= 50 (.getBlue c)))
      (is (= 255 (.getAlpha c)))))
  (testing "handles fractional alpha"
    (let [c (color/->awt-color {:r 0 :g 0 :b 0 :a 0.5})]
      (is (< (Math/abs (- 128 (.getAlpha c))) 2)))))

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
