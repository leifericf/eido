(ns eido.render
  (:require
    [eido.color :as color])
  (:import
    [java.awt AlphaComposite BasicStroke Graphics2D RenderingHints]
    [java.awt.geom Ellipse2D$Double Rectangle2D$Double]
    [java.awt.image BufferedImage]))

(defmulti render-op
  "Renders a single IR op onto the graphics context."
  (fn [^Graphics2D _g op] (:op op)))

(defn- apply-fill [^Graphics2D g shape fill]
  (when fill
    (.setColor g (color/->awt-color fill))
    (.fill g shape)))

(defn- apply-stroke [^Graphics2D g shape stroke-color stroke-width]
  (when stroke-color
    (.setColor g (color/->awt-color stroke-color))
    (.setStroke g (BasicStroke. (float (or stroke-width 1))))
    (.draw g shape)))

(defmethod render-op :rect
  [^Graphics2D g {:keys [x y w h fill stroke-color stroke-width]}]
  (let [shape (Rectangle2D$Double. (double x) (double y)
                                   (double w) (double h))]
    (apply-fill g shape fill)
    (apply-stroke g shape stroke-color stroke-width)))

(defmethod render-op :circle
  [^Graphics2D g {:keys [cx cy r fill stroke-color stroke-width]}]
  (let [d (* 2.0 r)
        shape (Ellipse2D$Double. (double (- cx r)) (double (- cy r))
                                d d)]
    (apply-fill g shape fill)
    (apply-stroke g shape stroke-color stroke-width)))

(defn render
  "Renders compiled IR into a BufferedImage."
  [ir]
  (let [[w h]  (:ir/size ir)
        img    (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        ^Graphics2D g (.createGraphics img)]
    (.setRenderingHint g
                       RenderingHints/KEY_ANTIALIASING
                       RenderingHints/VALUE_ANTIALIAS_ON)
    ;; Fill background
    (.setComposite g (AlphaComposite/getInstance
                       AlphaComposite/SRC_OVER 1.0))
    (.setColor g (color/->awt-color (:ir/background ir)))
    (.fillRect g 0 0 w h)
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
            :scale     (.scale g
                               (double (first args))
                               (double (second args)))))
        (render-op g op)
        (.setTransform g saved)))
    (.dispose g)
    img))

(comment
  (render {:ir/size [200 200]
           :ir/background {:r 255 :g 255 :b 255 :a 1.0}
           :ir/ops [{:op :circle :cx 100 :cy 100 :r 50
                     :fill {:r 200 :g 0 :b 0 :a 1.0}
                     :stroke-color nil :stroke-width nil
                     :opacity 1.0}]})
  )
