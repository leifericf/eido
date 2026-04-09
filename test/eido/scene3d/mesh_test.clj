(ns eido.scene3d.mesh-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.scene3d.mesh :as mesh]))

(deftest cube-mesh-test
  (testing "default unit cube at origin"
    (let [m (mesh/cube-mesh)]
      (is (= 6 (count m)) "cube has 6 faces")
      (is (every? #(= 4 (count (:face/vertices %))) m)
          "all faces are quads")
      (is (every? :face/normal m)
          "all faces have normals")))
  (testing "positioned cube"
    (let [m (mesh/cube-mesh [1 2 3] 2)]
      (is (= 6 (count m)))
      (let [all-verts (mapcat :face/vertices m)
            xs (map first all-verts)]
        (is (= 1.0 (apply min xs)))
        (is (= 3.0 (apply max xs)))))))

(deftest prism-mesh-test
  (let [triangle [[0 0] [1 0] [0.5 1]]
        m (mesh/prism-mesh triangle 2.0)]
    (testing "triangle prism has 2 caps + 3 sides = 5 faces"
      (is (= 5 (count m))))
    (testing "all faces have normals"
      (is (every? :face/normal m)))))

(deftest cylinder-mesh-test
  (let [m (mesh/cylinder-mesh 1.0 2.0 8)]
    (testing "8-sided cylinder has 2 caps + 8 sides = 10 faces"
      (is (= 10 (count m))))
    (testing "all faces have normals"
      (is (every? :face/normal m)))))

(deftest sphere-mesh-test
  (let [m (mesh/sphere-mesh 1.0 8 4)]
    (testing "sphere produces faces"
      (is (pos? (count m))))
    (testing "all faces are triangles or quads"
      (is (every? #(<= 3 (count (:face/vertices %)) 4) m)))
    (testing "vertices are on the unit sphere"
      (doseq [face m
              [x y z] (:face/vertices face)]
        (let [dist (Math/sqrt (+ (* x x) (* y y) (* z z)))]
          (is (< (abs (- dist 1.0)) 0.001)
              (str "vertex " [x y z] " not on unit sphere")))))))

(deftest torus-mesh-test
  (let [m (mesh/torus-mesh 3.0 1.0 8 6)]
    (testing "torus face count = ring-segments * tube-segments"
      (is (= 48 (count m))))
    (testing "all faces are quads"
      (is (every? #(= 4 (count (:face/vertices %))) m)))))

(deftest extrude-mesh-test
  (let [square [[0 0] [1 0] [1 1] [0 1]]
        m (mesh/extrude-mesh square [0 2 0])]
    (testing "extruded square has 2 caps + 4 sides = 6 faces"
      (is (= 6 (count m))))
    (testing "all faces are quads"
      (is (every? #(= 4 (count (:face/vertices %))) m)))))

(deftest heightfield-mesh-normals-test
  (testing "flat heightfield normals point up (+Y)"
    (let [faces (mesh/heightfield-mesh
                  {:field {:field/type :field/constant :field/value 0.0}
                   :bounds [0 0 2 2]
                   :grid [3 3]
                   :height 1.0})]
      (is (pos? (count faces)))
      (is (every? (fn [face]
                    (let [[_ ny _] (:face/normal face)]
                      (pos? ny)))
                  faces)
          "all face normals should point up for a flat surface"))))

(deftest heightfield-mesh-single-row-test
  (testing "single row or column does not throw division by zero"
    (is (= [] (mesh/heightfield-mesh
                {:field {:field/type :field/constant :field/value 0.0}
                 :bounds [0 0 2 2]
                 :grid [1 3]
                 :height 1.0}))
        "single column produces no faces")
    (is (= [] (mesh/heightfield-mesh
                {:field {:field/type :field/constant :field/value 0.0}
                 :bounds [0 0 2 2]
                 :grid [3 1]
                 :height 1.0}))
        "single row produces no faces")))
