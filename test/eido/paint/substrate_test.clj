(ns eido.paint.substrate-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.paint.substrate :as substrate]))

(deftest nil-substrate-test
  (testing "nil spec returns 1.0"
    (is (= 1.0 (substrate/evaluate-substrate nil 50.0 50.0)))))

(deftest zero-tooth-test
  (testing "zero tooth returns 1.0 (no modulation)"
    (is (= 1.0 (substrate/evaluate-tooth {:substrate/tooth 0.0} 50.0 50.0)))))

(deftest tooth-reduces-deposition-test
  (testing "tooth creates variation in deposition"
    (let [values (mapv #(substrate/evaluate-tooth
                          {:substrate/tooth 0.5 :substrate/scale 0.15}
                          (double %) 50.0)
                       (range 0 100 5))]
      ;; Some values should be < 1.0 (tooth blocks deposition in valleys)
      (is (some #(< % 1.0) values)
          "tooth should reduce deposition at some locations")
      ;; All values should be >= 0
      (is (every? #(>= % 0.0) values)))))

(deftest absorbency-test
  (testing "absorbency scales deposition"
    (is (= 0.5 (substrate/evaluate-absorbency {:substrate/absorbency 0.5} 50.0 50.0)))))
