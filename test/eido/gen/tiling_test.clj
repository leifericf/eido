(ns eido.gen.tiling-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.tiling :as tiling]))

(deftest random-grid-dimensions-test
  (testing "random-grid returns correct dimensions"
    (let [grid (tiling/random-grid 2 5 3 42)]
      (is (= 3 (count grid)))
      (is (every? #(= 5 (count %)) grid))
      (is (every? (fn [row] (every? #{0 1} row)) grid)))))

(deftest random-grid-deterministic-test
  (testing "same seed produces same grid"
    (is (= (tiling/random-grid 4 6 6 42)
           (tiling/random-grid 4 6 6 42)))))

(deftest random-grid-seed-variety-test
  (testing "different seeds produce different grids"
    (is (not= (tiling/random-grid 2 6 6 1)
              (tiling/random-grid 2 6 6 2)))))

(deftest solve-wang-basic-test
  (testing "Wang solver finds valid tiling"
    (let [tiles (tiling/wang-basic)
          grid  (tiling/solve tiles 4 4 {:seed 5})]
      (is (some? grid))
      (is (= 4 (count grid)))
      (is (every? #(= 4 (count %)) grid))
      ;; Every value is a valid tile index
      (is (every? (fn [row] (every? #(< % (count tiles)) row)) grid)))))

(deftest solve-wang-edges-match-test
  (testing "Wang solution satisfies edge constraints"
    (let [tiles (tiling/wang-basic)
          grid  (tiling/solve tiles 4 4 {:seed 2})]
      (is (some? grid))
      ;; Check horizontal: east of left = west of right
      (doseq [r (range 4) c (range 3)]
        (let [left  (get-in grid [r c])
              right (get-in grid [r (inc c)])]
          (is (= (:e (:tile/edges (nth tiles left)))
                 (:w (:tile/edges (nth tiles right))))
              (str "H-mismatch at [" r "," c "]->[" r "," (inc c) "]"))))
      ;; Check vertical: south of top = north of bottom
      (doseq [r (range 3) c (range 4)]
        (let [top    (get-in grid [r c])
              bottom (get-in grid [(inc r) c])]
          (is (= (:s (:tile/edges (nth tiles top)))
                 (:n (:tile/edges (nth tiles bottom))))
              (str "V-mismatch at [" r "," c "]->[" (inc r) "," c "]")))))))

(deftest solve-wang-seed-variety-test
  (testing "different seeds produce different Wang tilings"
    (let [tiles (tiling/wang-basic)]
      (is (not= (tiling/solve tiles 4 4 {:seed 1})
                (tiling/solve tiles 4 4 {:seed 10}))))))

(deftest solve-pipe-test
  (testing "pipe solver finds valid tiling"
    (let [tiles (tiling/pipe-tiles)
          grid  (tiling/solve tiles 4 4 {:seed 1})]
      (is (some? grid))
      ;; Check horizontal edge matching
      (doseq [r (range 4) c (range 3)]
        (let [left  (get-in grid [r c])
              right (get-in grid [r (inc c)])]
          (is (= (:e (:tile/edges (nth tiles left)))
                 (:w (:tile/edges (nth tiles right))))))))))

(deftest solve-deterministic-test
  (testing "same seed produces same tiling"
    (let [tiles (tiling/wang-basic)]
      (is (= (tiling/solve tiles 4 4 {:seed 42})
             (tiling/solve tiles 4 4 {:seed 42}))))))

(deftest tiling->nodes-test
  (testing "tiling->nodes produces scene nodes"
    (let [tiles (tiling/truchet-arcs)
          grid  (tiling/random-grid 2 3 3 42)
          nodes (tiling/tiling->nodes grid tiles 40)]
      (is (pos? (count nodes)))
      (is (every? #(contains? % :node/type) nodes)))))
