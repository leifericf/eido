(ns eido.gen.knot-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.knot :as knot]))

(deftest solve-basic-test
  (testing "produces grid of correct dimensions"
    (let [grid (knot/solve 4 4 {:seed 0})]
      (is (= 4 (count grid)))
      (is (every? #(= 4 (count %)) grid))
      (is (every? (fn [row] (every? #{0 1} row)) grid)))))

(deftest solve-alternation-test
  (testing "adjacent crossings differ (alternation constraint)"
    (let [grid (knot/solve 5 5 {:seed 0})]
      ;; Horizontal neighbors differ
      (doseq [r (range 5) c (range 4)]
        (is (not= (get-in grid [r c])
                  (get-in grid [r (inc c)]))
            (str "H-same at [" r "," c "]")))
      ;; Vertical neighbors differ
      (doseq [r (range 4) c (range 5)]
        (is (not= (get-in grid [r c])
                  (get-in grid [(inc r) c]))
            (str "V-same at [" r "," c "]"))))))

(deftest solve-with-holes-test
  (testing "holes appear as nil in grid"
    (let [holes #{[1 1] [2 2]}
          grid  (knot/solve 4 4 {:seed 0 :holes holes})]
      (is (nil? (get-in grid [1 1])))
      (is (nil? (get-in grid [2 2])))
      ;; Non-hole cells are 0 or 1
      (doseq [r (range 4) c (range 4)
              :when (not (holes [r c]))]
        (is (#{0 1} (get-in grid [r c])))))))

(deftest solve-with-holes-alternation-test
  (testing "alternation holds between adjacent non-hole crossings"
    (let [holes #{[2 2]}
          grid  (knot/solve 5 5 {:seed 0 :holes holes})]
      (doseq [r (range 5) c (range 4)]
        (let [a (get-in grid [r c])
              b (get-in grid [r (inc c)])]
          (when (and a b)
            (is (not= a b)))))
      (doseq [r (range 4) c (range 5)]
        (let [a (get-in grid [r c])
              b (get-in grid [(inc r) c])]
          (when (and a b)
            (is (not= a b))))))))

(deftest solve-seed-variety-test
  (testing "different seeds produce different patterns"
    (is (not= (knot/solve 3 3 {:seed 0})
              (knot/solve 3 3 {:seed 1})))))

(deftest solve-deterministic-test
  (testing "same seed produces same result"
    (is (= (knot/solve 4 4 {:seed 42})
           (knot/solve 4 4 {:seed 42})))))

(deftest knot->nodes-test
  (testing "produces scene path nodes"
    (let [grid  (knot/solve 4 4 {:seed 0})
          nodes (knot/knot->nodes grid 40 [10 10])]
      (is (pos? (count nodes)))
      (is (every? #(= :shape/path (:node/type %)) nodes))
      (is (every? :path/commands nodes)))))

(deftest knot->nodes-style-test
  (testing "style is merged into nodes"
    (let [grid  (knot/solve 3 3 {:seed 0})
          style {:style/stroke [:color/rgb 0 0 0]
                 :style/stroke-width 2}
          nodes (knot/knot->nodes grid 40 [0 0] {:style style})]
      (is (every? :style/stroke nodes))
      (is (every? :style/stroke-width nodes)))))
