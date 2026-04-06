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
