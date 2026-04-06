(ns eido.compile-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.compile :as compile]))

(deftest compile-rect-test
  (testing "compiles a rectangle node into IR op"
    (let [scene {:image/size [800 600]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [100 200]
                   :rect/size [300 150]
                   :style/fill {:color [:color/rgb 200 0 0]}
                   :style/stroke {:color [:color/rgb 0 0 0] :width 2}
                   :node/opacity 0.8}]}
          ir (compile/compile scene)
          op (first (:ir/ops ir))]
      (is (= [800 600] (:ir/size ir)))
      (is (= {:r 255 :g 255 :b 255 :a 1.0} (:ir/background ir)))
      (is (= :rect (:op op)))
      (is (= 100 (:x op)))
      (is (= 200 (:y op)))
      (is (= 300 (:w op)))
      (is (= 150 (:h op)))
      (is (= {:r 200 :g 0 :b 0 :a 1.0} (:fill op)))
      (is (= {:r 0 :g 0 :b 0 :a 1.0} (:stroke-color op)))
      (is (= 2 (:stroke-width op)))
      (is (= 0.8 (:opacity op))))))

(deftest compile-circle-test
  (testing "compiles a circle node into IR op"
    (let [scene {:image/size [400 400]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/circle
                   :circle/center [200 200]
                   :circle/radius 50
                   :style/fill {:color [:color/rgba 0 255 0 0.5]}}]}
          ir (compile/compile scene)
          op (first (:ir/ops ir))]
      (is (= :circle (:op op)))
      (is (= 200 (:cx op)))
      (is (= 200 (:cy op)))
      (is (= 50 (:r op)))
      (is (= {:r 0 :g 255 :b 0 :a 0.5} (:fill op))))))

(deftest compile-default-opacity-test
  (testing "defaults opacity to 1.0 when not specified"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [0 0]
                   :rect/size [50 50]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= 1.0 (:opacity op))))))

(deftest compile-nil-fill-stroke-test
  (testing "nil fill and stroke when not specified"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/circle
                   :circle/center [50 50]
                   :circle/radius 20}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (nil? (:fill op)))
      (is (nil? (:stroke-color op)))
      (is (nil? (:stroke-width op))))))

(deftest compile-multiple-nodes-test
  (testing "compiles multiple nodes preserving order"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [0 0]
                   :rect/size [50 50]
                   :style/fill {:color [:color/rgb 255 0 0]}}
                  {:node/type :shape/circle
                   :circle/center [50 50]
                   :circle/radius 20
                   :style/fill {:color [:color/rgb 0 0 255]}}]}
          ops (:ir/ops (compile/compile scene))]
      (is (= 2 (count ops)))
      (is (= :rect (:op (first ops))))
      (is (= :circle (:op (second ops)))))))

;; --- v0.2 group tests ---

(deftest compile-group-flattens-test
  (testing "group with two children flattens to two IR ops"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :group
                   :group/children
                   [{:node/type :shape/rect
                     :rect/xy [0 0]
                     :rect/size [50 50]
                     :style/fill {:color [:color/rgb 255 0 0]}}
                    {:node/type :shape/circle
                     :circle/center [50 50]
                     :circle/radius 20
                     :style/fill {:color [:color/rgb 0 0 255]}}]}]}
          ops (:ir/ops (compile/compile scene))]
      (is (= 2 (count ops)))
      (is (= :rect (:op (first ops))))
      (is (= :circle (:op (second ops)))))))

(deftest compile-nested-groups-test
  (testing "nested groups flatten correctly"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :group/children
                   [{:node/type :group
                     :group/children
                     [{:node/type :shape/rect
                       :rect/xy [0 0]
                       :rect/size [10 10]}]}
                    {:node/type :shape/circle
                     :circle/center [50 50]
                     :circle/radius 5}]}]}
          ops (:ir/ops (compile/compile scene))]
      (is (= 2 (count ops)))
      (is (= :rect (:op (first ops))))
      (is (= :circle (:op (second ops)))))))

(deftest compile-empty-group-test
  (testing "empty group produces no ops"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :group/children []}]}
          ops (:ir/ops (compile/compile scene))]
      (is (= 0 (count ops))))))
