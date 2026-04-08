(ns eido.visual-test
  "Visual regression tests — renders deterministic scenes and compares
  pixel-by-pixel against committed reference PNGs."
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]
    [eido.core :as eido]
    [eido.scene3d :as s3d])
  (:import
    [java.awt.image BufferedImage]
    [javax.imageio ImageIO]))

;; --- test scenes ---

(defn- perspective-torus-scene []
  {:image/size [200 200]
   :image/background [:color/rgb 240 240 240]
   :image/nodes
   [(s3d/render-mesh
      (s3d/perspective {:scale 80 :origin [100 100]
                        :yaw 0.5 :pitch -0.3 :distance 8})
      (s3d/torus-mesh 1.0 0.4 16 12)
      {:style {:style/fill [:color/rgb 100 140 180]
               :style/stroke {:color [:color/rgb 60 100 140] :width 0.3}}
       :light {:light/direction [1 2 1]
               :light/ambient 0.3
               :light/intensity 0.7}})]})

(defn- isometric-cube-scene []
  {:image/size [200 200]
   :image/background [:color/rgb 240 240 240]
   :image/nodes
   [(s3d/render-mesh
      (s3d/isometric {:scale 60 :origin [100 110]})
      (s3d/cube-mesh [0 0 0] 1.5)
      {:style {:style/fill [:color/rgb 180 120 100]
               :style/stroke {:color [:color/rgb 140 80 60] :width 0.3}}
       :light {:light/direction [-1 2 0.5]
               :light/ambient 0.25
               :light/intensity 0.75}})]})

(defn- orthographic-sphere-scene []
  {:image/size [200 200]
   :image/background [:color/rgb 240 240 240]
   :image/nodes
   [(s3d/render-mesh
      (s3d/orthographic {:scale 60 :origin [100 100]
                         :yaw 0.8 :pitch -0.4})
      (s3d/sphere-mesh 1.5 16 12)
      {:style {:style/fill [:color/rgb 120 180 120]
               :style/stroke {:color [:color/rgb 80 140 80] :width 0.2}}
       :light {:light/direction [0 1 1]
               :light/ambient 0.35
               :light/intensity 0.65}})]})

(defn- flat-2d-scene []
  {:image/size [200 200]
   :image/background [:color/rgb 255 255 255]
   :image/nodes
   [{:node/type :shape/circle
     :circle/center [100 100]
     :circle/radius 60
     :style/fill [:color/rgb 70 130 200]
     :style/stroke {:color [:color/rgb 30 90 160] :width 2}}
    {:node/type :shape/rect
     :rect/xy [40 40]
     :rect/size [50 50]
     :style/fill [:color/rgb 200 100 80]
     :style/stroke {:color [:color/rgb 160 60 40] :width 1.5}}]})

;; --- image comparison ---

(def ^:private ref-dir "test/resources/visual-refs")

(def ^:private test-scenes
  [["perspective-torus" perspective-torus-scene]
   ["isometric-cube"    isometric-cube-scene]
   ["orthographic-sphere" orthographic-sphere-scene]
   ["flat-2d"           flat-2d-scene]])

(defn- load-png
  "Loads a PNG file as a BufferedImage, or nil if it doesn't exist."
  ^BufferedImage [^String path]
  (let [f (io/file path)]
    (when (.exists f)
      (ImageIO/read f))))

(defn- image-diff
  "Compares two BufferedImages pixel-by-pixel.
  Returns {:identical? bool :differing-pixels n :max-channel-diff n :total-pixels n}."
  [^BufferedImage a ^BufferedImage b]
  (let [w (.getWidth a)
        h (.getHeight a)]
    (if (or (not= w (.getWidth b)) (not= h (.getHeight b)))
      {:identical? false :reason :size-mismatch
       :a-size [w h] :b-size [(.getWidth b) (.getHeight b)]}
      (let [total (* w h)]
        (loop [x 0 y 0 diff-count 0 max-diff 0]
          (if (>= y h)
            {:identical?       (zero? diff-count)
             :differing-pixels diff-count
             :max-channel-diff max-diff
             :total-pixels     total}
            (if (>= x w)
              (recur 0 (inc y) diff-count max-diff)
              (let [pa (.getRGB a x y)
                    pb (.getRGB b x y)]
                (if (== pa pb)
                  (recur (inc x) y diff-count max-diff)
                  (let [ra (bit-and (bit-shift-right pa 16) 0xFF)
                        ga (bit-and (bit-shift-right pa 8) 0xFF)
                        ba (bit-and pa 0xFF)
                        rb (bit-and (bit-shift-right pb 16) 0xFF)
                        gb (bit-and (bit-shift-right pb 8) 0xFF)
                        bb (bit-and pb 0xFF)
                        d  (max (abs (- ra rb)) (abs (- ga gb)) (abs (- ba bb)))]
                    (recur (inc x) y (inc diff-count) (max max-diff d))))))))))))

(defn- render-scene
  "Renders a scene to a BufferedImage."
  ^BufferedImage [scene-fn]
  (eido/render (scene-fn)))

(defn- ref-path [scene-name]
  (str ref-dir "/" scene-name ".png"))

;; --- reference generation ---

(defn regenerate-refs!
  "Re-renders all test scenes and writes them as reference PNGs.
  Call from the REPL when intentionally changing visual output."
  []
  (doseq [[scene-name scene-fn] test-scenes]
    (let [img  (render-scene scene-fn)
          path (ref-path scene-name)]
      (ImageIO/write img "png" (io/file path))
      (println "Wrote" path)))
  :done)

;; --- tests ---

;; Allow small tolerance for cross-platform anti-aliasing differences.
;; Java2D produces slightly different results on macOS vs Linux due to
;; FPU behavior and font rendering. Max 2 channel diff, <0.1% of pixels.
(def ^:private max-channel-tolerance 2)
(def ^:private max-pixel-diff-pct 0.2)

(deftest visual-regression-test
  (doseq [[scene-name scene-fn] test-scenes]
    (testing (str "visual regression: " scene-name)
      (let [ref-file (ref-path scene-name)
            ref-img  (load-png ref-file)]
        (is (some? ref-img)
            (str "Reference image missing: " ref-file
                 ". Run (eido.visual-test/regenerate-refs!) to generate."))
        (when ref-img
          (let [actual  (render-scene scene-fn)
                result  (image-diff ref-img actual)
                diff-pct (when (:total-pixels result)
                           (* 100.0 (/ (:differing-pixels result 0)
                                       (double (:total-pixels result)))))]
            (is (or (:identical? result)
                    (and (<= (:max-channel-diff result) max-channel-tolerance)
                         (<= diff-pct max-pixel-diff-pct)))
                (str scene-name " differs from reference: "
                     (:differing-pixels result) " pixels differ ("
                     (format "%.2f" (or diff-pct 0.0)) "%), "
                     "max channel diff = " (:max-channel-diff result)))))))))
