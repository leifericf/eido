(ns ^{:stability :provisional} eido.paint.sensor
  "Sensor modulation for the paint engine.

  Maps input values (pressure, speed, tilt, distance along stroke)
  to brush parameter adjustments via interpolated curves.")

;; --- curve interpolation ---

(defn curve-lookup
  "Interpolates a value from a [[t value] ...] curve at parameter t.
  Curve must be sorted by t. Returns 1.0 for nil curves."
  ^double [curve ^double t]
  (if (nil? curve)
    1.0
    (let [t (Math/max 0.0 (Math/min 1.0 t))
          n (count curve)]
      (cond
        (<= n 0)  1.0
        (== n 1)  (double (second (first curve)))
        :else
        (loop [i 0]
          (if (>= i (dec n))
            (double (second (nth curve (dec n))))
            (let [[t0 v0] (nth curve i)
                  [t1 v1] (nth curve (inc i))]
              (if (<= (double t0) t (double t1))
                (let [seg-t (if (== (double t0) (double t1))
                              0.0
                              (/ (- t (double t0)) (- (double t1) (double t0))))]
                  (+ (* (- 1.0 seg-t) (double v0))
                     (* seg-t (double v1))))
                (recur (inc i))))))))))

;; --- sensor inputs ---

(defn compute-sensor-inputs
  "Computes sensor input values from raw stroke sample data.
  Returns a map of sensor values normalized to [0..1] or natural ranges.

  sample: {:x :y :pressure :speed :tilt-x :tilt-y}
  stroke-t: normalized position along stroke [0..1]
  prev-sample: previous sample (for direction computation)"
  [sample ^double stroke-t prev-sample]
  (let [pressure (double (get sample :pressure 1.0))
        speed    (double (get sample :speed 0.0))
        tilt-x   (double (get sample :tilt-x 0.0))
        tilt-y   (double (get sample :tilt-y 0.0))
        ;; Compute stroke direction angle
        angle    (if prev-sample
                   (let [dx (- (double (:x sample)) (double (:x prev-sample)))
                         dy (- (double (:y sample)) (double (:y prev-sample)))]
                     (if (and (< (Math/abs dx) 0.001) (< (Math/abs dy) 0.001))
                       0.0
                       (Math/atan2 dy dx)))
                   0.0)]
    {:sensor/pressure   pressure
     :sensor/speed      speed
     :sensor/tilt-x     tilt-x
     :sensor/tilt-y     tilt-y
     :sensor/distance   stroke-t
     :sensor/angle      angle
     :sensor/tilt-elevation (Math/sqrt (+ (* tilt-x tilt-x) (* tilt-y tilt-y)))}))

;; --- dynamics application ---

(defn apply-dynamics
  "Applies a vector of dynamic mappings to a base parameter map.
  Each dynamic is {:sensor/input :pressure, :sensor/target :paint/opacity,
                   :sensor/curve [[0 0.2] [1 1.0]]}.
  Returns an updated parameter map with modulated values."
  [base-params dynamics sensor-inputs]
  (if (empty? dynamics)
    base-params
    (reduce
      (fn [params dyn]
        (let [input-key  (:sensor/input dyn)
              target-key (:sensor/target dyn)
              curve      (:sensor/curve dyn)
              ;; Support both :pressure and :sensor/pressure as input keys
          sensor-key (keyword "sensor" (name input-key))
          input-val  (double (or (get sensor-inputs sensor-key)
                                 (get sensor-inputs input-key)
                                 1.0))
              ;; Look up the curve value for this input
              modulator  (curve-lookup curve input-val)
              ;; Multiply the base parameter by the modulator
              base-val   (double (get params target-key 1.0))]
          (assoc params target-key (* base-val modulator))))
      base-params
      dynamics)))

(comment
  (curve-lookup [[0.0 0.2] [0.5 1.0] [1.0 0.3]] 0.25)
  ;; => 0.6 (interpolated between 0.2 and 1.0)

  (apply-dynamics
    {:paint/opacity 0.8 :paint/radius 10.0}
    [{:sensor/input :pressure :sensor/target :paint/opacity
      :sensor/curve [[0.0 0.2] [1.0 1.0]]}
     {:sensor/input :pressure :sensor/target :paint/radius
      :sensor/curve [[0.0 0.5] [1.0 1.0]]}]
    {:sensor/pressure 0.5})
  ;; => {:paint/opacity 0.48, :paint/radius 7.5}
  )
