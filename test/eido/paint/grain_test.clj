(ns eido.paint.grain-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.paint.grain :as grain]))

(deftest fbm-grain-test
  (testing "returns values in [0, 1]"
    (doseq [x (range 0 100 10)
            y (range 0 100 10)]
      (let [v (grain/evaluate-grain {:grain/type :fbm :grain/scale 0.1}
                                    (double x) (double y))]
        (is (>= v 0.0) (str "should be >= 0 at " x "," y))
        (is (<= v 1.0) (str "should be <= 1 at " x "," y))))))

(deftest nil-grain-test
  (testing "nil spec returns 1.0 (no modulation)"
    (is (= 1.0 (grain/evaluate-grain nil 50.0 50.0)))))

(deftest grain-types-produce-values-test
  (testing "all grain types produce non-zero variation"
    (doseq [grain-type [:fbm :turbulence :ridge :fiber :weave :canvas]]
      (let [values (mapv #(grain/evaluate-grain
                            {:grain/type grain-type :grain/scale 0.1}
                            (double %) 50.0)
                         (range 0 100 5))]
        ;; Should have some variation (not all the same)
        (is (> (count (distinct (mapv #(Math/round (* % 10.0)) values))) 1)
            (str grain-type " should produce variation"))))))
