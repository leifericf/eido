(ns eido.ir.vary-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.color :as color]
    [eido.ir :as ir]
    [eido.ir.generator :as gen]
    [eido.ir.lower :as lower]
    [eido.ir.vary :as vary]
    [eido.engine.render :as render]))

(deftest by-index-test
  (let [desc (vary/by-index 5 (fn [i] {:node/opacity (/ (double i) 4.0)}))
        overrides (vary/resolve-overrides desc)]
    (is (= 5 (count overrides)))
    (is (= 0.0 (:node/opacity (first overrides))))
    (is (= 1.0 (:node/opacity (last overrides))))))

(deftest by-gradient-test
  (let [desc (vary/by-gradient 3
               [[0.0 [:color/rgb 255 0 0]]
                [1.0 [:color/rgb 0 0 255]]])
        overrides (vary/resolve-overrides desc)]
    (is (= 3 (count overrides)))
    (is (some? (:style/fill (first overrides))))))

(deftest by-noise-test
  (let [desc (vary/by-noise [[10 10] [50 50] [90 90]] 0.01 42
               (fn [v] {:node/opacity (/ (+ v 1.0) 2.0)}))
        overrides (vary/resolve-overrides desc)]
    (is (= 3 (count overrides)))
    (is (number? (:node/opacity (first overrides))))))

(deftest literal-vector-passthrough-test
  (let [overrides [{:style/fill [:color/rgb 255 0 0]}
                   {:style/fill [:color/rgb 0 255 0]}]]
    (is (= overrides (vary/resolve-overrides overrides)))))

(deftest scatter-with-vary-descriptor-test
  (testing "scatter generator with vary descriptor produces ops"
    (let [g (:item/generator
              (gen/scatter-gen
                {:node/type :shape/circle
                 :circle/center [0 0]
                 :circle/radius 5
                 :style/fill [:color/rgb 200 0 0]}
                [[20 20] [60 60] [100 100]]
                :overrides (vary/by-gradient 3
                             [[0.0 [:color/rgb 255 0 0]]
                              [1.0 [:color/rgb 0 0 255]]])))
          ops (gen/expand-generator g)]
      (is (vector? ops))
      (is (pos? (count ops))))))
