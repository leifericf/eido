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

(deftest auto-smooth-edges-test
  (let [hard (topo/auto-smooth-edges cube {:angle (/ Math/PI 4)})]
    (testing "cube edges are all 90 degrees, sharper than 45"
      (is (= 12 (count hard))))))
