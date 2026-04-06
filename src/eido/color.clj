(ns eido.color
  (:import
    [java.awt Color]))

(defn resolve-color
  "Resolves a color vector to a map with :r :g :b :a keys."
  [color-vec]
  (case (first color-vec)
    :color/rgb  (let [[_ r g b] color-vec]
                  {:r r :g g :b b :a 1.0})
    :color/rgba (let [[_ r g b a] color-vec]
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
