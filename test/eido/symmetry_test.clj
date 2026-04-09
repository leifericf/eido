(ns eido.symmetry-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.engine.compile :as compile]))

(deftest radial-symmetry-test
  (testing "radial symmetry produces n copies"
    (let [scene {:image/size [400 400]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :symmetry
                   :symmetry/type :radial
                   :symmetry/n 6
                   :symmetry/center [200 200]
                   :group/children
                   [{:node/type :shape/circle
                     :circle/center [200.0 100.0]
                     :circle/radius 20.0
                     :style/fill [:color/rgb 255 0 0]}]}]}
          ir (compile/compile scene)]
      ;; 6 copies of the circle
      (is (= 6 (count (:ir/ops ir)))))))

(deftest bilateral-symmetry-test
  (testing "bilateral symmetry produces 2 copies"
    (let [scene {:image/size [400 400]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :symmetry
                   :symmetry/type :bilateral
                   :symmetry/center [200 200]
                   :group/children
                   [{:node/type :shape/circle
                     :circle/center [100.0 150.0]
                     :circle/radius 20.0
                     :style/fill [:color/rgb 255 0 0]}]}]}
          ir (compile/compile scene)]
      (is (= 2 (count (:ir/ops ir)))))))

(deftest grid-symmetry-test
  (testing "grid symmetry produces cols*rows copies"
    (let [scene {:image/size [400 400]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :symmetry
                   :symmetry/type :grid
                   :symmetry/cols 3
                   :symmetry/rows 2
                   :symmetry/spacing [100 100]
                   :group/children
                   [{:node/type :shape/circle
                     :circle/center [20.0 20.0]
                     :circle/radius 10.0
                     :style/fill [:color/rgb 0 0 255]}]}]}
          ir (compile/compile scene)]
      (is (= 6 (count (:ir/ops ir)))))))
