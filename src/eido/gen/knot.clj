(ns eido.gen.knot
  "Celtic knot pattern generation via constraint satisfaction.

  A Celtic knot lives on a grid of crossing points. At each crossing,
  one strand passes over the other. Two constraints govern the pattern:

  1. Alternation — a strand alternates over, under, over, under as it
     travels through successive crossings.
  2. Connectivity — strands form closed loops.

  The solver assigns over/under states to crossings. The renderer draws
  smooth curved strands that bow outward at each crossing, creating the
  characteristic interlocking ribbon pattern. Undercrossings show a gap
  where one strand passes beneath another."
  (:require
    [clojure.core.logic :as l]
    [clojure.core.logic.fd :as fd]
    [eido.gen.prob :as prob]))

;; --- crossing grid solver ---

(defn solve
  "Assigns over/under states to a grid of crossings.
  rows, cols: grid dimensions
  opts:
    :seed (42)      — controls which valid pattern is found
    :holes #{}      — set of [r c] positions with no crossing
    :fixed {}       — map of [r c] -> 0 or 1 (pin specific crossings)

  Returns a 2D vector where each cell is 0 (horizontal-over),
  1 (vertical-over), or nil (hole)."
  [rows cols opts]
  (let [seed   (get opts :seed 42)
        holes  (get opts :holes #{})
        fixed  (get opts :fixed {})
        coords (vec (for [r (range rows) c (range cols)
                          :when (not (holes [r c]))]
                      [r c]))
        coord->idx (zipmap coords (range))
        n      (count coords)
        vars   (vec (repeatedly n l/lvar))
        domain (fd/interval 0 1)
        adj-pairs (into []
                    (mapcat
                      (fn [[r c]]
                        (cond-> []
                          (coord->idx [r (inc c)])
                          (conj [(coord->idx [r c]) (coord->idx [r (inc c)])])
                          (coord->idx [(inc r) c])
                          (conj [(coord->idx [r c]) (coord->idx [(inc r) c])]))))
                    coords)
        all-fixed (merge {(first coords) (mod seed 2)} fixed)]
    (when (pos? n)
      (when-let [result
                 (first
                   (l/run 1 [q]
                     (l/== q vars)
                     (l/everyg #(fd/in % domain) vars)
                     (l/everyg (fn [[[r c] val]]
                                 (if-let [idx (coord->idx [r c])]
                                   (fd/== (nth vars idx) val)
                                   l/succeed))
                               all-fixed)
                     (l/everyg (fn [[i j]]
                                 (fd/!= (nth vars i) (nth vars j)))
                               adj-pairs)))]
        (let [assignment (zipmap coords result)]
          (mapv (fn [r]
                  (mapv (fn [c]
                          (when-not (holes [r c])
                            (get assignment [r c])))
                        (range cols)))
                (range rows)))))))

;; --- strand tracing ---

(defn- cell-state [grid r c]
  (when (and (>= r 0) (< r (count grid))
             (>= c 0) (< c (count (first grid))))
    (get-in grid [r c])))

(defn- opposite-port [port]
  (case port :w :e, :e :w, :n :s, :s :n))

(defn- next-crossing
  "Returns [row col entry-port] of the next crossing from exit-port."
  [r c exit-port]
  (case exit-port
    :e [r (inc c) :w]
    :w [r (dec c) :e]
    :s [(inc r) c :n]
    :n [(dec r) c :s]))

(defn- trace-one-strand
  "Traces a single strand starting at (r, c) entering from port.
  Returns [strand visited-ports]."
  [grid rows cols start-r start-c start-port visited]
  (loop [cr start-r cc start-c port start-port
         strand [] seen visited]
    (if-let [state (cell-state grid cr cc)]
      (if (seen [cr cc port])
        [strand seen]
        (let [horizontal? (#{:w :e} port)
              h-over?     (zero? state)
              over?       (if horizontal? h-over? (not h-over?))
              exit-port   (opposite-port port)
              new-seen    (conj seen [cr cc port] [cr cc exit-port])
              step        {:pos [cr cc] :enter port :exit exit-port :over? over?}
              [nr nc np]  (next-crossing cr cc exit-port)]
          (if (and (>= nr 0) (< nr rows) (>= nc 0) (< nc cols)
                   (some? (cell-state grid nr nc))
                   (not (new-seen [nr nc np])))
            (recur nr nc np (conj strand step) new-seen)
            [(conj strand step) new-seen])))
      [strand seen])))

(defn trace-strands
  "Traces all distinct strands through the knot grid.
  Returns a vector of strands, each a vector of crossing visits."
  [grid]
  (let [rows (count grid)
        cols (count (first grid))]
    (loop [coords (for [r (range rows) c (range cols)
                        :when (some? (cell-state grid r c))
                        port [:w :n]]
                    [r c port])
           visited #{}
           strands []]
      (if-let [[r c port] (first coords)]
        (if (visited [r c port])
          (recur (rest coords) visited strands)
          (let [[strand new-visited] (trace-one-strand grid rows cols r c port visited)]
            (recur (rest coords) new-visited
                   (if (seq strand) (conj strands strand) strands))))
        strands))))

;; --- curved rendering ---
;;
;; The key visual trick: at each crossing, strands bow outward
;; perpendicular to their travel direction. The bow direction is
;; determined by the crossing state, creating a consistent rotational
;; flow. State 0 crossings rotate one way, state 1 the other.
;; Since the checkerboard alternates states, adjacent crossings
;; rotate in opposite directions — producing the sinusoidal weave.
;;
;; State 0 (h-over): H bows up, V bows right
;; State 1 (v-over): H bows down, V bows left

(defn- bezier-point
  "Evaluates quadratic Bezier at parameter t."
  [[x0 y0] [cx cy] [x1 y1] t]
  (let [t (double t) u (- 1.0 t)]
    [(+ (* u u (double x0)) (* 2.0 u t (double cx)) (* t t (double x1)))
     (+ (* u u (double y0)) (* 2.0 u t (double cy)) (* t t (double y1)))]))

(defn- crossing-node
  "Renders a single strand segment at a crossing as a curved path node.
  cfg: {:cs :bow :gap :ox :oy :grid}"
  [cfg visit base-style]
  (let [{:keys [cs bow gap ox oy grid]} cfg
        h     (/ (double cs) 2.0)
        {:keys [pos enter exit over?]} visit
        [r c] pos
        ;; Crossing center
        ccx   (+ (double ox) (* (double c) (double cs)))
        ccy   (+ (double oy) (* (double r) (double cs)))
        ;; Port positions (edge midpoints)
        port-xy (fn [port]
                  (case port
                    :w [(- ccx h) ccy]
                    :e [(+ ccx h) ccy]
                    :n [ccx (- ccy h)]
                    :s [ccx (+ ccy h)]))
        p1    (port-xy enter)
        p2    (port-xy exit)
        ;; Bow: perpendicular offset based on crossing state
        ;; State 0: H up, V right. State 1: H down, V left.
        state (long (get-in grid [r c]))
        horiz? (#{:w :e} enter)
        ctrl  (if horiz?
                [ccx (if (zero? state) (- ccy (double bow)) (+ ccy (double bow)))]
                [(if (zero? state) (+ ccx (double bow)) (- ccx (double bow))) ccy])]
    (if over?
      (merge base-style
        {:node/type     :shape/path
         :path/commands [[:move-to p1] [:quad-to ctrl p2]]})
      (let [gap-t (/ (double gap) (double cs))
            t1    (max 0.05 (- 0.5 gap-t))
            t2    (min 0.95 (+ 0.5 gap-t))
            mid1  (bezier-point p1 ctrl p2 t1)
            mid2  (bezier-point p1 ctrl p2 t2)]
        (merge base-style
          {:node/type     :shape/path
           :path/commands [[:move-to p1] [:line-to mid1]
                           [:move-to mid2] [:line-to p2]]})))))

(defn knot->nodes
  "Converts a knot grid to scene nodes with smooth curved strands.

  At each crossing, strands bow outward to create flowing ribbons.
  Undercrossings show a gap where the strand passes beneath.

  grid:      2D vector from solve
  cell-size: pixel spacing between crossings
  offset:    [x y] origin
  opts:
    :gap     (0.15) — fraction of cell-size for undercrossing gap
    :bow     (0.3)  — fraction of cell-size for curve amplitude
    :style   {}     — base style merged into all nodes
    :palette []     — distinct color per strand (cycles if fewer than strands)"
  ([grid cell-size] (knot->nodes grid cell-size [0 0] {}))
  ([grid cell-size offset] (knot->nodes grid cell-size offset {}))
  ([grid cell-size offset opts]
   (let [[ox oy] offset
         cs       (double cell-size)
         bow      (* cs (double (get opts :bow 0.3)))
         gap      (* cs (double (get opts :gap 0.15)))
         style    (:style opts)
         palette  (:palette opts)
         strands  (trace-strands grid)
         cfg      {:cs cs :bow bow :gap gap :ox ox :oy oy :grid grid}]
     (into []
       (mapcat
         (fn [strand-idx strand]
           (let [strand-color (when palette
                                (nth palette (mod strand-idx (count palette))))
                 base-style (cond-> (or style {})
                              strand-color
                              (assoc :style/stroke
                                {:color strand-color
                                 :width (get-in style [:style/stroke :width] 2)}))]
             (mapv #(crossing-node cfg % base-style) strand)))
         (range) strands)))))

(comment
  (let [grid (solve 6 6 {:seed 0})]
    (trace-strands grid))

  (let [grid (solve 5 5 {:seed 0 :holes #{[2 2]}})]
    (knot->nodes grid 50 [25 25]
      {:gap 0.18 :bow 0.3
       :palette [[:color/rgb 180 50 50]
                 [:color/rgb 50 100 180]
                 [:color/rgb 50 160 80]]
       :style {:style/stroke {:color [:color/rgb 40 40 40] :width 3}}}))
  )
