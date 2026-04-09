(ns eido.path.stroke-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.path.stroke :as stroke]))

;; --- profiles ---

(deftest resolve-profile-test
  (testing "named profiles resolve to vectors"
    (is (vector? (stroke/resolve-profile :pointed)))
    (is (vector? (stroke/resolve-profile :chisel)))
    (is (vector? (stroke/resolve-profile :brush))))
  (testing "vector profiles pass through"
    (let [p [[0.0 0.0] [0.5 5.0] [1.0 0.0]]]
      (is (= p (stroke/resolve-profile p))))))

;; --- width at t ---

(deftest width-at-test
  (testing "interpolates width from profile"
    (let [profile [[0.0 0.0] [0.5 10.0] [1.0 0.0]]]
      (is (= 0.0 (stroke/width-at profile 0.0)))
      (is (= 10.0 (stroke/width-at profile 0.5)))
      (is (= 0.0 (stroke/width-at profile 1.0)))
      (is (< 4.0 (stroke/width-at profile 0.25) 6.0)))))

;; --- outline generation ---

(deftest outline-commands-test
  (testing "generates a closed path from a line with pointed profile"
    (let [path-cmds [[:move-to [0 50]] [:line-to [100 50]]]
          result    (stroke/outline-commands path-cmds :pointed 10.0)]
      (is (vector? result))
      (is (pos? (count result)))
      (is (= :move-to (first (first result))))
      (is (= :close (first (last result)))))))

(deftest outline-straight-line-test
  (testing "outline of a horizontal line has correct approximate width"
    (let [result (stroke/outline-commands
                   [[:move-to [0 50]] [:line-to [100 50]]]
                   [[0.0 1.0] [1.0 1.0]]
                   10.0)]
      ;; The outline should form a closed shape roughly 10 units tall
      (let [ys (keep (fn [[cmd & args]]
                       (when (#{:move-to :line-to} cmd)
                         (second (first args))))
                     result)
            min-y (apply min ys)
            max-y (apply max ys)]
        (is (< (Math/abs (- (- max-y min-y) 10.0)) 2.0)
            "outline height should be approximately the stroke width")))))
