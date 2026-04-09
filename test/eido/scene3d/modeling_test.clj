(ns eido.scene3d.modeling-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.scene3d.mesh :as mesh]
    [eido.scene3d.modeling :as modeling]))

(def ^:private cube (mesh/cube-mesh))

(deftest extrude-faces-test
  (let [m (modeling/extrude-faces cube {:select/type :all
                                        :extrude/amount 0.5})]
    (testing "extruding all 6 faces of a cube"
      ;; Each face becomes: 1 cap + 4 side walls = 5 faces
      ;; Original face is replaced, so 6 * 5 = 30
      (is (= 30 (count m))))
    (testing "all faces have normals"
      (is (every? :face/normal m)))))

(deftest inset-faces-test
  (let [m (modeling/inset-faces cube {:select/type :all
                                      :inset/amount 0.2})]
    (testing "insetting all 6 faces of a cube"
      ;; Each quad face → 1 inner face + 4 border quads = 5 faces
      ;; 6 * 5 = 30
      (is (= 30 (count m))))
    (testing "all faces have normals"
      (is (every? :face/normal m)))))

(deftest bevel-faces-test
  (let [m (modeling/bevel-faces cube {:select/type :all
                                      :bevel/inset 0.1
                                      :bevel/depth 0.05})]
    (testing "bevel produces faces"
      (is (pos? (count m))))
    (testing "more faces than input"
      (is (> (count m) 6)))
    (testing "all faces have normals"
      (is (every? :face/normal m)))))

(deftest extrude-with-scale-test
  (let [m (modeling/extrude-faces cube {:select/type :all
                                        :extrude/amount 0.5
                                        :extrude/scale 0.5})]
    (testing "scaled extrusion still produces faces"
      (is (= 30 (count m))))))
