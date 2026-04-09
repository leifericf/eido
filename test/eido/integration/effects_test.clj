(ns eido.integration.effects-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.engine.compile :as compile]
    [eido.core :as eido]))

(deftest shadow-compile-test
  (testing "shadow effect compiles to buffer with shadow op"
    (let [scene {:image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/circle
                   :circle/center [100.0 100.0]
                   :circle/radius 40.0
                   :style/fill [:color/rgb 200 0 0]
                   :effect/shadow {:dx 5 :dy 5 :blur 10
                                   :color [:color/rgb 0 0 0]
                                   :opacity 0.5}}]}
          ir (compile/compile scene)]
      (is (pos? (count (:ir/ops ir)))))))

(deftest glow-compile-test
  (testing "glow effect compiles to buffer with glow op"
    (let [scene {:image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/circle
                   :circle/center [100.0 100.0]
                   :circle/radius 40.0
                   :style/fill [:color/rgb 200 0 0]
                   :effect/glow {:blur 10
                                 :color [:color/rgb 255 100 0]
                                 :opacity 0.7}}]}
          ir (compile/compile scene)]
      (is (pos? (count (:ir/ops ir)))))))

(deftest shadow-render-test
  (testing "shadow renders without error"
    (let [img (eido/render
                {:image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes
                 [{:node/type :shape/circle
                   :circle/center [100.0 100.0]
                   :circle/radius 40.0
                   :style/fill [:color/rgb 200 0 0]
                   :effect/shadow {:dx 5 :dy 5 :blur 8
                                   :color [:color/rgb 0 0 0]
                                   :opacity 0.4}}]})]
      (is (= 200 (.getWidth img)))
      ;; Check that the shadow area has dark pixels
      (let [px (.getRGB img 110 110)]
        (is (not= -1 px) "pixel under shadow should not be pure white")))))
