(ns eido.compile
  (:refer-clojure :exclude [compile])
  (:require
    [eido.color :as color]
    [eido.validate :as validate]))

(defn- resolve-fill
  "Resolves a fill value — either a bare color vector or a map with :color."
  [fill]
  (when fill
    (if (vector? fill)
      (color/resolve-color fill)
      (some-> (:color fill) color/resolve-color))))

(defn- compile-style
  "Extracts fill and stroke from a node, resolving colors."
  [node]
  (let [stroke (:style/stroke node)]
    (cond-> {:fill         (resolve-fill (:style/fill node))
             :stroke-color (some-> stroke :color color/resolve-color)
             :stroke-width (when stroke (:width stroke))
             :opacity      (get node :node/opacity 1.0)}
      (:cap stroke)  (assoc :stroke-cap (:cap stroke))
      (:join stroke) (assoc :stroke-join (:join stroke))
      (:dash stroke) (assoc :stroke-dash (:dash stroke)))))

(defmulti compile-node
  "Compiles a scene node into a flat IR op map."
  :node/type)

(defmethod compile-node :shape/rect
  [node]
  (let [[x y] (:rect/xy node)
        [w h] (:rect/size node)]
    (merge {:op :rect :x x :y y :w w :h h
            :corner-radius (:rect/corner-radius node)}
           (compile-style node))))

(defmethod compile-node :shape/circle
  [node]
  (let [[cx cy] (:circle/center node)]
    (merge {:op :circle :cx cx :cy cy :r (:circle/radius node)}
           (compile-style node))))

(defmethod compile-node :shape/line
  [node]
  (let [[x1 y1] (:line/from node)
        [x2 y2] (:line/to node)]
    (merge {:op :line :x1 x1 :y1 y1 :x2 x2 :y2 y2}
           (compile-style node))))

(defmethod compile-node :shape/ellipse
  [node]
  (let [[cx cy] (:ellipse/center node)]
    (merge {:op :ellipse :cx cx :cy cy
            :rx (:ellipse/rx node) :ry (:ellipse/ry node)}
           (compile-style node))))

(defn- compile-command
  "Flattens a scene path command into an IR command.
  Scene: [:move-to [x y]] -> IR: [:move-to x y]"
  [[cmd & args]]
  (case cmd
    :move-to  (let [[x y] (first args)] [:move-to x y])
    :line-to  (let [[x y] (first args)] [:line-to x y])
    :curve-to (let [[cx1 cy1] (first args)
                    [cx2 cy2] (second args)
                    [x y]     (nth args 2)]
                [:curve-to cx1 cy1 cx2 cy2 x y])
    :quad-to  (let [[cpx cpy] (first args)
                    [x y]     (second args)]
                [:quad-to cpx cpy x y])
    :close    [:close]))

(defmethod compile-node :shape/path
  [node]
  (merge {:op       :path
          :commands (mapv compile-command (:path/commands node))}
         (compile-style node)))

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

(defn- simplify-transform
  "Strips namespace prefix from transform keyword.
  [:transform/translate x y] -> [:translate x y]"
  [[k & args]]
  (into [(keyword (name k))] args))

(defn- accumulate-transforms
  "Concatenates node's transforms onto inherited transforms."
  [inherited node]
  (into inherited (mapv simplify-transform
                        (or (:node/transform node) []))))

(defn- compile-tree
  "Recursively compiles a node tree into a flat sequence of IR ops."
  [node ctx]
  (if (= :group (:node/type node))
    (let [child-ctx (-> ctx
                        (update :style (partial group-style node))
                        (update :opacity * (get node :node/opacity 1.0))
                        (update :transforms accumulate-transforms node))]
      (into [] (mapcat #(compile-tree % child-ctx))
            (:group/children node)))
    (let [effective-opacity (* (:opacity ctx)
                               (get node :node/opacity 1.0))
          transforms (accumulate-transforms (:transforms ctx) node)
          styled-node (-> (inherit-style node (:style ctx))
                          (assoc :node/opacity effective-opacity))]
      [(assoc (compile-node styled-node) :transforms transforms)])))

(defn compile
  "Compiles a scene map into an intermediate representation.
  Validates the scene first; throws ex-info with :errors on failure."
  [scene]
  (when-let [errors (validate/validate scene)]
    (throw (ex-info "Invalid scene"
                    {:errors errors})))
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
