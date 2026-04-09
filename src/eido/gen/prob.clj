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

;; --- convenience helpers ---

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
  (pick-weighted [:rare :common :epic] [1 5 2] 42))
