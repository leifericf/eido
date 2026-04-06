(ns eido.color
  (:import
    [java.awt Color]))

(defn- hue->rgb [p q t]
  (let [t (cond (< t 0) (+ t 1.0) (> t 1) (- t 1.0) :else t)]
    (cond
      (< t (/ 1.0 6)) (+ p (* (- q p) 6.0 t))
      (< t 0.5)       q
      (< t (/ 2.0 3)) (+ p (* (- q p) (- (/ 2.0 3) t) 6.0))
      :else            p)))

(defn- hsl->rgb
  "Converts HSL to [r g b] with values in 0-255.
  h: 0-360, s: 0-1, l: 0-1"
  [h s l]
  (let [h (mod h 360)
        h' (/ h 360.0)
        s  (max 0.0 (min 1.0 (double s)))
        l  (max 0.0 (min 1.0 (double l)))]
    (if (zero? s)
      (let [v (Math/round (* l 255.0))]
        [v v v])
      (let [q (if (< l 0.5) (* l (+ 1.0 s)) (+ l s (- (* l s))))
            p (- (* 2.0 l) q)]
        [(Math/round (* (hue->rgb p q (+ h' (/ 1.0 3))) 255.0))
         (Math/round (* (hue->rgb p q h') 255.0))
         (Math/round (* (hue->rgb p q (- h' (/ 1.0 3))) 255.0))]))))

(defn rgb->hsl
  "Converts RGB (0-255 each) to [h s l] where h: 0-360, s: 0-1, l: 0-1."
  [r g b]
  (let [r' (/ (double r) 255.0)
        g' (/ (double g) 255.0)
        b' (/ (double b) 255.0)
        cmax (max r' g' b')
        cmin (min r' g' b')
        delta (- cmax cmin)
        l (/ (+ cmax cmin) 2.0)]
    (if (zero? delta)
      [0 0.0 l]
      (let [s (/ delta (- 1.0 (Math/abs (- (* 2.0 l) 1.0))))
            h (cond
                (== cmax r') (* 60.0 (mod (/ (- g' b') delta) 6))
                (== cmax g') (* 60.0 (+ (/ (- b' r') delta) 2.0))
                :else        (* 60.0 (+ (/ (- r' g') delta) 4.0)))
            h (mod h 360)]
        [h s l]))))

(defn- parse-hex
  "Parses a hex color string to {:r :g :b :a}."
  [hex-str]
  (let [s (if (= \# (first hex-str)) (subs hex-str 1) hex-str)
        s (case (count s)
            3 (apply str (mapcat #(vector % %) s))
            4 (apply str (mapcat #(vector % %) s))
            6 s
            8 s
            (throw (ex-info "Invalid hex color"
                            {:input hex-str})))
        r (Integer/parseInt (subs s 0 2) 16)
        g (Integer/parseInt (subs s 2 4) 16)
        b (Integer/parseInt (subs s 4 6) 16)
        a (if (> (count s) 6)
            (/ (Integer/parseInt (subs s 6 8) 16) 255.0)
            1.0)]
    {:r r :g g :b b :a a}))

(defn resolve-color
  "Resolves a color vector to a map with :r :g :b :a keys."
  [color-vec]
  (case (first color-vec)
    :color/rgb  (let [[_ r g b] color-vec]
                  {:r r :g g :b b :a 1.0})
    :color/rgba (let [[_ r g b a] color-vec]
                  {:r r :g g :b b :a a})
    :color/hsl  (let [[_ h s l] color-vec
                       [r g b] (hsl->rgb h s l)]
                  {:r r :g g :b b :a 1.0})
    :color/hsla (let [[_ h s l a] color-vec
                       [r g b] (hsl->rgb h s l)]
                  {:r r :g g :b b :a a})
    :color/hex  (parse-hex (second color-vec))))

(defn ->awt-color
  "Converts a resolved color map to a java.awt.Color."
  [{:keys [r g b a]}]
  (Color. (int r) (int g) (int b) (int (* a 255))))

;; --- color manipulation ---

(defn- modify-hsl
  "Converts color to HSL, applies f to [h s l], converts back."
  [{:keys [r g b a]} f]
  (let [[h s l] (rgb->hsl r g b)
        [h' s' l'] (f h s l)
        [r' g' b'] (hsl->rgb h' (max 0.0 (min 1.0 s'))
                              (max 0.0 (min 1.0 l')))]
    {:r r' :g g' :b b' :a a}))

(defn lighten
  "Increases lightness by amount (0-1)."
  [color amount]
  (modify-hsl color (fn [h s l] [h s (+ l amount)])))

(defn darken
  "Decreases lightness by amount (0-1)."
  [color amount]
  (modify-hsl color (fn [h s l] [h s (- l amount)])))

(defn saturate
  "Increases saturation by amount (0-1)."
  [color amount]
  (modify-hsl color (fn [h s l] [h (+ s amount) l])))

(defn desaturate
  "Decreases saturation by amount (0-1)."
  [color amount]
  (modify-hsl color (fn [h s l] [h (- s amount) l])))

(defn rotate-hue
  "Shifts hue by degrees (can be negative)."
  [color degrees]
  (modify-hsl color (fn [h s l] [(mod (+ h degrees) 360) s l])))

(defn lerp
  "Linearly interpolates between color-a and color-b. t in [0, 1]."
  [color-a color-b t]
  (let [t (max 0.0 (min 1.0 (double t)))
        inv (- 1.0 t)]
    {:r (Math/round (+ (* inv (:r color-a)) (* t (:r color-b))))
     :g (Math/round (+ (* inv (:g color-a)) (* t (:g color-b))))
     :b (Math/round (+ (* inv (:b color-a)) (* t (:b color-b))))
     :a (+ (* inv (:a color-a)) (* t (:a color-b)))}))

(comment
  (resolve-color [:color/rgb 200 0 0])
  (resolve-color [:color/hsl 0 1.0 0.5])
  (resolve-color [:color/hex "#FF0000"])
  (lighten {:r 200 :g 0 :b 0 :a 1.0} 0.2)
  (rotate-hue {:r 255 :g 0 :b 0 :a 1.0} 120)
  (lerp {:r 0 :g 0 :b 0 :a 1.0} {:r 255 :g 255 :b 255 :a 1.0} 0.5)
  )
