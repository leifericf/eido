(ns eido.color.palette
  "Color harmony algorithms and palette generation. Provides complementary,
  analogous, triadic, and other schemes plus palette manipulation utilities."
  (:require
    [eido.color :as color]
    [eido.gen.prob :as prob])
  (:import
    [java.awt Color Graphics2D]
    [java.awt.image BufferedImage]
    [javax.imageio ImageIO]
    [java.io File]))

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
  Optional opts: {:easing fn} applies an easing function to t before lookup.
  Returns a color vector."
  ([stops t] (gradient-map stops t nil))
  ([stops t opts]
   (let [t (max 0.0 (min 1.0 (double t)))
         t (if-let [e (:easing opts)] (double (e t)) t)
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
               (recur (inc i))))))))))

;; --- palette manipulation ---

(defn warmer
  "Rotates all hues toward warm (positive degrees). Typical: 5-15."
  [palette amount]
  (mapv #(color/rotate-hue % amount) palette))

(defn cooler
  "Rotates all hues toward cool (negative degrees). Typical: 5-15."
  [palette amount]
  (mapv #(color/rotate-hue % (- amount)) palette))

(defn muted
  "Desaturates all colors by amount (0-1)."
  [palette amount]
  (mapv #(color/desaturate % amount) palette))

(defn vivid
  "Saturates all colors by amount (0-1)."
  [palette amount]
  (mapv #(color/saturate % amount) palette))

(defn darker
  "Darkens all colors by amount (0-1)."
  [palette amount]
  (mapv #(color/darken % amount) palette))

(defn lighter
  "Lightens all colors by amount (0-1)."
  [palette amount]
  (mapv #(color/lighten % amount) palette))

(defn ^{:convenience true}
  adjust
  "Applies multiple palette adjustments in one call.
  Wraps warmer/cooler/muted/vivid/darker/lighter.
  opts keys: :warmer, :cooler, :muted, :vivid, :darker, :lighter."
  [palette opts]
  (cond-> palette
    (:warmer opts)  (warmer (:warmer opts))
    (:cooler opts)  (cooler (:cooler opts))
    (:muted opts)   (muted (:muted opts))
    (:vivid opts)   (vivid (:vivid opts))
    (:darker opts)  (darker (:darker opts))
    (:lighter opts) (lighter (:lighter opts))))

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

;; --- palette analysis ---

(defn ^{:convenience true}
  min-contrast
  "Returns the minimum pairwise WCAG contrast ratio in a palette.
  Wraps (color/contrast) over all pairs. Useful for checking readability."
  [palette]
  (let [n (count palette)]
    (if (<= n 1)
      Double/POSITIVE_INFINITY
      (reduce min
        (for [i (range n)
              j (range (inc i) n)]
          (color/contrast (nth palette i) (nth palette j)))))))

(defn ^{:convenience true}
  sort-by-lightness
  "Sorts a palette from dark to light using OKLAB perceptual lightness.
  Wraps (sort-by (fn [c] (first (color/rgb->oklab ...))) palette)."
  [palette]
  (vec (sort-by
         (fn [c]
           (let [{:keys [r g b]} (color/resolve-color c)]
             (first (color/rgb->oklab r g b))))
         palette)))

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

;; --- color extraction ---

(defn- sample-pixels
  "Samples n random pixels from a BufferedImage as OKLAB [L a b] vectors."
  [^BufferedImage img n seed]
  (let [w (.getWidth img) h (.getHeight img)
        rng (java.util.Random. (long seed))]
    (mapv (fn [_]
            (let [px (.nextInt rng w) py (.nextInt rng h)
                  argb (.getRGB img px py)
                  r (bit-and (bit-shift-right argb 16) 0xFF)
                  g (bit-and (bit-shift-right argb 8) 0xFF)
                  b (bit-and argb 0xFF)]
              (color/rgb->oklab r g b)))
          (range n))))

(defn- oklab-dist-sq ^double [[^double L1 ^double a1 ^double b1]
                               [^double L2 ^double a2 ^double b2]]
  (let [dL (- L1 L2) da (- a1 a2) db (- b1 b2)]
    (+ (* dL dL) (* da da) (* db db))))

(defn- kmeans-init
  "K-means++ initialization: pick first centroid randomly, then weight
  subsequent picks by distance to nearest existing centroid."
  [samples k rng]
  (loop [centroids [(nth samples (.nextInt ^java.util.Random rng (count samples)))]
         remaining (dec k)]
    (if (zero? remaining)
      centroids
      (let [dists (mapv (fn [s] (reduce min (map #(oklab-dist-sq s %) centroids))) samples)
            total (reduce + dists)
            target (* (.nextDouble ^java.util.Random rng) total)
            idx (loop [i 0 acc 0.0]
                  (let [acc (+ acc (double (nth dists i)))]
                    (if (or (>= acc target) (>= i (dec (count samples))))
                      i (recur (inc i) acc))))]
        (recur (conj centroids (nth samples idx)) (dec remaining))))))

(defn- kmeans-step
  "One k-means iteration: assign samples to nearest centroid, recompute centroids."
  [samples centroids]
  (let [k (count centroids)
        assignments (mapv (fn [s]
                            (first (apply min-key second
                                     (map-indexed (fn [i c] [i (oklab-dist-sq s c)])
                                                  centroids))))
                          samples)
        new-centroids
        (mapv (fn [ci]
                (let [members (keep-indexed (fn [si ai] (when (= ai ci) (nth samples si)))
                                            assignments)]
                  (if (seq members)
                    (let [n (double (count members))]
                      [(/ (reduce + (map first members)) n)
                       (/ (reduce + (map second members)) n)
                       (/ (reduce + (map #(nth % 2) members)) n)])
                    (nth centroids ci))))
              (range k))]
    new-centroids))

(defn from-image
  "Extracts a palette of k dominant colors from an image using k-means
  clustering in OKLAB perceptual color space. Returns a palette vector
  sorted from dark to light.
  img: BufferedImage or file path string.
  opts: :samples (1000), :seed (0), :max-iter (20)."
  ([img k] (from-image img k nil))
  ([img k opts]
   (let [^BufferedImage bimg (if (string? img)
                               (ImageIO/read (File. ^String img))
                               img)
         n-samples (get opts :samples 1000)
         seed (get opts :seed 0)
         max-iter (get opts :max-iter 20)
         rng (java.util.Random. (long seed))
         samples (sample-pixels bimg n-samples (.nextLong rng))
         initial (kmeans-init samples k rng)
         centroids (loop [cs initial i 0]
                     (if (>= i max-iter)
                       cs
                       (let [new-cs (kmeans-step samples cs)
                             moved (reduce + (map oklab-dist-sq cs new-cs))]
                         (if (< moved 1e-8)
                           new-cs
                           (recur new-cs (inc i))))))]
     (sort-by-lightness
       (mapv (fn [[L a b]]
               (let [{:keys [r g b]} (color/resolve-color [:color/oklab L a b])]
                 [:color/rgb r g b]))
             centroids)))))

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
