(ns eido.ir.vary
  "Vary descriptors for per-item style variation.

  Vary descriptors generate override vectors that modify
  generated items (fill, stroke, opacity, transforms).

  Vary types:
    :vary/by-index    — override function called with index
    :vary/by-position — override function called with position
    :vary/by-noise    — noise-sampled at each position
    :vary/by-gradient — interpolated color gradient"
  (:require
    [eido.gen.vary :as vary]))

;; --- vary constructors ---

(defn by-index
  "Creates a vary descriptor that generates overrides by index."
  [n f]
  {:vary/type :vary/by-index
   :vary/n    n
   :vary/fn   f})

(defn by-position
  "Creates a vary descriptor that generates overrides by position."
  [positions f]
  {:vary/type      :vary/by-position
   :vary/positions positions
   :vary/fn        f})

(defn by-noise
  "Creates a vary descriptor that generates overrides from noise.
  opts: :noise-scale (required), :seed (default 0)."
  [positions f opts]
  {:vary/type      :vary/by-noise
   :vary/positions positions
   :vary/scale     (:noise-scale opts)
   :vary/seed      (get opts :seed 0)
   :vary/fn        f})

(defn by-gradient
  "Creates a vary descriptor that interpolates fill through gradient stops."
  [n stops]
  {:vary/type  :vary/by-gradient
   :vary/n     n
   :vary/stops stops})

;; --- resolution ---

(defn resolve-overrides
  "Evaluates a vary descriptor into an override vector.
  If the input is already a vector, returns it as-is."
  [vary-desc]
  (if (vector? vary-desc)
    vary-desc
    (case (:vary/type vary-desc)
      :vary/by-index    (vary/by-index (:vary/n vary-desc) (:vary/fn vary-desc))
      :vary/by-position (vary/by-position (:vary/positions vary-desc) (:vary/fn vary-desc))
      :vary/by-noise    (vary/by-noise (:vary/positions vary-desc)
                                       (:vary/fn vary-desc)
                                       {:noise-scale (:vary/scale vary-desc)
                                        :seed        (:vary/seed vary-desc)})
      :vary/by-gradient (vary/by-gradient (:vary/n vary-desc) (:vary/stops vary-desc))
      (throw (ex-info (str "Unknown vary type: " (:vary/type vary-desc))
                      {:vary vary-desc})))))
