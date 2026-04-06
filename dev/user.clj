(ns user
  (:require
    [eido.core :as eido])
  (:import
    [java.awt Dimension]
    [java.awt.image BufferedImage]
    [javax.swing ImageIcon JFrame JLabel SwingUtilities]))

(defonce ^:private frame (atom nil))

(defn show
  "Renders a scene and displays it in a reusable window.
  Call repeatedly to update the display."
  [scene]
  (let [^BufferedImage img (eido/render scene)
        w (.getWidth img)
        h (.getHeight img)]
    (SwingUtilities/invokeAndWait
      (fn []
        (let [^JFrame f (or @frame
                             (let [f (JFrame. "Eido")]
                               (.setDefaultCloseOperation
                                 f JFrame/DISPOSE_ON_CLOSE)
                               (reset! frame f)
                               f))
              label (JLabel. (ImageIcon. img))]
          (.setPreferredSize label (Dimension. w h))
          (doto (.getContentPane f)
            (.removeAll)
            (.add label))
          (doto f
            (.pack)
            (.setVisible true)))))
    img))

(comment
  (show {:image/size [800 600]
         :image/background [:color/rgb 255 255 255]
         :image/nodes
         [{:node/type :shape/circle
           :circle/center [400 300]
           :circle/radius 100
           :style/fill {:color [:color/rgb 200 0 0]}}
          {:node/type :shape/rect
           :rect/xy [200 150]
           :rect/size [400 300]
           :style/stroke {:color [:color/rgb 0 0 0] :width 2}
           :node/opacity 0.5}]})
  )
