(ns eido.gen.subdivide-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.subdivide :as sub]))

(deftest subdivide-determinism-test
  (testing "same seed produces same output"
    (is (= (sub/subdivide [0 0 400 400] {:seed 42})
           (sub/subdivide [0 0 400 400] {:seed 42})))))

(deftest subdivide-different-seeds-test
  (testing "different seeds produce different output"
    (is (not= (sub/subdivide [0 0 400 400] {:seed 42})
              (sub/subdivide [0 0 400 400] {:seed 99})))))

(deftest subdivide-produces-rects-test
  (testing "produces multiple rectangles"
    (let [rects (sub/subdivide [0 0 400 400] {:seed 42})]
      (is (vector? rects))
      (is (> (count rects) 1)))))

(deftest subdivide-depth-constraint-test
  (testing "no rect exceeds max depth"
    (let [rects (sub/subdivide [0 0 400 400] {:depth 3 :seed 42})]
      (is (every? #(<= (:depth %) 3) rects)))))

(deftest subdivide-min-size-constraint-test
  (testing "no rect dimension smaller than min-size"
    (let [rects (sub/subdivide [0 0 400 400] {:min-size 30 :seed 42})]
      (doseq [{[_ _ w h] :rect} rects]
        (is (>= w 29.9) (str "width " w " below min-size"))
        (is (>= h 29.9) (str "height " h " below min-size"))))))

(deftest subdivide-area-conservation-test
  (testing "total area approximately equals input area"
    (let [rects (sub/subdivide [0 0 400 400] {:padding 0 :seed 42})
          total (reduce + (map (fn [{[_ _ w h] :rect}] (* w h)) rects))]
      (is (< (Math/abs (- total (* 400 400))) 1.0)))))

(deftest subdivide-within-bounds-test
  (testing "all rects within original bounds"
    (let [rects (sub/subdivide [10 20 200 150] {:seed 42})]
      (doseq [{[x y w h] :rect} rects]
        (is (>= x 9.99))
        (is (>= y 19.99))
        (is (<= (+ x w) 210.01))
        (is (<= (+ y h) 170.01))))))

;; --- subdivide->nodes ---

(deftest subdivide->nodes-test
  (testing "produces valid rect scene nodes"
    (let [rects (sub/subdivide [0 0 200 200] {:seed 42})
          nodes (sub/subdivide->nodes rects (constantly [:color/rgb 200 100 50]))]
      (is (= (count rects) (count nodes)))
      (is (every? #(= :shape/rect (:node/type %)) nodes)))))

;; --- convenience helper tests ---

(deftest subdivide->palette-nodes-test
  (testing "produces colored rect nodes"
    (let [rects (sub/subdivide [0 0 200 200] {:seed 42})
          pal [[:color/rgb 255 0 0] [:color/rgb 0 255 0] [:color/rgb 0 0 255]]
          nodes (sub/subdivide->palette-nodes rects pal {:seed 42})]
      (is (= (count rects) (count nodes)))
      (is (every? #(= :shape/rect (:node/type %)) nodes))
      (is (every? :style/fill nodes)))))
