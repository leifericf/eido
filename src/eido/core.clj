(ns eido.core
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [eido.compile :as compile]
    [eido.render :as render])
  (:import
    [java.awt Color Graphics2D]
    [java.awt.image BufferedImage]
    [java.io File]
    [javax.imageio ImageIO ImageWriteParam]
    [javax.imageio.stream FileImageOutputStream]))

(defn read-scene
  "Reads an EDN file and returns the scene map."
  [path]
  (try
    (edn/read-string (slurp path))
    (catch Exception e
      (throw (ex-info "Failed to read scene file"
                      {:path path}
                      e)))))

(defn render
  "Renders a scene EDN map to a BufferedImage."
  [scene]
  (-> scene compile/compile render/render))

(defn render-file
  "Reads an EDN scene file and renders it to a BufferedImage."
  [path]
  (render (read-scene path)))

(def ^:private format-aliases
  {"jpg" "jpeg"})

(defn- detect-format
  "Detects image format from file extension."
  [path]
  (let [ext (some-> (re-find #"\.([^.]+)$" path) second str/lower-case)]
    (when-not ext
      (throw (ex-info "Cannot detect format from path"
                      {:path path})))
    (get format-aliases ext ext)))

(defn- ensure-rgb
  "Converts an ARGB BufferedImage to RGB by painting onto white."
  [^BufferedImage img]
  (let [w (.getWidth img)
        h (.getHeight img)
        rgb (BufferedImage. w h BufferedImage/TYPE_INT_RGB)
        ^Graphics2D g (.createGraphics rgb)]
    (.setColor g Color/WHITE)
    (.fillRect g 0 0 w h)
    (.drawImage g img 0 0 nil)
    (.dispose g)
    rgb))

(defn- write-jpeg
  "Writes a BufferedImage as JPEG with quality setting."
  [^BufferedImage img ^String path quality]
  (let [writer (.next (ImageIO/getImageWritersByFormatName "jpeg"))
        param (.getDefaultWriteParam writer)]
    (.setCompressionMode param ImageWriteParam/MODE_EXPLICIT)
    (.setCompressionQuality param (float quality))
    (with-open [out (FileImageOutputStream. (File. path))]
      (.setOutput writer out)
      (.write writer nil
              (javax.imageio.IIOImage. img nil nil)
              param)
      (.dispose writer)))
  path)

(defn render-to-file
  "Renders a scene and writes to file. Format detected from extension.
  Opts: :format (override), :quality (0.0-1.0, JPEG only, default 0.75)."
  ([scene path]
   (render-to-file scene path {}))
  ([scene path opts]
   (let [format (or (:format opts) (detect-format path))
         img    (render scene)]
     (case format
       "jpeg" (write-jpeg (ensure-rgb img) path
                          (get opts :quality 0.75))
       "bmp"  (ImageIO/write (ensure-rgb img) "bmp" (File. ^String path))
       ("png" "gif")
       (ImageIO/write img format (File. ^String path))

       (throw (ex-info "Unsupported export format"
                       {:path path :format format})))
     path)))

(comment
  (render {:image/size [800 600]
           :image/background [:color/rgb 255 255 255]
           :image/nodes
           [{:node/type :shape/circle
             :circle/center [400 300]
             :circle/radius 100
             :style/fill {:color [:color/rgb 200 0 0]}}]})

  (render-to-file
    {:image/size [400 300]
     :image/background [:color/rgb 240 240 240]
     :image/nodes
     [{:node/type :shape/rect
       :rect/xy [50 50]
       :rect/size [300 200]
       :style/fill {:color [:color/rgb 0 100 200]}
       :style/stroke {:color [:color/rgb 0 0 0] :width 2}}]}
    "/tmp/eido-test.png")

  ;; v0.3 — path example: star-like shape with curves
  (render {:image/size [400 400]
           :image/background [:color/rgb 30 30 40]
           :image/nodes
           [{:node/type :shape/path
             :path/commands [[:move-to [200 50]]
                             [:line-to [240 160]]
                             [:line-to [350 160]]
                             [:line-to [260 220]]
                             [:line-to [300 340]]
                             [:line-to [200 270]]
                             [:line-to [100 340]]
                             [:line-to [140 220]]
                             [:line-to [50 160]]
                             [:line-to [160 160]]
                             [:close]]
             :style/fill {:color [:color/rgb 255 200 50]}
             :style/stroke {:color [:color/rgb 200 150 0] :width 2}}]})
  )
