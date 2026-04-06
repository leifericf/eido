(ns eido.validate-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.validate :as validate]))

(def valid-scene
  {:image/size [800 600]
   :image/background [:color/rgb 255 255 255]
   :image/nodes
   [{:node/type :shape/circle
     :circle/center [400 300]
     :circle/radius 100
     :style/fill {:color [:color/rgb 200 0 0]}}]})

(deftest validate-valid-scene-test
  (testing "valid scene returns nil"
    (is (nil? (validate/validate valid-scene)))))

(deftest validate-missing-top-level-key-test
  (testing "missing :image/size produces error"
    (let [errors (validate/validate (dissoc valid-scene :image/size))]
      (is (vector? errors))
      (is (pos? (count errors)))
      (is (re-find #"image/size" (:message (first errors)))))))

(deftest validate-bad-rgb-test
  (testing "out-of-range RGB value produces error"
    (let [errors (validate/validate
                   (assoc valid-scene :image/background [:color/rgb 300 0 0]))]
      (is (some? errors))
      (is (pos? (count errors))))))

(deftest validate-missing-rect-key-test
  (testing "rect missing :rect/xy produces error with path"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/size [50 50]}]}
          errors (validate/validate scene)]
      (is (some? errors))
      (is (some #(re-find #"rect/xy" (:message %)) errors)))))

(deftest validate-nested-group-error-test
  (testing "invalid node inside group produces deep path"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :group/children
                   [{:node/type :shape/circle
                     :circle/center [50 50]
                     :circle/radius -5}]}]}
          errors (validate/validate scene)]
      (is (some? errors))
      (is (some #(seq (:path %)) errors)))))

(deftest validate-bad-opacity-test
  (testing "opacity > 1 produces error"
    (let [scene (assoc-in valid-scene [:image/nodes 0 :node/opacity] 2.0)
          errors (validate/validate scene)]
      (is (some? errors)))))

(deftest validate-unknown-node-type-test
  (testing "unknown node type produces error"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes [{:node/type :shape/polygon}]}
          errors (validate/validate scene)]
      (is (some? errors)))))

(deftest validate-multiple-errors-test
  (testing "multiple problems produce multiple errors"
    (let [scene {:image/background [:color/rgb 0 0 0]
                 :image/nodes "not a vector"}
          errors (validate/validate scene)]
      (is (some? errors))
      (is (> (count errors) 1)))))

(deftest validate-empty-scene-test
  (testing "empty map produces errors"
    (let [errors (validate/validate {})]
      (is (some? errors))
      (is (>= (count errors) 3)))))
