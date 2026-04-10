(ns eido.texture-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.texture :as texture]))

(def test-shape
  {:node/type :shape/path
   :path/commands [[:move-to [0 0]] [:line-to [100 0]]
                   [:line-to [100 100]] [:line-to [0 100]] [:close]]
   :style/fill [:color/rgba 200 50 50 1.0]})

(deftest layered-test
  (testing "returns a :group node"
    (let [result (texture/layered test-shape
                   {:layers 10 :opacity 0.04
                    :deform-fn (fn [node _i _s] node) :seed 42})]
      (is (= :group (:node/type result)))))
  (testing "has n children"
    (let [result (texture/layered test-shape
                   {:layers 5 :opacity 0.05
                    :deform-fn (fn [node _i _s] node) :seed 42})]
      (is (= 5 (count (:group/children result))))))
  (testing "each child has specified opacity"
    (let [result (texture/layered test-shape
                   {:layers 3 :opacity 0.04
                    :deform-fn (fn [node _i _s] node) :seed 42})]
      (is (every? #(= 0.04 (:node/opacity %))
                  (:group/children result)))))
  (testing "deform-fn is applied per layer"
    (let [deform (fn [node i _s] (assoc node :layer-index i))
          result (texture/layered test-shape
                   {:layers 3 :opacity 0.1
                    :deform-fn deform :seed 42})
          indices (mapv :layer-index (:group/children result))]
      (is (= [0 1 2] indices)))))

(deftest watercolor-test
  (testing "returns a :group node"
    (let [result (texture/watercolor test-shape {:layers 5 :seed 42})]
      (is (= :group (:node/type result)))))
  (testing "has specified number of layers"
    (let [result (texture/watercolor test-shape {:layers 10 :seed 42})]
      (is (= 10 (count (:group/children result)))))))
