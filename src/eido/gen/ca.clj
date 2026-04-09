(ns eido.gen.ca
  "Cellular automata and Gray-Scott reaction-diffusion simulation.
  Grid-based evolution systems for organic pattern generation."
  (:require
    [eido.gen.prob :as prob]))

;; --- Cellular Automata ---

(defn ca-grid
  "Creates an initial CA grid.
  init: :random (seeded), :empty, or :center (single live cell).
  Returns {:grid boolean-array :w w :h h}."
  [w h init seed]
  (let [n   (* (int w) (int h))
        grid (boolean-array n)]
    (case init
      :random (let [rng (prob/make-rng seed)]
                (dotimes [i n]
                  (aset grid i (< (.nextDouble rng) 0.3))))
      :empty  nil
      :center (aset grid (+ (quot (int w) 2) (* (quot (int h) 2) (int w))) true))
    {:grid grid :w (int w) :h (int h)}))

(defn- count-neighbors
  "Counts live neighbors of cell (x, y) with wrapping."
  [^booleans grid w h x y]
  (let [w (int w) h (int h) x (int x) y (int y)]
    (loop [dx -1 cnt 0]
      (if (> dx 1)
        cnt
        (let [cnt (loop [dy -1 cnt cnt]
                    (if (> dy 1)
                      cnt
                      (if (and (zero? dx) (zero? dy))
                        (recur (inc dy) cnt)
                        (let [nx (mod (+ x dx) w)
                              ny (mod (+ y dy) h)]
                          (recur (inc dy)
                                 (if (aget grid (+ (* ny w) nx))
                                   (inc cnt)
                                   cnt))))))]
          (recur (inc dx) cnt))))))

(defn- resolve-rule
  "Returns [birth-set survive-set] from a rule spec."
  [rule]
  (case rule
    :life     [#{3} #{2 3}]
    :highlife [#{3 6} #{2 3}]
    (let [{:keys [birth survive]} rule]
      [(set birth) (set survive)])))

(defn ca-step
  "Advances a CA grid by one generation.
  rule: :life, :highlife, or {:birth #{...} :survive #{...}}."
  [{:keys [^booleans grid w h]} rule]
  (let [w (int w) h (int h)
        n (* w h)
        next-grid (boolean-array n)
        [birth survive] (resolve-rule rule)]
    (dotimes [y h]
      (dotimes [x w]
        (let [idx (+ (* y w) x)
              alive (aget grid idx)
              neighbors (count-neighbors grid w h x y)]
          (aset next-grid idx
                (if alive
                  (contains? survive neighbors)
                  (contains? birth neighbors))))))
    {:grid next-grid :w w :h h}))

(defn ca-run
  "Runs CA for n steps. Returns the final grid."
  [grid-map rule n]
  (loop [g grid-map i 0]
    (if (>= i n) g (recur (ca-step g rule) (inc i)))))

(defn ca->nodes
  "Renders a CA grid as scene nodes. Only alive cells produce nodes.
  alive-style: style map for alive cells (e.g., {:style/fill color}).
  cell-size: pixel size per cell."
  [{:keys [^booleans grid w h]} cell-size alive-style]
  (let [cs (double cell-size)]
    (into []
          (for [y (range h)
                x (range w)
                :when (aget grid (+ (* (int y) (int w)) (int x)))]
            (merge {:node/type :shape/rect
                    :rect/xy   [(* x cs) (* y cs)]
                    :rect/size [cs cs]}
                   alive-style)))))

;; --- Reaction-Diffusion (Gray-Scott) ---

(def rd-presets
  "Named parameter sets for Gray-Scott reaction-diffusion."
  {:coral   {:feed 0.0545 :kill 0.062 :da 1.0 :db 0.5 :dt 1.0}
   :mitosis {:feed 0.0367 :kill 0.0649 :da 1.0 :db 0.5 :dt 1.0}
   :waves   {:feed 0.014  :kill 0.054 :da 1.0 :db 0.5 :dt 1.0}
   :spots   {:feed 0.035  :kill 0.065 :da 1.0 :db 0.5 :dt 1.0}})

(defn rd-grid
  "Creates initial reaction-diffusion concentration grids.
  init: :center-seed (circle of B in center), :random-seeds.
  Returns {:a double-array :b double-array :w w :h h}."
  [w h init seed]
  (let [w (int w) h (int h)
        n (* w h)
        a (double-array n 1.0)
        b (double-array n 0.0)]
    (case init
      :center-seed
      (let [cx (quot w 2) cy (quot h 2)
            r  (max 2 (quot (min w h) 10))
            rng (prob/make-rng seed)]
        (dotimes [dy (* 2 r)]
          (dotimes [dx (* 2 r)]
            (let [px (+ (- cx r) dx)
                  py (+ (- cy r) dy)]
              (when (and (>= px 0) (< px w) (>= py 0) (< py h))
                (let [dist-sq (+ (* (- px cx) (- px cx))
                                 (* (- py cy) (- py cy)))]
                  (when (< dist-sq (* r r))
                    (let [idx (+ (* py w) px)]
                      (aset b idx (+ 0.5 (* 0.1 (.nextDouble rng))))
                      (aset a idx 0.5)))))))))

      :random-seeds
      (let [rng (prob/make-rng seed)]
        (dotimes [_ (max 1 (quot n 50))]
          (let [x (.nextInt rng w)
                y (.nextInt rng h)
                idx (+ (* y w) x)]
            (aset b idx 1.0)
            (aset a idx 0.0)))))
    {:a a :b b :w w :h h}))

(defn rd-step
  "Advances reaction-diffusion by one time step.
  params: {:feed f :kill k :da da :db db :dt dt}."
  [{:keys [^doubles a ^doubles b w h]} params]
  (let [w    (int w) h (int h)
        n    (* w h)
        feed (double (:feed params))
        kill (double (:kill params))
        da   (double (:da params))
        db   (double (:db params))
        dt   (double (:dt params))
        na   (double-array n)
        nb   (double-array n)]
    (dotimes [y h]
      (dotimes [x w]
        (let [idx (+ (* y w) x)
              av  (aget a idx)
              bv  (aget b idx)
              ;; Laplacian with 3x3 kernel
              lap-a (+ (* -1.0 av)
                       (* 0.2 (+ (aget a (+ (* y w) (mod (inc x) w)))
                                 (aget a (+ (* y w) (mod (+ x w -1) w)))
                                 (aget a (+ (* (mod (inc y) h) w) x))
                                 (aget a (+ (* (mod (+ y h -1) h) w) x))))
                       (* 0.05 (+ (aget a (+ (* (mod (+ y h -1) h) w) (mod (+ x w -1) w)))
                                  (aget a (+ (* (mod (+ y h -1) h) w) (mod (inc x) w)))
                                  (aget a (+ (* (mod (inc y) h) w) (mod (+ x w -1) w)))
                                  (aget a (+ (* (mod (inc y) h) w) (mod (inc x) w))))))
              lap-b (+ (* -1.0 bv)
                       (* 0.2 (+ (aget b (+ (* y w) (mod (inc x) w)))
                                 (aget b (+ (* y w) (mod (+ x w -1) w)))
                                 (aget b (+ (* (mod (inc y) h) w) x))
                                 (aget b (+ (* (mod (+ y h -1) h) w) x))))
                       (* 0.05 (+ (aget b (+ (* (mod (+ y h -1) h) w) (mod (+ x w -1) w)))
                                  (aget b (+ (* (mod (+ y h -1) h) w) (mod (inc x) w)))
                                  (aget b (+ (* (mod (inc y) h) w) (mod (+ x w -1) w)))
                                  (aget b (+ (* (mod (inc y) h) w) (mod (inc x) w))))))
              abb (* av bv bv)]
          (aset na idx (max 0.0 (min 1.0
                         (+ av (* dt (- (* da lap-a) abb (* feed (- av 1.0))))))))
          (aset nb idx (max 0.0 (min 1.0
                         (+ bv (* dt (+ (* db lap-b) abb (- (* (+ feed kill) bv)))))))))))
    {:a na :b nb :w w :h h}))

(defn rd-run
  "Runs reaction-diffusion for n steps. Returns the final grid."
  [rd-map params n]
  (loop [g rd-map i 0]
    (if (>= i n) g (recur (rd-step g params) (inc i)))))

(defn rd->nodes
  "Renders an RD state as scene nodes. One rect per cell.
  color-fn: (fn [a-val b-val] -> color-vector)."
  [{:keys [^doubles a ^doubles b w h]} cell-size color-fn]
  (let [cs (double cell-size)]
    (mapv (fn [i]
            (let [x (mod i (int w))
                  y (quot i (int w))]
              {:node/type  :shape/rect
               :rect/xy    [(* x cs) (* y cs)]
               :rect/size  [cs cs]
               :style/fill (color-fn (aget a i) (aget b i))}))
          (range (* (int w) (int h))))))

(comment
  (def g (ca-grid 10 10 :random 42))
  (ca-step g :life)
  (ca-run g :life 5)
  (def rd (rd-grid 50 50 :center-seed 42))
  (rd-step rd (:coral rd-presets))
  (rd-run rd (:coral rd-presets) 100))
