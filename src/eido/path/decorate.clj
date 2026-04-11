(ns eido.path.decorate
  "Place copies of a shape at regular intervals along a path, optionally
  rotating each copy to follow the path tangent."
  (:require
    [eido.text :as text]))

(defn- coerce-doubles
  "Coerces path command coordinates to doubles for Java interop."
  [path-cmds]
  (mapv (fn [[cmd & args :as c]]
          (case cmd
            (:move-to :line-to)
            (let [[x y] (first args)]
              [cmd [(double x) (double y)]])
            :curve-to
            (let [[[c1x c1y] [c2x c2y] [px py]] args]
              [:curve-to [(double c1x) (double c1y)]
                         [(double c2x) (double c2y)]
                         [(double px)  (double py)]])
            :quad-to
            (let [[[cx cy] [px py]] args]
              [:quad-to [(double cx) (double cy)]
                        [(double px) (double py)]])
            c))
        path-cmds))

(defn decorate-path
  "Places copies of shape at intervals along path-cmds.
  opts: :spacing (required), :rotate? (default false).
  Returns a vector of group nodes with transforms."
  [path-cmds shape opts]
  (let [spacing-val (:spacing opts)]
    (if-not (and spacing-val (pos? spacing-val))
      []
      (let [path-cmds (coerce-doubles path-cmds)
            total    (text/path-length path-cmds)
            spacing  (double spacing-val)
            rotate?  (get opts :rotate? false)
            n        (int (Math/floor (/ total spacing)))]
    (into []
          (keep (fn [i]
                  (let [d (* i spacing)
                        {:keys [point angle]} (text/point-at path-cmds d)]
                    (when point
                      (let [[px py] point
                            transforms (cond-> [[:transform/translate px py]]
                                         (and rotate? (not (zero? angle)))
                                         (conj [:transform/rotate angle]))]
                        {:node/type      :group
                         :node/transform transforms
                         :group/children [shape]})))))
          (range (inc n)))))))

(comment
  (decorate-path
    [[:move-to [0.0 0.0]] [:line-to [200.0 0.0]]]
    {:node/type :shape/circle :circle/center [0.0 0.0] :circle/radius 5.0
     :style/fill [:color/rgb 255 0 0]}
    {:spacing 30 :rotate? true})
  )
