(ns eido.core
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [eido.engine.compile :as compile]
    [eido.engine.gif :as gif]
    [eido.engine.render :as render]
    [eido.engine.svg :as svg]
    [eido.io.polyline :as polyline]
    [eido.validate :as validate])
  (:import
    [java.awt Color Graphics2D]
    [java.awt.image BufferedImage]
    [java.io File]
    [javax.imageio ImageIO IIOImage ImageWriteParam]
    [javax.imageio.metadata IIOMetadataNode]
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

(defn validate
  "Validates a scene map against the Eido scene spec.
  Returns nil if valid, or a vector of error maps with
  :path, :pred, :message, and :value."
  [scene]
  (validate/validate scene))

(defn format-errors
  "Formats validation errors as a human-readable string.
  Takes a vector of error maps as returned by validate."
  [errors]
  (validate/format-errors errors))

(defn explain
  "Validates a scene and prints human-readable errors to *out*.
  Returns the error vector, or nil if valid."
  [scene]
  (validate/explain scene))

(def ^:dynamic *validate*
  "Controls whether scene validation runs before compilation.
  Defaults to true. Bind to false in REPL/watch workflows for faster
  re-renders. The `show` and `watch-*` dev helpers automatically skip
  validation after the first successful render."
  true)

(defn- validated-compile
  "Validates then compiles a scene.
  Skips validation if *validate* is false or scene has :eido/validate false."
  [scene]
  (when (and *validate* (not (false? (:eido/validate scene))))
    (compile/validate-scene! scene))
  (compile/compile scene))

(defn- render-image
  "Renders a single scene to a BufferedImage."
  ([scene] (render-image scene {}))
  ([scene opts]
   (render/render (validated-compile scene) opts)))

(defn- render-image-unchecked
  "Renders a scene without validation (for internal use after first frame)."
  ([scene] (render-image-unchecked scene {}))
  ([scene opts]
   (render/render (compile/compile scene) opts)))

(defn render-file
  "Reads an EDN scene file and renders it to a BufferedImage."
  [path]
  (render-image (read-scene path)))

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

(defn render-to-svg
  "Renders a scene to an SVG XML string.
  Opts: :scale, :transparent-background."
  ([scene] (render-to-svg scene {}))
  ([scene opts]
   (svg/render (validated-compile scene)
               (select-keys opts [:scale :transparent-background]))))

(defn render-to-animated-svg-str
  "Renders a sequence of scenes to an animated SVG string using SMIL.
  fps: frames per second.
  Opts: :scale, :transparent-background."
  ([scenes fps] (render-to-animated-svg-str scenes fps {}))
  ([scenes fps opts]
   (let [scene-vec (vec scenes)]
     ;; Validate first frame; skip for rest
     (when (seq scene-vec)
       (compile/validate-scene! (first scene-vec)))
     (let [irs (mapv compile/compile scene-vec)]
       (svg/render-animated irs fps
         (select-keys opts [:scale :transparent-background]))))))

(defn render-to-animated-svg
  "Renders a sequence of scenes to an animated SVG file.
  fps: frames per second.
  Opts: :scale, :transparent-background."
  ([scenes path fps] (render-to-animated-svg scenes path fps {}))
  ([scenes path fps opts]
   (spit path (render-to-animated-svg-str scenes fps opts))
   path))

(defn- write-png-with-dpi
  "Writes PNG with DPI metadata."
  [^BufferedImage img ^String path dpi]
  (let [writer (.next (ImageIO/getImageWritersByFormatName "png"))
        param  (.getDefaultWriteParam writer)
        meta   (.getDefaultImageMetadata writer
                 (javax.imageio.ImageTypeSpecifier/createFromRenderedImage img)
                 param)
        ppm    (int (Math/round (/ (double dpi) 0.0254)))
        phys   (doto (IIOMetadataNode. "pHYs")
                 (.setAttribute "pixelsPerUnitXAxis" (str ppm))
                 (.setAttribute "pixelsPerUnitYAxis" (str ppm))
                 (.setAttribute "unitSpecifier" "meter"))
        root   (let [r (IIOMetadataNode. "javax_imageio_png_1.0")]
                 (.appendChild r phys)
                 r)]
    (.mergeTree meta "javax_imageio_png_1.0" root)
    (with-open [out (FileImageOutputStream. (File. path))]
      (.setOutput writer out)
      (.write writer nil
              (javax.imageio.IIOImage. img nil meta)
              param)
      (.dispose writer)))
  path)

(defn- write-tiff
  "Writes a BufferedImage as TIFF with optional DPI metadata and compression.
  Compression: :lzw, :deflate, :none (default :lzw)."
  [^BufferedImage img ^String path dpi compression]
  (let [writer (.next (ImageIO/getImageWritersByFormatName "tiff"))
        param  (.getDefaultWriteParam writer)
        _      (let [comp (case (or compression :lzw)
                           :lzw     "LZW"
                           :deflate "Deflate"
                           :none    nil)]
                 (when comp
                   (.setCompressionMode param ImageWriteParam/MODE_EXPLICIT)
                   (.setCompressionType param comp)))
        ts     (javax.imageio.ImageTypeSpecifier/createFromRenderedImage img)
        meta   (.getDefaultImageMetadata writer ts param)
        _      (when dpi
                 (let [root (.getAsTree meta "javax_imageio_tiff_image_1.0")
                       ifd  (.item (.getChildNodes root) 0)
                       dpi-str (str (int dpi) "/1")]
                   ;; XResolution (tag 282)
                   (let [field (doto (IIOMetadataNode. "TIFFField")
                                 (.setAttribute "number" "282")
                                 (.setAttribute "name" "XResolution"))
                         rats  (IIOMetadataNode. "TIFFRationals")
                         rat   (doto (IIOMetadataNode. "TIFFRational")
                                 (.setAttribute "value" dpi-str))]
                     (.appendChild rats rat)
                     (.appendChild field rats)
                     (.appendChild ifd field))
                   ;; YResolution (tag 283)
                   (let [field (doto (IIOMetadataNode. "TIFFField")
                                 (.setAttribute "number" "283")
                                 (.setAttribute "name" "YResolution"))
                         rats  (IIOMetadataNode. "TIFFRationals")
                         rat   (doto (IIOMetadataNode. "TIFFRational")
                                 (.setAttribute "value" dpi-str))]
                     (.appendChild rats rat)
                     (.appendChild field rats)
                     (.appendChild ifd field))
                   ;; ResolutionUnit (tag 296) = 2 (inch)
                   (let [field (doto (IIOMetadataNode. "TIFFField")
                                 (.setAttribute "number" "296")
                                 (.setAttribute "name" "ResolutionUnit"))
                         shorts (IIOMetadataNode. "TIFFShorts")
                         short  (doto (IIOMetadataNode. "TIFFShort")
                                  (.setAttribute "value" "2"))]
                     (.appendChild shorts short)
                     (.appendChild field shorts)
                     (.appendChild ifd field))
                   (.mergeTree meta "javax_imageio_tiff_image_1.0" root)))]
    (with-open [out (FileImageOutputStream. (File. path))]
      (.setOutput writer out)
      (.write writer nil (IIOImage. img nil meta) param)
      (.dispose writer)))
  path)

(defn render-to-file
  "Renders a scene and writes to file. Format detected from extension.
  Supported formats: PNG, JPEG, GIF, BMP, TIFF, SVG.
  Opts: :format, :quality (JPEG), :scale, :transparent-background,
        :dpi (PNG/TIFF), :tiff/compression (:lzw :deflate :none)."
  ([scene path]
   (render-to-file scene path {}))
  ([scene path opts]
   (let [format      (or (:format opts) (detect-format path))
         dpi         (or (:dpi opts) (:image/dpi scene))
         render-opts (select-keys opts [:scale :transparent-background])]
     (if (= format "svg")
       (spit path (render-to-svg scene (select-keys opts [:scale :transparent-background])))
       (let [img (render-image scene render-opts)]
         (case format
           "jpeg" (write-jpeg (ensure-rgb img) path
                              (get opts :quality 0.75))
           "bmp"  (ImageIO/write (ensure-rgb img) "bmp" (File. ^String path))
           "png"  (if dpi
                    (write-png-with-dpi img path dpi)
                    (ImageIO/write img "png" (File. ^String path)))
           "gif"   (ImageIO/write img "gif" (File. ^String path))
           ("tiff"
            "tif")  (write-tiff img path dpi (:tiff/compression opts))

           (throw (ex-info "Unsupported export format"
                           {:path path :format format})))))
     path)))

(defn- pad-index
  "Zero-pads an index to the given width."
  [i width]
  (let [s (str i)]
    (if (>= (count s) width)
      s
      (str (apply str (repeat (- width (count s)) "0")) s))))

(defn render-animation
  "Renders a sequence of scenes to numbered PNG files in a directory.
  Returns a vector of written file paths.
  Opts: :scale, :transparent-background, :prefix (default \"frame-\")."
  ([scenes dir] (render-animation scenes dir {}))
  ([scenes dir opts]
   (let [scene-vec   (vec scenes)
         n           (count scene-vec)
         prefix      (get opts :prefix "frame-")
         pad-width   (count (str (dec n)))
         render-opts (select-keys opts [:scale :transparent-background])]
     (.mkdirs (File. ^String dir))
     ;; Validate first frame; skip validation for subsequent frames
     (when (pos? n)
       (compile/validate-scene! (nth scene-vec 0)))
     (mapv (fn [i]
             (let [scene (nth scene-vec i)
                   fname (str prefix (pad-index i pad-width) ".png")
                   path  (str dir "/" fname)
                   img   (render-image-unchecked scene render-opts)]
               (ImageIO/write img "png" (File. ^String path))
               path))
           (range n)))))

(defn render-to-gif
  "Renders a sequence of scenes to an animated GIF.
  fps: frames per second.
  Opts: :scale, :transparent-background, :loop (default true)."
  ([scenes path fps] (render-to-gif scenes path fps {}))
  ([scenes path fps opts]
   (let [render-opts (select-keys opts [:scale :transparent-background])
         scene-vec   (vec scenes)]
     ;; Validate first frame; skip validation for subsequent frames
     (when (seq scene-vec)
       (compile/validate-scene! (first scene-vec)))
     (let [images   (mapv #(render-image-unchecked % render-opts) scene-vec)
           delay-ms (quot 1000 fps)
           loop?    (get opts :loop true)]
       (gif/write-animated-gif images path delay-ms loop?)))))

(defn render-batch
  "Renders a sequence of export jobs to files. Each job is a map with
  :scene, :path, and optional :opts. Returns a vector of written paths.
  With a generator fn and count, calls (f i) for each index."
  ([jobs]
   (mapv (fn [{:keys [scene path opts]}]
           (render-to-file scene path (or opts {})))
         jobs))
  ([generate-fn n]
   (render-batch (map generate-fn (range n)))))

(defn- animation?
  "Returns true if input is a sequence of scenes (not a single scene map)."
  [input]
  (and (sequential? input) (not (map? input))))

(defn- detect-animation-format
  "Detects the output format for an animation from the output path."
  [output]
  (cond
    (str/ends-with? output "/")    :frames
    (str/ends-with? output ".gif") :gif
    (str/ends-with? output ".svg") :svg
    :else (throw (ex-info "Cannot detect animation format from path"
                          {:output output}))))

(defn render
  "Renders a scene or animation.

  Single scene:
    (render scene)                         → BufferedImage
    (render scene {:output \"out.png\"})   → writes file, returns path
    (render scene {:format :svg})          → SVG string
    (render scene {:format :polylines})    → {:polylines [...] :bounds [w h]}

  Animation (sequence of scenes):
    (render frames {:output \"a.gif\" :fps 30})   → animated GIF
    (render frames {:output \"a.svg\" :fps 30})   → animated SVG file
    (render frames {:output \"dir/\" :fps 30})    → PNG sequence
    (render frames {:format :svg :fps 30})        → animated SVG string

  Common opts: :scale, :transparent-background, :quality (JPEG), :dpi (PNG/TIFF),
               :tiff/compression (:lzw :deflate :none),
               :loop (GIF, default true), :prefix (frame sequence).

  Validation: scenes are validated before compilation by default. Bind
  *validate* to false for faster REPL iteration, or set :eido/validate
  false on the scene map."
  ([input] (render input {}))
  ([input opts]
   (let [output (:output opts)
         format (:format opts)
         render-opts (dissoc opts :output :fps :loop :format :prefix)]
     (if (animation? input)
       (let [fps (or (:fps opts)
                     (throw (ex-info "Animation requires :fps" {})))]
         (if output
           (case (detect-animation-format output)
             :gif    (render-to-gif input output fps
                       (merge render-opts (select-keys opts [:loop])))
             :svg    (render-to-animated-svg input output fps render-opts)
             :frames (render-animation input output
                       (merge render-opts (select-keys opts [:prefix]))))
           (if (= :svg format)
             (render-to-animated-svg-str input fps render-opts)
             (mapv #(render-image % render-opts) input))))
       (cond
         (= :polylines format)
         (let [ir   (validated-compile input)
               data (polyline/extract-polylines ir
                      (select-keys opts [:flatness :segments]))]
           (if output
             (do (spit output (polyline/polylines->edn data)) output)
             data))

         output         (render-to-file input output
                          (merge render-opts (when format {:format (name format)})))
         (= :svg format) (render-to-svg input render-opts)
         :else           (render-image input render-opts))))))

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

  ;; TIFF output for archival print
  (render {:image/size [400 300]
           :image/background [:color/rgb 255 255 255]
           :image/nodes
           [{:node/type :shape/circle
             :circle/center [200 150]
             :circle/radius 80
             :style/fill {:color [:color/rgb 200 0 0]}}]}
    {:output "/tmp/eido-test.tiff" :dpi 300 :tiff/compression :lzw})
  )
