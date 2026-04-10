(ns eido.gen.prob
  "Seeded probability distributions and sampling utilities.

  All functions take an explicit seed (long) for deterministic,
  reproducible results. No global RNG state is used.")

;; --- seeded PRNG ---

(defn make-rng
  "Creates a java.util.Random from a seed."
  ^java.util.Random [seed]
  (java.util.Random. (long seed)))

;; --- continuous distributions ---

(defn uniform
  "Returns a vector of n uniform doubles in [lo, hi)."
  [n lo hi seed]
  (let [rng    (make-rng seed)
        lo-d   (double lo)
        span   (double (- hi lo))]
    (mapv (fn [_] (+ lo-d (* (.nextDouble rng) span)))
          (clojure.core/range n))))

(defn gaussian
  "Returns a vector of n Gaussian-distributed doubles with given mean and sd."
  [n mean sd seed]
  (let [rng  (make-rng seed)
        mean (double mean)
        sd   (double sd)]
    (mapv (fn [_] (+ mean (* (.nextGaussian rng) sd)))
          (range n))))

;; --- discrete distributions ---

(defn- build-cdf
  "Builds a cumulative distribution function from weights.
  Returns a double-array of cumulative probabilities."
  ^doubles [weights]
  (let [n     (count weights)
        total (double (reduce + weights))
        cdf   (double-array n)]
    (loop [i 0 acc 0.0]
      (when (< i n)
        (let [acc (+ acc (/ (double (nth weights i)) total))]
          (aset cdf i acc)
          (recur (inc i) acc))))
    cdf))

(defn- sample-cdf
  "Returns the index selected by value u from the CDF."
  ^long [^doubles cdf ^double u]
  (let [n (alength cdf)]
    (loop [i 0]
      (if (or (>= i (dec n)) (< u (aget cdf i)))
        i
        (recur (inc i))))))

(defn weighted-choice
  "Returns a single index chosen from weights (vector of positive numbers)."
  ^long [weights seed]
  (let [rng (make-rng seed)
        cdf (build-cdf weights)]
    (sample-cdf cdf (.nextDouble rng))))

(defn weighted-sample
  "Returns a vector of n indices sampled from weights (with replacement)."
  [n weights seed]
  (let [rng (make-rng seed)
        cdf (build-cdf weights)]
    (mapv (fn [_] (sample-cdf cdf (.nextDouble rng)))
          (range n))))

;; --- shuffling ---

(defn shuffle-seeded
  "Returns a deterministically shuffled vector of coll (Fisher-Yates)."
  [coll seed]
  (let [rng (make-rng seed)
        arr (object-array coll)
        n   (alength arr)]
    (loop [i (dec n)]
      (when (pos? i)
        (let [j (.nextInt rng (inc i))
              tmp (aget arr i)]
          (aset arr i (aget arr j))
          (aset arr j tmp)
          (recur (dec i)))))
    (vec arr)))

;; --- coin / pick ---

(defn coin
  "Returns true with probability p (0.0 to 1.0)."
  [p seed]
  (let [rng (make-rng seed)]
    (< (.nextDouble rng) (double p))))

(defn pick
  "Picks one element from coll uniformly at random."
  [coll seed]
  (let [rng (make-rng seed)
        v   (vec coll)]
    (nth v (.nextInt rng (count v)))))

(defn pick-weighted
  "Picks one element from items using corresponding weights."
  [items weights seed]
  (let [idx (weighted-choice weights seed)]
    (nth items idx)))

;; --- distribution spec sampling ---

(defn sample
  "Samples a single value from a distribution spec.
  Spec is a plain map with :type and type-specific keys:
    {:type :uniform  :lo 0.0 :hi 1.0}
    {:type :gaussian :mean 0.0 :sd 1.0}
    {:type :choice   :options [:a :b :c]}
    {:type :weighted-choice :options [:a :b] :weights [3 1]}
    {:type :boolean  :probability 0.5}
    {:type :pareto   :alpha 2.0 :min 1.0}           ;; optional :max
    {:type :triangular :min 0 :max 1 :mode 0.5}
    {:type :eased    :easing fn :lo 0 :hi 1}
  Same spec format used by eido.gen.series/series-params."
  [spec seed]
  (case (:type spec)
    :uniform        (first (uniform 1 (:lo spec) (:hi spec) seed))
    :gaussian       (first (gaussian 1 (:mean spec) (:sd spec) seed))
    :choice         (pick (:options spec) seed)
    :weighted-choice (pick-weighted (:options spec) (:weights spec) seed)
    :boolean        (coin (get spec :probability 0.5) seed)
    :pareto         (let [rng (make-rng seed)
                          alpha (double (:alpha spec))
                          mn (double (:min spec))
                          mx (:max spec)]
                      (if mx
                        (let [mx (double mx)]
                          (loop []
                            (let [v (* mn (Math/pow (- 1.0 (.nextDouble rng))
                                                    (/ -1.0 alpha)))]
                              (if (<= v mx) v (recur)))))
                        (* mn (Math/pow (- 1.0 (.nextDouble rng))
                                        (/ -1.0 alpha)))))
    :triangular     (let [rng (make-rng seed)
                          mn (double (:min spec))
                          mx (double (:max spec))
                          mode (double (:mode spec))
                          u (.nextDouble rng)
                          fc (/ (- mode mn) (- mx mn))]
                      (if (< u fc)
                        (+ mn (Math/sqrt (* u (- mx mn) (- mode mn))))
                        (- mx (Math/sqrt (* (- 1.0 u) (- mx mn) (- mx mode))))))
    :eased          (let [rng (make-rng seed)
                          easing (:easing spec)
                          lo (double (get spec :lo 0))
                          hi (double (get spec :hi 1))
                          u (double (easing (.nextDouble rng)))]
                      (+ lo (* u (- hi lo))))))

(defn sample-n
  "Samples n values from a distribution spec.
  Each value gets a deterministic sub-seed derived from the base seed
  and its index, ensuring independent samples."
  [spec n seed]
  (let [rng (make-rng seed)]
    (mapv (fn [_] (sample spec (.nextLong rng)))
          (range n))))

;; --- convenience helpers ---

(defn ^{:convenience true}
  pareto
  "Returns a vector of n Pareto-distributed doubles.
  Wraps (sample-n {:type :pareto ...})."
  [n alpha min-val seed]
  (sample-n {:type :pareto :alpha alpha :min min-val} n seed))

(defn ^{:convenience true}
  triangular
  "Returns a vector of n triangular-distributed doubles in [min-val, max-val].
  Wraps (sample-n {:type :triangular ...})."
  [n min-val max-val mode seed]
  (sample-n {:type :triangular :min min-val :max max-val :mode mode} n seed))

(defn ^{:convenience true}
  mixture
  "Samples n values from a mix of source vectors, weighted by mix-weights.
  Wraps weighted-choice to select which source to draw from.
  Example: (mixture [(uniform 100 1 5 s1) (gaussian 100 20 3 s2)] [3 1] 50 seed)"
  [sources mix-weights n seed]
  (let [rng (make-rng seed)
        cdf (build-cdf mix-weights)]
    (mapv (fn [_]
            (let [src-idx (sample-cdf cdf (.nextDouble rng))
                  src (nth sources src-idx)]
              (nth src (.nextInt rng (count src)))))
          (clojure.core/range n))))

(comment
  (make-rng 42)
  (uniform 5 0.0 10.0 42)
  (gaussian 5 50.0 5.0 42)
  (weighted-choice [1 3 6] 42)
  (weighted-sample 10 [1 3 6] 42)
  (shuffle-seeded [1 2 3 4 5] 42)
  (coin 0.5 42)
  (pick [:a :b :c] 42)
  (pick-weighted [:rare :common :epic] [1 5 2] 42)
  (sample {:type :uniform :lo 0.0 :hi 1.0} 42)
  (sample {:type :gaussian :mean 50.0 :sd 5.0} 42)
  (sample {:type :choice :options [:a :b :c]} 42)
  (sample {:type :pareto :alpha 2.0 :min 1.0} 42)
  (sample {:type :triangular :min 0 :max 10 :mode 3} 42)
  (sample {:type :eased :easing #(* % %) :lo 0 :hi 100} 42)
  (sample-n {:type :uniform :lo 0.0 :hi 10.0} 5 42)
  (pareto 10 2.0 1.0 42)
  (triangular 10 0 10 5 42))
