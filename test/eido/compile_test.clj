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

;; --- v0.2 style inheritance tests ---

(deftest compile-style-inherit-fill-test
  (testing "child inherits fill from parent group"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :style/fill {:color [:color/rgb 255 0 0]}
                   :group/children
                   [{:node/type :shape/rect
                     :rect/xy [0 0]
                     :rect/size [50 50]}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (:fill op))))))

(deftest compile-style-override-fill-test
  (testing "child overrides parent fill"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :style/fill {:color [:color/rgb 255 0 0]}
                   :group/children
                   [{:node/type :shape/rect
                     :rect/xy [0 0]
                     :rect/size [50 50]
                     :style/fill {:color [:color/rgb 0 0 255]}}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= {:r 0 :g 0 :b 255 :a 1.0} (:fill op))))))

(deftest compile-style-inherit-stroke-test
  (testing "child inherits stroke from parent group"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :style/stroke {:color [:color/rgb 0 255 0] :width 3}
                   :group/children
                   [{:node/type :shape/circle
                     :circle/center [50 50]
                     :circle/radius 20}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= {:r 0 :g 255 :b 0 :a 1.0} (:stroke-color op)))
      (is (= 3 (:stroke-width op))))))

(deftest compile-style-nested-inherit-test
  (testing "grandchild inherits fill through parent"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :style/fill {:color [:color/rgb 255 0 0]}
                   :group/children
                   [{:node/type :group
                     :group/children
                     [{:node/type :shape/rect
                       :rect/xy [0 0]
                       :rect/size [10 10]}]}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (:fill op))))))

;; --- v0.2 opacity inheritance tests ---

(deftest compile-opacity-multiplicative-test
  (testing "group opacity multiplies with child opacity"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :node/opacity 0.5
                   :group/children
                   [{:node/type :shape/rect
                     :rect/xy [0 0]
                     :rect/size [50 50]
                     :node/opacity 0.5}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= 0.25 (:opacity op))))))

(deftest compile-opacity-inherit-default-test
  (testing "child without opacity inherits group opacity"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :node/opacity 0.5
                   :group/children
                   [{:node/type :shape/rect
                     :rect/xy [0 0]
                     :rect/size [50 50]}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= 0.5 (:opacity op))))))

(deftest compile-opacity-three-levels-test
  (testing "opacity accumulates through three levels"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :node/opacity 0.5
                   :group/children
                   [{:node/type :group
                     :node/opacity 0.5
                     :group/children
                     [{:node/type :shape/rect
                       :rect/xy [0 0]
                       :rect/size [10 10]
                       :node/opacity 0.5}]}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= 0.125 (:opacity op))))))

;; --- v0.2 transform accumulation tests ---

(deftest compile-transform-leaf-test
  (testing "leaf shape with transform produces :transforms in IR"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [0 0]
                   :rect/size [50 50]
                   :node/transform [[:transform/translate 10 20]]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= [[:translate 10 20]] (:transforms op))))))

(deftest compile-transform-group-to-child-test
  (testing "group transform propagates to child"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :node/transform [[:transform/translate 100 200]]
                   :group/children
                   [{:node/type :shape/rect
                     :rect/xy [0 0]
                     :rect/size [50 50]}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= [[:translate 100 200]] (:transforms op))))))

(deftest compile-transform-concatenation-test
  (testing "group + child transforms concatenate in order"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :node/transform [[:transform/translate 10 20]]
                   :group/children
                   [{:node/type :shape/rect
                     :rect/xy [0 0]
                     :rect/size [50 50]
                     :node/transform [[:transform/rotate 1.57]]}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= [[:translate 10 20] [:rotate 1.57]] (:transforms op))))))

(deftest compile-transform-nested-groups-test
  (testing "transforms accumulate through nested groups"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :node/transform [[:transform/translate 10 20]]
                   :group/children
                   [{:node/type :group
                     :node/transform [[:transform/scale 2 2]]
                     :group/children
                     [{:node/type :shape/circle
                       :circle/center [0 0]
                       :circle/radius 5}]}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= [[:translate 10 20] [:scale 2 2]] (:transforms op))))))

(deftest compile-no-transforms-test
  (testing "no transforms produces empty vector"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [0 0]
                   :rect/size [50 50]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= [] (:transforms op))))))

;; --- v0.3 path tests ---

(deftest compile-path-triangle-test
  (testing "compiles a triangle path with flattened commands"
    (let [scene {:image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [50 150]]
                                   [:line-to [100 50]]
                                   [:line-to [150 150]]
                                   [:close]]
                   :style/fill {:color [:color/rgb 255 0 0]}}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= :path (:op op)))
      (is (= [[:move-to 50 150]
              [:line-to 100 50]
              [:line-to 150 150]
              [:close]]
             (:commands op)))
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (:fill op))))))

(deftest compile-path-curve-test
  (testing "compiles curve-to with flattened control points"
    (let [scene {:image/size [200 200]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [10 10]]
                                   [:curve-to [20 80] [80 80] [90 10]]
                                   [:close]]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= [[:move-to 10 10]
              [:curve-to 20 80 80 80 90 10]
              [:close]]
             (:commands op))))))

(deftest compile-path-style-test
  (testing "path compiles with full style and opacity"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [0 0]]
                                   [:line-to [100 100]]]
                   :style/fill {:color [:color/rgb 0 255 0]}
                   :style/stroke {:color [:color/rgb 255 0 0] :width 3}
                   :node/opacity 0.7}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= {:r 0 :g 255 :b 0 :a 1.0} (:fill op)))
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (:stroke-color op)))
      (is (= 3 (:stroke-width op)))
      (is (= 0.7 (:opacity op))))))

(deftest compile-path-empty-commands-test
  (testing "empty commands list compiles to empty IR commands"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands []}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= :path (:op op)))
      (is (= [] (:commands op))))))

;; --- flat fill color vector tests ---

;; --- ellipse tests ---

(deftest compile-ellipse-test
  (testing "compiles an ellipse node into IR op"
    (let [scene {:image/size [400 400]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/ellipse
                   :ellipse/center [200 200]
                   :ellipse/rx 80
                   :ellipse/ry 40
                   :style/fill {:color [:color/rgb 0 255 0]}}]}
          ir (compile/compile scene)
          op (first (:ir/ops ir))]
      (is (= :ellipse (:op op)))
      (is (= 200 (:cx op)))
      (is (= 200 (:cy op)))
      (is (= 80 (:rx op)))
      (is (= 40 (:ry op)))
      (is (= {:r 0 :g 255 :b 0 :a 1.0} (:fill op))))))

;; --- line tests ---

(deftest compile-line-test
  (testing "compiles a line node into IR op"
    (let [scene {:image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/line
                   :line/from [10 20]
                   :line/to [190 180]
                   :style/stroke {:color [:color/rgb 255 0 0] :width 2}}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= :line (:op op)))
      (is (= 10 (:x1 op)))
      (is (= 20 (:y1 op)))
      (is (= 190 (:x2 op)))
      (is (= 180 (:y2 op)))
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (:stroke-color op))))))

;; --- flat fill color vector tests ---

(deftest compile-flat-fill-test
  (testing "bare color vector as :style/fill"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [0 0]
                   :rect/size [50 50]
                   :style/fill [:color/rgb 255 0 0]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (:fill op)))))
  (testing "HSL color vector as :style/fill"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/circle
                   :circle/center [50 50]
                   :circle/radius 20
                   :style/fill [:color/hsl 0 1.0 0.5]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (:fill op)))))
  (testing "map-style fill still works"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [0 0]
                   :rect/size [50 50]
                   :style/fill {:color [:color/rgb 0 255 0]}}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= {:r 0 :g 255 :b 0 :a 1.0} (:fill op))))))

(deftest compile-flat-fill-inherit-test
  (testing "flat fill inherits from group"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :style/fill [:color/rgb 255 0 0]
                   :group/children
                   [{:node/type :shape/rect
                     :rect/xy [0 0]
                     :rect/size [50 50]}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (:fill op))))))

(deftest compile-path-inherits-style-test
  (testing "path inside group inherits fill from parent"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :style/fill {:color [:color/rgb 0 0 255]}
                   :node/opacity 0.5
                   :group/children
                   [{:node/type :shape/path
                     :path/commands [[:move-to [0 0]]
                                     [:line-to [50 50]]
                                     [:line-to [0 50]]
                                     [:close]]}]}]}
          op (first (:ir/ops (compile/compile scene)))]
      (is (= {:r 0 :g 0 :b 255 :a 1.0} (:fill op)))
      (is (= 0.5 (:opacity op))))))
