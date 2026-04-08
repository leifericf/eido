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

;; --- light constructors ---

(deftest directional-constructor-test
  (let [l (material/directional [1 2 1] :multiplier 0.8 :ambient 0.2)]
    (is (= :directional (:light/type l)))
    (is (= [1 2 1] (:light/direction l)))
    (is (= 0.8 (:light/multiplier l)))))

(deftest omni-constructor-test
  (let [l (material/omni [100 50 200] :decay :inverse-square :decay-start 10.0)]
    (is (= :omni (:light/type l)))
    (is (= [100 50 200] (:light/position l)))
    (is (= :inverse-square (:light/decay l)))))

(deftest spot-constructor-test
  (let [l (material/spot [0 200 0] [0 -1 0] :hotspot 30 :falloff 40)]
    (is (= :spot (:light/type l)))
    (is (= 30 (:light/hotspot l)))
    (is (= 40 (:light/falloff l)))))

(deftest hemisphere-constructor-test
  (let [l (material/hemisphere [:color/rgb 135 180 220] [:color/rgb 40 30 20])]
    (is (= :hemisphere (:light/type l)))
    (is (= [:color/rgb 135 180 220] (:light/sky-color l)))))

;; --- directional shading ---

(deftest shade-directional-test
  (testing "directional light facing normal gives full diffuse"
    (let [normal    [0.0 0.0 1.0]
          cam-dir   [0.0 0.0 1.0]
          mat       (material/phong :ambient 0.2 :diffuse 0.8
                                    :specular 0.0 :color [:color/rgb 200 100 50])
          light     (material/directional [0 0 1] :multiplier 1.0)
          [_ r g b] (material/shade-multi-light normal cam-dir [0 0 0] mat [light])]
      ;; brightness = 0.2 (ambient) + 0.8 (diffuse * multiplier * cos=1)
      (is (= 200 r))
      (is (= 100 g))
      (is (= 50 b)))))

(deftest shade-directional-specular-test
  (testing "specular adds white highlight"
    (let [normal    [0.0 0.0 1.0]
          cam-dir   [0.0 0.0 1.0]
          mat-spec  (material/phong :ambient 0.2 :diffuse 0.5
                                    :specular 0.5 :shininess 32.0
                                    :color [:color/rgb 100 100 100])
          mat-no    (material/phong :ambient 0.2 :diffuse 0.5
                                    :specular 0.0
                                    :color [:color/rgb 100 100 100])
          light     (material/directional [0 0 1] :multiplier 1.0)
          [_ rs _ _] (material/shade-multi-light normal cam-dir [0 0 0] mat-spec [light])
          [_ rn _ _] (material/shade-multi-light normal cam-dir [0 0 0] mat-no [light])]
      (is (> rs rn)))))

;; --- omni (point) light ---

(deftest shade-omni-test
  (testing "omni light: closer face is brighter"
    (let [mat   (material/phong :ambient 0.0 :diffuse 1.0 :specular 0.0
                                :color [:color/rgb 200 200 200])
          light (material/omni [0 0 5] :multiplier 1.0 :decay :inverse-square)
          ;; Face at z=4 (distance 1 from light)
          [_ r-close _ _] (material/shade-multi-light
                            [0 0 1] [0 0 1] [0 0 4] mat [light])
          ;; Face at z=0 (distance 5 from light)
          [_ r-far _ _]   (material/shade-multi-light
                            [0 0 1] [0 0 1] [0 0 0] mat [light])]
      (is (> r-close r-far)))))

;; --- spot light ---

(deftest shade-spot-test
  (testing "spot light: face in cone is lit, outside is dark"
    (let [mat   (material/phong :ambient 0.0 :diffuse 1.0 :specular 0.0
                                :color [:color/rgb 200 200 200])
          light (material/spot [0 10 0] [0 -1 0]
                  :hotspot 20 :falloff 25 :multiplier 1.0)
          ;; Face directly below (in cone)
          [_ r-in _ _]  (material/shade-multi-light
                           [0 1 0] [0 0 1] [0 0 0] mat [light])
          ;; Face far to the side (outside cone)
          [_ r-out _ _] (material/shade-multi-light
                           [0 1 0] [0 0 1] [100 0 0] mat [light])]
      (is (> r-in r-out)))))

;; --- hemisphere light ---

(deftest shade-hemisphere-test
  (testing "upward face gets sky, downward gets ground"
    (let [mat   (material/phong :ambient 0.0 :diffuse 0.0 :specular 0.0
                                :color [:color/rgb 255 255 255])
          light (material/hemisphere [:color/rgb 200 200 255]
                                     [:color/rgb 50 40 30]
                                     :multiplier 1.0)
          [_ r-up _ _]   (material/shade-multi-light
                            [0 1 0] [0 0 1] [0 0 0] mat [light])
          [_ r-down _ _] (material/shade-multi-light
                            [0 -1 0] [0 0 1] [0 0 0] mat [light])]
      (is (> r-up r-down)))))

;; --- multi-light ---

(deftest shade-multi-light-test
  (testing "two lights brighter than one"
    (let [mat    (material/phong :ambient 0.0 :diffuse 1.0 :specular 0.0
                                 :color [:color/rgb 200 200 200])
          light1 (material/directional [0 0 1] :multiplier 0.5)
          light2 (material/directional [1 0 0] :multiplier 0.5)
          [_ r1 _ _] (material/shade-multi-light
                        [0.5 0 0.5] [0 0 1] [0 0 0] mat [light1])
          [_ r2 _ _] (material/shade-multi-light
                        [0.5 0 0.5] [0 0 1] [0 0 0] mat [light1 light2])]
      (is (> r2 r1)))))

;; --- render-mesh integration ---

(deftest render-mesh-with-material-test
  (testing "render-mesh works with material and single light"
    (let [mesh (s3d/cube-mesh [0 0 0] 1.0)
          proj (s3d/isometric {:scale 80 :origin [200 200]})
          result (s3d/render-mesh proj mesh
                   {:style {:style/fill [:color/rgb 150 100 200]
                            :material (material/phong :specular 0.4 :shininess 32.0)}
                    :light (material/directional [1 2 1]
                             :multiplier 0.8 :ambient 0.2)})]
      (is (= :group (:node/type result)))
      (is (pos? (count (:group/children result)))))))

(deftest render-mesh-multi-light-test
  (testing "render-mesh works with multiple lights"
    (let [mesh (s3d/sphere-mesh 1.0 12 8)
          proj (s3d/isometric {:scale 80 :origin [200 200]})
          result (s3d/render-mesh proj mesh
                   {:style {:style/fill [:color/rgb 150 150 150]
                            :material (material/phong :specular 0.3 :shininess 16.0)}
                    :lights [(material/directional [1 1 1] :multiplier 0.6)
                             (material/omni [3 2 0] :multiplier 0.4 :decay :inverse)
                             (material/hemisphere [:color/rgb 100 120 160]
                                                  [:color/rgb 30 20 10]
                                                  :multiplier 0.2)]})]
      (is (= :group (:node/type result)))
      (is (pos? (count (:group/children result)))))))
