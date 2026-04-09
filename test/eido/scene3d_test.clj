(ns eido.scene3d-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.ir.field :as field]
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

;; --- mesh deformations ---

;; --- platonic solids ---

(deftest tetrahedron-mesh-test
  (let [mesh (s3d/tetrahedron-mesh 1.0)]
    (is (= 4 (count mesh)))
    (testing "each face is a triangle"
      (doseq [face mesh]
        (is (= 3 (count (:face/vertices face))))))
    (testing "vertices are approximately radius 1 from origin"
      (doseq [face mesh
              v (:face/vertices face)]
        (is (< (abs (- (m/magnitude v) 1.0)) 0.01))))))

(deftest octahedron-mesh-test
  (let [mesh (s3d/octahedron-mesh 1.0)]
    (is (= 8 (count mesh)))
    (doseq [face mesh]
      (is (= 3 (count (:face/vertices face)))))))

(deftest dodecahedron-mesh-test
  (let [mesh (s3d/dodecahedron-mesh 1.0)]
    (is (= 12 (count mesh)))
    (testing "each face is a pentagon"
      (doseq [face mesh]
        (is (= 5 (count (:face/vertices face))))))))

(deftest icosahedron-mesh-test
  (let [mesh (s3d/icosahedron-mesh 1.0)]
    (is (= 20 (count mesh)))
    (testing "each face is a triangle"
      (doseq [face mesh]
        (is (= 3 (count (:face/vertices face))))))
    (testing "vertices are approximately radius 1 from origin"
      (doseq [face mesh
              v (:face/vertices face)]
        (is (< (abs (- (m/magnitude v) 1.0)) 0.01))))))

(deftest platonic-scaling-test
  (testing "radius parameter scales the solid"
    (let [r2 (s3d/icosahedron-mesh 2.0)]
      (doseq [face r2
              v (:face/vertices face)]
        (is (< (abs (- (m/magnitude v) 2.0)) 0.02))))))

;; --- heightfield ---

(deftest heightfield-mesh-test
  (let [mesh (s3d/heightfield-mesh
               {:field {:field/type :field/constant :field/value 0.5}
                :bounds [-1 -1 2 2]
                :grid [4 4]
                :height 2.0})]
    (testing "4x4 grid produces 9 quads (3x3 cells)"
      (is (= 9 (count mesh))))
    (testing "each face is a quad"
      (doseq [face mesh]
        (is (= 4 (count (:face/vertices face))))))
    (testing "constant field produces flat surface at height*value"
      (let [ys (map second (mapcat :face/vertices mesh))]
        (is (every? #(< (abs (- % 1.0)) 0.01) ys))))))

(deftest heightfield-mesh-noise-test
  (let [mesh (s3d/heightfield-mesh
               {:field (field/noise-field :scale 1.0 :variant :fbm :seed 42)
                :bounds [0 0 4 4]
                :grid [8 8]
                :height 1.0})]
    (testing "8x8 grid produces 49 quads"
      (is (= 49 (count mesh))))
    (testing "heights vary with noise"
      (let [ys (map second (mapcat :face/vertices mesh))]
        (is (> (count (distinct (map #(Math/round (* 100.0 (double %))) ys))) 1))))))

;; --- surface of revolution ---

(deftest revolve-mesh-test
  (let [mesh (s3d/revolve-mesh
               {:profile [[0 0] [1.0 0.5] [0.5 1.0] [0 1.5]]
                :segments 8})]
    (testing "3 profile segments * 8 rotation segments = 24 quads"
      (is (= 24 (count mesh))))
    (testing "each face is a quad"
      (doseq [face mesh]
        (is (= 4 (count (:face/vertices face))))))
    (testing "top/bottom vertices on Y axis"
      (let [all-verts (mapcat :face/vertices mesh)
            bottom-verts (filter #(< (abs (second %)) 0.01) all-verts)]
        (is (seq bottom-verts))))))

(deftest revolve-mesh-closed-test
  (testing "first and last ring connect seamlessly"
    (let [mesh (s3d/revolve-mesh
                 {:profile [[0.5 0] [1.0 0.5] [0.5 1.0]]
                  :segments 6})]
      (is (= 12 (count mesh))))))

;; --- subdivision ---

(deftest subdivide-cube-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        sub1 (s3d/subdivide mesh 1)]
    (testing "one iteration on a cube: 6 faces * 4 = 24 quads"
      (is (= 24 (count sub1))))
    (testing "all subdivided faces are quads"
      (doseq [face sub1]
        (is (= 4 (count (:face/vertices face))))))
    (testing "all faces have normals"
      (doseq [face sub1]
        (is (some? (:face/normal face)))))))

(deftest subdivide-two-iterations-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        sub2 (s3d/subdivide mesh 2)]
    (testing "two iterations: 6 * 4 * 4 = 96 quads"
      (is (= 96 (count sub2))))))

(deftest subdivide-preserves-style-test
  (let [mesh (mapv #(assoc % :face/style {:style/fill [:color/rgb 200 100 50]})
               (s3d/cube-mesh [0 0 0] 2))
        sub (s3d/subdivide mesh 1)]
    (testing "face style propagates to subdivided faces"
      (doseq [face sub]
        (is (= {:style/fill [:color/rgb 200 100 50]} (:face/style face)))))))

(deftest subdivide-icosahedron-test
  (let [mesh (s3d/icosahedron-mesh 1.0)
        sub  (s3d/subdivide mesh 1)]
    (testing "icosahedron (20 tris) → 60 quads after one iteration"
      (is (= 60 (count sub))))
    (testing "all subdivided faces are quads"
      (doseq [face sub]
        (is (= 4 (count (:face/vertices face))))))))

(deftest subdivide-zero-iterations-test
  (let [mesh (s3d/cube-mesh)]
    (testing "0 iterations returns mesh unchanged"
      (is (= mesh (s3d/subdivide mesh 0))))))

(deftest subdivide-composes-with-deform-test
  (testing "deform then subdivide works"
    (let [result (-> (s3d/cube-mesh [0 0 0] 2)
                     (s3d/deform-mesh {:deform/type :twist
                                       :deform/axis :y
                                       :deform/amount 0.5})
                     (s3d/subdivide 1))]
      (is (= 24 (count result))))))

;; --- per-face color ---

(deftest color-mesh-field-test
  (let [mesh (s3d/sphere-mesh 1.5 8 4)
        colored (s3d/color-mesh mesh
                  {:color/type :field
                   :color/field (field/noise-field :scale 1.0 :variant :fbm :seed 42)
                   :color/palette [[:color/rgb 0 0 0]
                                   [:color/rgb 255 255 255]]})]
    (testing "same face count"
      (is (= (count mesh) (count colored))))
    (testing "every face has a fill style"
      (doseq [face colored]
        (is (some? (get-in face [:face/style :style/fill])))))
    (testing "colors vary across faces"
      (let [fills (map #(get-in % [:face/style :style/fill]) colored)]
        (is (> (count (distinct fills)) 1))))))

(deftest color-mesh-axis-gradient-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        colored (s3d/color-mesh mesh
                  {:color/type :axis-gradient
                   :color/axis :y
                   :color/palette [[:color/rgb 0 0 255]
                                   [:color/rgb 255 0 0]]})]
    (testing "bottom faces are bluer, top faces are redder"
      (let [face-data (map (fn [f]
                             {:y (second (m/face-centroid (:face/vertices f)))
                              :r (nth (get-in f [:face/style :style/fill]) 1)})
                           colored)
            sorted (sort-by :y face-data)]
        (is (< (:r (first sorted)) (:r (last sorted))))))))

(deftest color-mesh-normal-map-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        colored (s3d/color-mesh mesh
                  {:color/type :normal-map
                   :color/palette [[:color/rgb 255 0 0]
                                   [:color/rgb 0 255 0]
                                   [:color/rgb 0 0 255]]})]
    (testing "faces get different colors based on normal direction"
      (let [fills (map #(get-in % [:face/style :style/fill]) colored)]
        (is (> (count (distinct fills)) 1))))))

(deftest color-mesh-preserves-other-style-test
  (let [mesh (mapv #(assoc % :face/style {:style/stroke {:color [:color/rgb 0 0 0] :width 1}})
               (s3d/cube-mesh [0 0 0] 2))
        colored (s3d/color-mesh mesh
                  {:color/type :field
                   :color/field (field/constant-field 0.5)
                   :color/palette [[:color/rgb 100 100 100]
                                   [:color/rgb 200 200 200]]})]
    (testing "existing stroke style is preserved"
      (doseq [face colored]
        (is (some? (get-in face [:face/style :style/stroke])))))))

;; --- face selection + polygonal modeling ---

(deftest extrude-faces-all-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        extruded (s3d/extrude-faces mesh {:select/by :all
                                          :extrude/amount 0.5})]
    (testing "each face becomes cap + side walls"
      ;; 6 original quad faces → 6 caps + 6*4 side walls = 30 faces
      (is (= 30 (count extruded))))
    (testing "all faces have normals"
      (doseq [face extruded]
        (is (some? (:face/normal face)))))))

(deftest extrude-faces-by-normal-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        ;; Select only upward-facing faces
        extruded (s3d/extrude-faces mesh {:select/by :normal
                                          :select/direction [0 1 0]
                                          :select/tolerance 0.1
                                          :extrude/amount 1.0})]
    (testing "only top face extruded: 5 unchanged + 1 cap + 4 walls = 10"
      (is (= 10 (count extruded))))))

(deftest extrude-faces-with-scale-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        extruded (s3d/extrude-faces mesh {:select/by :normal
                                          :select/direction [0 1 0]
                                          :select/tolerance 0.1
                                          :extrude/amount 1.0
                                          :extrude/scale 0.5})]
    (testing "cap vertices are scaled toward centroid"
      (let [cap-face (first (filter
                              (fn [f]
                                (let [ys (map second (:face/vertices f))]
                                  (every? #(> % 2.5) ys)))
                              extruded))
            cap-verts (:face/vertices cap-face)
            xs (map first cap-verts)]
        (is (< (- (apply max xs) (apply min xs)) 2.0))))))

(deftest extrude-faces-by-field-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        extruded (s3d/extrude-faces mesh
                   {:select/by :field
                    :select/field (field/constant-field 1.0)
                    :select/threshold 0.5
                    :extrude/amount 0.5})]
    (testing "constant field above threshold selects all faces"
      (is (= 30 (count extruded))))))

(deftest extrude-faces-chaining-test
  (testing "chained extrusions build on each other"
    (let [mesh (s3d/cube-mesh [0 0 0] 2)
          result (-> mesh
                     (s3d/extrude-faces {:select/by :normal
                                         :select/direction [0 1 0]
                                         :select/tolerance 0.1
                                         :extrude/amount 1.0})
                     (s3d/extrude-faces {:select/by :normal
                                         :select/direction [0 1 0]
                                         :select/tolerance 0.1
                                         :extrude/amount 0.5}))]
      (is (pos? (count result)))
      (testing "highest vertices are above original + both extrusions"
        (let [max-y (apply max (map second (mapcat :face/vertices result)))]
          (is (> max-y 3.0)))))))

(deftest extrude-faces-by-axis-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        extruded (s3d/extrude-faces mesh {:select/by :axis
                                          :select/axis :y
                                          :select/min 1.5
                                          :select/max 3.0
                                          :extrude/amount 0.5})]
    (testing "selects faces with centroids in y range"
      (is (> (count extruded) (count mesh))))))

(deftest inset-faces-all-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        inset (s3d/inset-faces mesh {:select/by :all
                                     :inset/amount 0.2})]
    (testing "each face becomes inner face + border quads"
      ;; 6 faces × (1 inner + 4 border) = 30
      (is (= 30 (count inset))))))

(deftest inset-faces-by-normal-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        inset (s3d/inset-faces mesh {:select/by :normal
                                     :select/direction [0 1 0]
                                     :select/tolerance 0.1
                                     :inset/amount 0.3})]
    (testing "only top face inset: 5 unchanged + 1 inner + 4 border = 10"
      (is (= 10 (count inset))))))

(deftest inset-then-extrude-test
  (testing "inset + extrude creates recessed panels"
    (let [mesh (s3d/cube-mesh [0 0 0] 2)
          result (-> mesh
                     (s3d/inset-faces {:select/by :all :inset/amount 0.2})
                     (s3d/extrude-faces {:select/by :all :extrude/amount -0.1}))]
      (is (pos? (count result))))))

;; --- mesh deformations ---

(deftest deform-mesh-twist-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        twisted (s3d/deform-mesh mesh {:deform/type :twist
                                       :deform/axis :y
                                       :deform/amount 1.0})]
    (testing "same number of faces"
      (is (= (count mesh) (count twisted))))
    (testing "vertices change"
      (is (not= (:face/vertices (first mesh))
                (:face/vertices (first twisted)))))
    (testing "normals are recomputed"
      (is (some? (:face/normal (first twisted)))))))

(deftest deform-mesh-taper-test
  (let [mesh [(s3d/make-face [[1 0 0] [0 0 1] [-1 0 0]])
              (s3d/make-face [[1 2 0] [0 2 1] [-1 2 0]])]
        tapered (s3d/deform-mesh mesh {:deform/type :taper
                                       :deform/axis :y
                                       :deform/amount 0.5})]
    (testing "top face vertices are scaled down"
      (let [top-verts (:face/vertices (second tapered))
            xs (map #(abs (first %)) top-verts)]
        (is (every? #(< % 1.0) xs))))))

(deftest deform-mesh-bend-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        bent (s3d/deform-mesh mesh {:deform/type :bend
                                    :deform/axis :y
                                    :deform/amount 1.0})]
    (testing "same face count"
      (is (= (count mesh) (count bent))))
    (testing "vertices change"
      (is (not= (mapv :face/vertices mesh)
                (mapv :face/vertices bent))))))

(deftest deform-mesh-inflate-test
  (let [mesh (s3d/sphere-mesh 1.0 8 4)
        inflated (s3d/deform-mesh mesh {:deform/type :inflate
                                        :deform/amount 0.5})]
    (testing "vertices move outward"
      (let [orig-dists (map #(m/magnitude %) (mapcat :face/vertices mesh))
            new-dists  (map #(m/magnitude %) (mapcat :face/vertices inflated))]
        (is (> (reduce + new-dists) (reduce + orig-dists)))))))

(deftest deform-mesh-crumple-test
  (let [mesh (s3d/cube-mesh [0 0 0] 2)
        c1 (s3d/deform-mesh mesh {:deform/type :crumple
                                   :deform/amplitude 0.1
                                   :deform/seed 42})
        c2 (s3d/deform-mesh mesh {:deform/type :crumple
                                   :deform/amplitude 0.1
                                   :deform/seed 42})]
    (testing "deterministic with same seed"
      (is (= (mapv :face/vertices c1) (mapv :face/vertices c2))))
    (testing "vertices change"
      (is (not= (mapv :face/vertices mesh) (mapv :face/vertices c1))))))

(deftest deform-mesh-displace-test
  (let [mesh (s3d/sphere-mesh 1.0 8 4)
        displaced (s3d/deform-mesh mesh
                    {:deform/type :displace
                     :deform/field {:field/type :field/constant
                                    :field/value 1.0}
                     :deform/amplitude 0.5})]
    (testing "all vertices move outward by 0.5 along normal"
      (let [orig-dists (map m/magnitude (mapcat :face/vertices mesh))
            new-dists  (map m/magnitude (mapcat :face/vertices displaced))]
        (is (> (reduce + new-dists) (reduce + orig-dists)))))))

(deftest deform-mesh-chaining-test
  (testing "deformations can be chained via ->"
    (let [mesh (s3d/cube-mesh [0 0 0] 2)
          result (-> mesh
                     (s3d/deform-mesh {:deform/type :twist
                                       :deform/axis :y
                                       :deform/amount 0.5})
                     (s3d/deform-mesh {:deform/type :inflate
                                       :deform/amount 0.1}))]
      (is (= (count mesh) (count result))))))

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
        (is (= :shape/path (:node/type child)))))
    (testing "each child carries :node/depth"
      (doseq [child (:group/children result)]
        (is (number? (:node/depth child)))))))

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

;; --- depth-sort ---

(deftest depth-sort-orders-by-depth-test
  (testing "nodes are sorted farthest-first by :node/depth"
    (let [a {:node/type :shape/circle :circle/center [0 0] :circle/radius 1
             :style/fill [:color/rgb 255 0 0] :node/depth 5.0}
          b {:node/type :shape/circle :circle/center [0 0] :circle/radius 1
             :style/fill [:color/rgb 0 255 0] :node/depth 2.0}
          c {:node/type :shape/circle :circle/center [0 0] :circle/radius 1
             :style/fill [:color/rgb 0 0 255] :node/depth -1.0}
          sorted (s3d/depth-sort [a b c])]
      (is (= [-1.0 2.0 5.0] (mapv :node/depth sorted))))))

(deftest depth-sort-flattens-groups-test
  (testing "groups are flattened and interleaved with other nodes"
    (let [group {:node/type :group
                 :group/children [{:node/type :shape/path :node/depth 3.0
                                   :path/commands []}
                                  {:node/type :shape/path :node/depth 1.0
                                   :path/commands []}]}
          particles [{:node/type :shape/circle :node/depth 2.0
                      :circle/center [0 0] :circle/radius 1
                      :style/fill [:color/rgb 0 0 0]}]
          sorted (s3d/depth-sort group particles)]
      (is (= 3 (count sorted)))
      (is (= [1.0 2.0 3.0] (mapv :node/depth sorted)))
      (is (= [:shape/path :shape/circle :shape/path]
             (mapv :node/type sorted))))))

(deftest depth-sort-nodes-without-depth-test
  (testing "nodes without :node/depth are placed at the back"
    (let [with-depth {:node/type :shape/circle :node/depth 1.0
                      :circle/center [0 0] :circle/radius 1
                      :style/fill [:color/rgb 0 0 0]}
          without    {:node/type :shape/circle
                      :circle/center [0 0] :circle/radius 1
                      :style/fill [:color/rgb 0 0 0]}
          sorted (s3d/depth-sort [with-depth without])]
      (is (nil? (:node/depth (first sorted))))
      (is (= 1.0 (:node/depth (second sorted)))))))

(deftest depth-sort-multiple-collections-test
  (testing "accepts multiple collections as separate arguments"
    (let [a [{:node/type :shape/circle :node/depth 3.0
              :circle/center [0 0] :circle/radius 1
              :style/fill [:color/rgb 0 0 0]}]
          b [{:node/type :shape/circle :node/depth 1.0
              :circle/center [0 0] :circle/radius 1
              :style/fill [:color/rgb 0 0 0]}]
          sorted (s3d/depth-sort a b)]
      (is (= [1.0 3.0] (mapv :node/depth sorted))))))
