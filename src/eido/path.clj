(ns eido.path
  (:import
    [java.awt.geom Area GeneralPath PathIterator]))

;; --- path commands ↔ Java 2D ---

(defn- commands->general-path
  "Builds a GeneralPath from scene-format path commands."
  ^GeneralPath [commands]
  (let [p (GeneralPath.)]
    (doseq [[cmd & args] commands]
      (case cmd
        :move-to  (let [[x y] (first args)]
                    (.moveTo p (double x) (double y)))
        :line-to  (let [[x y] (first args)]
                    (.lineTo p (double x) (double y)))
        :quad-to  (let [[cx cy] (first args)
                        [x y]   (second args)]
                    (.quadTo p (double cx) (double cy)
                               (double x) (double y)))
        :curve-to (let [[c1x c1y] (first args)
                        [c2x c2y] (second args)
                        [x y]     (nth args 2)]
                    (.curveTo p (double c1x) (double c1y)
                                (double c2x) (double c2y)
                                (double x) (double y)))
        :close    (.closePath p)))
    p))

(defn- area->commands
  "Converts a Java 2D Area back to scene-format path commands."
  [^Area area]
  (let [iter (.getPathIterator area nil)
        coords (double-array 6)]
    (loop [cmds []]
      (if (.isDone iter)
        cmds
        (let [seg (.currentSegment iter coords)]
          (.next iter)
          (recur
            (conj cmds
              (case (int seg)
                0 [:move-to [(aget coords 0) (aget coords 1)]]
                1 [:line-to [(aget coords 0) (aget coords 1)]]
                2 [:quad-to [(aget coords 0) (aget coords 1)]
                            [(aget coords 2) (aget coords 3)]]
                3 [:curve-to [(aget coords 0) (aget coords 1)]
                             [(aget coords 2) (aget coords 3)]
                             [(aget coords 4) (aget coords 5)]]
                4 [:close]))))))))

;; --- boolean operations ---

(defn- boolean-op
  "Performs a boolean operation on two path command sequences."
  [cmds-a cmds-b op-fn]
  (let [a (Area. (commands->general-path cmds-a))
        b (Area. (commands->general-path cmds-b))]
    (op-fn a b)
    (area->commands a)))

(defn union
  "Returns path commands for the union of two shapes."
  [cmds-a cmds-b]
  (boolean-op cmds-a cmds-b (fn [^Area a ^Area b] (.add a b))))

(defn intersection
  "Returns path commands for the intersection of two shapes."
  [cmds-a cmds-b]
  (boolean-op cmds-a cmds-b (fn [^Area a ^Area b] (.intersect a b))))

(defn difference
  "Returns path commands for shape A minus shape B."
  [cmds-a cmds-b]
  (boolean-op cmds-a cmds-b (fn [^Area a ^Area b] (.subtract a b))))

(defn xor
  "Returns path commands for the symmetric difference of two shapes."
  [cmds-a cmds-b]
  (boolean-op cmds-a cmds-b (fn [^Area a ^Area b] (.exclusiveOr a b))))

(comment
  (union [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]]
          [:line-to [100.0 100.0]] [:line-to [0.0 100.0]] [:close]]
         [[:move-to [50.0 50.0]] [:line-to [150.0 50.0]]
          [:line-to [150.0 150.0]] [:line-to [50.0 150.0]] [:close]])
  )
