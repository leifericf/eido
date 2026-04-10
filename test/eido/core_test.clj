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

(deftest render-to-svg-test
  (testing "renders scene to SVG string"
    (let [svg (eido/render-to-svg sample-scene)]
      (is (string? svg))
      (is (re-find #"<svg" svg))
      (is (re-find #"<circle" svg))
      (is (re-find #"<rect" svg))
      (is (re-find #"</svg>" svg)))))

(deftest render-to-file-svg-test
  (testing "writes SVG file via render-to-file"
    (let [path (str (File/createTempFile "eido-test" ".svg"))]
      (eido/render-to-file sample-scene path)
      (let [f (File. ^String path)
            content (slurp f)]
        (is (.exists f))
        (is (re-find #"<svg" content))
        (.delete f)))))

(deftest render-scale-integration-test
  (testing "scale option produces larger image"
    (let [img (eido/render sample-scene {:scale 2})]
      (is (= 1600 (.getWidth img)))
      (is (= 1200 (.getHeight img))))))

(deftest render-transparent-integration-test
  (testing "transparent background produces transparent pixels"
    (let [img (eido/render
                {:image/size [100 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes []}
                {:transparent-background true})
          argb (.getRGB img 50 50)
          alpha (bit-and (bit-shift-right argb 24) 0xFF)]
      (is (= 0 alpha)))))

(deftest render-to-file-dpi-test
  (testing "PNG with DPI metadata writes successfully"
    (let [path (str (File/createTempFile "eido-dpi" ".png"))]
      (eido/render-to-file sample-scene path {:dpi 300})
      (let [f (File. ^String path)]
        (is (.exists f))
        (is (pos? (.length f)))
        (let [img (ImageIO/read f)]
          (is (= 800 (.getWidth img))))
        (.delete f)))))

(deftest render-to-file-dpi-from-scene-test
  (testing "PNG auto-reads :image/dpi from scene"
    (let [scene (assoc sample-scene :image/dpi 300)
          path  (str (File/createTempFile "eido-auto-dpi" ".png"))]
      (eido/render-to-file scene path)
      (let [f (File. ^String path)]
        (is (.exists f))
        (is (pos? (.length f)))
        (.delete f))))
  (testing "TIFF auto-reads :image/dpi from scene"
    (let [scene (assoc sample-scene :image/dpi 300)
          path  (str (File/createTempFile "eido-auto-dpi" ".tiff"))]
      (eido/render-to-file scene path)
      (let [f (File. ^String path)]
        (is (.exists f))
        (is (pos? (.length f)))
        (.delete f)))))

(deftest render-to-file-tiff-test
  (testing "writes a valid TIFF file"
    (let [path (str (File/createTempFile "eido-test" ".tiff"))]
      (eido/render-to-file sample-scene path)
      (let [f (File. ^String path)
            img (ImageIO/read f)]
        (is (.exists f))
        (is (pos? (.length f)))
        (is (= 800 (.getWidth img)))
        (is (= 600 (.getHeight img)))
        (.delete f)))))

(deftest render-to-file-tif-extension-test
  (testing "writes TIFF with .tif extension"
    (let [path (str (File/createTempFile "eido-test" ".tif"))]
      (eido/render-to-file sample-scene path)
      (let [f (File. ^String path)]
        (is (.exists f))
        (is (pos? (.length f)))
        (.delete f)))))

(deftest render-to-file-tiff-dpi-test
  (testing "TIFF with DPI metadata writes successfully"
    (let [path (str (File/createTempFile "eido-dpi" ".tiff"))]
      (eido/render-to-file sample-scene path {:dpi 300})
      (let [f (File. ^String path)]
        (is (.exists f))
        (is (pos? (.length f)))
        (let [img (ImageIO/read f)]
          (is (= 800 (.getWidth img))))
        (.delete f)))))

(deftest render-to-file-tiff-compression-test
  (testing "TIFF with LZW compression produces smaller file than uncompressed"
    (let [path-lzw (str (File/createTempFile "eido-lzw" ".tiff"))
          path-none (str (File/createTempFile "eido-none" ".tiff"))]
      (eido/render-to-file sample-scene path-lzw {:tiff/compression :lzw})
      (eido/render-to-file sample-scene path-none {:tiff/compression :none})
      (let [lzw (File. ^String path-lzw)
            none (File. ^String path-none)]
        (is (< (.length lzw) (.length none)))
        (.delete lzw)
        (.delete none)))))

(deftest render-polylines-test
  (testing "format :polylines returns polyline data"
    (let [result (eido/render sample-scene {:format :polylines})]
      (is (map? result))
      (is (vector? (:polylines result)))
      (is (= [800 600] (:bounds result)))
      (is (pos? (count (:polylines result))))))
  (testing "format :polylines with output writes EDN file"
    (let [path (str (File/createTempFile "eido-polys" ".edn"))
          result (eido/render sample-scene {:format :polylines :output path})
          f (File. ^String path)]
      (is (= path result))
      (is (.exists f))
      (is (pos? (.length f)))
      (let [data (clojure.edn/read-string (slurp f))]
        (is (vector? (:polylines data)))
        (is (= [800 600] (:bounds data))))
      (.delete f))))

(deftest render-polylines-empty-scene-test
  (testing "polylines format with no nodes returns empty polylines"
    (let [result (eido/render {:image/size [100 100]
                               :image/background [:color/rgb 255 255 255]
                               :image/nodes []}
                              {:format :polylines})]
      (is (= [] (:polylines result)))
      (is (= [100 100] (:bounds result))))))

(deftest render-polylines-animation-throws-test
  (testing "polylines format rejects animation input"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"does not support animations"
          (eido/render
            [{:image/size [100 100]
              :image/background [:color/rgb 0 0 0]
              :image/nodes []}
             {:image/size [100 100]
              :image/background [:color/rgb 0 0 0]
              :image/nodes []}]
            {:format :polylines :fps 30})))))

(deftest render-to-file-tiff-format-override-test
  (testing ":format tiff overrides extension"
    (let [path (str (File/createTempFile "eido-test" ".png"))]
      (eido/render-to-file sample-scene path {:format "tiff"})
      (let [f (File. ^String path)
            img (ImageIO/read f)]
        (is (some? img))
        (is (= 800 (.getWidth img)))
        (.delete f)))))

(deftest render-batch-test
  (testing "renders multiple jobs to files"
    (let [scene {:image/size [50 50]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes []}
          p1 (str (File/createTempFile "batch1" ".png"))
          p2 (str (File/createTempFile "batch2" ".png"))
          result (eido/render-batch [{:scene scene :path p1}
                                     {:scene scene :path p2}])]
      (is (= [p1 p2] result))
      (is (.exists (File. ^String p1)))
      (is (.exists (File. ^String p2)))
      (.delete (File. ^String p1))
      (.delete (File. ^String p2)))))

(deftest render-batch-empty-test
  (testing "empty jobs returns empty vector"
    (is (= [] (eido/render-batch [])))))

(deftest render-batch-generator-test
  (testing "generator fn produces files"
    (let [paths (atom [])
          result (eido/render-batch
                   (fn [i]
                     (let [p (str (File/createTempFile (str "gen" i) ".png"))]
                       (swap! paths conj p)
                       {:scene {:image/size [10 10]
                                :image/background [:color/rgb 0 0 0]
                                :image/nodes []}
                        :path p}))
                   3)]
      (is (= 3 (count result)))
      (doseq [p @paths]
        (is (.exists (File. ^String p)))
        (.delete (File. ^String p))))))

(deftest render-to-file-svg-transparent-test
  (testing "SVG with transparent-background omits background rect"
    (let [path (str (File/createTempFile "eido-test" ".svg"))]
      (eido/render-to-file
        {:image/size [100 100]
         :image/background [:color/rgb 255 255 255]
         :image/nodes
         [{:node/type :shape/circle
           :circle/center [50 50]
           :circle/radius 20
           :style/fill {:color [:color/rgb 255 0 0]}}]}
        path {:transparent-background true})
      (let [content (slurp path)]
        (is (re-find #"<circle" content))
        ;; Should have only one element (the circle), no bg rect
        (is (= 1 (count (re-seq #"<circle" content))))
        ;; The bg rect would have width="100" height="100" — should not appear
        (is (not (re-find #"width=\"100\" height=\"100\" fill=\"rgb" content))))
      (.delete (File. ^String path)))))

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

;; --- render-animation ---

(def ^:private simple-frames
  (mapv (fn [i]
          {:image/size [100 100]
           :image/background [:color/rgb 0 0 0]
           :image/nodes
           [{:node/type :shape/circle
             :circle/center [50 50]
             :circle/radius (+ 10 (* i 10))
             :style/fill {:color [:color/rgb 255 0 0]}}]})
    (range 3)))

(deftest render-animation-test
  (testing "renders correct number of files"
    (let [dir (str (File/createTempFile "eido-anim" "") ".d")]
      (.mkdirs (File. dir))
      (let [paths (eido/render-animation simple-frames dir)]
        (is (= 3 (count paths)))
        (doseq [p paths]
          (is (.exists (File. ^String p))))
        (doseq [p paths] (.delete (File. ^String p)))
        (.delete (File. dir)))))

  (testing "files are named with prefix and index"
    (let [dir (str (File/createTempFile "eido-anim" "") ".d")]
      (.mkdirs (File. dir))
      (let [paths (eido/render-animation simple-frames dir)]
        (is (= (str dir "/frame-0.png") (first paths)))
        (is (= (str dir "/frame-2.png") (last paths)))
        (doseq [p paths] (.delete (File. ^String p)))
        (.delete (File. dir)))))

  (testing "custom prefix"
    (let [dir (str (File/createTempFile "eido-anim" "") ".d")]
      (.mkdirs (File. dir))
      (let [paths (eido/render-animation simple-frames dir {:prefix "f-"})]
        (is (= (str dir "/f-0.png") (first paths)))
        (doseq [p paths] (.delete (File. ^String p)))
        (.delete (File. dir)))))

  (testing "padding for larger frame counts"
    (let [dir    (str (File/createTempFile "eido-anim" "") ".d")
          frames (repeat 100 (first simple-frames))]
      (.mkdirs (File. dir))
      (let [paths (eido/render-animation frames dir)]
        (is (= (str dir "/frame-00.png") (first paths)))
        (is (= (str dir "/frame-99.png") (last paths)))
        (doseq [p paths] (.delete (File. ^String p)))
        (.delete (File. dir)))))

  (testing "rendered images have correct dimensions"
    (let [dir (str (File/createTempFile "eido-anim" "") ".d")]
      (.mkdirs (File. dir))
      (let [paths (eido/render-animation simple-frames dir)
            img   (ImageIO/read (File. ^String (first paths)))]
        (is (= 100 (.getWidth img)))
        (is (= 100 (.getHeight img)))
        (doseq [p paths] (.delete (File. ^String p)))
        (.delete (File. dir))))))

;; --- render-to-gif ---

(deftest render-to-gif-test
  (testing "writes a valid animated GIF"
    (let [path (str (File/createTempFile "eido-gif" ".gif"))]
      (eido/render-to-gif simple-frames path {:fps 30})
      (is (.exists (File. path)))
      (is (pos? (.length (File. path))))
      (.delete (File. path))))

  (testing "GIF is readable with correct dimensions"
    (let [path (str (File/createTempFile "eido-gif" ".gif"))]
      (eido/render-to-gif simple-frames path {:fps 10})
      (let [img (ImageIO/read (File. path))]
        (is (= 100 (.getWidth img)))
        (is (= 100 (.getHeight img))))
      (.delete (File. path))))

  (testing "supports render opts"
    (let [path (str (File/createTempFile "eido-gif" ".gif"))]
      (eido/render-to-gif simple-frames path {:fps 30 :scale 2})
      (let [img (ImageIO/read (File. path))]
        (is (= 200 (.getWidth img)))
        (is (= 200 (.getHeight img))))
      (.delete (File. path)))))

;; --- animated SVG ---

(deftest render-to-animated-svg-str-test
  (testing "produces SVG string with animation"
    (let [out (eido/render-to-animated-svg-str simple-frames {:fps 10})]
      (is (string? out))
      (is (re-find #"<svg" out))
      (is (re-find #"<animate" out))
      (is (re-find #"</svg>" out))))

  (testing "contains all frames"
    (let [out (eido/render-to-animated-svg-str simple-frames {:fps 10})]
      (is (= 3 (count (re-seq #"<g " out))))))

  (testing "scale option works"
    (let [out (eido/render-to-animated-svg-str simple-frames {:fps 10 :scale 2})]
      (is (re-find #"width=\"200\"" out))
      (is (re-find #"height=\"200\"" out)))))

(deftest render-to-animated-svg-test
  (testing "writes animated SVG to file"
    (let [path (str (File/createTempFile "eido-asvg" ".svg"))]
      (eido/render-to-animated-svg simple-frames path {:fps 10})
      (is (.exists (File. path)))
      (let [content (slurp path)]
        (is (re-find #"<svg" content))
        (is (re-find #"<animate" content)))
      (.delete (File. path)))))

;; --- edge cases ---

(deftest single-frame-animation-test
  (testing "render-to-gif with single frame"
    (let [path (str (File/createTempFile "eido-1f" ".gif"))]
      (eido/render-to-gif [(first simple-frames)] path {:fps 10})
      (is (.exists (File. path)))
      (.delete (File. path))))

  (testing "render-animation with single frame"
    (let [dir (str (File/createTempFile "eido-1f" "") ".d")]
      (.mkdirs (File. dir))
      (let [paths (eido/render-animation [(first simple-frames)] dir)]
        (is (= 1 (count paths)))
        (doseq [p paths] (.delete (File. ^String p)))
        (.delete (File. dir)))))

  (testing "render-to-animated-svg-str with single frame"
    (let [out (eido/render-to-animated-svg-str [(first simple-frames)] {:fps 10})]
      (is (re-find #"<svg" out))
      (is (= 1 (count (re-seq #"<g " out)))))))

(deftest version-key-roundtrip-test
  (testing "scene with version renders normally"
    (let [scene {:eido/version "1.0"
                 :image/size [100 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes [{:node/type :shape/circle
                                :circle/center [50 50]
                                :circle/radius 30
                                :style/fill {:color [:color/rgb 200 0 0]}}]}
          img (eido/render scene)]
      (is (= 100 (.getWidth img)))
      (is (= 100 (.getHeight img)))))

  (testing "scene with version validates"
    (is (nil? (eido/validate {:eido/version "1.0"
                              :image/size [100 100]
                              :image/background [:color/rgb 0 0 0]
                              :image/nodes []})))))

;; --- unified render tests ---

(deftest unified-render-image-test
  (testing "scene without opts returns BufferedImage"
    (let [img (eido/render sample-scene)]
      (is (instance? BufferedImage img))
      (is (= 800 (.getWidth img)))))
  (testing "scene with :scale returns scaled BufferedImage"
    (let [img (eido/render sample-scene {:scale 2})]
      (is (= 1600 (.getWidth img))))))

(deftest unified-render-to-file-test
  (testing "scene with :output writes file"
    (let [path (str (File/createTempFile "eido-uni" ".png"))]
      (eido/render sample-scene {:output path})
      (let [f (File. ^String path)]
        (is (.exists f))
        (is (pos? (.length f)))
        (.delete f))))
  (testing "scene with :output .svg writes SVG"
    (let [path (str (File/createTempFile "eido-uni" ".svg"))]
      (eido/render sample-scene {:output path})
      (let [content (slurp path)]
        (is (re-find #"<svg" content)))
      (.delete (File. ^String path)))))

(deftest unified-render-svg-string-test
  (testing "scene with :format :svg returns SVG string"
    (let [svg (eido/render sample-scene {:format :svg})]
      (is (string? svg))
      (is (re-find #"<svg" svg)))))

(deftest unified-render-gif-test
  (testing "frames with :output .gif writes animated GIF"
    (let [path (str (File/createTempFile "eido-uni" ".gif"))]
      (eido/render simple-frames {:output path :fps 10})
      (let [f (File. ^String path)]
        (is (.exists f))
        (is (pos? (.length f)))
        (.delete f)))))

(deftest unified-render-animated-svg-test
  (testing "frames with :output .svg writes animated SVG"
    (let [path (str (File/createTempFile "eido-uni" ".svg"))]
      (eido/render simple-frames {:output path :fps 10})
      (let [content (slurp path)]
        (is (re-find #"<svg" content))
        (is (re-find #"<animate" content)))
      (.delete (File. ^String path))))
  (testing "frames with :format :svg returns animated SVG string"
    (let [svg (eido/render simple-frames {:format :svg :fps 10})]
      (is (string? svg))
      (is (re-find #"<animate" svg)))))

(deftest unified-render-frames-test
  (testing "frames with :output dir/ writes PNG sequence"
    (let [dir (str (File/createTempFile "eido-uni" "") ".d/")]
      (.mkdirs (File. dir))
      (let [paths (eido/render simple-frames {:output dir :fps 10})]
        (is (= 3 (count paths)))
        (doseq [p paths] (.delete (File. ^String p)))
        (.delete (File. dir))))))

(deftest unified-render-requires-fps-test
  (testing "animation without :fps throws"
    (is (thrown? clojure.lang.ExceptionInfo
          (eido/render simple-frames {:output "/tmp/test.gif"})))))
