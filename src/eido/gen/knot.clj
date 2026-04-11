(ns eido.gen.knot
  "Celtic knot pattern generation via constraint satisfaction.

  A Celtic knot is drawn on a grid of crossing points. At each crossing,
  one strand passes over the other. The key constraints are:

  1. Alternation: as a strand travels through crossings, it must
     alternate over-under-over-under.
  2. Connectivity: strands form closed loops (every crossing has
     exactly one strand entering and one leaving on each axis).

  The solver assigns over/under states to each crossing such that
  these constraints are globally satisfied, then traces the resulting
  strands as paths with gaps at undercrossings."
  (:require
    [clojure.core.logic :as l]
    [clojure.core.logic.fd :as fd]
    [eido.gen.prob :as prob]))

;; --- crossing grid model ---
;;
;; Grid of crossings at integer coordinates (r, c).
;; Each crossing has a state: 0 = horizontal-over (H passes over V)
;;                            1 = vertical-over   (V passes over H)
;;
;; For proper alternation on horizontal strands:
;;   crossing (r, c) and crossing (r, c+1) must differ
;;   (the horizontal strand alternates over/under as it moves right)
;;
;; For proper alternation on vertical strands:
;;   crossing (r, c) and crossing (r+1, c) must differ
;;   (the vertical strand alternates as it moves down)
;;
;; This means: every pair of adjacent crossings (horizontal or vertical)
;; must have different states. This is exactly graph 2-coloring on the
;; grid graph — a checkerboard pattern is always valid!
;;
;; To make it interesting, we support irregular grids where some
;; crossings are absent, breaking the simple checkerboard.

(defn solve
  "Assigns over/under states to a grid of crossings.
  rows, cols: grid dimensions
  opts:
    :seed (42)      — controls which valid pattern is found
    :holes #{}      — set of [r c] positions with no crossing (creates
                       irregular patterns that break the checkerboard)
    :fixed {}       — map of [r c] -> 0 or 1 (pin specific crossings)

  Returns a 2D vector where each cell is 0 (horizontal-over),
  1 (vertical-over), or nil (hole). Returns nil if unsatisfiable."
  [rows cols opts]
  (let [seed   (get opts :seed 42)
        holes  (get opts :holes #{})
        fixed  (get opts :fixed {})
        ;; Create variables for non-hole crossings
        coords (for [r (range rows) c (range cols)
                     :when (not (holes [r c]))]
                 [r c])
        coord->idx (zipmap coords (range))
        n      (count coords)
        vars   (vec (repeatedly n l/lvar))
        domain (fd/interval 0 1)
        ;; Adjacent pairs: crossings that share a strand segment
        adj-pairs (into []
                    (keep (fn [[r c]]
                            (let [right [r (inc c)]
                                  below [(inc r) c]]
                              (concat
                                (when (coord->idx right)
                                  [[(coord->idx [r c]) (coord->idx right)]])
                                (when (coord->idx below)
                                  [[(coord->idx [r c]) (coord->idx below)]])))))
                    coords)
        adj-pairs (into [] (mapcat identity) adj-pairs)
        ;; Seed variety: pin the first crossing based on seed
        seed-pin (mod seed 2)
        ;; Fixed constraints from opts + seed
        all-fixed (merge {(first coords) seed-pin} fixed)]
    (when (pos? n)
      (when-let [result
                 (first
                   (l/run 1 [q]
                     (l/== q vars)
                     (l/everyg #(fd/in % domain) vars)
                     ;; Pin fixed crossings
                     (l/everyg (fn [[[r c] val]]
                                 (if-let [idx (coord->idx [r c])]
                                   (fd/== (nth vars idx) val)
                                   l/succeed))
                               all-fixed)
                     ;; Alternation: adjacent crossings must differ
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

(defn- trace-strand
  "Traces a single strand through the crossing grid.
  Starts at (r, c) moving in direction dir (:right, :left, :up, :down).
  Returns a sequence of {:pos [r c] :over? bool :dir dir} steps."
  [grid rows cols start-r start-c start-dir]
  (loop [r start-r c start-c dir start-dir steps []]
    (if (or (< r 0) (>= r rows) (< c 0) (>= c cols)
            (nil? (get-in grid [r c])))
      steps
      (let [state (get-in grid [r c])
            h-over? (zero? state)
            over? (case dir
                    (:right :left) h-over?
                    (:up :down) (not h-over?))
            step {:pos [r c] :over? over? :dir dir}
            ;; At a crossing, the strand turns:
            ;; If we're the horizontal strand, continue horizontally
            ;; If we're the vertical strand, continue vertically
            next-dir (case dir
                       :right :right
                       :left  :left
                       :down  :down
                       :up    :up)
            [nr nc] (case next-dir
                      :right [r (inc c)]
                      :left  [r (dec c)]
                      :down  [(inc r) c]
                      :up    [(dec r) c])]
        ;; Stop if we've returned to start (closed loop)
        (if (and (seq steps)
                 (= r start-r) (= c start-c) (= dir start-dir))
          steps
          (recur nr nc next-dir (conj steps step)))))))

;; --- rendering ---

(defn knot->nodes
  "Converts a knot grid to scene nodes (path segments with gaps).
  grid:      2D vector from solve
  cell-size: pixel spacing between crossings
  offset:    [x y] origin
  opts:
    :gap     (0.15)  — fraction of cell-size for undercrossing gap
    :style   {}      — base style merged into all path nodes"
  ([grid cell-size] (knot->nodes grid cell-size [0 0] {}))
  ([grid cell-size offset] (knot->nodes grid cell-size offset {}))
  ([grid cell-size offset opts]
   (let [[ox oy] offset
         ox   (double ox) oy (double oy)
         cs   (double cell-size)
         gap  (* cs (double (get opts :gap 0.15)))
         style (:style opts)
         rows (count grid)
         cols (count (first grid))
         px   (fn [c] (+ ox (* (double c) cs)))
         py   (fn [r] (+ oy (* (double r) cs)))
         nodes (atom [])]
     ;; Draw horizontal segments
     (doseq [r (range rows)]
       (doseq [c (range cols)]
         (when-let [state (get-in grid [r c])]
           (let [x (px c) y (py r)
                 h-over? (zero? state)]
             ;; Horizontal strand at this crossing
             (let [x-left  (if (pos? c) (- x (/ cs 2.0)) x)
                   x-right (if (< c (dec cols)) (+ x (/ cs 2.0)) x)]
               (if h-over?
                 ;; Horizontal is on top — draw full segment
                 (swap! nodes conj
                   {:node/type :shape/path
                    :path/commands [[:move-to [x-left y]] [:line-to [x-right y]]]})
                 ;; Horizontal is under — draw with gap at crossing center
                 (do
                   (when (> (- x gap) x-left)
                     (swap! nodes conj
                       {:node/type :shape/path
                        :path/commands [[:move-to [x-left y]]
                                        [:line-to [(- x gap) y]]]}))
                   (when (< (+ x gap) x-right)
                     (swap! nodes conj
                       {:node/type :shape/path
                        :path/commands [[:move-to [(+ x gap) y]]
                                        [:line-to [x-right y]]]})))))
             ;; Vertical strand at this crossing
             (let [y-top    (if (pos? r) (- y (/ cs 2.0)) y)
                   y-bottom (if (< r (dec rows)) (+ y (/ cs 2.0)) y)]
               (if (not h-over?)
                 ;; Vertical is on top — draw full segment
                 (swap! nodes conj
                   {:node/type :shape/path
                    :path/commands [[:move-to [x y-top]] [:line-to [x y-bottom]]]})
                 ;; Vertical is under — draw with gap
                 (do
                   (when (> (- y gap) y-top)
                     (swap! nodes conj
                       {:node/type :shape/path
                        :path/commands [[:move-to [x y-top]]
                                        [:line-to [x (- y gap)]]]}))
                   (when (< (+ y gap) y-bottom)
                     (swap! nodes conj
                       {:node/type :shape/path
                        :path/commands [[:move-to [x (+ y gap)]]
                                        [:line-to [x y-bottom]]]})))))))))
     (let [result @nodes]
       (if style (mapv #(merge style %) result) result)))))

(comment
  ;; Simple 4x4 knot
  (let [grid (solve 4 4 {:seed 42})]
    (println grid)
    (knot->nodes grid 40 [20 20]
      {:style {:style/stroke [:color/rgb 40 60 120]
               :style/stroke-width 3}}))

  ;; Knot with holes (irregular pattern)
  (let [grid (solve 5 5 {:seed 7
                          :holes #{[1 1] [2 3] [3 2]}})]
    (println grid)
    (knot->nodes grid 40 [20 20]))

  ;; Different seeds
  (doseq [s (range 4)]
    (println "Seed" s ":" (solve 3 3 {:seed s})))
  )
