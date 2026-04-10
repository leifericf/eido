(ns eido.path-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.path :as path]))

(def rect-a [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]]
             [:line-to [100.0 100.0]] [:line-to [0.0 100.0]] [:close]])

(def rect-b [[:move-to [50.0 50.0]] [:line-to [150.0 50.0]]
             [:line-to [150.0 150.0]] [:line-to [50.0 150.0]] [:close]])

(deftest union-test
  (testing "union of two overlapping rects produces a path"
    (let [result (path/union rect-a rect-b)]
      (is (vector? result))
      (is (pos? (count result)))
      (is (= :move-to (first (first result)))))))

(deftest intersection-test
  (testing "intersection of two overlapping rects produces a smaller area"
    (let [result (path/intersection rect-a rect-b)]
      (is (vector? result))
      (is (pos? (count result))))))

(deftest difference-test
  (testing "difference A-B removes B's area from A"
    (let [result (path/difference rect-a rect-b)]
      (is (vector? result))
      (is (pos? (count result))))))

(deftest xor-test
  (testing "xor of overlapping rects produces a path"
    (let [result (path/xor rect-a rect-b)]
      (is (vector? result))
      (is (pos? (count result))))))

(deftest xor-identical-test
  (testing "xor of identical shapes produces empty path"
    (let [result (path/xor rect-a rect-a)]
      ;; Should produce no visible area (empty or just a close)
      (is (<= (count (filter #(#{:line-to :curve-to} (first %)) result)) 1)))))

(deftest union-non-overlapping-test
  (testing "union of non-overlapping shapes preserves both"
    (let [far-rect [[:move-to [200.0 200.0]] [:line-to [300.0 200.0]]
                    [:line-to [300.0 300.0]] [:line-to [200.0 300.0]] [:close]]
          result (path/union rect-a far-rect)]
      (is (pos? (count result)))
      ;; Should have multiple move-to (two separate subpaths)
      (is (>= (count (filter #(= :move-to (first %)) result)) 2)))))

;; --- simplify ---

(deftest simplify-test
  (testing "straight line simplifies to 2 points"
    (let [points [[0 0] [10 0] [20 0] [30 0] [40 0]]
          result (path/simplify points 1.0)]
      (is (= 2 (count result)))
      (is (= [0 0] (first result)))
      (is (= [40 0] (last result)))))
  (testing "epsilon=0 returns all points"
    (let [points [[0 0] [10 5] [20 0] [30 5] [40 0]]
          result (path/simplify points 0.0)]
      (is (= (count points) (count result)))))
  (testing "preserves shape for large features"
    (let [points [[0 0] [50 0] [50 50] [0 50]]
          result (path/simplify points 1.0)]
      (is (= 4 (count result))))))

(deftest simplify-commands-test
  (testing "simplifies path commands"
    (let [cmds [[:move-to [0 0]] [:line-to [10 0]] [:line-to [20 0]]
                [:line-to [30 0]] [:line-to [40 0]]]
          result (path/simplify-commands cmds 1.0)]
      (is (= :move-to (ffirst result)))
      (is (= 2 (count result))))))

;; --- contains-point? ---

(deftest contains-point-test
  (testing "point inside square"
    (is (path/contains-point? [[0 0] [100 0] [100 100] [0 100]] [50 50])))
  (testing "point outside square"
    (is (not (path/contains-point? [[0 0] [100 0] [100 100] [0 100]] [150 50]))))
  (testing "point above square"
    (is (not (path/contains-point? [[0 0] [100 0] [100 100] [0 100]] [50 -10]))))
  (testing "works with triangle"
    (is (path/contains-point? [[0 0] [100 0] [50 100]] [50 30]))
    (is (not (path/contains-point? [[0 0] [100 0] [50 100]] [90 90])))))

;; --- inset ---

(defn- approx=
  "Checks two points are approximately equal."
  [[x1 y1] [x2 y2] tol]
  (and (< (Math/abs (- (double x1) (double x2))) tol)
       (< (Math/abs (- (double y1) (double y2))) tol)))

(deftest inset-square-test
  (testing "square inset by 10 produces smaller square"
    (let [polygon [[0 0] [100 0] [100 100] [0 100]]
          result (path/inset polygon 10.0)]
      (is (= 4 (count result)))
      (is (approx= (nth result 0) [10 10] 0.5))
      (is (approx= (nth result 1) [90 10] 0.5))
      (is (approx= (nth result 2) [90 90] 0.5))
      (is (approx= (nth result 3) [10 90] 0.5)))))

(deftest inset-zero-test
  (testing "inset 0 returns original polygon"
    (let [polygon [[0.0 0.0] [100.0 0.0] [100.0 100.0] [0.0 100.0]]
          result (path/inset polygon 0.0)]
      (is (= 4 (count result)))
      (is (approx= (nth result 0) [0 0] 0.5)))))

(deftest inset-triangle-test
  (testing "triangle inset produces smaller triangle"
    (let [polygon [[50 0] [100 100] [0 100]]
          result (path/inset polygon 5.0)]
      (is (= 3 (count result)))
      ;; Smaller triangle: all points should be inside the original
      (is (every? (fn [pt] (path/contains-point? polygon pt)) result)))))
