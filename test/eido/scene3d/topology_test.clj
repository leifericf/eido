(ns eido.scene3d.topology-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.scene3d.mesh :as mesh]
    [eido.scene3d.topology :as topo]))

(def ^:private cube (mesh/cube-mesh))

(deftest build-face-adjacency-test
  (let [{:keys [edge-faces vert-faces face-data]} (topo/build-face-adjacency cube)]
    (testing "cube has 12 edges"
      (is (= 12 (count edge-faces))))
    (testing "each edge is shared by exactly 2 faces"
      (is (every? #(= 2 (count %)) (vals edge-faces))))
    (testing "cube has 8 unique vertices"
      (is (= 8 (count vert-faces))))
    (testing "each vertex touches 3 faces"
      (is (every? #(= 3 (count %)) (vals vert-faces))))
    (testing "face-data has 6 entries"
      (is (= 6 (count face-data))))))

(deftest compute-vertex-normals-test
  (let [{:keys [face-data vert-faces]} (topo/build-face-adjacency cube)
        vnormals (topo/compute-vertex-normals face-data vert-faces)]
    (testing "8 vertex normals computed"
      (is (= 8 (count vnormals))))
    (testing "normals are roughly unit-length"
      (doseq [[_v [nx ny nz]] vnormals]
        (let [len (Math/sqrt (+ (* nx nx) (* ny ny) (* nz nz)))]
          (is (< (abs (- len 1.0)) 0.01)))))))

(deftest subdivide-test
  (testing "one iteration of Catmull-Clark on a cube"
    (let [m (topo/subdivide cube {:iterations 1})]
      (testing "each quad becomes 4 quads (6*4 = 24)"
        (is (= 24 (count m))))
      (testing "all faces are quads"
        (is (every? #(= 4 (count (:face/vertices %))) m)))))
  (testing "two iterations"
    (let [m (topo/subdivide cube {:iterations 2})]
      (testing "24*4 = 96 faces after 2 iterations"
        (is (= 96 (count m))))))
  (testing "zero iterations returns input unchanged"
    (is (= cube (topo/subdivide cube {:iterations 0})))))

(deftest subdivide-edge-point-test
  (testing "Catmull-Clark edge point uses (v0+v1+F1+F2)/4"
    ;; On a unit cube [0,0,0]-[1,1,1], the edge from [1,0,0] to [1,1,0]
    ;; is shared by front face (centroid [0.5,0.5,0]) and right face
    ;; (centroid [1,0.5,0.5]). The correct edge point is:
    ;; ([1,0,0] + [1,1,0] + [0.5,0.5,0] + [1,0.5,0.5]) / 4 = [0.875, 0.5, 0.125]
    (let [m         (topo/subdivide cube {:iterations 1})
          all-verts (into #{} (mapcat :face/vertices) m)
          target    [0.875 0.5 0.125]
          found     (some (fn [v]
                            (< (reduce + (map #(Math/abs (- %1 %2)) v target))
                               1e-9))
                          all-verts)]
      (is found "edge point [0.875 0.5 0.125] must appear in subdivided mesh"))))

(deftest auto-smooth-edges-test
  (let [hard (topo/auto-smooth-edges cube {:angle (/ Math/PI 4)})]
    (testing "cube edges are all 90 degrees, sharper than 45"
      (is (= 12 (count hard))))))
