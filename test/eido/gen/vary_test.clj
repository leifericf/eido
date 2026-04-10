(ns eido.gen.vary-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.vary :as vary]))

(deftest by-index-test
  (testing "generates n overrides via index function"
    (let [result (vary/by-index 5 (fn [i] {:node/opacity (/ (double i) 4.0)}))]
      (is (= 5 (count result)))
      (is (= 0.0 (:node/opacity (first result))))
      (is (= 1.0 (:node/opacity (last result)))))))

(deftest by-position-test
  (testing "generates overrides from positions"
    (let [pts [[10 20] [30 40]]
          result (vary/by-position pts (fn [x y] {:style/fill [:color/rgb (int x) (int y) 0]}))]
      (is (= 2 (count result)))
      (is (= [:color/rgb 10 20 0] (:style/fill (first result)))))))

(deftest by-noise-test
  (testing "generates overrides from noise at positions"
    (let [pts [[10 20] [30 40] [50 60]]
          result (vary/by-noise pts (fn [v] {:node/opacity (+ 0.5 (* 0.5 v))}) {:noise-scale 0.01 :seed 42})]
      (is (= 3 (count result)))
      (is (every? #(number? (:node/opacity %)) result)))))

(deftest by-gradient-test
  (testing "generates fill overrides along a gradient"
    (let [stops [[0.0 [:color/rgb 0 0 0]] [1.0 [:color/rgb 255 255 255]]]
          result (vary/by-gradient 3 stops)]
      (is (= 3 (count result)))
      (is (= [:color/rgb 0 0 0] (:style/fill (first result))))
      (is (= [:color/rgb 255 255 255] (:style/fill (last result)))))))

(deftest apply-overrides-test
  (testing "merges overrides onto children"
    (let [children [{:node/type :group :group/children [{:style/fill [:color/rgb 0 0 0]}]}
                    {:node/type :group :group/children [{:style/fill [:color/rgb 0 0 0]}]}]
          overrides [{:style/fill [:color/rgb 255 0 0]}
                     {:style/fill [:color/rgb 0 255 0]}]
          result (vary/apply-overrides children overrides)]
      (is (= [:color/rgb 255 0 0] (get-in (first result) [:group/children 0 :style/fill])))
      (is (= [:color/rgb 0 255 0] (get-in (second result) [:group/children 0 :style/fill]))))))

(deftest apply-overrides-wrapping-test
  (testing "overrides wrap when shorter than children"
    (let [children (repeat 4 {:node/type :group :group/children [{:style/fill [:color/rgb 0 0 0]}]})
          overrides [{:style/fill [:color/rgb 255 0 0]}
                     {:style/fill [:color/rgb 0 255 0]}]
          result (vary/apply-overrides (vec children) overrides)]
      (is (= 4 (count result)))
      ;; Index 2 wraps to override 0 (red)
      (is (= [:color/rgb 255 0 0] (get-in (nth result 2) [:group/children 0 :style/fill]))))))
