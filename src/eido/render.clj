(ns eido.render
  (:require
    [eido.color :as color])
  (:import
    [java.awt AlphaComposite BasicStroke Color Graphics2D LinearGradientPaint
              RadialGradientPaint RenderingHints TexturePaint]
    [java.awt.geom Arc2D$Double Ellipse2D$Double GeneralPath Point2D$Float
                    Rectangle2D$Double RoundRectangle2D$Double]
    [java.awt.image BufferedImage]))

(defn- ->awt-color
  "Converts a resolved color map to a java.awt.Color."
  ^Color [{:keys [r g b a]}]
  (Color. (int r) (int g) (int b) (int (* a 255))))

(defmulti render-op
  "Renders a single IR op onto the graphics context."
  (fn [^Graphics2D _g op] (:op op)))

(defn- gradient->paint
  "Converts a resolved gradient map to a Java2D Paint object."
  [gradient]
  (let [stops      (:gradient/stops gradient)
        fractions  (float-array (mapv first stops))
        colors     (into-array Color (mapv #(->awt-color (second %)) stops))]
    (case (:gradient/type gradient)
      :linear (let [[x1 y1] (:gradient/from gradient)
                    [x2 y2] (:gradient/to gradient)]
                (LinearGradientPaint.
                  (Point2D$Float. (float x1) (float y1))
                  (Point2D$Float. (float x2) (float y2))
                  fractions colors))
      :radial (let [[cx cy] (:gradient/center gradient)
                    r       (:gradient/radius gradient)]
                (RadialGradientPaint.
                  (Point2D$Float. (float cx) (float cy))
                  (float r)
                  ^floats fractions ^"[Ljava.awt.Color;" colors)))))

(declare render-ir-op)

(defn- make-pattern-paint
  "Renders pattern tile ops to a BufferedImage and creates a TexturePaint."
  [pattern-fill]
  (let [[tw th] (:pattern/size pattern-fill)
        tile-img (BufferedImage. (int tw) (int th) BufferedImage/TYPE_INT_ARGB)
        tile-g   (.createGraphics tile-img)]
    (.setRenderingHint tile-g
                       RenderingHints/KEY_ANTIALIASING
                       RenderingHints/VALUE_ANTIALIAS_ON)
    (doseq [op (:pattern/ops pattern-fill)]
      (render-ir-op tile-g tile-img [(int tw) (int th)] op))
    (.dispose tile-g)
    (TexturePaint. tile-img
                   (Rectangle2D$Double. 0.0 0.0 (double tw) (double th)))))

(def ^:private pattern->paint
  "Cached version of make-pattern-paint — identical pattern specs reuse tiles."
  (memoize make-pattern-paint))

(defn- apply-fill [^Graphics2D g shape fill]
  (when fill
    (cond
      (:gradient/type fill)
      (let [saved-paint (.getPaint g)]
        (.setPaint g (gradient->paint fill))
        (.fill g shape)
        (.setPaint g saved-paint))

      (= :pattern (:fill/type fill))
      (let [saved-paint (.getPaint g)]
        (.setPaint g (pattern->paint fill))
        (.fill g shape)
        (.setPaint g saved-paint))

      :else
      (do (.setColor g (->awt-color fill))
          (.fill g shape)))))

(def ^:private cap-map
  {:butt   BasicStroke/CAP_BUTT
   :round  BasicStroke/CAP_ROUND
   :square BasicStroke/CAP_SQUARE})

(def ^:private join-map
  {:miter BasicStroke/JOIN_MITER
   :round BasicStroke/JOIN_ROUND
   :bevel BasicStroke/JOIN_BEVEL})

(defn- apply-stroke [^Graphics2D g shape {:keys [stroke-color stroke-width
                                                  stroke-cap stroke-join
                                                  stroke-dash]}]
  (when stroke-color
    (.setColor g (->awt-color stroke-color))
    (let [w     (float (or stroke-width 1))
          cap   (get cap-map stroke-cap BasicStroke/CAP_SQUARE)
          join  (get join-map stroke-join BasicStroke/JOIN_MITER)
          dash  (when stroke-dash (float-array stroke-dash))]
      (.setStroke g (if dash
                      (BasicStroke. w cap join (float 10.0) dash (float 0.0))
                      (BasicStroke. w cap join))))
    (.draw g shape)))

(defmethod render-op :rect
  [^Graphics2D g {:keys [x y w h fill corner-radius] :as op}]
  (let [shape (if corner-radius
                (let [r (double corner-radius)]
                  (RoundRectangle2D$Double. (double x) (double y)
                                            (double w) (double h) r r))
                (Rectangle2D$Double. (double x) (double y)
                                     (double w) (double h)))]
    (apply-fill g shape fill)
    (apply-stroke g shape op)))

(defmethod render-op :circle
  [^Graphics2D g {:keys [cx cy r fill] :as op}]
  (let [d (* 2.0 r)
        shape (Ellipse2D$Double. (double (- cx r)) (double (- cy r))
                                d d)]
    (apply-fill g shape fill)
    (apply-stroke g shape op)))

(def ^:private arc-mode-map
  {:open  Arc2D$Double/OPEN
   :chord Arc2D$Double/CHORD
   :pie   Arc2D$Double/PIE})

(defmethod render-op :arc
  [^Graphics2D g {:keys [cx cy rx ry start extent mode fill] :as op}]
  (let [shape (Arc2D$Double. (double (- cx rx)) (double (- cy ry))
                              (double (* 2.0 rx)) (double (* 2.0 ry))
                              (double start) (double extent)
                              (int (get arc-mode-map mode Arc2D$Double/OPEN)))]
    (apply-fill g shape fill)
    (apply-stroke g shape op)))

(defmethod render-op :line
  [^Graphics2D g {:keys [x1 y1 x2 y2] :as op}]
  (let [shape (java.awt.geom.Line2D$Double. (double x1) (double y1)
                                             (double x2) (double y2))]
    (apply-stroke g shape op)))

(defmethod render-op :ellipse
  [^Graphics2D g {:keys [cx cy rx ry fill] :as op}]
  (let [shape (Ellipse2D$Double. (double (- cx rx)) (double (- cy ry))
                                 (double (* 2.0 rx)) (double (* 2.0 ry)))]
    (apply-fill g shape fill)
    (apply-stroke g shape op)))

(defn- build-path
  "Builds a GeneralPath from a sequence of IR path commands."
  ^GeneralPath [commands]
  (let [p (GeneralPath.)]
    (doseq [[cmd & args] commands]
      (case cmd
        :move-to  (.moveTo p (double (first args)) (double (second args)))
        :line-to  (.lineTo p (double (first args)) (double (second args)))
        :curve-to (.curveTo p
                    (double (nth args 0)) (double (nth args 1))
                    (double (nth args 2)) (double (nth args 3))
                    (double (nth args 4)) (double (nth args 5)))
        :quad-to  (.quadTo p
                    (double (nth args 0)) (double (nth args 1))
                    (double (nth args 2)) (double (nth args 3)))
        :close    (.closePath p)))
    p))

(defmethod render-op :path
  [^Graphics2D g {:keys [commands fill fill-rule] :as op}]
  (let [shape (build-path commands)]
    (when (= :even-odd fill-rule)
      (.setWindingRule shape GeneralPath/WIND_EVEN_ODD))
    (apply-fill g shape fill)
    (apply-stroke g shape op)))

(defn- op->clip-shape
  "Converts a clip IR op to a java.awt.Shape for clipping."
  ^java.awt.Shape [{:keys [op] :as clip}]
  (case op
    :rect    (let [{:keys [x y w h corner-radius]} clip]
               (if corner-radius
                 (RoundRectangle2D$Double. (double x) (double y)
                                           (double w) (double h)
                                           (double corner-radius) (double corner-radius))
                 (Rectangle2D$Double. (double x) (double y) (double w) (double h))))
    :circle  (let [{:keys [cx cy r]} clip
                   d (* 2.0 r)]
               (Ellipse2D$Double. (double (- cx r)) (double (- cy r)) d d))
    :ellipse (let [{:keys [cx cy rx ry]} clip]
               (Ellipse2D$Double. (double (- cx rx)) (double (- cy ry))
                                   (double (* 2.0 rx)) (double (* 2.0 ry))))
    :path    (build-path (:commands clip))))

(defn- apply-transforms
  "Applies a sequence of transform ops to the graphics context."
  [^Graphics2D g transforms]
  (doseq [[t & args] transforms]
    (case t
      :translate (.translate g (double (first args)) (double (second args)))
      :rotate    (.rotate g (double (first args)))
      :shear-x   (.shear g (double (first args)) 0.0)
      :shear-y   (.shear g 0.0 (double (first args)))
      :scale     (.scale g (double (first args)) (double (second args))))))

(defn- render-single-op
  "Renders a single leaf IR op onto g with transforms, clip, and opacity."
  [^Graphics2D g op]
  (let [saved-transform (.getTransform g)
        saved-clip      (.getClip g)]
    (.setComposite g (AlphaComposite/getInstance
                       AlphaComposite/SRC_OVER
                       (float (:opacity op))))
    (apply-transforms g (:transforms op))
    (when-let [clip-op (:clip op)]
      (.setClip g (op->clip-shape clip-op)))
    (render-op g op)
    (.setTransform g saved-transform)
    (.setClip g saved-clip)))

(def ^:private composite-rules
  {:src-over  AlphaComposite/SRC_OVER
   :src-in    AlphaComposite/SRC_IN
   :src-out   AlphaComposite/SRC_OUT
   :dst-over  AlphaComposite/DST_OVER
   :xor       AlphaComposite/XOR})

(set! *unchecked-math* true)

(defn- argb-a ^long [^long px] (bit-and (unsigned-bit-shift-right px 24) 0xFF))
(defn- argb-r ^long [^long px] (bit-and (unsigned-bit-shift-right px 16) 0xFF))
(defn- argb-g ^long [^long px] (bit-and (unsigned-bit-shift-right px 8) 0xFF))
(defn- argb-b ^long [^long px] (bit-and px 0xFF))

(defn- pack-argb ^long [^long a ^long r ^long g ^long b]
  (bit-or (bit-shift-left a 24) (bit-shift-left r 16)
          (bit-shift-left g 8) b))

(defn- filter-pixel
  "Applies a color filter to a single ARGB int pixel."
  [argb filter-type]
  (let [argb (unchecked-int argb)
        a (argb-a argb)
        r (argb-r argb)
        g (argb-g argb)
        b (argb-b argb)]
    (unchecked-int
      (case filter-type
        :grayscale
        (let [lum (int (+ (* 0.299 r) (* 0.587 g) (* 0.114 b)))]
          (pack-argb a lum lum lum))
        :sepia
        (let [lum (int (+ (* 0.299 r) (* 0.587 g) (* 0.114 b)))]
          (pack-argb a (min 255 (+ lum 94)) (min 255 (+ lum 38)) lum))
        :invert
        (pack-argb a (- 255 r) (- 255 g) (- 255 b))))))

(defn- apply-pixel-filter
  "Applies a per-pixel filter to a BufferedImage in place."
  [^BufferedImage img filter-type]
  (let [w (.getWidth img)
        h (.getHeight img)
        ^ints data (.getRGB img 0 0 w h nil 0 w)]
    (dotimes [i (alength data)]
      (aset data i (unchecked-int (filter-pixel (aget data i) filter-type))))
    (.setRGB img 0 0 w h data 0 w)))

(defn- box-blur-pass
  "Single horizontal+vertical box blur pass on ARGB int array."
  [^ints src ^ints dst w h radius]
  (let [diam (inc (* 2 radius))
        inv  (/ 1.0 diam)]
    ;; Horizontal pass: src -> dst
    (dotimes [y h]
      (let [row (* y w)]
        (loop [x 0 sa 0 sr 0 sg 0 sb 0]
          (when (< x w)
            (let [rx (min (dec w) (+ x radius))
                  rv (aget src (+ row rx))
                  sa (+ sa (argb-a rv))
                  sr (+ sr (argb-r rv))
                  sg (+ sg (argb-g rv))
                  sb (+ sb (argb-b rv))
                  lx (- x radius 1)]
              (if (>= lx 0)
                (let [lv (aget src (+ row lx))
                      sa (- sa (argb-a lv))
                      sr (- sr (argb-r lv))
                      sg (- sg (argb-g lv))
                      sb (- sb (argb-b lv))]
                  (aset dst (+ row x)
                    (unchecked-int
                      (pack-argb (int (* sa inv)) (int (* sr inv))
                                 (int (* sg inv)) (int (* sb inv)))))
                  (recur (inc x) sa sr sg sb))
                (do
                  (aset dst (+ row x)
                    (unchecked-int
                      (pack-argb (int (* sa inv)) (int (* sr inv))
                                 (int (* sg inv)) (int (* sb inv)))))
                  (recur (inc x) sa sr sg sb))))))))
    ;; Vertical pass: dst -> src
    (dotimes [x w]
      (loop [y 0 sa 0 sr 0 sg 0 sb 0]
        (when (< y h)
          (let [ry (min (dec h) (+ y radius))
                rv (aget dst (+ (* ry w) x))
                sa (+ sa (argb-a rv))
                sr (+ sr (argb-r rv))
                sg (+ sg (argb-g rv))
                sb (+ sb (argb-b rv))
                ly (- y radius 1)]
            (if (>= ly 0)
              (let [lv (aget dst (+ (* ly w) x))
                    sa (- sa (argb-a lv))
                    sr (- sr (argb-r lv))
                    sg (- sg (argb-g lv))
                    sb (- sb (argb-b lv))]
                (aset src (+ (* y w) x)
                  (unchecked-int
                    (pack-argb (int (* sa inv)) (int (* sr inv))
                               (int (* sg inv)) (int (* sb inv)))))
                (recur (inc y) sa sr sg sb))
              (do
                (aset src (+ (* y w) x)
                  (unchecked-int
                    (pack-argb (int (* sa inv)) (int (* sr inv))
                               (int (* sg inv)) (int (* sb inv)))))
                (recur (inc y) sa sr sg sb)))))))))


(defn- box-blur
  "Applies a box blur approximation of Gaussian blur (3 passes)."
  [^BufferedImage img radius]
  (when (pos? radius)
    (let [w (.getWidth img)
          h (.getHeight img)
          ^ints src (.getRGB img 0 0 w h nil 0 w)
          ^ints dst (int-array (alength src))]
      (dotimes [_ 3]
        (box-blur-pass src dst w h (int radius)))
      (.setRGB img 0 0 w h src 0 w))))

(defn- apply-grain
  "Adds procedural film grain to a BufferedImage."
  [^BufferedImage img amount seed]
  (let [w (.getWidth img)
        h (.getHeight img)
        ^ints data (.getRGB img 0 0 w h nil 0 w)
        rng (java.util.Random. (long (or seed 0)))
        amt (double amount)]
    (dotimes [i (alength data)]
      (let [px    (aget data i)
            a     (argb-a px)
            noise (int (* amt (- (* 2.0 (.nextDouble rng)) 1.0) 128))]
        (when (pos? a)
          (aset data i
            (unchecked-int
              (pack-argb a
                (max 0 (min 255 (+ (argb-r px) noise)))
                (max 0 (min 255 (+ (argb-g px) noise)))
                (max 0 (min 255 (+ (argb-b px) noise)))))))))
    (.setRGB img 0 0 w h data 0 w)))

(defn- apply-posterize
  "Quantizes each color channel to n levels."
  [^BufferedImage img levels]
  (let [w (.getWidth img)
        h (.getHeight img)
        ^ints data (.getRGB img 0 0 w h nil 0 w)
        levels (int levels)
        step (/ 255.0 (dec levels))]
    (dotimes [i (alength data)]
      (let [px (aget data i)
            a  (argb-a px)]
        (when (pos? a)
          (aset data i
            (unchecked-int
              (pack-argb a
                (int (* (Math/round (/ (double (argb-r px)) step)) step))
                (int (* (Math/round (/ (double (argb-g px)) step)) step))
                (int (* (Math/round (/ (double (argb-b px)) step)) step))))))))
    (.setRGB img 0 0 w h data 0 w)))

(defn- apply-duotone
  "Maps luminance through two colors: c1 for dark, c2 for light."
  [^BufferedImage img color1 color2]
  (let [w  (.getWidth img)
        h  (.getHeight img)
        ^ints data (.getRGB img 0 0 w h nil 0 w)
        c1 (color/resolve-color color1)
        c2 (color/resolve-color color2)]
    (dotimes [i (alength data)]
      (let [px  (aget data i)
            a   (argb-a px)]
        (when (pos? a)
          (let [lum (/ (+ (* 0.299 (argb-r px))
                          (* 0.587 (argb-g px))
                          (* 0.114 (argb-b px)))
                       255.0)
                r (int (+ (* (- 1.0 lum) (:r c1)) (* lum (:r c2))))
                g (int (+ (* (- 1.0 lum) (:g c1)) (* lum (:g c2))))
                b (int (+ (* (- 1.0 lum) (:b c1)) (* lum (:b c2))))]
            (aset data i
              (unchecked-int (pack-argb a r g b)))))))
    (.setRGB img 0 0 w h data 0 w)))

(defn- apply-halftone
  "Converts image to halftone dot pattern."
  [^BufferedImage img dot-size angle]
  (let [w (.getWidth img)
        h (.getHeight img)
        ^ints src-data (.getRGB img 0 0 w h nil 0 w)
        out (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        ^Graphics2D g (.createGraphics out)
        dot-size (double (or dot-size 6))
        angle-rad (* (double (or angle 0)) (/ Math/PI 180.0))
        cos-a (Math/cos angle-rad)
        sin-a (Math/sin angle-rad)
        half (/ dot-size 2.0)
        diag (Math/sqrt (+ (* w w) (* h h)))
        steps (int (Math/ceil (/ diag dot-size)))]
    (.setColor g (Color. 255 255 255))
    (.fillRect g 0 0 w h)
    (.setColor g (Color. 0 0 0))
    (.setRenderingHint g RenderingHints/KEY_ANTIALIASING
                       RenderingHints/VALUE_ANTIALIAS_ON)
    (let [ellipse (Ellipse2D$Double.)]
      (doseq [gi (range (- steps) (inc steps))
              gj (range (- steps) (inc steps))]
        (let [gx (* gi dot-size)
              gy (* gj dot-size)
              cx (+ (* gx cos-a) (* gy (- sin-a)) (/ w 2.0))
              cy (+ (* gx sin-a) (* gy cos-a) (/ h 2.0))
              ix (int cx) iy (int cy)]
          (when (and (>= ix 0) (< ix w) (>= iy 0) (< iy h))
            (let [px  (aget src-data (+ (* iy w) ix))
                  lum (/ (+ (* 0.299 (argb-r px))
                            (* 0.587 (argb-g px))
                            (* 0.114 (argb-b px)))
                         255.0)
                  r (* half (- 1.0 lum))]
              (when (> r 0.3)
                (.setFrame ellipse (- cx r) (- cy r) (* 2.0 r) (* 2.0 r))
                (.fill g ellipse)))))))
    (.dispose g)
    (let [^ints out-data (.getRGB out 0 0 w h nil 0 w)]
      (.setRGB img 0 0 w h out-data 0 w))))

(defn- apply-filter
  "Applies a filter to a BufferedImage in place."
  [^BufferedImage img filter-spec]
  (if (vector? filter-spec)
    (let [[filter-type & args] filter-spec]
      (case filter-type
        :blur      (box-blur img (first args))
        :grain     (apply-grain img (first args) (second args))
        :posterize (apply-posterize img (first args))
        :duotone   (apply-duotone img (first args) (second args))
        :halftone  (apply-halftone img (first args) (second args))))
    (apply-pixel-filter img filter-spec)))

(def ^:private blend-modes
  #{:multiply :screen :overlay})

(defn- blend-channel
  "Blends a single channel (0-255) using the given mode."
  ^long [mode ^long s ^long d]
  (case mode
    :multiply (unchecked-int (/ (* s d) 255))
    :screen   (- 255 (unchecked-int (/ (* (- 255 s) (- 255 d)) 255)))
    :overlay  (if (< d 128)
                (unchecked-int (/ (* 2 s d) 255))
                (- 255 (unchecked-int (/ (* 2 (- 255 s) (- 255 d)) 255))))))

(defn- blend-pixels
  "Blends source buffer onto destination image using a blend mode."
  [^BufferedImage dst ^BufferedImage src mode ^double opacity]
  (let [w (.getWidth src)
        h (.getHeight src)
        ^ints src-data (.getRGB src 0 0 w h nil 0 w)
        ^ints dst-data (.getRGB dst 0 0 w h nil 0 w)]
    (dotimes [i (alength src-data)]
      (let [s (aget src-data i)
            sa (argb-a s)]
        (when (pos? sa)
          (let [d  (aget dst-data i)
                sr (argb-r s) sg (argb-g s) sb (argb-b s)
                dr (argb-r d) dg (argb-g d) db (argb-b d)
                da (argb-a d)
                ;; Blend each channel
                br (blend-channel mode sr dr)
                bg (blend-channel mode sg dg)
                bb (blend-channel mode sb db)
                ;; Apply source alpha and opacity
                mix (* (/ sa 255.0) opacity)
                fr (int (+ (* br mix) (* dr (- 1.0 mix))))
                fg (int (+ (* bg mix) (* dg (- 1.0 mix))))
                fb (int (+ (* bb mix) (* db (- 1.0 mix))))
                fa (max da (int (* sa opacity)))]
            (aset dst-data i
              (unchecked-int (pack-argb fa fr fg fb)))))))
    (.setRGB dst 0 0 w h dst-data 0 w)))

(set! *unchecked-math* false)

(defn- render-buffer-op
  "Renders a compositing group: children to off-screen buffer, then onto g."
  [^Graphics2D g ^BufferedImage dst-img [w h]
   {:keys [composite filter opacity ops transforms clip]}]
  (let [buf (BufferedImage. (int w) (int h) BufferedImage/TYPE_INT_ARGB)
        bg  (.createGraphics buf)]
    (.setRenderingHints bg (.getRenderingHints g))
    (doseq [child-op ops]
      (render-ir-op bg buf [w h] child-op))
    (.dispose bg)
    ;; Apply filter to buffer before compositing
    (when filter
      (apply-filter buf filter))
    (if (blend-modes composite)
      ;; Custom pixel blending — operate directly on destination image
      (blend-pixels dst-img buf composite opacity)
      ;; Porter-Duff compositing via Java2D
      (let [saved-transform (.getTransform g)
            saved-clip      (.getClip g)]
        (apply-transforms g transforms)
        (when clip (.setClip g (op->clip-shape clip)))
        (.setComposite g (AlphaComposite/getInstance
                           (get composite-rules composite AlphaComposite/SRC_OVER)
                           (float opacity)))
        (.drawImage g buf 0 0 nil)
        (.setTransform g saved-transform)
        (.setClip g saved-clip)))))

(defn- render-ir-op
  "Dispatches rendering of a single IR op (leaf or buffer)."
  [^Graphics2D g ^BufferedImage img size op]
  (if (= :buffer (:op op))
    (render-buffer-op g img size op)
    (render-single-op g op)))

(defn render
  "Renders compiled IR into a BufferedImage.
  Opts: :scale (number, default 1), :transparent-background (boolean)."
  ([ir] (render ir {}))
  ([ir opts]
   (let [scale  (get opts :scale 1)
         [w h]  (:ir/size ir)
         sw     (int (* w scale))
         sh     (int (* h scale))
         img    (BufferedImage. sw sh BufferedImage/TYPE_INT_ARGB)
         ^Graphics2D g (.createGraphics img)]
     (.setRenderingHint g
                        RenderingHints/KEY_ANTIALIASING
                        (if (get opts :antialias true)
                          RenderingHints/VALUE_ANTIALIAS_ON
                          RenderingHints/VALUE_ANTIALIAS_OFF))
     (when (not= scale 1)
       (.scale g (double scale) (double scale)))
     ;; Fill background (unless transparent requested)
     (when-not (:transparent-background opts)
       (.setComposite g (AlphaComposite/getInstance
                          AlphaComposite/SRC_OVER 1.0))
       (.setColor g (->awt-color (:ir/background ir)))
       (.fillRect g 0 0 w h))
     ;; Render ops in order
     (doseq [op (:ir/ops ir)]
       (render-ir-op g img [w h] op))
     (.dispose g)
     img)))

(comment
  (render {:ir/size [200 200]
           :ir/background {:r 255 :g 255 :b 255 :a 1.0}
           :ir/ops [{:op :circle :cx 100 :cy 100 :r 50
                     :fill {:r 200 :g 0 :b 0 :a 1.0}
                     :stroke-color nil :stroke-width nil
                     :opacity 1.0}]})
  )
