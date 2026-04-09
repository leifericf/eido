(ns eido.path.morph-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.path.morph :as morph]))

(def square [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]]
             [:line-to [100.0 100.0]] [:line-to [0.0 100.0]] [:close]])

(def shifted [[:move-to [50.0 0.0]] [:line-to [150.0 0.0]]
              [:line-to [150.0 100.0]] [:line-to [50.0 100.0]] [:close]])

(deftest morph-t0-test
  (testing "t=0 returns commands-a"
    (is (= square (morph/morph square shifted 0.0)))))

(deftest morph-t1-test
  (testing "t=1 returns commands-b"
    (is (= shifted (morph/morph square shifted 1.0)))))

(deftest morph-midpoint-test
  (testing "t=0.5 interpolates points"
    (let [result (morph/morph square shifted 0.5)
          [_ [x y]] (first result)]
      (is (< (Math/abs (- x 25.0)) 0.01))
      (is (< (Math/abs (- y 0.0)) 0.01)))))

(deftest resample-test
  (testing "resample returns exact number of points"
    (let [result (morph/resample square 10)]
      (is (= 10 (count (filter #(#{:move-to :line-to} (first %)) result)))))))

(deftest resample-preserves-start-test
  (testing "resample starts at same point"
    (let [[_ start-a] (first square)
          result (morph/resample square 10)
          [_ start-r] (first result)]
      (is (< (Math/abs (- (first start-a) (first start-r))) 0.01))
      (is (< (Math/abs (- (second start-a) (second start-r))) 0.01)))))

(deftest morph-auto-test
  (testing "morph-auto handles different-length paths"
    (let [tri [[:move-to [50.0 0.0]] [:line-to [100.0 100.0]]
               [:line-to [0.0 100.0]] [:close]]
          result (morph/morph-auto square tri 0.5)]
      (is (vector? result))
      (is (pos? (count result))))))
