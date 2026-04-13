(ns eido.integration.workflow-test
  "End-to-end workflow tests — exercises complete render pipelines
  and verifies output structure (not pixel values)."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [eido.animate :as anim]
    [eido.color :as color]
    [eido.color.palette :as palette]
    [eido.core :as eido]
    [eido.gen.series :as series]
    [eido.io.plotter :as plotter]
    [eido.manifest :as manifest]
    [eido.scene :as scene]
    [eido.scene3d :as s3d])
  (:import
    [java.awt.image BufferedImage]
    [java.io File]
    [javax.imageio ImageIO ImageReader]
    [javax.imageio.stream FileImageInputStream]))

;; --- helpers ---

(defn- tmp-path [ext]
  (str (File/createTempFile "eido-wf-" ext)))

(defn- tmp-dir []
  (let [d (File/createTempFile "eido-wf-" "")]
    (.delete d)
    (.mkdirs d)
    (str d)))

(defn- delete-dir! [path]
  (let [dir (io/file path)]
    (doseq [f (reverse (file-seq dir))]
      (.delete f))))

(defn- gif-frame-count [path]
  (let [readers (ImageIO/getImageReadersByFormatName "gif")]
    (when (.hasNext readers)
      (let [^ImageReader reader (.next readers)]
        (try
          (.setInput reader (FileImageInputStream. (File. ^String path)))
          (.getNumImages reader true)
          (finally
            (.dispose reader)))))))

(def simple-scene
  {:image/size [100 100]
   :image/background [:color/rgb 240 240 240]
   :image/nodes
   [{:node/type     :shape/circle
     :circle/center [50 50]
     :circle/radius 30
     :style/fill    [:color/rgb 200 50 50]}]})

(def simple-frames
  (anim/frames 5
    (fn [t]
      {:image/size [60 60]
       :image/background [:color/rgb 30 30 40]
       :image/nodes
       [{:node/type     :shape/circle
         :circle/center [30 30]
         :circle/radius (* 20 (inc t))
         :style/fill    [:color/hsl (* 360 t) 0.8 0.5]}]})))

;; --- workflow tests ---

(deftest sketching-workflow-test
  (let [img (eido/render simple-scene)]
    (is (instance? BufferedImage img))
    (is (= 100 (.getWidth img)))
    (is (= 100 (.getHeight img)))))

(deftest editions-workflow-test
  (let [dir (tmp-dir)]
    (try
      (let [result (series/export-edition-package
                     {:spec {:hue {:type :uniform :lo 0.0 :hi 360.0}}
                      :master-seed 42
                      :start 0 :end 3
                      :scene-fn (fn [params _ed]
                                  {:image/size [20 20]
                                   :image/background [:color/hsl (:hue params) 0.5 0.9]
                                   :image/nodes []})
                      :output-dir dir
                      :contact-cols 3
                      :thumb-size [20 20]})]
        (testing "edition files exist"
          (doseq [e (:editions result)]
            (is (.exists (io/file (:file e))))))
        (testing "per-edition manifests exist and are parseable"
          (doseq [e (:editions result)]
            (let [m-path (manifest/manifest-path (:file e))
                  m      (manifest/read-manifest m-path)]
              (is (map? (:scene m)))
              (is (some? (:seed m))))))
        (testing "contact sheet exists"
          (is (.exists (io/file (:contact-sheet result))))))
      (finally
        (delete-dir! dir)))))

(deftest plotter-workflow-test
  (let [dir   (tmp-dir)
        scene {:image/size [200 200]
               :image/background [:color/rgb 255 255 255]
               :image/nodes
               [{:node/type :shape/circle
                 :circle/center [60 60] :circle/radius 30
                 :style/stroke {:color [:color/rgb 255 0 0] :width 2}}
                {:node/type :shape/circle
                 :circle/center [140 140] :circle/radius 30
                 :style/stroke {:color [:color/rgb 0 0 255] :width 2}}]}]
    (try
      (let [results (plotter/export-layers scene dir {})]
        (testing "one file per pen"
          (is (= 2 (count results))))
        (testing "each is valid SVG without fills"
          (doseq [{:keys [file]} results]
            (let [svg (slurp file)]
              (is (str/starts-with? svg "<svg"))
              (is (not (re-find #"fill=\"rgb" svg))))))
        (testing "preview exists"
          (is (.exists (io/file dir "preview.svg")))))
      (finally
        (delete-dir! dir)))))

(deftest print-workflow-test
  (let [path (tmp-path ".tiff")]
    (try
      (let [paper (scene/paper :a4)
            s     (-> paper
                      (assoc :image/background :white
                             :image/nodes
                             [{:node/type     :shape/circle
                               :circle/center [10.5 14.85]
                               :circle/radius 3.0
                               :style/fill    [:color/rgb 200 50 50]}])
                      scene/with-units)]
        (eido/render s {:output path})
        (testing "TIFF file exists and has content"
          (is (.exists (io/file path)))
          (is (> (.length (io/file path)) 1000))))
      (finally
        (io/delete-file path true)))))

(deftest animation-workflow-test
  (let [path (tmp-path ".gif")]
    (try
      (eido/render simple-frames {:output path :fps 10})
      (testing "GIF exists"
        (is (.exists (io/file path))))
      (testing "has correct frame count"
        (is (= 5 (gif-frame-count path))))
      (finally
        (io/delete-file path true)))))

(deftest three-d-workflow-test
  (let [path (tmp-path ".png")]
    (try
      (let [proj  (s3d/perspective {:fov 60 :near 0.1 :far 100
                                    :width 200 :height 200})
            cam   (s3d/orbit proj [0 0 0]
                    {:radius 4 :yaw 0.5 :pitch -0.3})
            nodes (s3d/sphere cam [0 0 0]
                    {:radius 1.5
                     :style {:style/fill [:color/rgb 100 150 255]}
                     :subdivisions 2})
            s     {:image/size [200 200]
                   :image/background [:color/rgb 30 30 40]
                   :image/nodes (if (sequential? nodes) (vec nodes) [nodes])}]
        (eido/render s {:output path})
        (testing "3D render produces PNG"
          (is (.exists (io/file path)))
          (let [img (ImageIO/read (io/file path))]
            (is (= 200 (.getWidth img))))))
      (finally
        (io/delete-file path true)))))

(deftest color-workflow-test
  (let [path (tmp-path ".png")]
    (try
      (let [base [:color/hsl 200 0.7 0.5]
            pal  (palette/analogous base 5)
            s    {:image/size [250 50]
                  :image/background [:color/rgb 240 240 240]
                  :image/nodes
                  (vec (map-indexed
                         (fn [i c]
                           {:node/type :shape/rect
                            :rect/xy [(+ 5 (* i 50)) 5]
                            :rect/size [40 40]
                            :style/fill c})
                         pal))}]
        (eido/render s {:output path})
        (testing "palette scene renders"
          (is (.exists (io/file path)))))
      (finally
        (io/delete-file path true)))))

;; --- I/O format tests ---

(deftest png-dpi-test
  (let [path (tmp-path ".png")]
    (try
      (eido/render simple-scene {:output path :dpi 300})
      (testing "PNG with DPI exists"
        (is (.exists (io/file path)))
        (is (> (.length (io/file path)) 100)))
      (finally
        (io/delete-file path true)))))

(deftest svg-static-test
  (let [svg (eido/render simple-scene {:format :svg})]
    (testing "returns SVG string"
      (is (string? svg))
      (is (str/starts-with? svg "<svg"))
      (is (str/includes? svg "</svg>"))
      (is (re-find #"circle|ellipse|path" svg)))))

(deftest tiff-compression-test
  (let [paths (vec (repeatedly 3 #(tmp-path ".tiff")))]
    (try
      (let [[lzw deflate none] paths]
        (eido/render simple-scene {:output lzw :tiff/compression :lzw})
        (eido/render simple-scene {:output deflate :tiff/compression :deflate})
        (eido/render simple-scene {:output none :tiff/compression :none})
        (testing "all TIFF files exist"
          (doseq [p paths]
            (is (.exists (io/file p)))))
        (testing "uncompressed is larger than compressed"
          (is (> (.length (io/file none))
                 (.length (io/file lzw))))))
      (finally
        (doseq [p paths]
          (io/delete-file p true))))))

(deftest jpeg-quality-test
  (let [high-path (tmp-path ".jpg")
        low-path  (tmp-path ".jpg")]
    (try
      (eido/render simple-scene {:output high-path :quality 0.95})
      (eido/render simple-scene {:output low-path :quality 0.1})
      (testing "both exist"
        (is (.exists (io/file high-path)))
        (is (.exists (io/file low-path))))
      (testing "high quality is larger"
        (is (> (.length (io/file high-path))
               (.length (io/file low-path)))))
      (finally
        (io/delete-file high-path true)
        (io/delete-file low-path true)))))

(deftest polyline-export-test
  (let [scene {:image/size [200 200]
               :image/background [:color/rgb 255 255 255]
               :image/nodes
               [{:node/type :shape/rect
                 :rect/xy [10 10] :rect/size [80 80]
                 :style/stroke {:color [:color/rgb 0 0 0] :width 1}}]}
        data  (eido/render scene {:format :polylines})]
    (testing "returns polyline data"
      (is (map? data))
      (is (seq (:polylines data)))
      (is (= [200 200] (:bounds data))))
    (testing "EDN roundtrip"
      (let [path (tmp-path ".edn")]
        (try
          (eido/render scene {:format :polylines :output path})
          (let [read-back (edn/read-string (slurp path))]
            (is (seq (:polylines read-back)))
            (is (= [200 200] (:bounds read-back))))
          (finally
            (io/delete-file path true)))))))

(deftest animated-svg-test
  (let [svg (eido/render simple-frames {:format :svg :fps 10})]
    (testing "returns animated SVG string"
      (is (string? svg))
      (is (str/starts-with? svg "<svg"))
      (is (or (str/includes? svg "animate")
              (str/includes? svg "visibility")
              (str/includes? svg "keyTimes"))))))

(deftest png-sequence-test
  (let [dir (tmp-dir)]
    (try
      (eido/render simple-frames {:output (str dir "/") :fps 10})
      (testing "PNG files exist for each frame"
        (let [files (->> (io/file dir)
                         (.listFiles)
                         (filter #(str/ends-with? (.getName %) ".png")))]
          (is (= 5 (count files)))))
      (finally
        (delete-dir! dir)))))

(def ^:private two-color-stroke-scene
  {:image/size [200 200]
   :image/background [:color/rgb 255 255 255]
   :image/nodes
   [{:node/type :shape/rect
     :rect/xy [10 10] :rect/size [80 80]
     :style/stroke {:color [:color/rgb 255 0 0] :width 1}}
    {:node/type :shape/circle
     :circle/center [140 140] :circle/radius 30
     :style/stroke {:color [:color/rgb 0 0 255] :width 1}}]})

(deftest dxf-export-test
  (testing ":format :dxf returns a DXF R12 string"
    (let [out (eido/render two-color-stroke-scene {:format :dxf})]
      (is (string? out))
      (is (str/includes? out "AC1009") "R12 header")
      (is (str/includes? out "POLYLINE"))
      (is (str/ends-with? out "EOF\n"))
      (is (str/includes? out "pen-255-0-0"))
      (is (str/includes? out "pen-0-0-255"))))
  (testing ":output \"…dxf\" writes a DXF file"
    (let [path (tmp-path ".dxf")]
      (try
        (eido/render two-color-stroke-scene {:output path})
        (let [content (slurp path)]
          (is (str/includes? content "AC1009"))
          (is (str/includes? content "POLYLINE"))
          (is (str/ends-with? content "EOF\n")))
        (finally
          (io/delete-file path true)))))
  (testing "writer options (:scale, :optimize-travel) flow through render"
    (let [out (eido/render two-color-stroke-scene
                {:format :dxf :scale 2.0 :optimize-travel false})]
      (is (str/includes? out "20.000000")
          "10 * scale 2.0 = 20 appears as a vertex coordinate"))))

(deftest gcode-export-test
  (testing ":format :gcode returns a GRBL G-code string"
    (let [out (eido/render two-color-stroke-scene {:format :gcode})]
      (is (string? out))
      (is (str/includes? out "G21") "millimetres header")
      (is (str/includes? out "G90") "absolute mode")
      (is (str/includes? out "M3 S1000") "spindle on by default")
      (is (str/includes? out "pen-rgb-255-0-0"))
      (is (str/includes? out "pen-rgb-0-0-255"))
      (is (str/ends-with? out "G0 X0 Y0 ; park\n"))))
  (testing ":output \"…gcode\" writes a G-code file"
    (let [path (tmp-path ".gcode")]
      (try
        (eido/render two-color-stroke-scene {:output path})
        (let [content (slurp path)]
          (is (str/includes? content "G21"))
          (is (str/includes? content "M0"))
          (is (str/ends-with? content "G0 X0 Y0 ; park\n")))
        (finally
          (io/delete-file path true)))))
  (testing "writer options (:feed, :laser-mode) flow through render"
    (let [m4 (eido/render two-color-stroke-scene
               {:format :gcode :laser-mode true :feed 500})]
      (is (str/includes? m4 "M4 S1000") "laser-mode swaps M3 → M4")
      (is (str/includes? m4 "F500")     ":feed reaches the writer"))))

(deftest hpgl-export-test
  (testing ":format :hpgl returns an HPGL string"
    (let [out (eido/render two-color-stroke-scene {:format :hpgl})]
      (is (string? out))
      (is (str/includes? out "IN;")  "initialize plotter")
      (is (str/includes? out "PA;")  "absolute coordinates")
      (is (str/includes? out "SP1;") "first pen selected")
      (is (str/includes? out "SP2;") "second pen selected")
      (is (re-find #"PU\d+,\d+;PD" out)
          "pen-up move into pen-down draw")
      (is (str/ends-with? out "SP0;\n") "deselects pen at end")))
  (testing ":output \"…hpgl\" writes an HPGL file"
    (let [path (tmp-path ".hpgl")]
      (try
        (eido/render two-color-stroke-scene {:output path})
        (let [content (slurp path)]
          (is (str/includes? content "IN;"))
          (is (str/includes? content "SP1;"))
          (is (str/ends-with? content "SP0;\n")))
        (finally
          (io/delete-file path true)))))
  (testing "writer options (:scale, :optimize-travel) flow through render"
    (let [out (eido/render two-color-stroke-scene
                {:format :hpgl :scale 1 :optimize-travel false})]
      ;; rect at x=10..90 with scale 1 → 10..90 in plotter units
      (is (str/includes? out "PU10,") "scale 1 keeps raw coords"))))
