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

;; --- OKLAB / OKLCH conversions ---
;; Based on Björn Ottosson's published formulas (public domain).
;; Conversion chain: sRGB -> linear RGB -> OKLAB -> OKLCH (and reverse).

(defn- srgb->linear ^double [^long c]
  (let [c (/ (double c) 255.0)]
    (if (<= c 0.04045)
      (/ c 12.92)
      (Math/pow (/ (+ c 0.055) 1.055) 2.4))))

(defn- linear->srgb ^long [^double c]
  (let [c (max 0.0 (min 1.0 c))]
    (long (Math/round (* 255.0
      (if (<= c 0.0031308)
        (* 12.92 c)
        (- (* 1.055 (Math/pow c (/ 1.0 2.4))) 0.055)))))))

(defn- linear-rgb->oklab [^double r ^double g ^double b]
  (let [l (+ (* 0.4122214708 r) (* 0.5363325363 g) (* 0.0514459929 b))
        m (+ (* 0.2119034982 r) (* 0.6806995451 g) (* 0.1073969566 b))
        s (+ (* 0.0883024619 r) (* 0.2817188376 g) (* 0.6299787005 b))
        l_ (Math/cbrt l) m_ (Math/cbrt m) s_ (Math/cbrt s)]
    [(+ (* 0.2104542553 l_) (* 0.7936177850 m_) (* -0.0040720468 s_))
     (+ (* 1.9779984951 l_) (* -2.4285922050 m_) (* 0.4505937099 s_))
     (+ (* 0.0259040371 l_) (* 0.7827717662 m_) (* -0.8086757660 s_))]))

(defn- oklab->linear-rgb [^double L ^double a ^double b]
  (let [l_ (+ L (* 0.3963377774 a) (* 0.2158037573 b))
        m_ (+ L (* -0.1055613458 a) (* -0.0638541728 b))
        s_ (+ L (* -0.0894841775 a) (* -1.2914855480 b))
        l (* l_ l_ l_) m (* m_ m_ m_) s (* s_ s_ s_)]
    [(+ (* 4.0767416621 l) (* -3.3077115913 m) (* 0.2309699292 s))
     (+ (* -1.2684380046 l) (* 2.6097574011 m) (* -0.3413193965 s))
     (+ (* -0.0041960863 l) (* -0.7034186147 m) (* 1.7076147010 s))]))

(defn- oklab->oklch [^double L ^double a ^double b]
  (let [C (Math/sqrt (+ (* a a) (* b b)))
        h (mod (Math/toDegrees (Math/atan2 b a)) 360.0)]
    [L C h]))

(defn- oklch->oklab [^double L ^double C ^double h]
  (let [h-rad (Math/toRadians h)]
    [L (* C (Math/cos h-rad)) (* C (Math/sin h-rad))]))

(defn rgb->oklab
  "Converts sRGB (0-255 each) to OKLAB [L a b].
  L: 0-1 (lightness), a: ~-0.4 to 0.4, b: ~-0.4 to 0.4."
  [r g b]
  (linear-rgb->oklab (srgb->linear r) (srgb->linear g) (srgb->linear b)))

(defn rgb->oklch
  "Converts sRGB (0-255 each) to OKLCH [L C h].
  L: 0-1, C: 0-0.4+, h: 0-360 degrees."
  [r g b]
  (let [[L a b] (rgb->oklab r g b)]
    (oklab->oklch L a b)))

;; --- HSL conversions ---

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
  "Resolves a color value to a map with :r :g :b :a keys.
  Accepts tagged vectors ([:color/rgb 255 0 0], [:color/name \"red\"])
  or bare keywords (:red, :cornflowerblue) as shorthand for named colors."
  [color]
  (if (keyword? color)
    (let [name-str (name color)]
      (if-let [[r g b] (get named-colors name-str)]
        {:r r :g g :b b :a 1.0}
        (throw (ex-info "Unknown color name"
                        {:input color}))))
    (case (first color)
      :color/rgb  (let [[_ r g b] color]
                    {:r r :g g :b b :a 1.0})
      :color/rgba (let [[_ r g b a] color]
                    {:r r :g g :b b :a a})
      :color/hsl  (let [[_ h s l] color
                         [r g b] (hsl->rgb h s l)]
                    {:r r :g g :b b :a 1.0})
      :color/hsla (let [[_ h s l a] color
                         [r g b] (hsl->rgb h s l)]
                    {:r r :g g :b b :a a})
      :color/hsb  (let [[_ h s b] color
                         [r g b] (hsb->rgb h s b)]
                    {:r r :g g :b b :a 1.0})
      :color/hsba (let [[_ h s b a] color
                         [r g b] (hsb->rgb h s b)]
                    {:r r :g g :b b :a a})
      :color/oklab  (let [[_ L a b] color
                          [rl gl bl] (oklab->linear-rgb L a b)]
                      {:r (linear->srgb rl) :g (linear->srgb gl)
                       :b (linear->srgb bl) :a 1.0})
      :color/oklaba (let [[_ L a b alpha] color
                          [rl gl bl] (oklab->linear-rgb L a b)]
                      {:r (linear->srgb rl) :g (linear->srgb gl)
                       :b (linear->srgb bl) :a alpha})
      :color/oklch  (let [[_ L C h] color
                          [L' a b] (oklch->oklab L C h)
                          [rl gl bl] (oklab->linear-rgb L' a b)]
                      {:r (linear->srgb rl) :g (linear->srgb gl)
                       :b (linear->srgb bl) :a 1.0})
      :color/oklcha (let [[_ L C h alpha] color
                          [L' a b] (oklch->oklab L C h)
                          [rl gl bl] (oklab->linear-rgb L' a b)]
                      {:r (linear->srgb rl) :g (linear->srgb gl)
                       :b (linear->srgb bl) :a alpha})
      :color/hex  (parse-hex (second color))
      :color/name (let [name-str (str/lower-case (second color))]
                    (if-let [[r g b] (get named-colors name-str)]
                      {:r r :g g :b b :a 1.0}
                      (throw (ex-info "Unknown color name"
                                      {:input (second color)})))))))

;; --- color manipulation ---

(defn- ensure-map
  "Coerces a color to a resolved map. Accepts color vectors, keywords, and maps."
  [color]
  (if (or (vector? color) (keyword? color))
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
  Accepts color vectors or maps; returns a color vector.
  Optional opts map: {:space :oklab} for perceptually uniform interpolation."
  ([color-a color-b t]
   (let [a (ensure-map color-a)
         b (ensure-map color-b)
         t (max 0.0 (min 1.0 (double t)))
         inv (- 1.0 t)]
     (map->vec
       {:r (Math/round (+ (* inv (:r a)) (* t (:r b))))
        :g (Math/round (+ (* inv (:g a)) (* t (:g b))))
        :b (Math/round (+ (* inv (:b a)) (* t (:b b))))
        :a (+ (* inv (:a a)) (* t (:a b)))})))
  ([color-a color-b t opts]
   (if (= :oklab (:space opts))
     (let [a (ensure-map color-a)
           b (ensure-map color-b)
           t (max 0.0 (min 1.0 (double t)))
           inv (- 1.0 t)
           [La aa ba] (rgb->oklab (:r a) (:g a) (:b a))
           [Lb ab bb] (rgb->oklab (:r b) (:g b) (:b b))
           L (+ (* inv La) (* t Lb))
           ok-a (+ (* inv aa) (* t ab))
           ok-b (+ (* inv ba) (* t bb))
           [rl gl bl] (oklab->linear-rgb L ok-a ok-b)
           alpha (+ (* inv (:a a)) (* t (:a b)))]
       (map->vec {:r (linear->srgb rl) :g (linear->srgb gl)
                  :b (linear->srgb bl) :a alpha}))
     (lerp color-a color-b t))))

;; --- contrast and distance ---

(defn contrast
  "WCAG 2.0 luminance contrast ratio between two colors.
  Returns a double >= 1.0 (1 = identical, 21 = black/white).
  Accepts any color form (vectors, keywords, maps)."
  [color-a color-b]
  (let [{ra :r ga :g ba :b} (ensure-map color-a)
        {rb :r gb :g bb :b} (ensure-map color-b)
        lum (fn [r g b]
              (+ (* 0.2126 (srgb->linear r))
                 (* 0.7152 (srgb->linear g))
                 (* 0.0722 (srgb->linear b))))
        la (lum ra ga ba)
        lb (lum rb gb bb)
        l1 (max la lb)
        l2 (min la lb)]
    (/ (+ l1 0.05) (+ l2 0.05))))

(defn perceptual-distance
  "OKLAB deltaE — Euclidean distance in OKLAB perceptual color space.
  Returns a double >= 0.0 (0 = identical colors).
  More perceptually meaningful than RGB distance.
  Accepts any color form (vectors, keywords, maps)."
  [color-a color-b]
  (let [{ra :r ga :g ba :b} (ensure-map color-a)
        {rb :r gb :g bb :b} (ensure-map color-b)
        [La aa ab] (rgb->oklab ra ga ba)
        [Lb ba2 bb2] (rgb->oklab rb gb bb)
        dL (- La Lb) da (- aa ba2) db (- ab bb2)]
    (Math/sqrt (+ (* dL dL) (* da da) (* db db)))))

;; --- convenience helpers ---

(defn ^{:convenience true}
  rgb
  "Shorthand for [:color/rgb r g b]."
  [r g b]
  [:color/rgb r g b])

(defn ^{:convenience true}
  hsl
  "Shorthand for [:color/hsl h s l]. Wraps hue with mod 360."
  [h s l]
  [:color/hsl (mod h 360) s l])

(defn ^{:convenience true}
  oklab
  "Shorthand for [:color/oklab L a b]."
  [L a b]
  [:color/oklab L a b])

(defn ^{:convenience true}
  oklch
  "Shorthand for [:color/oklch L C h]."
  [L C h]
  [:color/oklch L C h])

(defn ^{:convenience true :convenience-for 'eido.color/lerp}
  lerp-oklab
  "Interpolates between two colors in OKLAB space. t in [0, 1].
  Wraps (lerp color-a color-b t {:space :oklab})."
  [color-a color-b t]
  (lerp color-a color-b t {:space :oklab}))

(comment
  (resolve-color [:color/rgb 200 0 0])
  (resolve-color [:color/hsl 0 1.0 0.5])
  (resolve-color [:color/hex "#FF0000"])
  (resolve-color :crimson)
  (resolve-color [:color/oklab 0.63 0.22 0.13])
  (resolve-color [:color/oklch 0.63 0.26 29])
  (lighten :red 0.2)
  (rotate-hue [:color/rgb 255 0 0] 120)
  (lerp :black :white 0.5)
  (lerp :red :cyan 0.5 {:space :oklab})
  (lerp-oklab :red :blue 0.5)
  (rgb 255 0 0)
  (hsl 200 0.8 0.5)
  (oklab 0.63 0.22 0.13)
  (oklch 0.7 0.15 200)
  (rgb->oklab 255 0 0)
  (rgb->oklch 255 0 0)
  )

