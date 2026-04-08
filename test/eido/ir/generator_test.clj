(ns eido.ir.generator-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.color :as color]
    [eido.ir :as ir]
    [eido.ir.generator :as gen]
    [eido.ir.lower :as lower]
    [eido.render :as render]))

;; --- helpers ---

(defn- render-semantic [ir-container]
  (render/render (lower/lower ir-container) {}))

;; --- constructor tests ---

(deftest flow-field-constructor-test
  (let [g (gen/flow-field [0 0 200 200]
            :opts {:density 20 :steps 30 :seed 42})]
    (is (= :generator/flow-field
           (get-in g [:item/generator :generator/type])))))

(deftest contour-constructor-test
  (let [g (gen/contour [0 0 200 200]
            :opts {:thresholds [0.0] :resolution 5})]
    (is (= :generator/contour
           (get-in g [:item/generator :generator/type])))))

(deftest scatter-constructor-test
  (let [g (gen/scatter-gen
            {:node/type :shape/circle
             :circle/center [0 0]
             :circle/radius 5
             :style/fill [:color/rgb 255 0 0]}
            [[10 10] [20 20] [30 30]])]
    (is (= :generator/scatter
           (get-in g [:item/generator :generator/type])))))

(deftest voronoi-constructor-test
  (let [g (gen/voronoi-gen
            [[50 50] [150 50] [100 150]]
            [0 0 200 200])]
    (is (= :generator/voronoi
           (get-in g [:item/generator :generator/type])))))

(deftest decorator-constructor-test
  (let [g (gen/decorator-gen
            [[:move-to [0.0 100.0]] [:line-to [200.0 100.0]]]
            {:node/type :shape/circle
             :circle/center [0 0]
             :circle/radius 3}
            :spacing 15)]
    (is (= :generator/decorator
           (get-in g [:item/generator :generator/type])))))

;; --- expansion tests ---

(deftest flow-field-expand-test
  (testing "flow field produces path ops"
    (let [g (:item/generator
              (gen/flow-field [0 0 100 100]
                :opts {:density 25 :steps 20 :seed 42}
                :style {:stroke {:color [:color/rgb 0 0 0] :width 1}}))
          ops (gen/expand-generator g)]
      (is (vector? ops))
      (is (pos? (count ops))))))

(deftest contour-expand-test
  (testing "contour produces path ops"
    (let [g (:item/generator
              (gen/contour [0 0 100 100]
                :opts {:thresholds [0.0] :resolution 5 :noise-scale 0.02 :seed 42}
                :style {:stroke {:color [:color/rgb 0 0 0] :width 1}}))
          ops (gen/expand-generator g)]
      (is (vector? ops))
      (is (pos? (count ops))))))

(deftest scatter-expand-test
  (testing "scatter produces ops for each position"
    (let [g (:item/generator
              (gen/scatter-gen
                {:node/type :shape/circle
                 :circle/center [0 0]
                 :circle/radius 5
                 :style/fill [:color/rgb 200 0 0]}
                [[20 20] [60 60] [100 100]]))
          ops (gen/expand-generator g)]
      (is (vector? ops))
      (is (pos? (count ops))))))

(deftest voronoi-expand-test
  (testing "voronoi produces path ops for cells"
    (let [g (:item/generator
              (gen/voronoi-gen
                [[30 30] [70 30] [50 70] [20 80] [80 80]]
                [0 0 100 100]
                :style {:stroke {:color [:color/rgb 0 0 0] :width 1}}))
          ops (gen/expand-generator g)]
      (is (vector? ops))
      (is (pos? (count ops))))))

(deftest delaunay-expand-test
  (testing "delaunay produces line ops"
    (let [g (:item/generator
              (gen/delaunay-gen
                [[30 30] [70 30] [50 70]]
                [0 0 100 100]
                :style {:stroke {:color [:color/rgb 0 0 0] :width 1}}))
          ops (gen/expand-generator g)]
      (is (vector? ops))
      (is (pos? (count ops))))))

(deftest decorator-expand-test
  (testing "decorator produces ops along path"
    (let [g (:item/generator
              (gen/decorator-gen
                [[:move-to [0.0 50.0]] [:line-to [200.0 50.0]]]
                {:node/type :shape/circle
                 :circle/center [0 0]
                 :circle/radius 4
                 :style/fill [:color/rgb 200 0 0]}
                :spacing 30))
          ops (gen/expand-generator g)]
      (is (vector? ops))
      (is (pos? (count ops))))))

;; --- render tests ---

(deftest flow-field-renders-test
  (testing "flow field renders via semantic IR"
    (let [semantic (ir/container
                     [200 200]
                     (color/resolve-color [:color/rgb 255 255 255])
                     [(gen/flow-field [0 0 200 200]
                        :opts {:density 30 :steps 20 :seed 42}
                        :style {:stroke {:color [:color/rgb 0 0 0] :width 1}})])
          img (render-semantic semantic)]
      (is (some? img)))))

(deftest voronoi-renders-test
  (testing "voronoi renders via semantic IR"
    (let [semantic (ir/container
                     [200 200]
                     (color/resolve-color [:color/rgb 255 255 255])
                     [(gen/voronoi-gen
                        [[40 40] [160 40] [100 160] [40 120] [160 120]]
                        [0 0 200 200]
                        :style {:stroke {:color [:color/rgb 0 0 0] :width 1}})])
          img (render-semantic semantic)]
      (is (some? img)))))
