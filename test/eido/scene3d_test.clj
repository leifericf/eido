(ns eido.scene3d-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.math3d :as m]
    [eido.scene3d :as s3d]))

(def ^:private eps 1e-9)

(defn- approx=
  [a b]
  (and (= (count a) (count b))
       (every? #(< (abs %) eps) (map - a b))))

;; --- projection constructors ---

(deftest isometric-test
  (let [proj (s3d/isometric {:scale 50 :origin [400 300]})]
    (is (= :isometric (:projection/type proj)))
    (is (= 50.0 (:projection/scale proj)))
    (is (= [400 300] (:projection/origin proj)))))

(deftest isometric-defaults-test
  (let [proj (s3d/isometric {})]
    (is (= 1.0 (:projection/scale proj)))
    (is (= [0 0] (:projection/origin proj)))))

(deftest orthographic-test
  (let [proj (s3d/orthographic {:scale 50 :origin [400 300]
                                :yaw 0.5 :pitch -0.3})]
    (is (= :orthographic (:projection/type proj)))
    (is (= 0.5 (:projection/yaw proj)))
    (is (= -0.3 (:projection/pitch proj)))
    (is (= 0.0 (:projection/roll proj)) "roll defaults to 0")))

(deftest orthographic-roll-test
  (let [proj (s3d/orthographic {:scale 50 :origin [200 200]
                                :yaw 0.0 :pitch 0.0 :roll 0.75})]
    (is (= 0.75 (:projection/roll proj)))))

(deftest perspective-test
  (let [proj (s3d/perspective {:scale 50 :origin [400 300]
                               :distance 5 :yaw 0.5 :pitch -0.3})]
    (is (= :perspective (:projection/type proj)))
    (is (= 5.0 (:projection/distance proj)))
    (is (= 0.0 (:projection/roll proj)) "roll defaults to 0")))

(deftest perspective-roll-test
  (let [proj (s3d/perspective {:scale 50 :origin [200 200]
                               :distance 5 :roll 1.2})]
    (is (= 1.2 (:projection/roll proj)))))

;; --- camera utilities ---

(deftest look-at-camera-at-z-test
  (testing "camera at +Z looking at origin gives yaw=0 pitch=0 roll=0"
    (let [proj (s3d/look-at (s3d/orthographic {:scale 90 :origin [200 200]})
                            [0 0 5] [0 0 0])]
      (is (< (abs (:projection/yaw proj)) eps))
      (is (< (abs (:projection/pitch proj)) eps))
      (is (< (abs (:projection/roll proj)) eps)))))

(deftest look-at-camera-at-x-test
  (testing "camera at +X looking at origin gives yaw=pi/2"
    (let [proj (s3d/look-at (s3d/orthographic {:scale 90 :origin [200 200]})
                            [5 0 0] [0 0 0])]
      (is (< (abs (- (:projection/yaw proj) (/ Math/PI 2))) eps))
      (is (< (abs (:projection/pitch proj)) eps)))))

(deftest look-at-camera-above-test
  (testing "camera above looking down gives pitch near -pi/2"
    (let [proj (s3d/look-at (s3d/orthographic {:scale 90 :origin [200 200]})
                            [0 5 0] [0 0 0])]
      (is (< (abs (- (:projection/pitch proj) (- (/ Math/PI 2)))) eps)))))

(deftest look-at-preserves-base-test
  (testing "look-at preserves type, scale, origin, distance"
    (let [base (s3d/perspective {:scale 80 :origin [300 250] :distance 8})
          proj (s3d/look-at base [0 0 5] [0 0 0])]
      (is (= :perspective (:projection/type proj)))
      (is (= 80.0 (:projection/scale proj)))
      (is (= [300 250] (:projection/origin proj)))
      (is (= 8.0 (:projection/distance proj))))))

(deftest look-at-roll-from-up-vector-test
  (testing "tilted up vector produces non-zero roll"
    (let [proj (s3d/look-at (s3d/orthographic {:scale 90 :origin [200 200]})
                            [0 0 5] [0 0 0] [1 0 0])]
      (is (< (abs (- (:projection/roll proj) (/ Math/PI 2))) eps)))))

(deftest orbit-identity-test
  (testing "orbit yaw=0 pitch=0 matches look-at from +Z"
    (let [proj (s3d/orbit (s3d/orthographic {:scale 90 :origin [200 200]})
                          [0 0 0] 5 0.0 0.0)]
      (is (< (abs (:projection/yaw proj)) eps))
      (is (< (abs (:projection/pitch proj)) eps)))))

(deftest orbit-pitch-convention-test
  (testing "orbit pitch matches camera pitch convention"
    (let [proj (s3d/orbit (s3d/orthographic {:scale 90 :origin [200 200]})
                          [0 0 0] 5 0.0 -0.45)]
      (is (< (abs (- (:projection/pitch proj) -0.45)) eps)))))

(deftest orbit-yaw-test
  (testing "orbit yaw=pi/2 places camera at +X"
    (let [proj (s3d/orbit (s3d/orthographic {:scale 90 :origin [200 200]})
                          [0 0 0] 5 (/ Math/PI 2) 0.0)]
      (is (< (abs (- (:projection/yaw proj) (/ Math/PI 2))) eps)))))

(deftest fov->distance-test
  (testing "90-degree FOV with half-width=1 gives distance=1"
    (is (< (abs (- (s3d/fov->distance (/ Math/PI 2) 1.0) 1.0)) eps)))
  (testing "narrower FOV gives larger distance"
    (is (> (s3d/fov->distance (/ Math/PI 6) 1.0)
           (s3d/fov->distance (/ Math/PI 3) 1.0)))))

;; --- mesh constructors ---

(deftest cube-mesh-test
  (let [mesh (s3d/cube-mesh)]
    (is (= 6 (count mesh)))
    (testing "each face has 4 vertices"
      (doseq [face mesh]
        (is (= 4 (count (:face/vertices face))))))
    (testing "each face has a normal"
      (doseq [face mesh]
        (is (some? (:face/normal face)))))))

(deftest cube-mesh-positioned-test
  (let [mesh (s3d/cube-mesh [1 2 3] 2)]
    (is (= 6 (count mesh)))
    (testing "vertices are offset and scaled"
      (let [all-verts (mapcat :face/vertices mesh)
            xs (map first all-verts)
            ys (map second all-verts)
            zs (map #(nth % 2) all-verts)]
        (is (== 1.0 (apply min xs)))
        (is (== 3.0 (apply max xs)))
        (is (== 2.0 (apply min ys)))
        (is (== 4.0 (apply max ys)))
        (is (== 3.0 (apply min zs)))
        (is (== 5.0 (apply max zs)))))))

(deftest prism-mesh-test
  (let [mesh (s3d/prism-mesh [[0 0] [1 0] [0.5 1]] 2)]
    (testing "triangle base: 2 caps + 3 sides = 5 faces"
      (is (= 5 (count mesh))))))

(deftest cylinder-mesh-test
  (let [mesh (s3d/cylinder-mesh 1 2 8)]
    (testing "8 sides + 2 caps = 10 faces"
      (is (= 10 (count mesh))))))

(deftest sphere-mesh-test
  (let [mesh (s3d/sphere-mesh 1 8 4)]
    (testing "produces faces"
      (is (pos? (count mesh))))
    (testing "all vertices are approximately radius 1 from origin"
      (doseq [face mesh
              v (:face/vertices face)]
        (is (< (abs (- (m/magnitude v) 1.0)) 0.15))))))

(deftest extrude-mesh-test
  (let [mesh (s3d/extrude-mesh [[0 0] [1 0] [1 1] [0 1]] [0 2 0])]
    (testing "square extruded: 2 caps + 4 sides = 6 faces"
      (is (= 6 (count mesh))))))

(deftest torus-mesh-test
  (let [mesh (s3d/torus-mesh 1.8 0.7 24 12)]
    (testing "24 ring * 12 tube = 288 quads"
      (is (= 288 (count mesh))))
    (testing "each face has 4 vertices"
      (doseq [face mesh]
        (is (= 4 (count (:face/vertices face))))))
    (testing "all normals are non-zero"
      (doseq [face mesh]
        (is (> (m/magnitude (:face/normal face)) 0))))))

(deftest torus-mesh-small-test
  (let [mesh (s3d/torus-mesh 2.0 0.5 6 4)]
    (is (= 24 (count mesh)))))

(deftest cone-mesh-test
  (let [mesh (s3d/cone-mesh 1.0 2.0 16)]
    (testing "1 base + 16 side triangles = 17 faces"
      (is (= 17 (count mesh))))
    (testing "side faces are triangles"
      (doseq [face (rest mesh)]
        (is (= 3 (count (:face/vertices face))))))
    (testing "apex is at [0 2 0]"
      (let [apex-verts (mapcat :face/vertices (rest mesh))]
        (is (some #(approx= [0.0 2.0 0.0] %) apex-verts))))))

(deftest cone-mesh-normals-test
  (let [mesh (s3d/cone-mesh 1.0 2.0 8)]
    (testing "all normals are non-zero"
      (doseq [face mesh]
        (is (> (m/magnitude (:face/normal face)) 0))))))

;; --- mesh transforms ---

(deftest translate-mesh-test
  (let [mesh [(s3d/make-face [[0 0 0] [1 0 0] [1 1 0] [0 1 0]])]
        moved (s3d/translate-mesh mesh [10 20 30])]
    (is (approx= [10.0 20.0 30.0]
          (first (:face/vertices (first moved)))))))

(deftest rotate-mesh-test
  (let [mesh [(s3d/make-face [[1 0 0] [0 1 0] [0 0 1]])]
        rotated (s3d/rotate-mesh mesh :z (/ Math/PI 2))]
    (is (approx= [0.0 1.0 0.0]
          (first (:face/vertices (first rotated)))))))

(deftest scale-mesh-uniform-test
  (let [mesh [(s3d/make-face [[1 2 3] [4 5 6] [7 8 9]])]
        scaled (s3d/scale-mesh mesh 2)]
    (is (approx= [2.0 4.0 6.0]
          (first (:face/vertices (first scaled)))))))

(deftest scale-mesh-per-axis-test
  (let [mesh [(s3d/make-face [[1 2 3] [4 5 6] [7 8 9]])]
        scaled (s3d/scale-mesh mesh [1 2 3])]
    (is (approx= [1.0 4.0 9.0]
          (first (:face/vertices (first scaled)))))))

;; --- mesh utilities ---

(deftest merge-meshes-bare-test
  (let [m (s3d/merge-meshes (s3d/cube-mesh) (s3d/cone-mesh 1 2 8))]
    (is (= (+ 6 9) (count m)))))

(deftest merge-meshes-styled-test
  (let [style-a {:style/fill [:color/rgb 255 0 0]}
        style-b {:style/fill [:color/rgb 0 255 0]}
        m (s3d/merge-meshes
            [(s3d/cube-mesh) style-a]
            [(s3d/cone-mesh 1 2 8) style-b])]
    (testing "correct total face count"
      (is (= 15 (count m))))
    (testing "first mesh faces have style-a"
      (is (= style-a (:face/style (first m)))))
    (testing "second mesh faces have style-b"
      (is (= style-b (:face/style (nth m 6)))))))

(deftest merge-meshes-mixed-test
  (testing "bare and styled meshes can be mixed"
    (let [m (s3d/merge-meshes
              (s3d/cube-mesh)
              [(s3d/cone-mesh 1 2 8) {:style/fill [:color/rgb 0 0 255]}])]
      (is (= 15 (count m)))
      (is (nil? (:face/style (first m))))
      (is (some? (:face/style (nth m 6)))))))

(deftest mesh-bounds-test
  (let [b (s3d/mesh-bounds (s3d/cube-mesh [1 2 3] 2))]
    (is (approx= [1.0 2.0 3.0] (:min b)))
    (is (approx= [3.0 4.0 5.0] (:max b)))))

(deftest mesh-center-test
  (is (approx= [1.0 1.0 1.0] (s3d/mesh-center (s3d/cube-mesh [0 0 0] 2))))
  (is (approx= [2.0 3.0 4.0] (s3d/mesh-center (s3d/cube-mesh [1 2 3] 2)))))

;; --- render-mesh ---

(deftest render-mesh-produces-group-test
  (let [proj (s3d/isometric {:scale 50 :origin [200 200]})
        mesh (s3d/cube-mesh)
        result (s3d/render-mesh proj mesh {})]
    (is (= :group (:node/type result)))
    (is (vector? (:group/children result)))
    (testing "back-face culling removes roughly half"
      (is (<= (count (:group/children result)) 6))
      (is (pos? (count (:group/children result)))))
    (testing "each child is a path"
      (doseq [child (:group/children result)]
        (is (= :shape/path (:node/type child)))))))

(deftest render-mesh-applies-style-test
  (let [proj (s3d/isometric {:scale 50 :origin [200 200]})
        mesh (s3d/cube-mesh)
        result (s3d/render-mesh proj mesh
                 {:style {:style/fill [:color/rgb 100 150 200]
                          :style/stroke {:color [:color/rgb 0 0 0]
                                         :width 1}}})]
    (doseq [child (:group/children result)]
      (is (some? (:style/fill child)))
      (is (some? (:style/stroke child))))))

(deftest render-mesh-with-lighting-test
  (let [proj (s3d/isometric {:scale 50 :origin [200 200]})
        mesh (s3d/cube-mesh)
        result (s3d/render-mesh proj mesh
                 {:style {:style/fill [:color/rgb 200 200 200]}
                  :light {:light/direction [1 2 1]
                          :light/ambient 0.2
                          :light/intensity 0.8}})]
    (testing "different faces get different brightness"
      (let [fills (map :style/fill (:group/children result))
            rs (map #(nth % 1) fills)]
        (is (< 1 (count (distinct rs))))))))

(deftest render-mesh-no-cull-test
  (let [proj (s3d/isometric {:scale 50 :origin [200 200]})
        mesh (s3d/cube-mesh)
        result (s3d/render-mesh proj mesh {:cull-back false})]
    (testing "all 6 faces rendered without culling"
      (is (= 6 (count (:group/children result)))))))

(deftest render-mesh-wireframe-test
  (let [proj (s3d/isometric {:scale 50 :origin [200 200]})
        mesh (s3d/cube-mesh)
        result (s3d/render-mesh proj mesh
                 {:wireframe true
                  :style {:style/stroke {:color [:color/rgb 0 0 0] :width 1}}})]
    (is (= :group (:node/type result)))
    (testing "cube has 12 unique edges"
      (is (= 12 (count (:group/children result)))))
    (testing "each child is a :shape/line"
      (doseq [child (:group/children result)]
        (is (= :shape/line (:node/type child)))
        (is (some? (:line/from child)))
        (is (some? (:line/to child)))))))

(deftest render-mesh-wireframe-stroke-test
  (let [proj (s3d/isometric {:scale 50 :origin [200 200]})
        mesh (s3d/cube-mesh)
        stroke {:color [:color/rgb 255 0 0] :width 2}
        result (s3d/render-mesh proj mesh
                 {:wireframe true :style {:style/stroke stroke}})]
    (testing "stroke style is applied to all edges"
      (doseq [child (:group/children result)]
        (is (= stroke (:style/stroke child)))))))

;; --- convenience functions ---

(deftest cube-convenience-test
  (let [proj (s3d/isometric {:scale 50 :origin [200 200]})
        result (s3d/cube proj [0 0 0] 1 {})]
    (is (= :group (:node/type result)))
    (is (pos? (count (:group/children result))))))

(deftest sphere-convenience-test
  (let [proj (s3d/isometric {:scale 50 :origin [200 200]})
        result (s3d/sphere proj [0 0 0] 1 {})]
    (is (= :group (:node/type result)))
    (is (pos? (count (:group/children result))))))
