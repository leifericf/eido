(ns eido.core-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.core :as eido])
  (:import
    [java.awt.image BufferedImage]
    [java.io File]))

(defn- pixel-rgb
  "Returns [r g b] for the pixel at (x, y) in a BufferedImage."
  [^BufferedImage img x y]
  (let [argb (.getRGB img x y)]
    [(bit-and (bit-shift-right argb 16) 0xFF)
     (bit-and (bit-shift-right argb 8) 0xFF)
     (bit-and argb 0xFF)]))

(def sample-scene
  {:image/size [800 600]
   :image/background [:color/rgb 255 255 255]
   :image/nodes
   [{:node/type :shape/rect
     :rect/xy [100 100]
     :rect/size [200 150]
     :style/fill {:color [:color/rgb 0 128 255]}}
    {:node/type :shape/circle
     :circle/center [400 300]
     :circle/radius 80
     :style/fill {:color [:color/rgb 200 0 0]}
     :style/stroke {:color [:color/rgb 0 0 0] :width 2}}]})

(deftest render-integration-test
  (testing "full scene renders to correct dimensions"
    (let [img (eido/render sample-scene)]
      (is (instance? BufferedImage img))
      (is (= 800 (.getWidth img)))
      (is (= 600 (.getHeight img)))))
  (testing "background is white"
    (let [img (eido/render sample-scene)]
      (is (= [255 255 255] (pixel-rgb img 10 10)))))
  (testing "rect fill is visible at center"
    (let [img (eido/render sample-scene)]
      (is (= [0 128 255] (pixel-rgb img 200 175)))))
  (testing "circle fill is visible at center"
    (let [img (eido/render sample-scene)]
      (is (= [200 0 0] (pixel-rgb img 400 300))))))

(deftest render-to-file-test
  (testing "writes a valid PNG file"
    (let [path (str (File/createTempFile "eido-test" ".png"))
          result (eido/render-to-file sample-scene path)]
      (is (= path result))
      (let [f (File. ^String path)]
        (is (.exists f))
        (is (pos? (.length f)))
        (.delete f)))))

;; --- v0.2 integration tests ---

(def composition-scene
  {:image/size [400 400]
   :image/background [:color/rgb 255 255 255]
   :image/nodes
   [{:node/type :group
     :node/transform [[:transform/translate 200 200]]
     :style/fill {:color [:color/rgb 255 0 0]}
     :group/children
     [{:node/type :shape/circle
       :circle/center [0 0]
       :circle/radius 50}
      {:node/type :shape/rect
       :rect/xy [-30 -30]
       :rect/size [60 60]
       :style/fill {:color [:color/rgb 0 0 255]}
       :node/opacity 0.5}]}]})

(deftest composition-integration-test
  (testing "group with transform, style inheritance, and opacity"
    (let [img (eido/render composition-scene)]
      (is (= 400 (.getWidth img)))
      (is (= 400 (.getHeight img)))
      (is (= [255 255 255] (pixel-rgb img 10 10))
          "corner should be background")
      ;; Circle at (200,200) with inherited red fill, under the blue rect
      ;; Blue rect at (170-230, 170-230) with 0.5 opacity over the red circle
      ;; At (200,200) — center — the blue rect paints over the red circle
      (let [[r _ b] (pixel-rgb img 200 200)]
        (is (pos? b) "blue component should be present at center")))))

(deftest backward-compat-test
  (testing "v0.1 sample scene still renders correctly"
    (let [img (eido/render sample-scene)]
      (is (= [0 128 255] (pixel-rgb img 200 175))
          "rect fill unchanged")
      (is (= [200 0 0] (pixel-rgb img 400 300))
          "circle fill unchanged"))))
