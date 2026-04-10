(ns eido.gen.contour-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.contour :as contour]
    [eido.gen.noise :as noise]))

(deftest contour-lines-basic-test
  (testing "generates path nodes from noise"
    (let [paths (contour/contour-lines noise/perlin2d [0 0 200 200]
                  {:thresholds [0.0] :resolution 5 :noise-scale 0.02 :seed 42})]
      (is (vector? paths))
      (is (pos? (count paths)))
      (is (every? #(= :shape/path (:node/type %)) paths)))))

(deftest contour-lines-multiple-thresholds-test
  (testing "more thresholds produce more paths"
    (let [single (contour/contour-lines noise/perlin2d [0 0 200 200]
                   {:thresholds [0.0] :resolution 5 :noise-scale 0.02 :seed 42})
          multi  (contour/contour-lines noise/perlin2d [0 0 200 200]
                   {:thresholds [-0.3 -0.1 0.0 0.1 0.3] :resolution 5
                    :noise-scale 0.02 :seed 42})]
      (is (>= (count multi) (count single))))))

(deftest contour-lines-deterministic-test
  (testing "same parameters produce same result"
    (is (= (contour/contour-lines noise/perlin2d [0 0 200 200]
             {:thresholds [0.0] :resolution 5 :noise-scale 0.02 :seed 42})
           (contour/contour-lines noise/perlin2d [0 0 200 200]
             {:thresholds [0.0] :resolution 5 :noise-scale 0.02 :seed 42})))))

(deftest contour-lines-commands-test
  (testing "paths have valid commands"
    (let [paths (contour/contour-lines noise/perlin2d [0 0 200 200]
                  {:thresholds [0.0] :resolution 5 :noise-scale 0.02 :seed 42})]
      (is (every? (fn [p]
                    (let [cmds (:path/commands p)]
                      (and (= :move-to (first (first cmds)))
                           (every? #(#{:move-to :line-to} (first %))
                                   cmds))))
                  paths)))))
