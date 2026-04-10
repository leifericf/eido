(ns eido.path.distort
  "Point-level path distortion using noise, waves, roughening, and jitter.
  Each distortion displaces individual points without changing path topology."
  (:require
    [eido.gen.noise :as noise]))

;; --- distortion functions ---

(defn- displace-point
  "Displaces a point [x y] by [dx dy]."
  [[x y] [dx dy]]
  [(+ (double x) dx) (+ (double y) dy)])

(defn- noise-displacement
  "Returns [dx dy] noise displacement for a point."
  [x y {:keys [amplitude frequency seed] :or {amplitude 5 frequency 0.1 seed 0}}]
  (let [amp (double amplitude)
        freq (double frequency)
        opts (when seed {:seed seed})]
    (if (zero? amp)
      [0.0 0.0]
      [(* amp (noise/perlin2d (* x freq) (* y freq) opts))
       (* amp (noise/perlin2d (* x freq 1.3) (* y freq 1.7) opts))])))

(defn- wave-displacement
  "Returns [dx dy] wave displacement for a point."
  [x y {:keys [axis amplitude wavelength] :or {axis :y amplitude 5 wavelength 50}}]
  (let [amp (double amplitude)
        wl  (double wavelength)]
    (case axis
      :y [0.0 (* amp (Math/sin (* 2.0 Math/PI (/ x wl))))]
      :x [(* amp (Math/sin (* 2.0 Math/PI (/ y wl)))) 0.0])))

(defn- roughen-displacement
  "Returns [dx dy] pseudo-random displacement based on point index and coords."
  [x y i {:keys [amount seed] :or {amount 3 seed 0}}]
  (let [amt  (double amount)
        opts (when seed {:seed seed})]
    [(* amt (noise/perlin2d (+ (* i 17.3) x 0.5) (+ y 0.3) opts))
     (* amt (noise/perlin2d (+ x 0.3) (+ (* i 23.7) y 0.5) opts))]))

(defn- jitter-displacement
  "Returns [dx dy] jitter displacement using noise at unique offsets per point."
  [x y i {:keys [amount seed] :or {amount 3 seed 0}}]
  (let [amt  (double amount)
        opts (when seed {:seed seed})]
    [(* amt (noise/perlin2d (+ (* i 31.1) 100) (+ y 200) opts))
     (* amt (noise/perlin2d (+ x 300) (+ (* i 47.3) 400) opts))]))

;; --- command distortion ---

(defn- distort-command
  "Applies displacement to a single path command's coordinates."
  [i [cmd & args :as command] opts]
  (case cmd
    :move-to (let [[x y] (first args)
                   disp  (case (:type opts)
                           :noise   (noise-displacement x y opts)
                           :wave    (wave-displacement x y opts)
                           :roughen (roughen-displacement x y i opts)
                           :jitter  (jitter-displacement x y i opts))]
               [:move-to (displace-point [x y] disp)])
    :line-to (let [[x y] (first args)
                   disp  (case (:type opts)
                           :noise   (noise-displacement x y opts)
                           :wave    (wave-displacement x y opts)
                           :roughen (roughen-displacement x y i opts)
                           :jitter  (jitter-displacement x y i opts))]
               [:line-to (displace-point [x y] disp)])
    :curve-to (let [[c1 c2 pt] args
                    disp-fn (fn [p]
                              (case (:type opts)
                                :noise   (noise-displacement (p 0) (p 1) opts)
                                :wave    (wave-displacement (p 0) (p 1) opts)
                                :roughen (roughen-displacement (p 0) (p 1) i opts)
                                :jitter  (jitter-displacement (p 0) (p 1) i opts)))]
                [:curve-to (displace-point c1 (disp-fn c1))
                           (displace-point c2 (disp-fn c2))
                           (displace-point pt (disp-fn pt))])
    :quad-to (let [[cp pt] args
                   disp-fn (fn [p]
                             (case (:type opts)
                               :noise   (noise-displacement (p 0) (p 1) opts)
                               :wave    (wave-displacement (p 0) (p 1) opts)
                               :roughen (roughen-displacement (p 0) (p 1) i opts)
                               :jitter  (jitter-displacement (p 0) (p 1) i opts)))]
               [:quad-to (displace-point cp (disp-fn cp))
                         (displace-point pt (disp-fn pt))])
    command))

(defn distort-commands
  "Distorts path commands using the specified method.
  opts keys: :type (:noise, :wave, :roughen, :jitter)
  For :noise — :amplitude, :frequency, :seed
  For :wave  — :axis (:x or :y), :amplitude, :wavelength
  For :roughen/:jitter — :amount, :seed"
  [commands opts]
  (mapv (fn [i cmd] (distort-command i cmd opts))
        (range)
        commands))

(comment
  (distort-commands
    [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]] [:line-to [100.0 100.0]] [:close]]
    {:type :noise :amplitude 5 :frequency 0.1 :seed 42})
  (distort-commands
    [[:move-to [0.0 50.0]] [:line-to [50.0 50.0]] [:line-to [100.0 50.0]]]
    {:type :wave :axis :y :amplitude 10 :wavelength 50})
  )
