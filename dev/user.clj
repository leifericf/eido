(ns user
  (:require
    [eido.core :as eido])
  (:import
    [java.awt Dimension]
    [java.awt.image BufferedImage]
    [java.io File]
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

;; --- watch ---

(defonce ^:private watches (atom {}))
(defonce ^:private watch-counter (atom 0))

(defn- next-watch-key []
  (keyword (str "watch-" (swap! watch-counter inc))))

(defn watch-file
  "Watches an EDN scene file for changes and re-renders the preview.
  Returns a watch key for use with `unwatch`.
  Options: :interval-ms (default 500)"
  ([path] (watch-file path {}))
  ([path {:keys [interval-ms] :or {interval-ms 500}}]
   (let [key     (next-watch-key)
         running (atom true)
         f       (File. ^String path)
         thread  (Thread.
                   (fn []
                     (loop [last-mod 0]
                       (when @running
                         (let [current-mod (.lastModified f)]
                           (when (> current-mod last-mod)
                             (try
                               (show (eido/read-scene path))
                               (catch Exception e
                                 (println (str "watch: " (.getMessage e))))))
                           (Thread/sleep interval-ms)
                           (recur current-mod))))))]
     (.setDaemon thread true)
     (.start thread)
     (swap! watches assoc key {:type :file :running running :thread thread})
     key)))

(defn watch-scene
  "Watches an atom holding a scene map, re-renders on change.
  Returns a watch key for use with `unwatch`."
  [scene-atom]
  (let [key (next-watch-key)]
    (add-watch scene-atom key
      (fn [_ _ _ new-scene]
        (try
          (show new-scene)
          (catch Exception e
            (println (str "watch: " (.getMessage e)))))))
    (swap! watches assoc key {:type :scene :atom scene-atom :key key})
    key))

(defn unwatch
  "Stops a watch by key. With no args, stops all watches."
  ([]
   (doseq [k (keys @watches)]
     (unwatch k)))
  ([watch-key]
   (when-let [w (get @watches watch-key)]
     (case (:type w)
       :file  (reset! (:running w) false)
       :scene (remove-watch (:atom w) (:key w)))
     (swap! watches dissoc watch-key))))

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

  ;; Watch a file
  (def wk (watch-file "/tmp/scene.edn"))
  (unwatch wk)

  ;; Watch an atom
  (def my-scene (atom {:image/size [400 400]
                       :image/background [:color/rgb 255 255 255]
                       :image/nodes []}))
  (def wk (watch-scene my-scene))
  (swap! my-scene assoc :image/nodes
    [{:node/type :shape/circle
      :circle/center [200 200]
      :circle/radius 80
      :style/fill {:color [:color/rgb 0 128 255]}}])
  (unwatch)
  )
