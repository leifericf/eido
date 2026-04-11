(ns eido.gen.coloring-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.coloring :as coloring]
    [eido.gen.voronoi :as voronoi]
    [eido.gen.subdivide :as subdivide]))

(def ^:private palette
  [[:color/rgb 255 0 0] [:color/rgb 0 255 0]
   [:color/rgb 0 0 255] [:color/rgb 255 255 0]])

(deftest cells-adjacency-test
  (testing "4-point Voronoi produces correct adjacency"
    (let [points [[50 50] [150 50] [50 150] [150 150]]
          cells  (voronoi/voronoi-cells points [0 0 200 200])
          adj    (coloring/cells-adjacency cells)]
      (is (set? adj))
      (is (every? (fn [[i j]] (< i j)) adj))
      (is (pos? (count adj))))))

(deftest rects-adjacency-test
  (testing "subdivision rects produce adjacency"
    (let [rects (subdivide/subdivide [0 0 200 200] {:seed 42 :depth 3})
          adj   (coloring/rects-adjacency rects)]
      (is (set? adj))
      (is (pos? (count adj)))
      (is (every? (fn [[i j]] (< i j)) adj)))))

(deftest color-regions-basic-test
  (testing "colors Voronoi cells with no adjacent same-color"
    (let [points [[50 50] [150 50] [100 150] [50 150] [150 150]]
          cells  (voronoi/voronoi-cells points [0 0 200 200])
          adj    (coloring/cells-adjacency cells)
          result (coloring/color-regions cells adj palette {:seed 42})]
      (is (some? result))
      (is (= (count cells) (count result)))
      (is (every? :style/fill result))
      (doseq [[i j] adj]
        (is (not= (:style/fill (nth result i))
                   (:style/fill (nth result j))))))))

(deftest color-regions-seed-variety-test
  (testing "different seeds produce different colorings"
    (let [points [[50 50] [150 50] [100 150] [50 150] [150 150]]
          cells  (voronoi/voronoi-cells points [0 0 200 200])
          adj    (coloring/cells-adjacency cells)
          r1 (mapv :style/fill (coloring/color-regions cells adj palette {:seed 1}))
          r2 (mapv :style/fill (coloring/color-regions cells adj palette {:seed 2}))]
      (is (not= r1 r2)))))

(deftest color-regions-deterministic-test
  (testing "same seed produces same result"
    (let [points [[50 50] [150 50] [100 150]]
          cells  (voronoi/voronoi-cells points [0 0 200 200])
          adj    (coloring/cells-adjacency cells)]
      (is (= (coloring/color-regions cells adj palette {:seed 42})
             (coloring/color-regions cells adj palette {:seed 42}))))))

(deftest color-regions-with-pin-test
  (testing "pinning specific regions to colors"
    (let [points [[50 50] [150 50] [100 150] [50 150] [150 150]]
          cells  (voronoi/voronoi-cells points [0 0 200 200])
          adj    (coloring/cells-adjacency cells)
          red    [:color/rgb 255 0 0]
          result (coloring/color-regions cells adj palette {:seed 1 :pin {0 red}})]
      (is (some? result))
      (is (= red (:style/fill (nth result 0))))
      ;; Adjacency still respected
      (doseq [[i j] adj]
        (is (not= (:style/fill (nth result i))
                   (:style/fill (nth result j))))))))

(deftest color-regions-impossible-test
  (testing "triangle with 2 colors returns nil"
    (let [points [[50 50] [150 50] [100 150]]
          cells  (voronoi/voronoi-cells points [0 0 200 200])
          adj    (coloring/cells-adjacency cells)
          pal2   [[:color/rgb 255 0 0] [:color/rgb 0 255 0]]]
      (is (nil? (coloring/color-regions cells adj pal2 {:seed 42}))))))
