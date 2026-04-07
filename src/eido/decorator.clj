(ns eido.decorator
  (:require
    [eido.text :as text]))

(defn decorate-path
  "Places copies of shape at intervals along path-cmds.
  spacing: distance between placements.
  rotate?: if true, rotates shapes to follow path tangent.
  Returns a vector of group nodes with transforms."
  [path-cmds shape spacing rotate?]
  (let [;; Ensure doubles for Java interop
        path-cmds (mapv (fn [[cmd & args :as c]]
                          (case cmd
                            :move-to  [:move-to [(double ((first args) 0))
                                                 (double ((first args) 1))]]
                            :line-to  [:line-to [(double ((first args) 0))
                                                 (double ((first args) 1))]]
                            :curve-to (let [[c1 c2 pt] args]
                                        [:curve-to [(double (c1 0)) (double (c1 1))]
                                                   [(double (c2 0)) (double (c2 1))]
                                                   [(double (pt 0)) (double (pt 1))]])
                            :quad-to  (let [[cp pt] args]
                                        [:quad-to [(double (cp 0)) (double (cp 1))]
                                                  [(double (pt 0)) (double (pt 1))]])
                            c))
                        path-cmds)
        total    (text/path-length path-cmds)
        spacing  (double spacing)
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
          (range (inc n)))))

(comment
  (decorate-path
    [[:move-to [0.0 0.0]] [:line-to [200.0 0.0]]]
    {:node/type :shape/circle :circle/center [0.0 0.0] :circle/radius 5.0
     :style/fill [:color/rgb 255 0 0]}
    30 true)
  )
