(ns eido.integration.pattern-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.engine.compile :as compile]))

(deftest pattern-fill-compiles-test
  (testing "pattern fill expands to buffer with tiled content"
    (let [scene {:image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [10.0 10.0]
                   :rect/size [180.0 180.0]
                   :style/fill {:fill/type :pattern
                                :pattern/size [20 20]
                                :pattern/nodes
                                [{:node/type :shape/circle
                                  :circle/center [10.0 10.0]
                                  :circle/radius 4.0
                                  :style/fill [:color/rgb 200 0 0]}]}}]}
          ir (compile/compile scene)]
      (is (= [200 200] (:ir/size ir)))
      ;; Should have ops (the exact structure depends on implementation)
      (is (pos? (count (:ir/ops ir)))))))
