(ns eido.gen.voronoi-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.voronoi :as voronoi]))

(deftest voronoi-cells-count-test
  (testing "produces one cell per point"
    (let [points [[50 50] [150 50] [50 150] [150 150]]
          cells (voronoi/voronoi-cells points [0 0 200 200])]
      (is (= 4 (count cells)))
      (is (every? #(= :shape/path (:node/type %)) cells)))))

(deftest voronoi-cells-closed-test
  (testing "each cell is a closed path"
    (let [cells (voronoi/voronoi-cells [[50 50] [150 150]] [0 0 200 200])]
      (is (every? (fn [c]
                    (= :close (first (last (:path/commands c)))))
                  cells)))))

(deftest voronoi-cells-deterministic-test
  (testing "same input produces same output"
    (let [pts [[30 70] [170 40] [100 180]]]
      (is (= (voronoi/voronoi-cells pts [0 0 200 200])
             (voronoi/voronoi-cells pts [0 0 200 200]))))))

(deftest delaunay-edges-test
  (testing "3 points produce a triangle (3 edges)"
    (let [edges (voronoi/delaunay-edges [[50 50] [150 50] [100 150]] [0 0 200 200])]
      (is (= 3 (count edges)))
      (is (every? #(= :shape/line (:node/type %)) edges)))))

(deftest delaunay-edges-4-points-test
  (testing "4 points in a square produce edges"
    (let [edges (voronoi/delaunay-edges [[50 50] [150 50] [50 150] [150 150]]
                  [0 0 200 200])]
      (is (pos? (count edges)))
      (is (<= 4 (count edges) 6)))))
