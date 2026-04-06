(ns eido.scene)

(defn grid
  "Generates a vector of nodes by calling f for each cell in a cols x rows grid.
  f receives (col row) and must return a node map (or nil to skip).
  Cells are visited row-major: (0,0) (1,0) (2,0) ... (0,1) (1,1) ..."
  [cols rows f]
  (into []
    (for [row (range rows)
          col (range cols)
          :let [node (f col row)]
          :when node]
      node)))

(defn distribute
  "Generates n nodes distributed evenly along a line from p1 to p2.
  f receives (x y t) where t is the normalized parameter [0.0, 1.0].
  For n=1, places at the midpoint. For n>1, includes both endpoints."
  [n [x1 y1] [x2 y2] f]
  (cond
    (<= n 0) []
    (= n 1)  (let [mx (/ (+ x1 x2) 2.0)
                    my (/ (+ y1 y2) 2.0)]
               [(f mx my 0.5)])
    :else    (into []
               (for [i (range n)
                     :let [t (/ (double i) (dec n))
                           x (+ x1 (* t (- x2 x1)))
                           y (+ y1 (* t (- y2 y1)))]]
                 (f x y t)))))

(defn radial
  "Generates n nodes distributed evenly around a circle.
  f receives (x y angle) where angle is in radians [0, 2*pi).
  Angles start from the top (12 o'clock) and proceed clockwise."
  [n cx cy radius f]
  (if (<= n 0)
    []
    (let [step (/ (* 2.0 Math/PI) n)]
      (into []
        (for [i (range n)
              :let [angle (* i step)
                    a (- angle (/ Math/PI 2.0))
                    x (+ cx (* radius (Math/cos a)))
                    y (+ cy (* radius (Math/sin a)))]]
          (f x y angle))))))

(defn polygon
  "Creates a closed path node from a sequence of [x y] points."
  [points]
  (let [pts (vec points)]
    {:node/type :shape/path
     :path/commands
     (if (empty? pts)
       []
       (into [[:move-to (first pts)]]
             (conj (mapv (fn [p] [:line-to p]) (rest pts))
                   [:close])))}))

(defn triangle
  "Creates a triangle path node from three [x y] points."
  [p1 p2 p3]
  (polygon [p1 p2 p3]))

(defn- catmull-rom->cubic
  "Converts four Catmull-Rom control points to a cubic bezier :curve-to command.
  p0, p1, p2, p3 are [x y] vectors. The curve spans from p1 to p2."
  [[x0 y0] [x1 y1] [x2 y2] [x3 y3]]
  (let [cp1x (+ x1 (/ (- x2 x0) 6.0))
        cp1y (+ y1 (/ (- y2 y0) 6.0))
        cp2x (- x2 (/ (- x3 x1) 6.0))
        cp2y (- y2 (/ (- y3 y1) 6.0))]
    [:curve-to [cp1x cp1y] [cp2x cp2y] [x2 y2]]))

(defn smooth-path
  "Creates a smooth path through a sequence of [x y] points using
  Catmull-Rom to cubic bezier conversion. Returns a path node."
  [points]
  (let [pts (vec points)
        n   (count pts)]
    {:node/type :shape/path
     :path/commands
     (cond
       (= n 0) []
       (= n 1) [[:move-to (first pts)]]
       (= n 2) [[:move-to (first pts)] [:line-to (second pts)]]
       :else
       (let [;; Pad with mirrored endpoints for natural boundary
             padded (vec (concat [(first pts)] pts [(peek pts)]))
             curves (for [i (range 1 (dec (count padded) ))]
                      (when (< (inc i) (count padded))
                        (catmull-rom->cubic
                          (nth padded (dec i))
                          (nth padded i)
                          (nth padded (inc i))
                          (nth padded (min (+ i 2) (dec (count padded)))))))]
         (into [[:move-to (first pts)]]
               (remove nil? (take (dec n) curves)))))}))

(comment
  (grid 3 2 (fn [c r]
              {:node/type :shape/circle
               :circle/center [(* c 50) (* r 50)]
               :circle/radius 10}))

  (distribute 5 [100 100] [700 100]
    (fn [x y _t]
      {:node/type :shape/circle
       :circle/center [x y]
       :circle/radius 20}))
  )
