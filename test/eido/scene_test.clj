(ns eido.scene-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.scene :as scene]))

;; --- grid tests ---

(deftest grid-basic-test
  (testing "produces correct col/row for each cell"
    (let [nodes (scene/grid 3 2 (fn [c r] {:col c :row r}))]
      (is (= 6 (count nodes)))
      (is (= {:col 0 :row 0} (first nodes)))
      (is (= {:col 2 :row 1} (last nodes))))))

(deftest grid-row-major-order-test
  (testing "visits cells in row-major order"
    (let [nodes (scene/grid 2 2 (fn [c r] [c r]))]
      (is (= [[0 0] [1 0] [0 1] [1 1]] nodes)))))

(deftest grid-nil-skip-test
  (testing "nil return from f skips that cell"
    (let [nodes (scene/grid 3 3 (fn [c r] (when (= c r) {:diag true})))]
      (is (= 3 (count nodes)))
      (is (every? #(= {:diag true} %) nodes)))))

(deftest grid-zero-dimensions-test
  (testing "zero cols or rows returns empty vector"
    (is (= [] (scene/grid 0 5 (fn [_ _] :x))))
    (is (= [] (scene/grid 5 0 (fn [_ _] :x))))))
