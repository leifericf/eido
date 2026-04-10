(ns eido.color.palette-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.color.palette :as palette])
  (:import
    [java.awt.image BufferedImage]))

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
                (palette/analogous [:color/rgb 255 0 0] 3))))
  (testing "n=1 returns single color without division by zero"
    (let [result (palette/analogous [:color/rgb 255 0 0] 1)]
      (is (= 1 (count result)))
      (is (= [:color/rgb 255 0 0] (first result))))))

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

;; --- weighted palette utilities ---

(def test-palette
  [[:color/rgb 255 0 0]
   [:color/rgb 0 255 0]
   [:color/rgb 0 0 255]])

(deftest weighted-pick-determinism-test
  (testing "same seed produces same color"
    (is (= (palette/weighted-pick test-palette [1 1 1] 42)
           (palette/weighted-pick test-palette [1 1 1] 42)))))

(deftest weighted-pick-valid-color-test
  (testing "returns a color from the palette"
    (is (some #{(palette/weighted-pick test-palette [1 1 1] 42)} test-palette))))

(deftest weighted-sample-count-test
  (testing "returns exactly n colors"
    (is (= 10 (count (palette/weighted-sample test-palette [1 1 1] 10 42))))))

(deftest weighted-sample-all-from-palette-test
  (testing "all sampled colors are from the palette"
    (let [sampled (palette/weighted-sample test-palette [1 1 1] 50 42)]
      (is (every? (set test-palette) sampled)))))

(deftest weighted-gradient-stop-count-test
  (testing "returns one stop per palette color"
    (is (= 3 (count (palette/weighted-gradient test-palette [1 1 1]))))))

(deftest weighted-gradient-monotonic-test
  (testing "stop positions are monotonically increasing"
    (let [stops (palette/weighted-gradient test-palette [3 1 1])
          positions (mapv first stops)]
      (is (apply < positions)))))

(deftest weighted-gradient-proportional-test
  (testing "first color occupies more space with higher weight"
    (let [stops-equal (palette/weighted-gradient test-palette [1 1 1])
          stops-heavy (palette/weighted-gradient test-palette [5 1 1])]
      ;; With equal weights, first stop is at 1/6 ≈ 0.167
      ;; With heavy first, first stop is at 5/14 ≈ 0.357
      (is (> (ffirst stops-heavy) (ffirst stops-equal))))))

(deftest shuffle-palette-determinism-test
  (testing "same seed produces same shuffle"
    (is (= (palette/shuffle-palette test-palette 42)
           (palette/shuffle-palette test-palette 42)))))

(deftest shuffle-palette-preserves-elements-test
  (testing "contains same colors"
    (is (= (set test-palette)
           (set (palette/shuffle-palette test-palette 42))))))

(deftest with-roles-test
  (testing "creates role map from palette"
    (let [pal [[:color/rgb 255 0 0] [:color/rgb 0 255 0] [:color/rgb 0 0 255]]
          roles (palette/with-roles [:bg :primary :accent] pal)]
      (is (= [:color/rgb 255 0 0] (:bg roles)))
      (is (= [:color/rgb 0 0 255] (:accent roles))))))

;; --- swatch ---

;; --- min-contrast ---

(deftest min-contrast-test
  (testing "returns the minimum pairwise contrast"
    (let [pal [:black :white :red]
          mc (palette/min-contrast pal)]
      (is (number? mc))
      (is (> mc 1.0))))
  (testing "single-color palette returns Infinity"
    (is (Double/isInfinite (palette/min-contrast [:red])))))

;; --- sort-by-lightness ---

(deftest sort-by-lightness-test
  (testing "orders from dark to light"
    (let [sorted (palette/sort-by-lightness [:white :black :red])]
      ;; black should be first, white should be last
      (is (= :black (first sorted)))
      (is (= :white (last sorted)))))
  (testing "preserves palette size"
    (is (= 3 (count (palette/sort-by-lightness [:red :green :blue]))))))

;; --- swatch ---

(deftest swatch-test
  (testing "returns a BufferedImage"
    (let [pal [:red :green :blue :yellow :white]
          img (palette/swatch pal)]
      (is (instance? BufferedImage img))))
  (testing "dimensions match defaults"
    (let [img (palette/swatch [:red :blue])]
      (is (= 400 (.getWidth ^BufferedImage img)))
      (is (= 60 (.getHeight ^BufferedImage img)))))
  (testing "custom dimensions"
    (let [img (palette/swatch [:red :blue] {:width 200 :height 40})]
      (is (= 200 (.getWidth ^BufferedImage img)))
      (is (= 40 (.getHeight ^BufferedImage img))))))
