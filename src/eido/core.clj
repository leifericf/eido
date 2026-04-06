(ns eido.core
  (:require
    [clojure.edn :as edn]
    [eido.compile :as compile]
    [eido.render :as render])
  (:import
    [java.io File]
    [javax.imageio ImageIO]))

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
