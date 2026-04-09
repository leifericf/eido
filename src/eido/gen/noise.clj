(ns eido.gen.noise)

;; --- permutation table ---

(def ^:private perm-base
  "Ken Perlin's original permutation table."
  [151 160 137 91 90 15 131 13 201 95 96 53 194 233 7 225 140 36 103 30
   69 142 8 99 37 240 21 10 23 190 6 148 247 120 234 75 0 26 197 62
   94 252 219 203 117 35 11 32 57 177 33 88 237 149 56 87 174 20 125 136
   171 168 68 175 74 165 71 134 139 48 27 166 77 146 158 231 83 111 229 122
   60 211 133 230 220 105 92 41 55 46 245 40 244 102 143 54 65 25 63 161
   1 216 80 73 209 76 132 187 208 89 18 169 200 196 135 130 116 188 159 86
   164 100 109 198 173 186 3 64 52 217 226 250 124 123 5 202 38 147 118
   126 255 82 85 212 207 206 59 227 47 16 58 17 182 189 28 42 223 183
   170 213 119 248 152 2 44 154 163 70 221 153 101 155 167 43 172 9 129
   22 39 253 19 98 108 110 79 113 224 232 178 185 112 104 218 246 97 228
   251 34 242 193 238 210 144 12 191 179 162 241 81 51 145 235 249 14 239
   107 49 192 214 31 181 199 106 157 184 84 204 176 115 121 50 45 127 4
   150 254 138 236 205 93 222 114 67 29 24 72 243 141 128 195 78 66 215
   61 156 180])

(defn- make-perm
  "Creates a 512-entry int-array permutation table, doubling perm-base."
  ^ints [seed]
  (let [^ints base (if (zero? seed)
                     (int-array perm-base)
                     (let [arr (int-array 256)
                           r   (java.util.Random. (long seed))]
                       (dotimes [i 256] (aset arr i i))
                       (loop [i 255]
                         (when (pos? i)
                           (let [j (.nextInt r (inc i))
                                 tmp (aget arr i)]
                             (aset arr i (aget arr j))
                             (aset arr j tmp)
                             (recur (dec i)))))
                       arr))
        result (int-array 512)]
    (System/arraycopy base 0 result 0 256)
    (System/arraycopy base 0 result 256 256)
    result))

(def ^:private default-perm (make-perm 0))

(def ^:private perm-cache
  "Cache of permutation tables by seed. Avoids regenerating the 512-entry
  table on every noise call for the same seed."
  (atom {0 default-perm}))

(defn- get-perm
  "Returns a permutation table for the given seed, caching results."
  [seed]
  (or (get @perm-cache seed)
      (let [p (make-perm seed)]
        (swap! perm-cache assoc seed p)
        p)))

(defn- perm-at ^long [^ints perm ^long i]
  (aget perm (bit-and i 255)))

;; --- gradient vectors ---
;; Flattened as [gx gy gz, gx gy gz, ...] for direct array access.

(def ^:private grad3-x (int-array [1 -1 1 -1 1 -1 1 -1 0 0 0 0]))
(def ^:private grad3-y (int-array [1 1 -1 -1 0 0 0 0 1 -1 1 -1]))
(def ^:private grad3-z (int-array [0 0 0 0 1 1 -1 -1 1 1 -1 -1]))

(defn- dot2 ^double [^long gi ^double x ^double y]
  (+ (* (double (aget ^ints grad3-x gi)) x)
     (* (double (aget ^ints grad3-y gi)) y)))

(defn- dot3 ^double [^long gi ^double x ^double y ^double z]
  (+ (* (double (aget ^ints grad3-x gi)) x)
     (* (double (aget ^ints grad3-y gi)) y)
     (* (double (aget ^ints grad3-z gi)) z)))

;; --- fade and lerp ---

(defn- fade ^double [^double t]
  (* t t t (+ (* t (- (* t 6.0) 15.0)) 10.0)))

(defn- nlerp ^double [^double a ^double b ^double t]
  (+ a (* t (- b a))))

;; --- perlin 2D ---

(defn perlin2d
  "2D Perlin noise. Returns a double in [-1, 1].
  Optionally accepts {:seed n} for a seeded permutation table."
  (^double [x y]
   (perlin2d x y nil))
  (^double [x y opts]
   (let [perm (if-let [s (:seed opts)]
                (get-perm s)
                default-perm)
         x  (double x)
         y  (double y)
         xi (long (Math/floor x))
         yi (long (Math/floor y))
         xf (- x xi)
         yf (- y yi)
         u  (fade xf)
         v  (fade yf)
         gi00 (rem (perm-at perm (+ (perm-at perm xi) yi)) 12)
         gi10 (rem (perm-at perm (+ (perm-at perm (inc xi)) yi)) 12)
         gi01 (rem (perm-at perm (+ (perm-at perm xi) (inc yi))) 12)
         gi11 (rem (perm-at perm (+ (perm-at perm (inc xi)) (inc yi))) 12)
         n00  (dot2 gi00 xf yf)
         n10  (dot2 gi10 (- xf 1.0) yf)
         n01  (dot2 gi01 xf (- yf 1.0))
         n11  (dot2 gi11 (- xf 1.0) (- yf 1.0))
         nx0  (nlerp n00 n10 u)
         nx1  (nlerp n01 n11 u)]
     (nlerp nx0 nx1 v))))

;; --- perlin 3D ---

(defn perlin3d
  "3D Perlin noise. Returns a double in [-1, 1].
  Useful for animating noise (use time as z).
  Optionally accepts {:seed n}."
  (^double [x y z]
   (perlin3d x y z nil))
  (^double [x y z opts]
   (let [perm (if-let [s (:seed opts)]
                (get-perm s)
                default-perm)
         x  (double x)
         y  (double y)
         z  (double z)
         xi (long (Math/floor x))
         yi (long (Math/floor y))
         zi (long (Math/floor z))
         xf (- x xi)
         yf (- y yi)
         zf (- z zi)
         u  (fade xf)
         v  (fade yf)
         w  (fade zf)
         aaa (rem (perm-at perm (+ (perm-at perm (+ (perm-at perm xi) yi)) zi)) 12)
         baa (rem (perm-at perm (+ (perm-at perm (+ (perm-at perm (inc xi)) yi)) zi)) 12)
         aba (rem (perm-at perm (+ (perm-at perm (+ (perm-at perm xi) (inc yi))) zi)) 12)
         bba (rem (perm-at perm (+ (perm-at perm (+ (perm-at perm (inc xi)) (inc yi))) zi)) 12)
         aab (rem (perm-at perm (+ (perm-at perm (+ (perm-at perm xi) yi)) (inc zi))) 12)
         bab (rem (perm-at perm (+ (perm-at perm (+ (perm-at perm (inc xi)) yi)) (inc zi))) 12)
         abb (rem (perm-at perm (+ (perm-at perm (+ (perm-at perm xi) (inc yi))) (inc zi))) 12)
         bbb (rem (perm-at perm (+ (perm-at perm (+ (perm-at perm (inc xi)) (inc yi))) (inc zi))) 12)]
     (nlerp
       (nlerp
         (nlerp (dot3 aaa xf yf zf)
                (dot3 baa (- xf 1.0) yf zf) u)
         (nlerp (dot3 aba xf (- yf 1.0) zf)
                (dot3 bba (- xf 1.0) (- yf 1.0) zf) u) v)
       (nlerp
         (nlerp (dot3 aab xf yf (- zf 1.0))
                (dot3 bab (- xf 1.0) yf (- zf 1.0)) u)
         (nlerp (dot3 abb xf (- yf 1.0) (- zf 1.0))
                (dot3 bbb (- xf 1.0) (- yf 1.0) (- zf 1.0)) u) v)
       w))))

;; --- fractal noise ---

(defn fbm
  "Fractal Brownian motion — layers of noise at increasing frequency.
  noise-fn must accept (x y) or (x y opts) and return a double.
  Options: :octaves (4), :lacunarity (2.0), :gain (0.5), :seed."
  ([noise-fn x y]
   (fbm noise-fn x y nil))
  ([noise-fn x y opts]
   (let [octaves    (get opts :octaves 4)
         lacunarity (get opts :lacunarity 2.0)
         gain       (get opts :gain 0.5)
         seed-opts  (when-let [s (:seed opts)] {:seed s})]
     (loop [i         0
            amplitude 1.0
            frequency 1.0
            total     0.0
            max-amp   0.0]
       (if (>= i octaves)
         (/ total max-amp)
         (let [v (noise-fn (* (double x) frequency)
                           (* (double y) frequency)
                           seed-opts)]
           (recur (inc i)
                  (* amplitude (double gain))
                  (* frequency (double lacunarity))
                  (+ total (* amplitude v))
                  (+ max-amp amplitude))))))))

(defn turbulence
  "Turbulence — fbm using absolute values of noise.
  Returns non-negative values. Same options as fbm."
  ([noise-fn x y]
   (turbulence noise-fn x y nil))
  ([noise-fn x y opts]
   (let [octaves    (get opts :octaves 4)
         lacunarity (get opts :lacunarity 2.0)
         gain       (get opts :gain 0.5)
         seed-opts  (when-let [s (:seed opts)] {:seed s})]
     (loop [i         0
            amplitude 1.0
            frequency 1.0
            total     0.0
            max-amp   0.0]
       (if (>= i octaves)
         (/ total max-amp)
         (let [v (Math/abs (double (noise-fn (* (double x) frequency)
                                            (* (double y) frequency)
                                            seed-opts)))]
           (recur (inc i)
                  (* amplitude (double gain))
                  (* frequency (double lacunarity))
                  (+ total (* amplitude v))
                  (+ max-amp amplitude))))))))

(defn ridge
  "Ridged multifractal noise — inverts absolute value for sharp ridges.
  Same options as fbm, plus :offset (1.0)."
  ([noise-fn x y]
   (ridge noise-fn x y nil))
  ([noise-fn x y opts]
   (let [octaves    (get opts :octaves 4)
         lacunarity (get opts :lacunarity 2.0)
         gain       (get opts :gain 0.5)
         offset     (get opts :offset 1.0)
         seed-opts  (when-let [s (:seed opts)] {:seed s})]
     (loop [i         0
            amplitude 1.0
            frequency 1.0
            total     0.0
            max-amp   0.0]
       (if (>= i octaves)
         (/ total max-amp)
         (let [v (- (double offset)
                    (Math/abs (double (noise-fn (* (double x) frequency)
                                               (* (double y) frequency)
                                               seed-opts))))]
           (recur (inc i)
                  (* amplitude (double gain))
                  (* frequency (double lacunarity))
                  (+ total (* amplitude v))
                  (+ max-amp amplitude))))))))

(comment
  (perlin2d 1.5 2.3)
  (perlin2d 1.5 2.3 {:seed 42})
  (perlin3d 1.5 2.3 0.5)
  (fbm perlin2d 1.5 2.3 {:octaves 6})
  (turbulence perlin2d 1.5 2.3 {:octaves 4})
  (ridge perlin2d 1.5 2.3 {:octaves 4})
  )
