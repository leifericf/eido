(ns eido.color
  "Color parsing, conversion, and manipulation.

  See also eido.color.palette for color harmonies & gradients."
  (:require
    [clojure.string :as str]))

(def named-colors
  "CSS Color Level 4 named colors mapped to [r g b] values."
  {"aliceblue"            [240 248 255]
   "antiquewhite"         [250 235 215]
   "aqua"                 [0 255 255]
   "aquamarine"           [127 255 212]
   "azure"                [240 255 255]
   "beige"                [245 245 220]
   "bisque"               [255 228 196]
   "black"                [0 0 0]
   "blanchedalmond"       [255 235 205]
   "blue"                 [0 0 255]
   "blueviolet"           [138 43 226]
   "brown"                [165 42 42]
   "burlywood"            [222 184 135]
   "cadetblue"            [95 158 160]
   "chartreuse"           [127 255 0]
   "chocolate"            [210 105 30]
   "coral"                [255 127 80]
   "cornflowerblue"       [100 149 237]
   "cornsilk"             [255 248 220]
   "crimson"              [220 20 60]
   "cyan"                 [0 255 255]
   "darkblue"             [0 0 139]
   "darkcyan"             [0 139 139]
   "darkgoldenrod"        [184 134 11]
   "darkgray"             [169 169 169]
   "darkgreen"            [0 100 0]
   "darkgrey"             [169 169 169]
   "darkkhaki"            [189 183 107]
   "darkmagenta"          [139 0 139]
   "darkolivegreen"       [85 107 47]
   "darkorange"           [255 140 0]
   "darkorchid"           [153 50 204]
   "darkred"              [139 0 0]
   "darksalmon"           [233 150 122]
   "darkseagreen"         [143 188 143]
   "darkslateblue"        [72 61 139]
   "darkslategray"        [47 79 79]
   "darkslategrey"        [47 79 79]
   "darkturquoise"        [0 206 209]
   "darkviolet"           [148 0 211]
   "deeppink"             [255 20 147]
   "deepskyblue"          [0 191 255]
   "dimgray"              [105 105 105]
   "dimgrey"              [105 105 105]
   "dodgerblue"           [30 144 255]
   "firebrick"            [178 34 34]
   "floralwhite"          [255 250 240]
   "forestgreen"          [34 139 34]
   "fuchsia"              [255 0 255]
   "gainsboro"            [220 220 220]
   "ghostwhite"           [248 248 255]
   "gold"                 [255 215 0]
   "goldenrod"            [218 165 32]
   "gray"                 [128 128 128]
   "green"                [0 128 0]
   "greenyellow"          [173 255 47]
   "grey"                 [128 128 128]
   "honeydew"             [240 255 240]
   "hotpink"              [255 105 180]
   "indianred"            [205 92 92]
   "indigo"               [75 0 130]
   "ivory"                [255 255 240]
   "khaki"                [240 230 140]
   "lavender"             [230 230 250]
   "lavenderblush"        [255 240 245]
   "lawngreen"            [124 252 0]
   "lemonchiffon"         [255 250 205]
   "lightblue"            [173 216 230]
   "lightcoral"           [240 128 128]
   "lightcyan"            [224 255 255]
   "lightgoldenrodyellow" [250 250 210]
   "lightgray"            [211 211 211]
   "lightgreen"           [144 238 144]
   "lightgrey"            [211 211 211]
   "lightpink"            [255 182 193]
   "lightsalmon"          [255 160 122]
   "lightseagreen"        [32 178 170]
   "lightskyblue"         [135 206 250]
   "lightslategray"       [119 136 153]
   "lightslategrey"       [119 136 153]
   "lightsteelblue"       [176 196 222]
   "lightyellow"          [255 255 224]
   "lime"                 [0 255 0]
   "limegreen"            [50 205 50]
   "linen"                [250 240 230]
   "magenta"              [255 0 255]
   "maroon"               [128 0 0]
   "mediumaquamarine"     [102 205 170]
   "mediumblue"           [0 0 205]
   "mediumorchid"         [186 85 211]
   "mediumpurple"         [147 112 219]
   "mediumseagreen"       [60 179 113]
   "mediumslateblue"      [123 104 238]
   "mediumspringgreen"    [0 250 154]
   "mediumturquoise"      [72 209 204]
   "mediumvioletred"      [199 21 133]
   "midnightblue"         [25 25 112]
   "mintcream"            [245 255 250]
   "mistyrose"            [255 228 225]
   "moccasin"             [255 228 181]
   "navajowhite"          [255 222 173]
   "navy"                 [0 0 128]
   "oldlace"              [253 245 230]
   "olive"                [128 128 0]
   "olivedrab"            [107 142 35]
   "orange"               [255 165 0]
   "orangered"            [255 69 0]
   "orchid"               [218 112 214]
   "palegoldenrod"        [238 232 170]
   "palegreen"            [152 251 152]
   "paleturquoise"        [175 238 238]
   "palevioletred"        [219 112 147]
   "papayawhip"           [255 239 213]
   "peachpuff"            [255 218 185]
   "peru"                 [205 133 63]
   "pink"                 [255 192 203]
   "plum"                 [221 160 221]
   "powderblue"           [176 224 230]
   "purple"               [128 0 128]
   "rebeccapurple"        [102 51 153]
   "red"                  [255 0 0]
   "rosybrown"            [188 143 143]
   "royalblue"            [65 105 225]
   "saddlebrown"          [139 69 19]
   "salmon"               [250 128 114]
   "sandybrown"           [244 164 96]
   "seagreen"             [46 139 87]
   "seashell"             [255 245 238]
   "sienna"               [160 82 45]
   "silver"               [192 192 192]
   "skyblue"              [135 206 235]
   "slateblue"            [106 90 205]
   "slategray"            [112 128 144]
   "slategrey"            [112 128 144]
   "snow"                 [255 250 250]
   "springgreen"          [0 255 127]
   "steelblue"            [70 130 180]
   "tan"                  [210 180 140]
   "teal"                 [0 128 128]
   "thistle"              [216 191 216]
   "tomato"               [255 99 71]
   "turquoise"            [64 224 208]
   "violet"               [238 130 238]
   "wheat"                [245 222 179]
   "white"                [255 255 255]
   "whitesmoke"           [245 245 245]
   "yellow"               [255 255 0]
   "yellowgreen"          [154 205 50]})

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

(defn- hsb->rgb
  "Converts HSB/HSV to [r g b] with values in 0-255.
  h: 0-360, s: 0-1, b: 0-1"
  [h s b]
  (let [h (mod h 360)
        s (max 0.0 (min 1.0 (double s)))
        b (max 0.0 (min 1.0 (double b)))]
    (if (zero? s)
      (let [v (Math/round (* b 255.0))]
        [v v v])
      (let [h' (/ h 60.0)
            i  (int (Math/floor h'))
            f  (- h' i)
            p  (Math/round (* b (- 1.0 s) 255.0))
            q  (Math/round (* b (- 1.0 (* s f)) 255.0))
            t  (Math/round (* b (- 1.0 (* s (- 1.0 f))) 255.0))
            v  (Math/round (* b 255.0))]
        (case (mod i 6)
          0 [v t p]
          1 [q v p]
          2 [p v t]
          3 [p q v]
          4 [t p v]
          5 [v p q])))))

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
    :color/hsb  (let [[_ h s b] color-vec
                       [r g b] (hsb->rgb h s b)]
                  {:r r :g g :b b :a 1.0})
    :color/hsba (let [[_ h s b a] color-vec
                       [r g b] (hsb->rgb h s b)]
                  {:r r :g g :b b :a a})
    :color/hex  (parse-hex (second color-vec))
    :color/name (let [name-str (str/lower-case (second color-vec))]
                  (if-let [[r g b] (get named-colors name-str)]
                    {:r r :g g :b b :a 1.0}
                    (throw (ex-info "Unknown color name"
                                    {:input (second color-vec)}))))))

;; --- color manipulation ---

(defn- ensure-map
  "Coerces a color to a resolved map. Accepts both color vectors and maps."
  [color]
  (if (vector? color)
    (resolve-color color)
    color))

(defn- map->vec
  "Converts a resolved color map to a color vector."
  [{:keys [r g b a]}]
  (if (= 1.0 a)
    [:color/rgb r g b]
    [:color/rgba r g b a]))

(defn- modify-hsl
  "Converts color to HSL, applies f to [h s l], converts back.
  Accepts color vectors or maps; returns a color vector."
  [color f]
  (let [{:keys [r g b a]} (ensure-map color)
        [h s l] (rgb->hsl r g b)
        [h' s' l'] (f h s l)
        [r' g' b'] (hsl->rgb h' (max 0.0 (min 1.0 s'))
                              (max 0.0 (min 1.0 l')))]
    (map->vec {:r r' :g g' :b b' :a a})))

(defn lighten
  "Increases lightness by amount (0-1). Accepts color vectors or maps."
  [color amount]
  (modify-hsl color (fn [h s l] [h s (+ l amount)])))

(defn darken
  "Decreases lightness by amount (0-1). Accepts color vectors or maps."
  [color amount]
  (modify-hsl color (fn [h s l] [h s (- l amount)])))

(defn saturate
  "Increases saturation by amount (0-1). Accepts color vectors or maps."
  [color amount]
  (modify-hsl color (fn [h s l] [h (+ s amount) l])))

(defn desaturate
  "Decreases saturation by amount (0-1). Accepts color vectors or maps."
  [color amount]
  (modify-hsl color (fn [h s l] [h (- s amount) l])))

(defn rotate-hue
  "Shifts hue by degrees (can be negative). Accepts color vectors or maps."
  [color degrees]
  (modify-hsl color (fn [h s l] [(mod (+ h degrees) 360) s l])))

(defn lerp
  "Linearly interpolates between two colors. t in [0, 1].
  Accepts color vectors or maps; returns a color vector."
  [color-a color-b t]
  (let [a (ensure-map color-a)
        b (ensure-map color-b)
        t (max 0.0 (min 1.0 (double t)))
        inv (- 1.0 t)]
    (map->vec
      {:r (Math/round (+ (* inv (:r a)) (* t (:r b))))
       :g (Math/round (+ (* inv (:g a)) (* t (:g b))))
       :b (Math/round (+ (* inv (:b a)) (* t (:b b))))
       :a (+ (* inv (:a a)) (* t (:a b)))})))

(comment
  (resolve-color [:color/rgb 200 0 0])
  (resolve-color [:color/hsl 0 1.0 0.5])
  (resolve-color [:color/hex "#FF0000"])
  (lighten [:color/rgb 200 0 0] 0.2)
  (rotate-hue [:color/rgb 255 0 0] 120)
  (lerp [:color/rgb 0 0 0] [:color/rgb 255 255 255] 0.5)
  )

