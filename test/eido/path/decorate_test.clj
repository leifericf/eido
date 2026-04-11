(ns eido.path.decorate-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.path.decorate :as decorator]))

(deftest decorate-path-test
  (testing "places shapes along a straight path"
    (let [path-cmds [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]]]
          shape {:node/type :shape/circle
                 :circle/center [0.0 0.0]
                 :circle/radius 3.0
                 :style/fill [:color/rgb 255 0 0]}
          result (decorator/decorate-path path-cmds shape {:spacing 20 :rotate? true})]
      (is (vector? result))
      (is (pos? (count result)))
      ;; Each result should be a group with transforms
      (is (every? #(= :group (:node/type %)) result))))
  (testing "rotate flag adds rotation transforms"
    (let [path-cmds [[:move-to [0.0 0.0]]
                     [:curve-to [30.0 -50.0] [70.0 50.0] [100.0 0.0]]]
          shape {:node/type :shape/circle
                 :circle/center [0.0 0.0]
                 :circle/radius 3.0
                 :style/fill [:color/rgb 255 0 0]}
          result (decorator/decorate-path path-cmds shape {:spacing 20 :rotate? true})]
      ;; Some nodes should have rotation
      (is (some (fn [n]
                  (some (fn [[t & _]] (= t :transform/rotate))
                        (:node/transform n)))
                result)))))

(deftest decorate-spacing-test
  (testing "smaller spacing produces more shapes"
    (let [path-cmds [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]]]
          shape {:node/type :shape/circle
                 :circle/center [0.0 0.0]
                 :circle/radius 2.0
                 :style/fill [:color/rgb 0 0 0]}
          few  (decorator/decorate-path path-cmds shape {:spacing 50})
          many (decorator/decorate-path path-cmds shape {:spacing 10})]
      (is (> (count many) (count few))))))

(deftest decorate-path-edge-cases-test
  (testing "zero or missing spacing returns empty"
    (let [path-cmds [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]]]
          shape {:node/type :shape/circle :circle/center [0.0 0.0]
                 :circle/radius 2.0 :style/fill [:color/rgb 0 0 0]}]
      (is (= [] (decorator/decorate-path path-cmds shape {:spacing 0})))
      (is (= [] (decorator/decorate-path path-cmds shape {}))))))
