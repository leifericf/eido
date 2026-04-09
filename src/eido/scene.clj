(ns eido.scene
  (:require
    [eido.text :as text]))

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

(defn regular-polygon
  "Creates a closed regular n-sided polygon path node centered at [cx cy]
  with the given radius. First vertex is at the top (12 o'clock)."
  [[cx cy] radius n]
  (polygon (radial n cx cy radius (fn [x y _a] [x y]))))

(defn star
  "Creates a closed star path node with n points centered at [cx cy].
  outer-radius is the distance to the tips, inner-radius to the inner vertices."
  [[cx cy] outer-radius inner-radius n]
  (let [step (/ Math/PI n)
        pts  (for [i (range (* 2 n))
                   :let [angle (- (* i step) (/ Math/PI 2.0))
                         r     (if (even? i) outer-radius inner-radius)
                         x     (+ cx (* r (Math/cos angle)))
                         y     (+ cy (* r (Math/sin angle)))]]
               [x y])]
    (polygon pts)))

;; --- text helpers ---

(defn text
  "Creates a text node rendered as vector paths."
  [content origin font-spec]
  {:node/type    :shape/text
   :text/content content
   :text/origin  origin
   :text/font    font-spec})

(defn text-glyphs
  "Creates a per-glyph text node. glyph-overrides is a vector of maps,
  each with :glyph/index and optional :style/fill, :style/stroke,
  :node/transform, :node/opacity."
  [content origin font-spec glyph-overrides]
  {:node/type    :shape/text-glyphs
   :text/content content
   :text/origin  origin
   :text/font    font-spec
   :text/glyphs  glyph-overrides})

(defn text-on-path
  "Creates text that follows a path. path-commands uses the same format
  as :shape/path — [[:move-to [x y]] [:curve-to ...] ...]."
  [content font-spec path-commands]
  {:node/type    :shape/text-on-path
   :text/content content
   :text/font    font-spec
   :text/path    path-commands})

(defn text-stack
  "Creates layered text (e.g. shadow + outline + fill).
  layers is a vector of style maps applied to copies of the same text.
  Earlier layers render behind later ones."
  [content origin font-spec layers]
  {:node/type :group
   :group/children
   (mapv (fn [layer]
           (merge (text content origin font-spec) layer))
         layers)})

(defn text-outline
  "Returns text as a path node — the vector outline of the glyphs.
  Useful as a clip mask, for boolean ops, or as a standalone shape."
  [content font-spec [ox oy]]
  (let [cmds (text/text->path-commands content font-spec)]
    {:node/type     :shape/path
     :path/commands cmds
     :path/fill-rule :even-odd
     :node/transform [[:transform/translate ox oy]]}))

(defn text-clip
  "Creates a group clipped to text outlines. Children are clipped to the
  text shape — anything inside the letters shows through."
  [content font-spec [ox oy] children]
  (let [cmds (text/text->path-commands content font-spec)]
    {:node/type      :group
     :group/clip     {:node/type     :shape/path
                      :path/commands cmds
                      :path/fill-rule :even-odd}
     :node/transform [[:transform/translate ox oy]]
     :group/children (vec children)}))


;; --- convenience helpers ---

(defn ^{:convenience true} circle-node
  "Creates a circle node. Shorthand for the {:node/type :shape/circle ...} map.
  Example: (circle-node [200 200] 50 [:color/name \"red\"])"
  ([center radius]
   {:node/type :shape/circle :circle/center center :circle/radius radius})
  ([center radius fill]
   {:node/type :shape/circle :circle/center center :circle/radius radius
    :style/fill fill}))

(defn ^{:convenience true} rect-node
  "Creates a rect node. Shorthand for the {:node/type :shape/rect ...} map.
  Example: (rect-node [10 10] [100 50] [:color/name \"blue\"])"
  ([xy size]
   {:node/type :shape/rect :rect/xy xy :rect/size size})
  ([xy size fill]
   {:node/type :shape/rect :rect/xy xy :rect/size size :style/fill fill}))

(defn ^{:convenience true} line-node
  "Creates a line node. Shorthand for the {:node/type :shape/line ...} map.
  Example: (line-node [0 0] [100 100] [:color/name \"black\"] 2)"
  ([from to]
   {:node/type :shape/line :line/from from :line/to to})
  ([from to color width]
   {:node/type :shape/line :line/from from :line/to to
    :style/stroke {:color color :width width}}))

(defn ^{:convenience true :convenience-for 'eido.scene/radial}
  polar->xy
  "Converts polar coordinates to [x y].
  Wraps the trig: (+ cx (* r (cos a))), (+ cy (* r (sin a)))."
  [[cx cy] radius angle]
  [(+ (double cx) (* (double radius) (Math/cos (double angle))))
   (+ (double cy) (* (double radius) (Math/sin (double angle))))])

(defn ^{:convenience true :convenience-for 'eido.scene/radial}
  ring
  "Places n copies of shape in a circle. Returns a vector of translated nodes.
  Convenience for (radial n cx cy radius (fn [x y _] shape-with-translate))."
  [n [cx cy] radius shape]
  (vec (for [i (range n)]
         (let [angle (* 2.0 Math/PI (/ (double i) (double n)))
               x (+ (double cx) (* (double radius) (Math/cos angle)))
               y (+ (double cy) (* (double radius) (Math/sin angle)))]
           (assoc shape :node/transform [[:transform/translate x y]])))))

(defn ^{:convenience true}
  points->path
  "Converts [x y] points to path commands.
  Convenience for (into [[:move-to p0]] (mapv (fn [p] [:line-to p]) rest))."
  ([points] (points->path points false))
  ([points closed?]
   (let [cmds (into [[:move-to (first points)]]
                    (mapv (fn [p] [:line-to p]) (rest points)))]
     (if closed? (conj cmds [:close]) cmds))))

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

  (text "Hello" [100 200] {:font/family "SansSerif" :font/size 48})
  )
