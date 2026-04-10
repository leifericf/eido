(ns eido.resolution-test
  "Verifies that the same scene rendered at different resolutions produces
  visually equivalent output — the composition scales correctly."
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.core :as eido])
  (:import
    [java.awt.image BufferedImage]))

(defn- downsample
  "Downsamples a BufferedImage by an integer factor using area averaging."
  [^BufferedImage img factor]
  (let [sw (/ (.getWidth img) factor)
        sh (/ (.getHeight img) factor)
        small (BufferedImage. sw sh BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics small)]
    (.drawImage g img 0 0 sw sh 0 0 (.getWidth img) (.getHeight img) nil)
    (.dispose g)
    small))

(defn- pixel-distance
  "Returns the maximum per-channel difference between two pixels."
  [^BufferedImage a ^BufferedImage b x y]
  (let [pa (.getRGB a x y)
        pb (.getRGB b x y)]
    (max (Math/abs (- (bit-and (bit-shift-right pa 16) 0xFF)
                      (bit-and (bit-shift-right pb 16) 0xFF)))
         (Math/abs (- (bit-and (bit-shift-right pa 8) 0xFF)
                      (bit-and (bit-shift-right pb 8) 0xFF)))
         (Math/abs (- (bit-and pa 0xFF)
                      (bit-and pb 0xFF))))))

(defn- images-similar?
  "Checks that two same-sized images are similar within tolerance.
  Returns true if at least the given percentage of pixels are within tolerance."
  [^BufferedImage a ^BufferedImage b tolerance match-pct]
  (let [w (.getWidth a)
        h (.getHeight a)
        total (* w h)
        matches (reduce (fn [acc y]
                          (reduce (fn [acc2 x]
                                    (if (<= (pixel-distance a b x y) tolerance)
                                      (inc acc2)
                                      acc2))
                                  acc (range w)))
                        0 (range h))]
    (>= (/ (double matches) total) match-pct)))

(def scale-scene
  "A simple scene with basic shapes — no generators, no hardcoded pixel values."
  {:image/size [200 200]
   :image/background [:color/rgb 240 240 240]
   :image/nodes
   [{:node/type :shape/circle
     :circle/center [100 100]
     :circle/radius 60
     :style/fill [:color/rgb 200 0 0]}
    {:node/type :shape/rect
     :rect/xy [40 40]
     :rect/size [80 80]
     :style/fill [:color/rgb 0 0 200]
     :node/opacity 0.6}
    {:node/type :shape/line
     :line/from [10 190]
     :line/to [190 10]
     :style/stroke {:color [:color/rgb 0 0 0] :width 3}}]})

(deftest resolution-scale-test
  (testing "scene at 1x and 5x scale produce visually equivalent compositions"
    (let [img-1x (eido/render scale-scene)
          img-5x (eido/render scale-scene {:scale 5})
          img-5x-down (downsample img-5x 5)]
      ;; Both should match within tolerance
      ;; Antialiasing differences are expected, so we allow 30 per channel
      ;; and require 95% of pixels to match
      (is (images-similar? img-1x img-5x-down 30 0.95)
          "1x and downsampled 5x should be visually equivalent"))))

(deftest resolution-units-scale-test
  (testing "scene described in cm produces same composition as equivalent pixel scene"
    (let [;; Scene in pixels
          px-scene {:image/size [1000 1000]
                    :image/background [:color/rgb 255 255 255]
                    :image/nodes
                    [{:node/type :shape/circle
                      :circle/center [500 500]
                      :circle/radius 300
                      :style/fill [:color/rgb 100 150 200]}]}
          ;; Equivalent scene in cm at 100 DPI (so 10cm = 1000px)
          cm-scene {:image/size [10.0 10.0]
                    :image/units :cm
                    :image/dpi 254  ;; 254 DPI = 100 px/cm
                    :image/background [:color/rgb 255 255 255]
                    :image/nodes
                    [{:node/type :shape/circle
                      :circle/center [5.0 5.0]
                      :circle/radius 3.0
                      :style/fill [:color/rgb 100 150 200]}]}
          img-px (eido/render px-scene)
          img-cm (eido/render ((requiring-resolve 'eido.scene/with-units) cm-scene))]
      (is (= (.getWidth img-px) (.getWidth img-cm))
          "pixel widths should match")
      (is (= (.getHeight img-px) (.getHeight img-cm))
          "pixel heights should match")
      (is (images-similar? img-px img-cm 5 0.99)
          "same composition in px and cm should produce identical output"))))
