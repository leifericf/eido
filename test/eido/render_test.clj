(ns eido.render-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.render :as render])
  (:import
    [java.awt.image BufferedImage]))

(defn- pixel-rgb
  "Returns [r g b] for the pixel at (x, y) in a BufferedImage."
  [^BufferedImage img x y]
  (let [argb (.getRGB img x y)]
    [(bit-and (bit-shift-right argb 16) 0xFF)
     (bit-and (bit-shift-right argb 8) 0xFF)
     (bit-and argb 0xFF)]))

(defn- pixel-alpha
  "Returns the alpha component (0-255) for the pixel at (x, y)."
  [^BufferedImage img x y]
  (bit-and (bit-shift-right (.getRGB img x y) 24) 0xFF))

(deftest render-background-test
  (testing "renders background color correctly"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 128 :g 64 :b 32 :a 1.0}
              :ir/ops []}
          img (render/render ir)]
      (is (instance? BufferedImage img))
      (is (= 100 (.getWidth img)))
      (is (= 100 (.getHeight img)))
      (is (= [128 64 32] (pixel-rgb img 50 50))))))

(deftest render-rect-fill-test
  (testing "renders filled rectangle at correct position"
    (let [ir {:ir/size [200 200]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops [{:op :rect :x 50 :y 50 :w 100 :h 100
                         :fill {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0}]}
          img (render/render ir)]
      (is (= [255 0 0] (pixel-rgb img 100 100))
          "center of rect should be red")
      (is (= [255 255 255] (pixel-rgb img 10 10))
          "outside rect should be background"))))

(deftest render-circle-fill-test
  (testing "renders filled circle at correct position"
    (let [ir {:ir/size [200 200]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops [{:op :circle :cx 100 :cy 100 :r 40
                         :fill {:r 0 :g 0 :b 255 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0}]}
          img (render/render ir)]
      (is (= [0 0 255] (pixel-rgb img 100 100))
          "center of circle should be blue")
      (is (= [255 255 255] (pixel-rgb img 5 5))
          "corner should be background"))))

(deftest render-stroke-test
  (testing "stroke is visible around shape"
    (let [ir {:ir/size [200 200]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops [{:op :rect :x 50 :y 50 :w 100 :h 100
                         :fill nil
                         :stroke-color {:r 0 :g 0 :b 0 :a 1.0}
                         :stroke-width 4
                         :opacity 1.0}]}
          img (render/render ir)]
      (is (= [255 255 255] (pixel-rgb img 100 100))
          "center of unfilled rect should be background")
      (is (= [0 0 0] (pixel-rgb img 50 50))
          "top-left corner of rect should have stroke"))))

(deftest render-opacity-test
  (testing "opacity blends shape with background"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 100 :h 100
                         :fill {:r 255 :g 255 :b 255 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 0.5}]}
          img (render/render ir)
          [r g b] (pixel-rgb img 50 50)]
      (is (< (Math/abs (- r 128)) 5)
          "blended red should be ~128")
      (is (< (Math/abs (- g 128)) 5)
          "blended green should be ~128"))))

(deftest render-order-test
  (testing "later nodes paint over earlier nodes"
    (let [ir {:ir/size [200 200]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops [{:op :rect :x 50 :y 50 :w 100 :h 100
                         :fill {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0}
                        {:op :rect :x 50 :y 50 :w 100 :h 100
                         :fill {:r 0 :g 0 :b 255 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0}]}
          img (render/render ir)]
      (is (= [0 0 255] (pixel-rgb img 100 100))
          "later blue rect should cover earlier red rect"))))

(deftest render-determinism-test
  (testing "same IR produces identical output"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 200 :g 200 :b 200 :a 1.0}
              :ir/ops [{:op :circle :cx 50 :cy 50 :r 30
                         :fill {:r 100 :g 50 :b 25 :a 1.0}
                         :stroke-color {:r 0 :g 0 :b 0 :a 1.0}
                         :stroke-width 2
                         :opacity 0.8}]}
          img1 (render/render ir)
          img2 (render/render ir)]
      (is (every? true?
                  (for [x (range 100) y (range 100)]
                    (= (.getRGB img1 x y)
                       (.getRGB img2 x y))))
          "all pixels must be identical across renders"))))

;; --- v0.2 transform rendering tests ---

(deftest render-translate-test
  (testing "translate moves shape to new position"
    (let [ir {:ir/size [200 200]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 50 :h 50
                         :fill {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0
                         :transforms [[:translate 50 50]]}]}
          img (render/render ir)]
      (is (= [255 0 0] (pixel-rgb img 75 75))
          "center of translated rect should be red")
      (is (= [255 255 255] (pixel-rgb img 25 25))
          "original position should be background"))))

(deftest render-scale-test
  (testing "scale enlarges shape"
    (let [ir {:ir/size [200 200]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 50 :h 50
                         :fill {:r 0 :g 255 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0
                         :transforms [[:scale 2 2]]}]}
          img (render/render ir)]
      (is (= [0 255 0] (pixel-rgb img 75 75))
          "scaled rect should cover (75,75)"))))

(deftest render-identity-rotation-test
  (testing "rotation by 0 is identical to no rotation"
    (let [base-ir {:ir/size [100 100]
                   :ir/background {:r 255 :g 255 :b 255 :a 1.0}
                   :ir/ops [{:op :rect :x 25 :y 25 :w 50 :h 50
                              :fill {:r 0 :g 0 :b 255 :a 1.0}
                              :stroke-color nil :stroke-width nil
                              :opacity 1.0
                              :transforms []}]}
          rot-ir (assoc-in base-ir [:ir/ops 0 :transforms]
                           [[:rotate 0.0]])
          img1 (render/render base-ir)
          img2 (render/render rot-ir)]
      (is (every? true?
                  (for [x (range 100) y (range 100)]
                    (= (.getRGB img1 x y)
                       (.getRGB img2 x y))))
          "zero rotation should be identical to no rotation"))))

(deftest render-no-transforms-backward-compat-test
  (testing "ops without :transforms key still render correctly"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops [{:op :rect :x 25 :y 25 :w 50 :h 50
                         :fill {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0}]}
          img (render/render ir)]
      (is (= [255 0 0] (pixel-rgb img 50 50))))))

;; --- v0.3 path rendering tests ---

(deftest render-path-fill-test
  (testing "renders a filled triangle path"
    (let [ir {:ir/size [200 200]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops [{:op :path
                         :commands [[:move-to 50 150]
                                    [:line-to 100 50]
                                    [:line-to 150 150]
                                    [:close]]
                         :fill {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0
                         :transforms []}]}
          img (render/render ir)]
      (is (= [255 0 0] (pixel-rgb img 100 120))
          "inside triangle should be red")
      (is (= [255 255 255] (pixel-rgb img 10 10))
          "outside triangle should be background"))))

(deftest render-path-stroke-test
  (testing "renders a stroked line path"
    (let [ir {:ir/size [200 200]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops [{:op :path
                         :commands [[:move-to 0 100]
                                    [:line-to 200 100]]
                         :fill nil
                         :stroke-color {:r 0 :g 0 :b 0 :a 1.0}
                         :stroke-width 4
                         :opacity 1.0
                         :transforms []}]}
          img (render/render ir)]
      (is (= [0 0 0] (pixel-rgb img 100 100))
          "pixel on the line should be stroke color"))))

(deftest render-path-curve-test
  (testing "renders a filled path with cubic bezier"
    (let [ir {:ir/size [200 200]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops [{:op :path
                         :commands [[:move-to 10 150]
                                    [:curve-to 10 10 190 10 190 150]
                                    [:close]]
                         :fill {:r 0 :g 0 :b 255 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0
                         :transforms []}]}
          img (render/render ir)]
      (is (= [0 0 255] (pixel-rgb img 100 100))
          "inside curved shape should be blue"))))

(deftest render-path-empty-test
  (testing "empty commands list renders without error"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 128 :g 128 :b 128 :a 1.0}
              :ir/ops [{:op :path
                         :commands []
                         :fill {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0
                         :transforms []}]}
          img (render/render ir)]
      (is (= [128 128 128] (pixel-rgb img 50 50))
          "background should be intact"))))

;; --- v0.6 render options tests ---

(deftest render-scale-test
  (testing "scale 2 produces 2x dimensions"
    (let [ir {:ir/size [100 50]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops []}
          img (render/render ir {:scale 2})]
      (is (= 200 (.getWidth img)))
      (is (= 100 (.getHeight img))))))

(deftest render-scale-pixel-test
  (testing "scaled rect fills correct area"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 50 :h 50
                         :fill {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0 :transforms []}]}
          img (render/render ir {:scale 2})]
      (is (= [255 0 0] (pixel-rgb img 50 50))
          "center of scaled rect should be red")
      (is (= [255 255 255] (pixel-rgb img 150 150))
          "outside scaled rect should be background"))))

(deftest render-transparent-bg-test
  (testing "transparent background skips bg fill"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops []}
          img (render/render ir {:transparent-background true})]
      (is (= 0 (pixel-alpha img 50 50))
          "alpha should be 0 (fully transparent)"))))

(deftest render-opts-backward-compat-test
  (testing "render without opts still works"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 128 :g 128 :b 128 :a 1.0}
              :ir/ops []}
          img (render/render ir)]
      (is (= [128 128 128] (pixel-rgb img 50 50))))))
