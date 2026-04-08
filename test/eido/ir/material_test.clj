(ns eido.ir.material-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.ir.material :as material]
    [eido.math3d :as m]
    [eido.scene3d :as s3d]))

(deftest phong-constructor-test
  (let [mat (material/phong :ambient 0.2 :diffuse 0.6 :specular 0.5
                            :shininess 64.0
                            :color [:color/rgb 200 100 50])]
    (is (= :material/phong (:material/type mat)))
    (is (= 0.5 (:material/specular mat)))
    (is (= 64.0 (:material/shininess mat)))))

(deftest shade-phong-diffuse-only-test
  (testing "with specular 0, diffuse shading produces expected values"
    (let [normal    [0.0 0.0 1.0]
          light-dir [0.0 0.0 1.0]
          cam-dir   [0.0 0.0 1.0]
          light     {:light/ambient 0.3 :light/intensity 0.7}
          mat       (material/phong :ambient 0.3 :diffuse 0.7
                                    :specular 0.0 :shininess 32.0
                                    :color [:color/rgb 200 100 50])
          [_ r g b] (material/shade-phong normal light-dir cam-dir light mat)]
      ;; brightness = min(1.0, 0.3 + 0.7*0.7*1.0) = 0.79
      ;; R = 200*0.79 ≈ 158, G = 100*0.79 ≈ 79, B = 50*0.79 ≈ 40
      (is (< (Math/abs (- r 158)) 2))
      (is (< (Math/abs (- g 79)) 2))
      (is (< (Math/abs (- b 40)) 2)))))

(deftest shade-phong-specular-test
  (testing "specular highlight brightens pixels"
    (let [normal    [0.0 0.0 1.0]  ;; face points at camera
          light-dir [0.0 0.0 1.0]  ;; light from same direction
          cam-dir   [0.0 0.0 1.0]  ;; camera from same direction
          light     {:light/ambient 0.2 :light/intensity 0.8}
          ;; With specular
          mat-spec  (material/phong :ambient 0.2 :diffuse 0.5
                                    :specular 0.5 :shininess 32.0
                                    :color [:color/rgb 100 100 100])
          [_ rs gs bs] (material/shade-phong normal light-dir cam-dir light mat-spec)
          ;; Without specular
          mat-no-spec (material/phong :ambient 0.2 :diffuse 0.5
                                      :specular 0.0 :shininess 32.0
                                      :color [:color/rgb 100 100 100])
          [_ rn gn bn] (material/shade-phong normal light-dir cam-dir light mat-no-spec)]
      ;; Specular should make the result brighter
      (is (> rs rn))
      (is (> gs gn)))))

(deftest shade-phong-side-lit-test
  (testing "side-lit face has no specular highlight"
    (let [normal    [0.0 0.0 1.0]
          light-dir [1.0 0.0 0.0]  ;; light from side (perpendicular)
          cam-dir   [0.0 0.0 1.0]
          light     {:light/ambient 0.2 :light/intensity 0.8}
          mat       (material/phong :ambient 0.2 :diffuse 0.5
                                    :specular 0.8 :shininess 64.0
                                    :color [:color/rgb 200 200 200])
          [_ r g b] (material/shade-phong normal light-dir cam-dir light mat)]
      ;; Only ambient lighting (dot(N,L) = 0)
      ;; brightness = 0.2, no specular (cos-angle <= 0)
      (is (< r 60)))))

(deftest shade-face-integration-test
  (testing "shade-face returns style map with shaded fill"
    (let [style     {:style/fill [:color/rgb 150 100 50]}
          normal    [0.0 1.0 0.0]
          light-dir (m/normalize [0.0 1.0 1.0])
          cam-dir   [0.0 0.0 1.0]
          light     {:light/ambient 0.3 :light/intensity 0.7}
          mat       (material/phong :specular 0.3 :shininess 16.0)
          result    (material/shade-face style normal light-dir cam-dir light mat)]
      (is (vector? (:style/fill result)))
      (is (= :color/rgb (first (:style/fill result)))))))

(deftest render-mesh-with-material-test
  (testing "render-mesh accepts material in style and produces output"
    (let [mesh (s3d/cube-mesh [0 0 0] 1.0)
          proj (s3d/isometric {:scale 80 :origin [200 200]})
          result (s3d/render-mesh proj mesh
                   {:style {:style/fill [:color/rgb 150 100 200]
                            :material (material/phong
                                        :specular 0.4
                                        :shininess 32.0)}
                    :light {:light/direction [1 2 1]
                            :light/ambient 0.2
                            :light/intensity 0.8}})]
      ;; Should produce a group with children
      (is (= :group (:node/type result)))
      (is (pos? (count (:group/children result)))))))
