(ns eido.gen.prob-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.prob :as prob]))

;; --- make-rng ---

(deftest make-rng-test
  (testing "creates a java.util.Random"
    (is (instance? java.util.Random (prob/make-rng 42)))))

;; --- uniform ---

(deftest uniform-determinism-test
  (testing "same seed produces same output"
    (is (= (prob/uniform 10 0.0 1.0 42)
           (prob/uniform 10 0.0 1.0 42)))))

(deftest uniform-different-seeds-test
  (testing "different seeds produce different output"
    (is (not= (prob/uniform 10 0.0 1.0 42)
              (prob/uniform 10 0.0 1.0 99)))))

(deftest uniform-bounds-test
  (testing "all values within [lo, hi)"
    (let [vals (prob/uniform 1000 -5.0 10.0 42)]
      (is (every? #(and (>= % -5.0) (< % 10.0)) vals)))))

(deftest uniform-count-test
  (testing "returns exactly n values"
    (is (= 50 (count (prob/uniform 50 0.0 1.0 42))))))

;; --- gaussian ---

(deftest gaussian-determinism-test
  (testing "same seed produces same output"
    (is (= (prob/gaussian 10 0.0 1.0 42)
           (prob/gaussian 10 0.0 1.0 42)))))

(deftest gaussian-approximate-stats-test
  (testing "mean and std-dev approximately correct over large sample"
    (let [vals  (prob/gaussian 10000 50.0 5.0 42)
          mean  (/ (reduce + vals) (count vals))
          diffs (map #(Math/pow (- % mean) 2) vals)
          sd    (Math/sqrt (/ (reduce + diffs) (count vals)))]
      (is (< (Math/abs (- mean 50.0)) 0.5))
      (is (< (Math/abs (- sd 5.0)) 0.5)))))

;; --- weighted-choice ---

(deftest weighted-choice-determinism-test
  (testing "same seed produces same index"
    (is (= (prob/weighted-choice [1 2 3] 42)
           (prob/weighted-choice [1 2 3] 42)))))

(deftest weighted-choice-valid-index-test
  (testing "returns valid index"
    (let [idx (prob/weighted-choice [1 2 3] 42)]
      (is (and (>= idx 0) (< idx 3))))))

(deftest weighted-choice-proportional-test
  (testing "frequencies roughly proportional to weights over many samples"
    (let [weights [1 3 6]
          ;; Use different sub-seeds to get independent samples
          indices (mapv #(prob/weighted-choice weights %) (range 10000))
          freqs   (frequencies indices)
          total   (double (count indices))]
      ;; Weight 0 is 10%, weight 1 is 30%, weight 2 is 60%
      ;; Tolerance wider because each sample uses a different seed (not independent draws)
      (is (< (Math/abs (- (/ (get freqs 0 0) total) 0.1)) 0.08))
      (is (< (Math/abs (- (/ (get freqs 1 0) total) 0.3)) 0.08))
      (is (< (Math/abs (- (/ (get freqs 2 0) total) 0.6)) 0.08)))))

;; --- weighted-sample ---

(deftest weighted-sample-count-test
  (testing "returns exactly n indices"
    (is (= 20 (count (prob/weighted-sample 20 [1 2 3] 42))))))

(deftest weighted-sample-determinism-test
  (testing "same seed produces same output"
    (is (= (prob/weighted-sample 10 [1 2 3] 42)
           (prob/weighted-sample 10 [1 2 3] 42)))))

(deftest weighted-sample-valid-indices-test
  (testing "all indices valid"
    (let [indices (prob/weighted-sample 100 [1 2 3 4] 42)]
      (is (every? #(and (>= % 0) (< % 4)) indices)))))

;; --- shuffle-seeded ---

(deftest shuffle-seeded-determinism-test
  (testing "same seed produces same shuffle"
    (is (= (prob/shuffle-seeded [1 2 3 4 5] 42)
           (prob/shuffle-seeded [1 2 3 4 5] 42)))))

(deftest shuffle-seeded-preserves-elements-test
  (testing "contains same elements"
    (is (= (sort (prob/shuffle-seeded [5 3 1 4 2] 42))
           [1 2 3 4 5]))))

(deftest shuffle-seeded-different-seeds-test
  (testing "different seeds produce different orderings"
    (is (not= (prob/shuffle-seeded (range 20) 42)
              (prob/shuffle-seeded (range 20) 99)))))

;; --- coin ---

(deftest coin-determinism-test
  (testing "same seed produces same result"
    (is (= (prob/coin 0.5 42)
           (prob/coin 0.5 42)))))

(deftest coin-always-true-test
  (testing "probability 1.0 always returns true"
    (is (every? true? (map #(prob/coin 1.0 %) (range 100))))))

(deftest coin-always-false-test
  (testing "probability 0.0 always returns false"
    (is (every? false? (map #(prob/coin 0.0 %) (range 100))))))

;; --- pick ---

(deftest pick-determinism-test
  (testing "same seed produces same element"
    (is (= (prob/pick [:a :b :c :d] 42)
           (prob/pick [:a :b :c :d] 42)))))

(deftest pick-valid-element-test
  (testing "returns an element from the collection"
    (let [coll [:a :b :c :d]]
      (is (some #{(prob/pick coll 42)} coll)))))

;; --- pick-weighted ---

(deftest pick-weighted-determinism-test
  (testing "same seed produces same element"
    (is (= (prob/pick-weighted [:a :b :c] [1 2 3] 42)
           (prob/pick-weighted [:a :b :c] [1 2 3] 42)))))

(deftest pick-weighted-valid-element-test
  (testing "returns an element from items"
    (let [items [:a :b :c]]
      (is (some #{(prob/pick-weighted items [1 2 3] 42)} items)))))

;; --- distribution spec sampling ---

(deftest sample-uniform-test
  (testing "samples a single value from uniform spec"
    (let [v (prob/sample {:type :uniform :lo 0.0 :hi 10.0} 42)]
      (is (number? v))
      (is (>= v 0.0))
      (is (< v 10.0))))
  (testing "deterministic"
    (is (= (prob/sample {:type :uniform :lo 0.0 :hi 1.0} 42)
           (prob/sample {:type :uniform :lo 0.0 :hi 1.0} 42)))))

(deftest sample-gaussian-test
  (testing "samples a single value from gaussian spec"
    (let [v (prob/sample {:type :gaussian :mean 50.0 :sd 5.0} 42)]
      (is (number? v))))
  (testing "deterministic"
    (is (= (prob/sample {:type :gaussian :mean 0.0 :sd 1.0} 42)
           (prob/sample {:type :gaussian :mean 0.0 :sd 1.0} 42)))))

(deftest sample-choice-test
  (testing "samples from options"
    (let [v (prob/sample {:type :choice :options [:a :b :c]} 42)]
      (is (some #{v} [:a :b :c]))))
  (testing "deterministic"
    (is (= (prob/sample {:type :choice :options [:x :y]} 42)
           (prob/sample {:type :choice :options [:x :y]} 42)))))

(deftest sample-weighted-choice-test
  (testing "samples from weighted options"
    (let [v (prob/sample {:type :weighted-choice
                          :options [:rare :common :epic]
                          :weights [1 5 2]} 42)]
      (is (some #{v} [:rare :common :epic])))))

(deftest sample-boolean-test
  (testing "samples a boolean"
    (is (boolean? (prob/sample {:type :boolean :probability 0.5} 42))))
  (testing "probability 1.0 always true"
    (is (every? true? (map #(prob/sample {:type :boolean :probability 1.0} %)
                           (range 100)))))
  (testing "default probability 0.5"
    (is (boolean? (prob/sample {:type :boolean} 42)))))

(deftest sample-n-test
  (testing "returns n values"
    (is (= 10 (count (prob/sample-n {:type :uniform :lo 0.0 :hi 1.0} 10 42)))))
  (testing "all values within bounds"
    (let [vals (prob/sample-n {:type :uniform :lo -5.0 :hi 5.0} 100 42)]
      (is (every? #(and (>= % -5.0) (< % 5.0)) vals))))
  (testing "deterministic"
    (is (= (prob/sample-n {:type :gaussian :mean 0.0 :sd 1.0} 5 42)
           (prob/sample-n {:type :gaussian :mean 0.0 :sd 1.0} 5 42))))
  (testing "works with choice specs"
    (let [vals (prob/sample-n {:type :choice :options [:a :b :c]} 20 42)]
      (is (= 20 (count vals)))
      (is (every? #{:a :b :c} vals)))))

;; --- convenience helper tests ---

(deftest mixture-test
  (testing "returns n values"
    (let [src1 (prob/uniform 50 0.0 10.0 42)
          src2 (prob/uniform 50 90.0 100.0 99)
          result (prob/mixture [src1 src2] [1 1] 20 42)]
      (is (= 20 (count result)))))
  (testing "deterministic"
    (let [src1 (prob/uniform 50 0.0 10.0 42)
          src2 (prob/uniform 50 90.0 100.0 99)]
      (is (= (prob/mixture [src1 src2] [1 1] 20 42)
             (prob/mixture [src1 src2] [1 1] 20 42))))))
