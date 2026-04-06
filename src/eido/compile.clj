(ns eido.compile
  (:refer-clojure :exclude [compile])
  (:require
    [eido.color :as color]))

(defn- compile-style
  "Extracts fill and stroke from a node, resolving colors."
  [node]
  (let [fill   (some-> (:style/fill node) :color color/resolve-color)
        stroke (:style/stroke node)]
    {:fill         fill
     :stroke-color (some-> stroke :color color/resolve-color)
     :stroke-width (when stroke (:width stroke))
     :opacity      (get node :node/opacity 1.0)}))

(defmulti compile-node
  "Compiles a scene node into a flat IR op map."
  :node/type)

(defmethod compile-node :shape/rect
  [node]
  (let [[x y] (:rect/xy node)
        [w h] (:rect/size node)]
    (merge {:op :rect :x x :y y :w w :h h}
           (compile-style node))))

(defmethod compile-node :shape/circle
  [node]
  (let [[cx cy] (:circle/center node)]
    (merge {:op :circle :cx cx :cy cy :r (:circle/radius node)}
           (compile-style node))))

(def ^:private default-ctx
  {:style {} :transforms [] :opacity 1.0})

(defn- inherit-style
  "Merges inherited style onto a node. Child keys win."
  [node inherited]
  (cond-> node
    (and (:style/fill inherited) (not (:style/fill node)))
    (assoc :style/fill (:style/fill inherited))
    (and (:style/stroke inherited) (not (:style/stroke node)))
    (assoc :style/stroke (:style/stroke inherited))))

(defn- group-style
  "Extracts style keys from a group node, merging with inherited."
  [node inherited]
  (cond-> inherited
    (:style/fill node)   (assoc :style/fill (:style/fill node))
    (:style/stroke node) (assoc :style/stroke (:style/stroke node))))

(defn- compile-tree
  "Recursively compiles a node tree into a flat sequence of IR ops."
  [node ctx]
  (if (= :group (:node/type node))
    (let [child-ctx (-> ctx
                        (update :style (partial group-style node))
                        (update :opacity * (get node :node/opacity 1.0)))]
      (into [] (mapcat #(compile-tree % child-ctx))
            (:group/children node)))
    (let [effective-opacity (* (:opacity ctx)
                               (get node :node/opacity 1.0))
          styled-node (-> (inherit-style node (:style ctx))
                          (assoc :node/opacity effective-opacity))]
      [(compile-node styled-node)])))

(defn compile
  "Compiles a scene map into an intermediate representation."
  [scene]
  {:ir/size       (:image/size scene)
   :ir/background (color/resolve-color (:image/background scene))
   :ir/ops        (into [] (mapcat #(compile-tree % default-ctx))
                         (:image/nodes scene))})

(comment
  (compile {:image/size [800 600]
            :image/background [:color/rgb 255 255 255]
            :image/nodes
            [{:node/type :shape/circle
              :circle/center [400 300]
              :circle/radius 100
              :style/fill {:color [:color/rgb 200 0 0]}}]})
  )
