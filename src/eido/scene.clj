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
