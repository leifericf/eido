(ns eido.scene3d.surface-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.scene3d.mesh :as mesh]
    [eido.scene3d.surface :as surface]))

(def ^:private cube (mesh/cube-mesh))

(deftest uv-project-box-test
  (let [m (surface/uv-project cube {:uv/type :box})]
    (testing "every face gets texture coordinates"
      (is (every? :face/texture-coords m)))
    (testing "UV values are in [0,1]"
      (doseq [face m
              [u v] (:face/texture-coords face)]
        (is (<= 0.0 u 1.0))
        (is (<= 0.0 v 1.0))))))

(deftest uv-project-spherical-test
  (let [sphere (mesh/sphere-mesh 1.0 8 4)
        m (surface/uv-project sphere {:uv/type :spherical})]
    (testing "every face gets texture coordinates"
      (is (every? :face/texture-coords m)))
    (testing "UV values are in [0,1]"
      (doseq [face m
              [u v] (:face/texture-coords face)]
        (is (<= 0.0 u 1.0))
        (is (<= 0.0 v 1.0))))))

(deftest uv-project-planar-test
  (let [m (surface/uv-project cube {:uv/type :planar :uv/axis :z})]
    (testing "every face gets texture coordinates"
      (is (every? :face/texture-coords m)))))

(deftest uv-project-with-scale-test
  (let [m (surface/uv-project cube {:uv/type :box :uv/scale 2.0})]
    (testing "scaled UVs can exceed 1.0"
      (let [max-u (apply max (mapcat #(map first (:face/texture-coords %)) m))]
        (is (> max-u 1.0))))))

(deftest color-mesh-test
  (let [palette [[:color/rgb 255 0 0] [:color/rgb 0 255 0] [:color/rgb 0 0 255]]
        m (surface/color-mesh cube {:color/type    :axis-gradient
                                    :color/axis    :y
                                    :color/palette palette})]
    (testing "all faces get a color style"
      (is (every? :face/style m)))
    (testing "styles contain fill from the palette"
      (is (every? #(some #{(:style/fill (:face/style %))} palette) m)))))
