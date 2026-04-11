(ns examples-csp
  "Generates visual examples for all four constraint-based generators.
  Run: clj -M:dev -e '(require (quote examples-csp)) (examples-csp/generate-all!)'"
  (:require
    [eido.core :as eido]
    [eido.gen.coloring :as coloring]
    [eido.gen.grammar :as grammar]
    [eido.gen.knot :as knot]
    [eido.gen.prob :as prob]
    [eido.gen.subdivide :as subdivide]
    [eido.gen.tiling :as tiling]
    [eido.gen.voronoi :as voronoi]))

(def ^:private out-dir "data/examples")

(defn- render! [scene path]
  (eido/render-to-file scene path)
  (println "  Rendered:" path))

(defn- stroke [color width]
  {:color color :width width})

;; --- Coloring examples ---

(defn- coloring-examples! []
  (println "Generating coloring examples...")
  (let [palettes
        [[[:color/rgb 230 80 60] [:color/rgb 240 160 50]
          [:color/rgb 250 210 80] [:color/rgb 180 60 90]]
         [[:color/rgb 20 60 120] [:color/rgb 40 120 180]
          [:color/rgb 80 180 220] [:color/rgb 200 230 240]]
         [[:color/rgb 30 80 50] [:color/rgb 60 140 70]
          [:color/rgb 140 180 60] [:color/rgb 200 160 40]]
         [[:color/rgb 255 50 100] [:color/rgb 50 255 150]
          [:color/rgb 100 50 255] [:color/rgb 255 200 50]]
         [[:color/rgb 140 90 60] [:color/rgb 200 160 100]
          [:color/rgb 80 60 40] [:color/rgb 220 200 160]]]]

    ;; Voronoi coloring
    (doseq [i (range 5)]
      (let [seed     (* (inc i) 17)
            n-points (+ 8 (* i 6))
            rng      (prob/make-rng seed)
            points   (mapv (fn [_] [(+ 20 (* (.nextDouble ^java.util.Random rng) 360))
                                    (+ 20 (* (.nextDouble ^java.util.Random rng) 360))])
                           (range n-points))
            cells    (voronoi/voronoi-cells points [0 0 400 400])
            adj      (coloring/cells-adjacency cells)
            colored  (coloring/color-regions cells adj (nth palettes i) {:seed seed})]
        (when colored
          (render!
            {:image/size [400 400]
             :image/background [:color/rgb 30 30 30]
             :image/nodes (mapv #(assoc % :style/stroke (stroke [:color/rgb 30 30 30] 2))
                                colored)}
            (str out-dir "/coloring/voronoi-" (inc i) ".png")))))

    ;; Subdivision coloring
    (doseq [i (range 5)]
      (let [seed    (* (+ i 6) 13)
            depth   (+ 3 (mod i 3))
            padding (if (even? i) 4 0)
            rects   (subdivide/subdivide [0 0 400 400]
                      {:seed seed :depth depth :padding padding})
            adj     (coloring/rects-adjacency rects)
            nodes   (mapv (fn [{[x y w h] :rect}]
                            {:node/type :shape/rect :rect/xy [x y] :rect/size [w h]})
                          rects)
            colored (coloring/color-regions nodes adj (nth palettes (mod i 5)) {:seed seed})]
        (when colored
          (render!
            {:image/size [400 400]
             :image/background [:color/rgb 30 30 30]
             :image/nodes (mapv #(assoc % :style/stroke (stroke [:color/rgb 30 30 30] 1.5))
                                colored)}
            (str out-dir "/coloring/subdivide-" (inc i) ".png")))))))

;; --- Tiling examples ---

(defn- tiling-examples! []
  (println "Generating tiling examples...")
  (let [size 400 cs 20]
    ;; Truchet arcs
    (doseq [i (range 4)]
      (let [tiles (tiling/truchet-arcs)
            grid  (tiling/random-grid 2 (quot size cs) (quot size cs) (* (inc i) 31))
            nodes (tiling/tiling->nodes grid tiles cs)]
        (render!
          {:image/size [size size]
           :image/background [:color/rgb 245 240 230]
           :image/nodes (mapv #(assoc % :style/stroke (stroke [:color/rgb 40 40 60] 2)) nodes)}
          (str out-dir "/tiling/truchet-" (inc i) ".png"))))

    ;; Truchet triangles
    (doseq [i (range 2)]
      (let [tiles (tiling/truchet-triangles)
            grid  (tiling/random-grid 2 (quot size cs) (quot size cs) (* (+ i 10) 7))
            nodes (tiling/tiling->nodes grid tiles cs)]
        (render!
          {:image/size [size size]
           :image/background [:color/rgb 240 235 220]
           :image/nodes (mapv #(assoc % :style/stroke (stroke [:color/rgb 80 50 30] 1.5)) nodes)}
          (str out-dir "/tiling/triangles-" (inc i) ".png"))))

    ;; Wang tiles (constraint-solved, rendered as colored grid)
    (doseq [i (range 2)]
      (let [tiles  (tiling/wang-basic)
            grid   (tiling/solve tiles 4 4 {:seed (* (+ i 5) 11)})
            colors {0 [:color/hsl 0 0.6 0.55]
                    1 [:color/hsl 120 0.6 0.55]
                    2 [:color/hsl 240 0.6 0.55]
                    3 [:color/hsl 50 0.6 0.55]}]
        (when grid
          (render!
            {:image/size [size size]
             :image/background [:color/rgb 30 30 30]
             :image/nodes (vec (for [r (range 4) c (range 4)]
                                 {:node/type :shape/rect
                                  :rect/xy [(* c 100) (* r 100)]
                                  :rect/size [100 100]
                                  :style/fill (colors (get-in grid [r c]))
                                  :style/stroke (stroke [:color/rgb 30 30 30] 2)}))}
            (str out-dir "/tiling/wang-" (inc i) ".png")))))

    ;; Pipe tiles (constraint-solved, seeds 1 and 3 known to work fast)
    (doseq [[idx seed] [[1 1] [2 3]]]
      (let [tiles (tiling/pipe-tiles)
            grid  (tiling/solve tiles 4 4 {:seed seed})]
        (when grid
          (render!
            {:image/size [size size]
             :image/background [:color/rgb 20 25 35]
             :image/nodes (mapv #(assoc % :style/stroke (stroke [:color/rgb 60 120 180] 3))
                                (tiling/tiling->nodes grid tiles 100))}
            (str out-dir "/tiling/pipes-" idx ".png")))))))

;; --- Knot examples ---

(defn- knot-examples! []
  (println "Generating knot examples...")
  (let [size 400]
    ;; Regular knots
    (doseq [i (range 4)]
      (let [n    (+ 5 (* i 3))
            cs   (/ (- size 40.0) (dec n))
            grid (knot/solve n n {:seed (mod i 2)})]
        (render!
          {:image/size [size size]
           :image/background [:color/rgb 245 240 225]
           :image/nodes (knot/knot->nodes grid cs [20 20]
                          {:gap 0.2
                           :style {:style/stroke (stroke [:color/rgb 40 60 120] 3)}})}
          (str out-dir "/knot/regular-" (inc i) ".png"))))

    ;; Knots with holes
    (doseq [i (range 4)]
      (let [n     8
            cs    (/ (- size 40.0) (dec n))
            rng   (prob/make-rng (* (inc i) 37))
            holes (into #{}
                    (take (+ 3 i)
                      (repeatedly #(vector (.nextInt ^java.util.Random rng n)
                                           (.nextInt ^java.util.Random rng n)))))
            grid  (knot/solve n n {:seed 0 :holes holes})]
        (render!
          {:image/size [size size]
           :image/background [:color/rgb 240 235 220]
           :image/nodes (knot/knot->nodes grid cs [20 20]
                          {:gap 0.18
                           :style {:style/stroke (stroke [:color/rgb 120 50 30] 2.5)}})}
          (str out-dir "/knot/holes-" (inc i) ".png"))))

    ;; Dense knots
    (doseq [i (range 2)]
      (let [n    15
            cs   (/ (- size 20.0) (dec n))
            grid (knot/solve n n {:seed (mod i 2)})]
        (render!
          {:image/size [size size]
           :image/background [:color/rgb 20 20 30]
           :image/nodes (knot/knot->nodes grid cs [10 10]
                          {:gap 0.15
                           :style {:style/stroke (stroke [:color/rgb 200 180 120] 1.5)}})}
          (str out-dir "/knot/dense-" (inc i) ".png"))))))

;; --- Grammar examples ---

(defn- grammar-examples! []
  (println "Generating grammar examples...")
  (let [size 400]
    ;; Bush variants
    (doseq [i (range 4)]
      (let [rules {"F" ["FF+[+F-F-F]-[-F+F+F]" "F+[+F]-[-F]" "F"]}
            angle (+ 20 (* i 5))
            cmds  (grammar/grammar->path-cmds "F" rules
                    {:iterations 4 :angle angle :length 4.0
                     :origin [200 380] :heading -90
                     :bounds [10 10 380 380] :seed (* (inc i) 23)})]
        (render!
          {:image/size [size size]
           :image/background [:color/rgb 245 242 235]
           :image/nodes [{:node/type :shape/path
                          :path/commands cmds
                          :style/stroke (stroke [:color/rgb 50 80 40] 1.5)}]}
          (str out-dir "/grammar/bush-" (inc i) ".png"))))

    ;; Koch-like
    (doseq [i (range 3)]
      (let [rules {"F" ["F+F-F-F+F" "F+F-F" "F"]}
            cmds  (grammar/grammar->path-cmds "F" rules
                    {:iterations 4 :angle 90 :length 3
                     :origin [50 300] :heading 0
                     :bounds [10 10 380 380] :seed (* (inc i) 41)})]
        (render!
          {:image/size [size size]
           :image/background [:color/rgb 240 240 245]
           :image/nodes [{:node/type :shape/path
                          :path/commands cmds
                          :style/stroke (stroke [:color/rgb 60 40 100] 1)}]}
          (str out-dir "/grammar/koch-" (inc i) ".png"))))

    ;; Sierpinski variants
    (doseq [i (range 3)]
      (let [rules {"F" ["F-G+F+G-F" "F+G-F" "F"]
                   "G" ["GG" "G"]}
            cmds  (grammar/grammar->path-cmds "F-G-G" rules
                    {:iterations 4 :angle 120 :length 4
                     :origin [50 350] :heading 0
                     :bounds [10 10 380 380] :seed (* (inc i) 57)})]
        (render!
          {:image/size [size size]
           :image/background [:color/rgb 30 25 35]
           :image/nodes [{:node/type :shape/path
                          :path/commands cmds
                          :style/stroke (stroke [:color/rgb 200 160 80] 1)}]}
          (str out-dir "/grammar/sierpinski-" (inc i) ".png"))))))

;; --- Generate all ---

(defn generate-all! []
  (println "Generating constraint-based generator examples...")
  (coloring-examples!)
  (tiling-examples!)
  (knot-examples!)
  (grammar-examples!)
  (println "Done! Examples in" out-dir))
