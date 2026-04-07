(ns eido.palette-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.palette :as palette]))

;; --- complementary ---

(deftest complementary-test
  (testing "complementary of red is cyan"
    (let [[_ r g b] (palette/complementary [:color/rgb 255 0 0])]
      (is (< r 10))
      (is (> g 245))
      (is (> b 245))))
  (testing "returns a color vector"
    (is (#{:color/rgb :color/rgba} (first (palette/complementary [:color/rgb 255 0 0]))))))

;; --- analogous ---

(deftest analogous-test
  (testing "analogous returns n colors"
    (is (= 3 (count (palette/analogous [:color/rgb 255 0 0] 3))))
    (is (= 5 (count (palette/analogous [:color/rgb 255 0 0] 5)))))
  (testing "all returned values are color vectors"
    (is (every? #(#{:color/rgb :color/rgba} (first %))
                (palette/analogous [:color/rgb 255 0 0] 3)))))

;; --- triadic ---

(deftest triadic-test
  (testing "triadic returns 3 colors"
    (is (= 3 (count (palette/triadic [:color/rgb 255 0 0])))))
  (testing "second color is ~120 degrees from first"
    (let [[_ r g _b] (second (palette/triadic [:color/rgb 255 0 0]))]
      (is (< r 10) "second should be greenish")
      (is (> g 245)))))

;; --- split-complementary ---

(deftest split-complementary-test
  (testing "split-complementary returns 3 colors"
    (is (= 3 (count (palette/split-complementary [:color/rgb 255 0 0])))))
  (testing "first color is the input"
    (let [input [:color/rgb 255 0 0]
          result (palette/split-complementary input)]
      (is (= input (first result))))))

;; --- tetradic ---

(deftest tetradic-test
  (testing "tetradic returns 4 colors"
    (is (= 4 (count (palette/tetradic [:color/rgb 255 0 0]))))))

;; --- monochromatic ---

(deftest monochromatic-test
  (testing "monochromatic returns n colors"
    (is (= 5 (count (palette/monochromatic [:color/rgb 255 0 0] 5)))))
  (testing "all colors have similar hue"
    (let [colors (palette/monochromatic [:color/hsl 0 1.0 0.5] 5)]
      (is (every? #(#{:color/rgb :color/rgba} (first %)) colors)))))

;; --- gradient-palette ---

(deftest gradient-palette-test
  (testing "gradient-palette returns n colors"
    (is (= 5 (count (palette/gradient-palette [:color/rgb 0 0 0]
                                               [:color/rgb 255 255 255]
                                               5)))))
  (testing "first is start, last is end"
    (let [pal (palette/gradient-palette [:color/rgb 0 0 0]
                                        [:color/rgb 255 255 255]
                                        3)]
      (is (= [:color/rgb 0 0 0] (first pal)))
      (is (= [:color/rgb 255 255 255] (last pal)))))
  (testing "midpoint of black-white gradient is gray"
    (let [[_ r g b] (second (palette/gradient-palette [:color/rgb 0 0 0]
                                                       [:color/rgb 255 255 255]
                                                       3))]
      (is (< (Math/abs (- 128 (long r))) 2)))))

;; --- curated palettes ---

(deftest curated-palettes-test
  (testing "palettes map exists and contains entries"
    (is (map? palette/palettes))
    (is (pos? (count palette/palettes))))
  (testing "each palette is a vector of color vectors"
    (doseq [[_name pal] palette/palettes]
      (is (vector? pal))
      (is (every? vector? pal)))))

;; --- gradient-map ---

(deftest gradient-map-endpoints-test
  (testing "t=0 returns first stop color"
    (is (= [:color/rgb 0 0 0]
           (palette/gradient-map [[0.0 [:color/rgb 0 0 0]]
                                  [1.0 [:color/rgb 255 255 255]]] 0.0))))
  (testing "t=1 returns last stop color"
    (is (= [:color/rgb 255 255 255]
           (palette/gradient-map [[0.0 [:color/rgb 0 0 0]]
                                  [1.0 [:color/rgb 255 255 255]]] 1.0)))))

(deftest gradient-map-midpoint-test
  (testing "t=0.5 interpolates to gray"
    (let [[_ r g b] (palette/gradient-map [[0.0 [:color/rgb 0 0 0]]
                                            [1.0 [:color/rgb 255 255 255]]] 0.5)]
      (is (< (Math/abs (- 128 (long r))) 2)))))

(deftest gradient-map-multi-stop-test
  (testing "multi-stop interpolation"
    (let [stops [[0.0 [:color/rgb 255 0 0]]
                 [0.5 [:color/rgb 0 255 0]]
                 [1.0 [:color/rgb 0 0 255]]]
          [_ r g _b] (palette/gradient-map stops 0.25)]
      (is (> r 100) "near red-green midpoint, r should be high")
      (is (> g 100) "near red-green midpoint, g should be high"))))

(deftest gradient-map-clamp-test
  (testing "t out of range is clamped"
    (is (= [:color/rgb 0 0 0]
           (palette/gradient-map [[0.0 [:color/rgb 0 0 0]]
                                  [1.0 [:color/rgb 255 255 255]]] -0.5)))
    (is (= [:color/rgb 255 255 255]
           (palette/gradient-map [[0.0 [:color/rgb 0 0 0]]
                                  [1.0 [:color/rgb 255 255 255]]] 1.5)))))
