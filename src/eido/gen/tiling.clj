(ns eido.gen.tiling
  "Constraint-based tiling and random tile assignment.
  Two modes:
  - Random grids: any tile can go anywhere (Truchet). Seed controls placement.
  - Constraint grids: adjacent edges must match (Wang tiles). Core.logic
    CLP(FD) finds valid tilings. Seed controls which solution is found.

  The tile set definition is the creative input — different tiles produce
  Truchet curves, maze patterns, circuit-board aesthetics, textile designs."
  (:require
    [clojure.core.logic :as l]
    [clojure.core.logic.fd :as fd]
    [eido.gen.prob :as prob]))

;; --- tile definitions ---

(defn tile
  "Creates a tile definition.
  id:    keyword identifier
  edges: {:n label :e label :s label :w label}
  draw:  keyword referencing a built-in visual or custom draw data."
  [id edges draw]
  {:tile/id    id
   :tile/edges edges
   :tile/draw  draw})

;; --- built-in tile sets ---

(defn truchet-arcs
  "Two Truchet tiles with quarter-arc pairs.
  Unconstrained — any tile can go anywhere. Visual interest comes from
  random arrangement creating emergent maze-like curves."
  []
  [(tile :truchet-a {:n :x :e :x :s :x :w :x} :truchet-a)
   (tile :truchet-b {:n :x :e :x :s :x :w :x} :truchet-b)])

(defn truchet-triangles
  "Two Truchet tiles with diagonal divisions.
  Unconstrained — produces emergent triangular patterns."
  []
  [(tile :tri-a {:n :x :e :x :s :x :w :x} :tri-a)
   (tile :tri-b {:n :x :e :x :s :x :w :x} :tri-b)])

(defn wang-basic
  "Four Wang tiles with 2 edge labels. No tile is self-compatible —
  the solver must mix tiles, producing non-trivial patterns.
  Each tile has exactly 2 valid horizontal and 2 valid vertical neighbors."
  []
  [(tile :w0 {:n :a :e :a :s :b :w :b} :wang-0)
   (tile :w1 {:n :b :e :b :s :a :w :a} :wang-1)
   (tile :w2 {:n :a :e :b :s :b :w :a} :wang-2)
   (tile :w3 {:n :b :e :a :s :a :w :b} :wang-3)])

(defn pipe-tiles
  "Six pipe/maze tiles: straight segments and elbows.
  Edge labels encode connectivity (open :o / closed :c) so pipes
  must connect properly at cell boundaries."
  []
  [(tile :pipe-h  {:n :c :e :o :s :c :w :o} :pipe-h)
   (tile :pipe-v  {:n :o :e :c :s :o :w :c} :pipe-v)
   (tile :elbow-ne {:n :o :e :o :s :c :w :c} :elbow-ne)
   (tile :elbow-se {:n :c :e :o :s :o :w :c} :elbow-se)
   (tile :elbow-sw {:n :c :e :c :s :o :w :o} :elbow-sw)
   (tile :elbow-nw {:n :o :e :c :s :c :w :o} :elbow-nw)])

;; --- random grid (unconstrained tiles) ---

(defn random-grid
  "Random tile assignment for unconstrained tile sets (Truchet etc.).
  Returns a 2D vector of tile indices.

  n-tiles:    number of available tiles
  cols, rows: grid dimensions
  seed:       for deterministic randomness
  opts (optional):
    :weight-fn (fn [row col] {tile-idx weight}) — spatial tile probability.
               Tiles with higher weight are more likely at that position.
               Omit or return nil for uniform distribution."
  ([n-tiles cols rows seed] (random-grid n-tiles cols rows seed {}))
  ([n-tiles cols rows seed opts]
   (let [rng       (prob/make-rng seed)
         weight-fn (:weight-fn opts)]
     (mapv (fn [r]
             (mapv (fn [c]
                     (if weight-fn
                       (let [weights (weight-fn r c)]
                         (if (and weights (seq weights))
                           ;; Weighted random selection
                           (let [entries (vec weights)
                                 total   (reduce + (map val entries))
                                 roll    (* (.nextDouble ^java.util.Random rng) (double total))]
                             (loop [remaining entries acc 0.0]
                               (if (empty? remaining)
                                 (key (first entries))
                                 (let [[tile-idx w] (first remaining)
                                       acc' (+ acc (double w))]
                                   (if (>= acc' roll)
                                     tile-idx
                                     (recur (rest remaining) acc'))))))
                           (.nextInt ^java.util.Random rng n-tiles)))
                       (.nextInt ^java.util.Random rng n-tiles)))
                   (range cols)))
           (range rows)))))

;; --- constraint solver ---

(defn- one-of
  "Goal: (vi, vj) must be one of the given [a b] pairs.
  Recursive conde chain — necessary because l/conde is a macro
  and can't take a dynamic collection."
  [vi vj pairs]
  (if (= 1 (count pairs))
    (let [[a b] (first pairs)]
      (l/all (fd/== vi a) (fd/== vj b)))
    (let [[a b] (first pairs)]
      (l/conde
        [(fd/== vi a) (fd/== vj b)]
        [(one-of vi vj (rest pairs))]))))

(defn solve
  "Solves a constrained tiling where adjacent tile edges must match.
  Uses core.logic CLP(FD) to find valid arrangements.

  tiles:      vector of tile definitions with :tile/edges
  cols, rows: grid dimensions
  opts:
    :seed (42) — pins the top-left cell to a specific tile, producing
                 different valid tilings for different seeds

  Returns a 2D vector of tile indices, or nil if no valid tiling exists."
  [tiles cols rows opts]
  (let [n       (* cols rows)
        seed    (get opts :seed 42)
        n-tiles (count tiles)
        ;; Which tile pairs are compatible as horizontal / vertical neighbors?
        ew-compat (vec (for [i (range n-tiles) j (range n-tiles)
                             :when (= (:e (:tile/edges (nth tiles i)))
                                      (:w (:tile/edges (nth tiles j))))]
                         [i j]))
        ns-compat (vec (for [i (range n-tiles) j (range n-tiles)
                             :when (= (:s (:tile/edges (nth tiles i)))
                                      (:n (:tile/edges (nth tiles j))))]
                         [i j]))
        ;; Seed variety: pin cell 0 to a seed-dependent tile,
        ;; and shuffle the compat pair ordering so the solver explores
        ;; different branches of the search tree.
        pinned-tile (mod seed n-tiles)
        ew-shuffled (prob/shuffle-seeded ew-compat seed)
        ns-shuffled (prob/shuffle-seeded ns-compat seed)
        vars    (vec (repeatedly n l/lvar))
        domain  (fd/interval 0 (dec n-tiles))
        idx     (fn [r c] (+ (* r cols) c))
        h-pairs (vec (for [r (range rows) c (range (dec cols))]
                       [(idx r c) (idx r (inc c))]))
        v-pairs (vec (for [r (range (dec rows)) c (range cols)]
                       [(idx r c) (idx (inc r) c)]))]
    (when (and (seq ew-shuffled) (seq ns-shuffled))
      (when-let [result
                 (first
                   (l/run 1 [q]
                     (l/== q vars)
                     (l/everyg #(fd/in % domain) vars)
                     ;; Pin top-left for seed variety
                     (fd/== (first vars) pinned-tile)
                     ;; Edge-matching constraints
                     (l/everyg (fn [[li ri]]
                                 (one-of (nth vars li) (nth vars ri) ew-shuffled))
                               h-pairs)
                     (l/everyg (fn [[ti bi]]
                                 (one-of (nth vars ti) (nth vars bi) ns-shuffled))
                               v-pairs)))]
        (mapv (fn [r]
                (mapv (fn [c] (nth result (idx r c)))
                      (range cols)))
              (range rows))))))

;; --- rendering helpers ---

(defn- truchet-a-paths [x y size]
  (let [s (double size) h (/ s 2.0)
        x (double x) y (double y)]
    [{:node/type     :shape/path
      :path/commands [[:move-to [(+ x h) y]]
                      [:quad-to [x y] [x (+ y h)]]]}
     {:node/type     :shape/path
      :path/commands [[:move-to [(+ x s) (+ y h)]]
                      [:quad-to [(+ x s) (+ y s)] [(+ x h) (+ y s)]]]}]))

(defn- truchet-b-paths [x y size]
  (let [s (double size) h (/ s 2.0)
        x (double x) y (double y)]
    [{:node/type     :shape/path
      :path/commands [[:move-to [(+ x h) y]]
                      [:quad-to [(+ x s) y] [(+ x s) (+ y h)]]]}
     {:node/type     :shape/path
      :path/commands [[:move-to [x (+ y h)]]
                      [:quad-to [x (+ y s)] [(+ x h) (+ y s)]]]}]))

(defn- tri-a-paths [x y size]
  (let [s (double size) x (double x) y (double y)]
    [{:node/type     :shape/path
      :path/commands [[:move-to [x y]]
                      [:line-to [(+ x s) (+ y s)]]]}]))

(defn- tri-b-paths [x y size]
  (let [s (double size) x (double x) y (double y)]
    [{:node/type     :shape/path
      :path/commands [[:move-to [(+ x s) y]]
                      [:line-to [x (+ y s)]]]}]))

(defn- pipe-paths [x y size tile-id]
  (let [s (double size) h (/ s 2.0)
        x (double x) y (double y)]
    (case tile-id
      :pipe-h    [{:node/type :shape/path
                   :path/commands [[:move-to [x (+ y h)]]
                                  [:line-to [(+ x s) (+ y h)]]]}]
      :pipe-v    [{:node/type :shape/path
                   :path/commands [[:move-to [(+ x h) y]]
                                  [:line-to [(+ x h) (+ y s)]]]}]
      :elbow-ne  [{:node/type :shape/path
                   :path/commands [[:move-to [(+ x h) y]]
                                  [:quad-to [(+ x h) (+ y h)] [(+ x s) (+ y h)]]]}]
      :elbow-se  [{:node/type :shape/path
                   :path/commands [[:move-to [(+ x s) (+ y h)]]
                                  [:quad-to [(+ x h) (+ y h)] [(+ x h) (+ y s)]]]}]
      :elbow-sw  [{:node/type :shape/path
                   :path/commands [[:move-to [(+ x h) (+ y s)]]
                                  [:quad-to [(+ x h) (+ y h)] [x (+ y h)]]]}]
      :elbow-nw  [{:node/type :shape/path
                   :path/commands [[:move-to [x (+ y h)]]
                                  [:quad-to [(+ x h) (+ y h)] [(+ x h) y]]]}]
      [])))

(defn- wang-color [tile-def]
  (case (:tile/id tile-def)
    :w0 [:color/hsl 0 0.7 0.6]
    :w1 [:color/hsl 120 0.7 0.6]
    :w2 [:color/hsl 240 0.7 0.6]
    :w3 [:color/hsl 60 0.7 0.6]
    [:color/rgb 200 200 200]))

(defn- render-cell [x y size tile-def opts]
  (let [draw     (:tile/draw tile-def)
        color-fn (get opts :color-fn wang-color)]
    (case draw
      :truchet-a (truchet-a-paths x y size)
      :truchet-b (truchet-b-paths x y size)
      :tri-a     (tri-a-paths x y size)
      :tri-b     (tri-b-paths x y size)
      (:pipe-h :pipe-v :elbow-ne :elbow-se :elbow-sw :elbow-nw)
      (pipe-paths x y size draw)
      (:wang-0 :wang-1 :wang-2 :wang-3)
      [{:node/type  :shape/rect
        :rect/xy    [x y]
        :rect/size  [size size]
        :style/fill (color-fn tile-def)}]
      ;; fallback: colored rect
      [{:node/type  :shape/rect
        :rect/xy    [x y]
        :rect/size  [size size]
        :style/fill (color-fn tile-def)}])))

(defn tiling->nodes
  "Converts a tiling grid to scene nodes.
  grid:      2D vector of tile indices
  tiles:     the tile definitions
  cell-size: pixel size per cell
  offset:    [x y] origin (default [0 0])
  opts:
    :style     — base style map merged into path nodes
    :color-fn  — (fn [tile-def] color) for filled tiles"
  ([grid tiles cell-size] (tiling->nodes grid tiles cell-size [0 0] {}))
  ([grid tiles cell-size offset] (tiling->nodes grid tiles cell-size offset {}))
  ([grid tiles cell-size offset opts]
   (let [[ox oy] offset
         ox (double ox) oy (double oy)
         cs (double cell-size)
         style (:style opts)]
     (into []
       (mapcat
         (fn [r row]
           (mapcat
             (fn [c tile-idx]
               (let [x (+ ox (* c cs))
                     y (+ oy (* r cs))
                     nodes (render-cell x y cs (nth tiles tile-idx) opts)]
                 (if style (mapv #(merge style %) nodes) nodes)))
             (range) row))
         (range) grid)))))

(comment
  ;; Truchet: random grid
  (let [tiles (truchet-arcs)
        grid  (random-grid 2 10 10 42)]
    (tiling->nodes grid tiles 30 [0 0]
      {:style {:style/stroke [:color/rgb 40 40 40]
               :style/stroke-width 2}}))

  ;; Wang: constraint-solved
  (let [tiles (wang-basic)
        grid  (solve tiles 5 5 {:seed 42})]
    (tiling->nodes grid tiles 40))

  ;; Pipes: constraint-solved
  (let [tiles (pipe-tiles)
        grid  (solve tiles 6 6 {:seed 42})]
    (tiling->nodes grid tiles 30 [0 0]
      {:style {:style/stroke [:color/rgb 40 40 40]
               :style/stroke-width 2}}))
  )
