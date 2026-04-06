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
                  {:r r :g g :b b :a a})))

(defn ->awt-color
  "Converts a resolved color map to a java.awt.Color."
  [{:keys [r g b a]}]
  (Color. (int r) (int g) (int b) (int (* a 255))))

(comment
  (resolve-color [:color/rgb 200 0 0])
  ;; => {:r 200, :g 0, :b 0, :a 1.0}

  (resolve-color [:color/rgba 200 0 0 0.5])
  ;; => {:r 200, :g 0, :b 0, :a 0.5}

  (->awt-color {:r 200 :g 100 :b 50 :a 1.0})
  ;; => #object[java.awt.Color ...]
  )
