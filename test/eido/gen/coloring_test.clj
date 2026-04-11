(ns eido.gen.coloring-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.coloring :as coloring]
    [eido.gen.voronoi :as voronoi]
    [eido.gen.subdivide :as subdivide]))

(deftest solve-triangle-test
  (testing "triangle (K3) needs 3 colors"
    (let [adj #{[0 1] [1 2] [0 2]}]
      (is (= 3 (count (distinct (coloring/solve 3 adj 3)))))
      (is (nil? (coloring/solve 3 adj 2))))))

(deftest solve-bipartite-test
  (testing "cycle of 4 (bipartite) needs only 2 colors"
    (let [result (coloring/solve 4 #{[0 1] [1 2] [2 3] [0 3]} 2)]
      (is (some? result))
      (is (every? #{0 1} result))
      (doseq [[i j] #{[0 1] [1 2] [2 3] [0 3]}]
        (is (not= (nth result i) (nth result j)))))))

(deftest solve-no-adjacency-test
  (testing "no edges means all regions get a valid color"
    (is (= [0 0 0 0 0] (coloring/solve 5 #{} 3)))))

(deftest solve-empty-test
  (testing "zero regions returns nil"
    (is (nil? (coloring/solve 0 #{} 3)))))

(deftest solve-deterministic-test
  (testing "same input gives same output"
    (let [adj #{[0 1] [1 2] [2 3] [0 3] [0 2]}]
      (is (= (coloring/solve 4 adj 4)
             (coloring/solve 4 adj 4))))))

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

(deftest color-regions-voronoi-test
  (testing "colors Voronoi cells with no adjacent same-color"
    (let [points  [[50 50] [150 50] [100 150] [50 150] [150 150]]
          bounds  [0 0 200 200]
          cells   (voronoi/voronoi-cells points bounds)
          adj     (coloring/cells-adjacency cells)
          palette [[:color/rgb 255 0 0] [:color/rgb 0 255 0]
                   [:color/rgb 0 0 255] [:color/rgb 255 255 0]]
          result  (coloring/color-regions cells adj palette {:seed 42})]
      (is (some? result))
      (is (= (count cells) (count result)))
      (is (every? :style/fill result))
      (doseq [[i j] adj]
        (is (not= (:style/fill (nth result i))
                   (:style/fill (nth result j))))))))

(deftest color-regions-seed-variety-test
  (testing "different seeds produce different colorings"
    (let [points  [[50 50] [150 50] [100 150] [50 150] [150 150]]
          bounds  [0 0 200 200]
          cells   (voronoi/voronoi-cells points bounds)
          adj     (coloring/cells-adjacency cells)
          palette [[:color/rgb 255 0 0] [:color/rgb 0 255 0]
                   [:color/rgb 0 0 255] [:color/rgb 255 255 0]]
          r1 (mapv :style/fill (coloring/color-regions cells adj palette {:seed 1}))
          r2 (mapv :style/fill (coloring/color-regions cells adj palette {:seed 2}))]
      (is (not= r1 r2)))))

(deftest solve-with-pin-test
  (testing "pinned regions get their assigned color"
    (let [result (coloring/solve 4 #{[0 1] [1 2] [2 3] [0 3]} 3
                   {:pin {0 2, 2 1}})]
      (is (some? result))
      (is (= 2 (nth result 0)))
      (is (= 1 (nth result 2)))
      ;; Adjacency still respected
      (doseq [[i j] #{[0 1] [1 2] [2 3] [0 3]}]
        (is (not= (nth result i) (nth result j)))))))

(deftest color-regions-with-pin-test
  (testing "pinning specific regions to colors"
    (let [points  [[50 50] [150 50] [100 150] [50 150] [150 150]]
          bounds  [0 0 200 200]
          cells   (voronoi/voronoi-cells points bounds)
          adj     (coloring/cells-adjacency cells)
          palette [[:color/rgb 255 0 0] [:color/rgb 0 255 0]
                   [:color/rgb 0 0 255] [:color/rgb 255 255 0]]
          red     [:color/rgb 255 0 0]
          result  (coloring/color-regions cells adj palette
                    {:seed 1 :pin {0 red}})]
      (is (some? result))
      ;; Region 0 has the pinned color
      (is (= red (:style/fill (nth result 0)))))))

(deftest color-regions-with-weight-fn-test
  (testing "weight-fn biases coloring"
    (let [nodes   (mapv (fn [i] {:node/type :shape/rect :rect/xy [(* i 50) 0] :rect/size [50 50]})
                        (range 4))
          adj     #{[0 1] [1 2] [2 3]}
          palette [[:color/rgb 255 0 0] [:color/rgb 0 255 0] [:color/rgb 0 0 255]]
          ;; Bias all regions toward color index 0
          wf      (fn [i _node] {0 10.0, 1 1.0, 2 1.0})
          result  (coloring/color-regions nodes adj palette
                    {:seed 1 :weight-fn wf :pin-ratio 0.5})]
      (is (some? result))
      (is (= 4 (count result)))
      (is (every? :style/fill result)))))
