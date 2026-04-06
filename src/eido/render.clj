(ns eido.render
  (:import
    [java.awt AlphaComposite BasicStroke Color Graphics2D RenderingHints]
    [java.awt.geom Ellipse2D$Double GeneralPath Rectangle2D$Double RoundRectangle2D$Double]
    [java.awt.image BufferedImage]))

(defn- ->awt-color
  "Converts a resolved color map to a java.awt.Color."
  ^Color [{:keys [r g b a]}]
  (Color. (int r) (int g) (int b) (int (* a 255))))

(defmulti render-op
  "Renders a single IR op onto the graphics context."
  (fn [^Graphics2D _g op] (:op op)))

(defn- apply-fill [^Graphics2D g shape fill]
  (when fill
    (.setColor g (->awt-color fill))
    (.fill g shape)))

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
  [^Graphics2D g {:keys [commands fill] :as op}]
  (let [shape (build-path commands)]
    (apply-fill g shape fill)
    (apply-stroke g shape op)))

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
       (let [saved (.getTransform g)]
         (.setComposite g (AlphaComposite/getInstance
                            AlphaComposite/SRC_OVER
                            (float (:opacity op))))
         (doseq [[t & args] (:transforms op)]
           (case t
             :translate (.translate g
                                    (double (first args))
                                    (double (second args)))
             :rotate    (.rotate g (double (first args)))
             :shear-x   (.shear g (double (first args)) 0.0)
             :shear-y   (.shear g 0.0 (double (first args)))
             :scale     (.scale g
                                (double (first args))
                                (double (second args)))))
         (render-op g op)
         (.setTransform g saved)))
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
