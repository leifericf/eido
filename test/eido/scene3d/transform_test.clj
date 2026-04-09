(ns eido.scene3d.transform-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.scene3d.mesh :as mesh]
    [eido.scene3d.transform :as xform]))

(def ^:private cube (mesh/cube-mesh))

(defn- approx=
  "True if two numbers are within epsilon."
  [a b]
  (< (abs (- (double a) (double b))) 0.001))

(defn- all-verts [m]
  (mapcat :face/vertices m))

(deftest translate-mesh-test
  (let [m (xform/translate-mesh cube [10 20 30])]
    (testing "face count preserved"
      (is (= 6 (count m))))
    (testing "vertices shifted"
      (let [xs (map first (all-verts m))]
        (is (approx= 10.0 (apply min xs)))
        (is (approx= 11.0 (apply max xs)))))))

(deftest rotate-mesh-test
  (testing "90 degree Y rotation"
    (let [m (xform/rotate-mesh cube :y (/ Math/PI 2))]
      (is (= 6 (count m)))
      (testing "normals still present"
        (is (every? :face/normal m))))))

(deftest scale-mesh-test
  (testing "uniform scale"
    (let [m (xform/scale-mesh cube 2.0)]
      (is (= 6 (count m)))
      (let [xs (map first (all-verts m))]
        (is (approx= 0.0 (apply min xs)))
        (is (approx= 2.0 (apply max xs))))))
  (testing "non-uniform scale"
    (let [m (xform/scale-mesh cube [1 2 3])]
      (let [ys (map second (all-verts m))
            zs (map #(nth % 2) (all-verts m))]
        (is (approx= 2.0 (apply max ys)))
        (is (approx= 3.0 (apply max zs)))))))

(deftest mirror-mesh-test
  (let [m (xform/mirror-mesh cube {:mirror/axis :x})]
    (testing "mirror produces faces"
      (is (pos? (count m))))
    (testing "all faces have normals"
      (is (every? :face/normal m))))
  (testing "mirror with merge doubles face count"
    (let [m (xform/mirror-mesh cube {:mirror/axis :x :mirror/merge true})]
      (is (= 12 (count m))))))

(deftest deform-mesh-test
  (testing "twist deformation"
    (let [m (xform/deform-mesh cube {:deform/type   :twist
                                     :deform/axis   :y
                                     :deform/amount 0.5})]
      (is (= 6 (count m)))
      (is (every? :face/normal m))))
  (testing "taper deformation"
    (let [m (xform/deform-mesh cube {:deform/type   :taper
                                     :deform/axis   :y
                                     :deform/amount 0.5})]
      (is (= 6 (count m)))
      (is (every? :face/normal m)))))
