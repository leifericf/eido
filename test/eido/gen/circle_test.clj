(ns eido.gen.circle-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.circle :as circle]))

;; --- circle-pack ---

(deftest circle-pack-determinism-test
  (testing "same seed produces same output"
    (is (= (circle/circle-pack 0 0 200 200 {:seed 42})
           (circle/circle-pack 0 0 200 200 {:seed 42})))))

(deftest circle-pack-different-seeds-test
  (testing "different seeds produce different output"
    (is (not= (circle/circle-pack 0 0 200 200 {:seed 42})
              (circle/circle-pack 0 0 200 200 {:seed 99})))))

(deftest circle-pack-no-overlaps-test
  (testing "no circles overlap (beyond epsilon)"
    (let [circles (circle/circle-pack 0 0 200 200
                    {:min-radius 3.0 :max-radius 30.0 :padding 1.0 :seed 42})
          eps 0.01]
      (doseq [i (range (count circles))
              j (range (inc i) (count circles))]
        (let [{[x1 y1] :center r1 :radius} (nth circles i)
              {[x2 y2] :center r2 :radius} (nth circles j)
              dx (- x2 x1) dy (- y2 y1)
              dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
          (is (>= dist (- (+ r1 r2 1.0) eps))
              (str "circles " i " and " j " overlap")))))))

(deftest circle-pack-within-bounds-test
  (testing "all circle centers within bounds"
    (let [circles (circle/circle-pack 10 20 100 80 {:seed 42})]
      (doseq [{[x y] :center} circles]
        (is (>= x 10))
        (is (<= x 110))
        (is (>= y 20))
        (is (<= y 100))))))

(deftest circle-pack-radius-constraints-test
  (testing "all radii within min/max"
    (let [circles (circle/circle-pack 0 0 200 200
                    {:min-radius 5.0 :max-radius 25.0 :seed 42})]
      (doseq [{r :radius} circles]
        (is (>= r 5.0))
        (is (<= r 25.0))))))

(deftest circle-pack-max-circles-test
  (testing "respects max-circles cap"
    (let [circles (circle/circle-pack 0 0 500 500
                    {:max-circles 10 :seed 42})]
      (is (<= (count circles) 10)))))

(deftest circle-pack-produces-output-test
  (testing "produces some circles in a reasonable area"
    (let [circles (circle/circle-pack 0 0 200 200 {:seed 42})]
      (is (pos? (count circles))))))

;; --- pack->nodes ---

(deftest pack->nodes-test
  (testing "produces valid circle scene nodes"
    (let [circles (circle/circle-pack 0 0 200 200 {:max-circles 5 :seed 42})
          nodes   (circle/pack->nodes circles {:style {:style/fill [:color/rgb 255 0 0]}})]
      (is (= (count circles) (count nodes)))
      (is (every? #(= :shape/circle (:node/type %)) nodes)))))
