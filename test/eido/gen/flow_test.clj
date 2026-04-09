(ns eido.gen.flow-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.flow :as flow]
    [eido.gen.noise :as noise]))

(deftest flow-field-basic-test
  (testing "generates path nodes"
    (let [paths (flow/flow-field 0 0 200 200 {:density 30 :steps 20 :seed 42})]
      (is (vector? paths))
      (is (pos? (count paths)))
      (is (every? #(= :shape/path (:node/type %)) paths)))))

(deftest flow-field-deterministic-test
  (testing "same seed produces same result"
    (is (= (flow/flow-field 0 0 200 200 {:density 30 :steps 20 :seed 42})
           (flow/flow-field 0 0 200 200 {:density 30 :steps 20 :seed 42})))))

(deftest flow-field-different-seeds-test
  (testing "different seeds produce different results"
    (is (not= (flow/flow-field 0 0 200 200 {:density 30 :steps 20 :seed 0})
              (flow/flow-field 0 0 200 200 {:density 30 :steps 20 :seed 99})))))

(deftest flow-field-density-test
  (testing "lower density produces more streamlines"
    (let [sparse (flow/flow-field 0 0 200 200 {:density 50 :steps 20 :seed 42})
          dense  (flow/flow-field 0 0 200 200 {:density 15 :steps 20 :seed 42})]
      (is (> (count dense) (count sparse))))))

(deftest flow-field-commands-test
  (testing "each path starts with move-to"
    (let [paths (flow/flow-field 0 0 200 200 {:density 30 :steps 20 :seed 42})]
      (is (every? #(= :move-to (first (first (:path/commands %))))
                  paths)))))
