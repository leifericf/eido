(ns eido.core
  "Rendering and export — the main entry point for Eido.

  Use `render` for one-stop rendering of scenes and animations to images,
  SVG, GIF, or polyline data. Specialized functions (`render-to-file`,
  `render-to-svg`, etc.) are also available for direct use."
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [eido.engine.compile :as compile]
    [eido.engine.gif :as gif]
    [eido.engine.render :as render]
    [eido.engine.svg :as svg]
    [eido.io.dxf :as dxf]
    [eido.io.gcode :as gcode]
    [eido.io.hpgl :as hpgl]
    [eido.io.polyline :as polyline]
    [eido.manifest :as manifest]
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
  Opts: :scale, :transparent-background, :stroke-only, :group-by-stroke,
        :deduplicate, :optimize-travel."
  ([scene] (render-to-svg scene {}))
  ([scene opts]
   (svg/render (validated-compile scene)
               (select-keys opts [:scale :transparent-background
                                  :stroke-only :group-by-stroke
                                  :deduplicate :optimize-travel]))))

(defn render-to-animated-svg-str
  "Renders a sequence of scenes to an animated SVG string using SMIL.
  Opts: :fps (required), :scale, :transparent-background."
  ([scenes opts]
   (let [fps (or (:fps opts)
                 (throw (ex-info "render-to-animated-svg-str requires :fps in opts" {})))
         _   (when-not (pos? fps)
               (throw (ex-info "render-to-animated-svg-str :fps must be positive" {:fps fps})))
         scene-vec (vec scenes)]
     ;; Validate first frame; skip for rest
     (when (seq scene-vec)
       (compile/validate-scene! (first scene-vec)))
     (let [irs (mapv compile/compile scene-vec)]
       (svg/render-animated irs fps
         (select-keys opts [:scale :transparent-background]))))))

(defn render-to-animated-svg
  "Renders a sequence of scenes to an animated SVG file.
  Opts: :fps (required), :scale, :transparent-background."
  ([scenes path opts]
   (spit path (render-to-animated-svg-str scenes opts))
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

(defn- add-tiff-rational-field
  "Appends a TIFFRational field to a TIFF IFD node."
  [ifd number name value]
  (let [field (doto (IIOMetadataNode. "TIFFField")
                (.setAttribute "number" (str number))
                (.setAttribute "name" name))
        rats  (IIOMetadataNode. "TIFFRationals")
        rat   (doto (IIOMetadataNode. "TIFFRational")
                (.setAttribute "value" value))]
    (.appendChild rats rat)
    (.appendChild field rats)
    (.appendChild ifd field)))

(defn- add-tiff-short-field
  "Appends a TIFFShort field to a TIFF IFD node."
  [ifd number name value]
  (let [field (doto (IIOMetadataNode. "TIFFField")
                (.setAttribute "number" (str number))
                (.setAttribute "name" name))
        shorts (IIOMetadataNode. "TIFFShorts")
        short  (doto (IIOMetadataNode. "TIFFShort")
                 (.setAttribute "value" (str value)))]
    (.appendChild shorts short)
    (.appendChild field shorts)
    (.appendChild ifd field)))

(defn- set-tiff-dpi
  "Sets DPI metadata on a TIFF image metadata object."
  [meta dpi]
  (let [root    (.getAsTree meta "javax_imageio_tiff_image_1.0")
        ifd     (.item (.getChildNodes root) 0)
        dpi-str (str (int dpi) "/1")]
    (add-tiff-rational-field ifd 282 "XResolution" dpi-str)
    (add-tiff-rational-field ifd 283 "YResolution" dpi-str)
    (add-tiff-short-field ifd 296 "ResolutionUnit" 2)
    (.mergeTree meta "javax_imageio_tiff_image_1.0" root)))

(defn- write-tiff
  "Writes a BufferedImage as TIFF with optional DPI metadata and compression.
  Compression: :lzw, :deflate, :none (default :lzw)."
  [^BufferedImage img ^String path dpi compression]
  (let [writer (.next (ImageIO/getImageWritersByFormatName "tiff"))
        param  (.getDefaultWriteParam writer)]
    (when-let [comp (case (or compression :lzw)
                      :lzw     "LZW"
                      :deflate "Deflate"
                      :none    nil)]
      (.setCompressionMode param ImageWriteParam/MODE_EXPLICIT)
      (.setCompressionType param comp))
    (let [ts   (javax.imageio.ImageTypeSpecifier/createFromRenderedImage img)
          meta (.getDefaultImageMetadata writer ts param)]
      (when dpi
        (set-tiff-dpi meta dpi))
      (with-open [out (FileImageOutputStream. (File. path))]
        (.setOutput writer out)
        (.write writer nil (IIOImage. img nil meta) param)
        (.dispose writer))))
  path)

(defn render-to-file
  "Renders a scene and writes to file. Format detected from extension.
  Supported formats: PNG, JPEG, GIF, BMP, TIFF, SVG, DXF, G-code, HPGL.
  Opts: :format, :quality (JPEG), :scale, :transparent-background,
        :dpi (PNG/TIFF), :tiff/compression (:lzw :deflate :none),
        :emit-manifest? (write EDN sidecar for reproducibility).
  DXF/G-code/HPGL also accept :flatness, :segments, :optimize-travel,
  and any writer-specific keys (see eido.io.dxf, eido.io.gcode,
  eido.io.hpgl)."
  ([scene path]
   (render-to-file scene path {}))
  ([scene path opts]
   (let [format      (or (:format opts) (detect-format path))
         dpi         (or (:dpi opts) (:image/dpi scene))
         render-opts (select-keys opts [:scale :transparent-background])]
     (case format
       "svg"   (spit path (render-to-svg scene opts))
       "dxf"   (spit path (dxf/write-dxf (validated-compile scene) opts))
       "gcode" (spit path (gcode/write-gcode (validated-compile scene) opts))
       "hpgl"  (spit path (hpgl/write-hpgl (validated-compile scene) opts))
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
     (when (:emit-manifest? opts)
       (manifest/write-manifest!
         {:scene       scene
          :output-path path
          :render-opts opts
          :seed        (:seed opts)
          :params      (:params opts)
          ;; Motion-stream formats silently drop fills/effects; surface
          ;; the loss in the sidecar so downstream tooling can flag it.
          :dropped     (when (#{"dxf" "gcode" "hpgl"} format)
                         (polyline/summarize-drops
                           (validated-compile scene)))}))
     path)))

(defn render-from-manifest
  "Reads a manifest EDN file and re-renders the scene using stored options.
  Override keys in opts to change output path, format, etc."
  ([path]
   (manifest/render-from-manifest path))
  ([path override-opts]
   (manifest/render-from-manifest path override-opts)))

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
  Opts: :fps (required), :scale, :transparent-background, :loop (default true)."
  ([scenes path opts]
   (let [fps (or (:fps opts)
                 (throw (ex-info "render-to-gif requires :fps in opts" {})))
         _   (when-not (pos? fps)
               (throw (ex-info "render-to-gif :fps must be positive" {:fps fps})))
         render-opts (select-keys opts [:scale :transparent-background])
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
    (render scene {:format :dxf})          → DXF R12 string
    (render scene {:format :gcode})        → GRBL G-code string
    (render scene {:format :hpgl})         → HPGL plotter string
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
     (when (and (animation? input)
                (contains? #{:polylines :dxf :gcode :hpgl} format))
       (throw (ex-info (str (name format)
                            " export does not support animations")
                       {:format format})))
     (if (animation? input)
       (let [fps (or (:fps opts)
                     (throw (ex-info "Animation requires :fps" {})))]
         (if output
           (case (detect-animation-format output)
             :gif    (render-to-gif input output
                       (merge render-opts {:fps fps} (select-keys opts [:loop])))
             :svg    (render-to-animated-svg input output
                       (merge render-opts {:fps fps}))
             :frames (render-animation input output
                       (merge render-opts (select-keys opts [:prefix]))))
           (if (= :svg format)
             (render-to-animated-svg-str input (merge render-opts {:fps fps}))
             (mapv #(render-image % render-opts) input))))
       (cond
         (= :polylines format)
         (let [ir   (validated-compile input)
               data (polyline/extract-polylines ir
                      (select-keys opts [:flatness :segments]))]
           (if output
             (do (spit output (polyline/polylines->edn data)) output)
             data))

         output            (render-to-file input output
                             (merge render-opts (when format {:format (name format)})))
         (= :svg format)   (render-to-svg input render-opts)
         (= :dxf format)   (dxf/write-dxf (validated-compile input) render-opts)
         (= :gcode format) (gcode/write-gcode (validated-compile input) render-opts)
         (= :hpgl format)  (hpgl/write-hpgl (validated-compile input) render-opts)
         format            (throw (ex-info
                                    (str "Format " format " has no in-memory "
                                         "representation; pass :output for a "
                                         "file, or omit :format to get a "
                                         "BufferedImage.")
                                    {:format format}))
         :else             (render-image input render-opts))))))

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
