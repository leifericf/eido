(ns eido.core
  (:require
    [eido.compile :as compile]
    [eido.render :as render])
  (:import
    [java.io File]
    [javax.imageio ImageIO]))

(defn render
  "Renders a scene EDN map to a BufferedImage."
  [scene]
  (-> scene compile/compile render/render))

(defn render-to-file
  "Renders a scene EDN map and writes it as a PNG file.
  Returns the file path."
  [scene path]
  (let [img (render scene)]
    (ImageIO/write img "png" (File. ^String path))
    path))

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
  )
