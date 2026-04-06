(ns eido.core-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.core :as eido])
  (:import
    [java.awt.image BufferedImage]
    [java.io File]
    [javax.imageio ImageIO]))

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

;; --- v0.6 export format tests ---

(deftest render-to-file-jpeg-test
  (testing "writes a valid JPEG file"
    (let [path (str (File/createTempFile "eido-test" ".jpg"))]
      (eido/render-to-file sample-scene path)
      (let [f (File. ^String path)
            img (ImageIO/read f)]
        (is (.exists f))
        (is (pos? (.length f)))
        (is (= 800 (.getWidth img)))
        (.delete f)))))

(deftest render-to-file-gif-test
  (testing "writes a valid GIF file"
    (let [path (str (File/createTempFile "eido-test" ".gif"))]
      (eido/render-to-file sample-scene path)
      (let [f (File. ^String path)]
        (is (.exists f))
        (is (pos? (.length f)))
        (.delete f)))))

(deftest render-to-file-bmp-test
  (testing "writes a valid BMP file"
    (let [path (str (File/createTempFile "eido-test" ".bmp"))]
      (eido/render-to-file sample-scene path)
      (let [f (File. ^String path)]
        (is (.exists f))
        (is (pos? (.length f)))
        (.delete f)))))

(deftest render-to-file-jpeg-quality-test
  (testing "higher quality produces larger file"
    (let [path-lo (str (File/createTempFile "eido-lo" ".jpg"))
          path-hi (str (File/createTempFile "eido-hi" ".jpg"))]
      (eido/render-to-file sample-scene path-lo {:quality 0.1})
      (eido/render-to-file sample-scene path-hi {:quality 1.0})
      (let [lo (File. ^String path-lo)
            hi (File. ^String path-hi)]
        (is (> (.length hi) (.length lo)))
        (.delete lo)
        (.delete hi)))))

(deftest render-to-file-unknown-ext-test
  (testing "throws for unknown extension"
    (is (thrown? clojure.lang.ExceptionInfo
          (eido/render-to-file sample-scene "/tmp/test.xyz")))))

(deftest render-to-file-format-override-test
  (testing ":format option overrides extension"
    (let [path (str (File/createTempFile "eido-test" ".png"))]
      (eido/render-to-file sample-scene path {:format "jpeg"})
      (let [f (File. ^String path)
            img (ImageIO/read f)]
        (is (some? img))
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

;; --- v0.4 file loading tests ---

(defn- write-temp-edn
  "Writes EDN content to a temp file, returns the path."
  [content]
  (let [f (File/createTempFile "eido-test" ".edn")]
    (spit f (pr-str content))
    (.getAbsolutePath f)))

(deftest read-scene-test
  (testing "reads a scene from an EDN file"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes []}
          path (write-temp-edn scene)]
      (is (= scene (eido/read-scene path)))
      (.delete (File. ^String path)))))

(deftest read-scene-missing-file-test
  (testing "throws ex-info for nonexistent file"
    (try
      (eido/read-scene "/tmp/nonexistent-eido-scene-12345.edn")
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (= "/tmp/nonexistent-eido-scene-12345.edn"
               (:path (ex-data e))))))))

(deftest read-scene-invalid-edn-test
  (testing "throws ex-info for invalid EDN"
    (let [path (let [f (File/createTempFile "eido-test" ".edn")]
                 (spit f "{{{invalid")
                 (.getAbsolutePath f))]
      (try
        (eido/read-scene path)
        (is false "should have thrown")
        (catch clojure.lang.ExceptionInfo e
          (is (= path (:path (ex-data e))))))
      (.delete (File. ^String path)))))

(deftest render-file-test
  (testing "renders a scene from an EDN file"
    (let [scene {:image/size [200 150]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [0 0]
                   :rect/size [200 150]
                   :style/fill {:color [:color/rgb 255 0 0]}}]}
          path (write-temp-edn scene)
          img (eido/render-file path)]
      (is (instance? BufferedImage img))
      (is (= 200 (.getWidth img)))
      (is (= 150 (.getHeight img)))
      (is (= [255 0 0] (pixel-rgb img 100 75)))
      (.delete (File. ^String path)))))

;; --- v0.3 integration tests ---

(def path-scene
  {:image/size [200 200]
   :image/background [:color/rgb 255 255 255]
   :image/nodes
   [{:node/type :shape/path
     :path/commands [[:move-to [50 150]]
                     [:line-to [100 30]]
                     [:line-to [150 150]]
                     [:close]]
     :style/fill {:color [:color/rgb 0 200 0]}
     :style/stroke {:color [:color/rgb 0 0 0] :width 2}}]})

(deftest path-integration-test
  (testing "path renders end-to-end from scene EDN"
    (let [img (eido/render path-scene)]
      (is (= 200 (.getWidth img)))
      (is (= 200 (.getHeight img)))
      (is (= [0 200 0] (pixel-rgb img 100 120))
          "inside triangle should be green fill")
      (is (= [255 255 255] (pixel-rgb img 10 10))
          "outside triangle should be background"))))

(def mixed-path-scene
  {:image/size [300 300]
   :image/background [:color/rgb 255 255 255]
   :image/nodes
   [{:node/type :group
     :node/transform [[:transform/translate 150 150]]
     :style/fill {:color [:color/rgb 255 0 0]}
     :node/opacity 0.8
     :group/children
     [{:node/type :shape/circle
       :circle/center [0 0]
       :circle/radius 60}
      {:node/type :shape/path
       :path/commands [[:move-to [-40 40]]
                       [:line-to [0 -40]]
                       [:line-to [40 40]]
                       [:close]]
       :style/fill {:color [:color/rgb 0 0 255]}
       :node/opacity 0.5}]}]})

;; --- v0.4 integration tests ---

(deftest render-file-integration-test
  (testing "EDN file round-trip: write, render-file, verify pixels"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/circle
                   :circle/center [50 50]
                   :circle/radius 30
                   :style/fill {:color [:color/rgb 0 255 0]}}]}
          path (write-temp-edn scene)
          img (eido/render-file path)]
      (is (= [0 255 0] (pixel-rgb img 50 50))
          "center of circle should be green")
      (is (= [0 0 0] (pixel-rgb img 5 5))
          "corner should be background")
      (.delete (File. ^String path)))))

(deftest mixed-path-integration-test
  (testing "path composes with other shapes in groups"
    (let [img (eido/render mixed-path-scene)]
      (is (= [255 255 255] (pixel-rgb img 5 5))
          "corner should be background")
      ;; Circle at (150,150) with inherited red fill
      ;; Path triangle overlaid with blue at 0.4 effective opacity (0.8 * 0.5)
      (let [[r _ b] (pixel-rgb img 150 155)]
        (is (pos? r) "red from circle should be visible")
        (is (pos? b) "blue from path should be blended in")))))
