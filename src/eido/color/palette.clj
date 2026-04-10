(ns eido.color.palette
  (:require
    [eido.color :as color]
    [eido.gen.prob :as prob])
  (:import
    [java.awt Color Graphics2D]
    [java.awt.image BufferedImage]))

;; --- harmony functions ---

(defn complementary
  "Returns the complementary color (180 degrees opposite)."
  [c]
  (color/rotate-hue c 180))

(defn analogous
  "Returns n colors spread evenly across a 60-degree arc centered on c."
  [c n]
  (if (<= n 1)
    [c]
    (let [spread 60
          step (/ (double spread) (dec n))]
      (mapv (fn [i]
              (color/rotate-hue c (- (* i step) (/ spread 2.0))))
            (range n)))))

(defn triadic
  "Returns 3 colors evenly spaced at 120-degree intervals."
  [c]
  [c (color/rotate-hue c 120) (color/rotate-hue c 240)])

(defn split-complementary
  "Returns the base color plus two colors flanking its complement (±30°)."
  [c]
  [c (color/rotate-hue c 150) (color/rotate-hue c 210)])

(defn tetradic
  "Returns 4 colors at 90-degree intervals (rectangle/square scheme)."
  [c]
  [c (color/rotate-hue c 90) (color/rotate-hue c 180) (color/rotate-hue c 270)])

;; --- generation ---

(defn monochromatic
  "Returns n colors with the same hue, varying lightness from dark to light."
  [c n]
  (let [dark  (color/darken c 0.35)
        light (color/lighten c 0.35)]
    (mapv (fn [i]
            (color/lerp dark light (/ (double i) (dec n))))
          (range n))))

(defn gradient-palette
  "Returns n colors evenly interpolated between start and end."
  [start end n]
  (mapv (fn [i]
          (color/lerp start end (/ (double i) (dec n))))
        (range n)))

;; --- curated palettes ---

(def palettes
  "Named palettes as vectors of color vectors."
  {:sunset      [[:color/rgb 255 94 77]
                 [:color/rgb 255 154 80]
                 [:color/rgb 255 206 115]
                 [:color/rgb 255 234 167]
                 [:color/rgb 106 44 112]]
   :ocean       [[:color/rgb 0 52 89]
                 [:color/rgb 0 126 167]
                 [:color/rgb 0 168 198]
                 [:color/rgb 138 204 216]
                 [:color/rgb 207 236 240]]
   :forest      [[:color/rgb 27 79 8]
                 [:color/rgb 55 129 35]
                 [:color/rgb 111 176 70]
                 [:color/rgb 181 215 127]
                 [:color/rgb 245 243 207]]
   :midnight    [[:color/rgb 10 10 35]
                 [:color/rgb 25 25 80]
                 [:color/rgb 60 50 120]
                 [:color/rgb 120 80 160]
                 [:color/rgb 200 150 200]]
   :fire        [[:color/rgb 60 0 0]
                 [:color/rgb 180 30 0]
                 [:color/rgb 255 80 0]
                 [:color/rgb 255 160 20]
                 [:color/rgb 255 230 80]]
   :pastel      [[:color/rgb 255 179 186]
                 [:color/rgb 255 223 186]
                 [:color/rgb 255 255 186]
                 [:color/rgb 186 255 201]
                 [:color/rgb 186 225 255]]
   :monochrome  [[:color/rgb 20 20 20]
                 [:color/rgb 70 70 70]
                 [:color/rgb 128 128 128]
                 [:color/rgb 190 190 190]
                 [:color/rgb 240 240 240]]
   :neon        [[:color/rgb 255 0 102]
                 [:color/rgb 0 255 136]
                 [:color/rgb 0 204 255]
                 [:color/rgb 204 0 255]
                 [:color/rgb 255 255 0]]
   :earth       [[:color/rgb 107 91 73]
                 [:color/rgb 162 138 100]
                 [:color/rgb 195 176 145]
                 [:color/rgb 222 211 183]
                 [:color/rgb 143 115 72]]})

(defn gradient-map
  "Interpolates through color stops at parameter t (0-1).
  stops: [[pos color] ...] sorted ascending by pos.
  Returns a color vector."
  [stops t]
  (let [t (max 0.0 (min 1.0 (double t)))
        n (count stops)]
    (cond
      (<= n 0) [:color/rgb 0 0 0]
      (= n 1)  (second (first stops))
      (<= t (ffirst stops)) (second (first stops))
      (>= t (first (nth stops (dec n)))) (second (nth stops (dec n)))
      :else
      (loop [i 0]
        (if (>= i (dec n))
          (second (nth stops (dec n)))
          (let [[p0 c0] (nth stops i)
                [p1 c1] (nth stops (inc i))]
            (if (<= p0 t p1)
              (let [seg-t (if (== p0 p1) 0.0 (/ (- t p0) (- p1 p0)))]
                (color/lerp c0 c1 seg-t))
              (recur (inc i)))))))))

;; --- weighted palette utilities ---

(defn weighted-pick
  "Picks one color from palette biased by weights.
  palette: vector of color vectors.
  weights: vector of positive numbers (same length as palette).
  seed: long for deterministic selection."
  [palette weights seed]
  (let [idx (prob/weighted-choice weights seed)]
    (nth palette idx)))

(defn weighted-sample
  "Samples n colors from palette with replacement, biased by weights."
  [palette weights n seed]
  (let [indices (prob/weighted-sample n weights seed)]
    (mapv #(nth palette %) indices)))

(defn weighted-gradient
  "Creates gradient stops where each color occupies proportional space.
  Returns stops [[pos color] ...] suitable for gradient-map.
  palette and weights must have the same length."
  [palette weights]
  (let [total  (double (reduce + weights))
        n      (count palette)
        widths (mapv #(/ (double %) total) weights)]
    (loop [i 0 pos 0.0 stops []]
      (if (>= i n)
        stops
        (let [w     (nth widths i)
              mid   (+ pos (/ w 2.0))
              color (nth palette i)]
          (recur (inc i) (+ pos w) (conj stops [mid color])))))))

(defn shuffle-palette
  "Returns a deterministically shuffled palette."
  [palette seed]
  (prob/shuffle-seeded palette seed))

;; --- convenience helpers ---

(defn ^{:convenience true}
  with-roles
  "Attaches role names to a palette for readable access.
  Wraps (zipmap roles palette).
  Example: (with-roles [:bg :primary :accent] pal) => {:bg c1 :primary c2 :accent c3}"
  [roles palette]
  (zipmap roles palette))

;; --- visual preview ---

(defn swatch
  "Returns a BufferedImage showing color bars for a palette.
  Useful with `show` at the REPL for quick visual feedback.
  opts: :width (400), :height (60)."
  ([palette] (swatch palette nil))
  ([palette opts]
   (let [w (int (get opts :width 400))
         h (int (get opts :height 60))
         n (count palette)
         bar-w (/ (double w) (max 1 n))
         img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
         ^Graphics2D gfx (.createGraphics img)]
     (doseq [i (range n)]
       (let [{:keys [r g b a]} (color/resolve-color (nth palette i))]
         (.setColor gfx (Color. (int r) (int g) (int b) (int (* 255 (double a)))))
         (.fillRect gfx (int (* i bar-w)) 0 (int (Math/ceil bar-w)) h)))
     (.dispose gfx)
     img)))

(comment
  (complementary [:color/rgb 255 0 0])
  (analogous [:color/rgb 255 0 0] 5)
  (triadic [:color/rgb 255 0 0])
  (gradient-palette [:color/rgb 0 0 0] [:color/rgb 255 255 255] 5)
  (monochromatic [:color/hsl 200 0.8 0.5] 5)
  (:sunset palettes)
  (weighted-pick (:sunset palettes) [1 1 1 1 5] 42)
  (weighted-gradient (:sunset palettes) [3 1 1 1 1])
  (swatch (:sunset palettes))
  )
