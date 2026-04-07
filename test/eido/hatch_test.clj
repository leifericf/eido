(ns eido.hatch-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.hatch :as hatch]))

;; --- line generation ---

(deftest hatch-lines-test
  (testing "generates lines within bounds"
    (let [lines (hatch/hatch-lines 0 0 100 100 {:angle 45 :spacing 10})]
      (is (vector? lines))
      (is (pos? (count lines)))
      (is (every? (fn [[x1 y1 x2 y2]]
                    (and (number? x1) (number? y1)
                         (number? x2) (number? y2)))
                  lines))))
  (testing "horizontal lines"
    (let [lines (hatch/hatch-lines 0 0 100 100 {:angle 0 :spacing 10})]
      (is (pos? (count lines)))
      ;; All lines should be horizontal (y1 == y2)
      (is (every? (fn [[_x1 y1 _x2 y2]] (< (Math/abs (- y1 y2)) 0.01)) lines))))
  (testing "vertical lines"
    (let [lines (hatch/hatch-lines 0 0 100 100 {:angle 90 :spacing 10})]
      (is (pos? (count lines)))
      ;; All lines should be vertical (x1 == x2)
      (is (every? (fn [[x1 _y1 x2 _y2]] (< (Math/abs (- x1 x2)) 0.01)) lines)))))

;; --- expansion to scene nodes ---

(deftest hatch-fill->nodes-test
  (testing "generates path nodes for hatching"
    (let [nodes (hatch/hatch-fill->nodes 0 0 200 200
                  {:hatch/angle 45 :hatch/spacing 8 :hatch/stroke-width 1})]
      (is (vector? nodes))
      (is (pos? (count nodes)))
      (is (every? #(= :shape/path (:node/type %)) nodes)))))

;; --- cross-hatching ---

(deftest cross-hatch-test
  (testing "cross-hatch with layers generates more lines"
    (let [single (hatch/hatch-fill->nodes 0 0 100 100
                   {:hatch/angle 45 :hatch/spacing 10 :hatch/stroke-width 1})
          cross  (hatch/hatch-fill->nodes 0 0 100 100
                   {:hatch/layers [{:angle 45 :spacing 10}
                                   {:angle -45 :spacing 10}]
                    :hatch/stroke-width 1})]
      (is (> (count cross) (count single))))))
