(ns eido.clojo.translate-test
  "The Eido-to-Clojo grammar translation. Pure, so it runs in the default lane
  with no zig toolchain."
  (:require
    [clojure.test :refer [deftest is testing]]
    [clojure.walk :as walk]
    [eido.clojo.translate :as t]))

(defn- x [v] (walk/postwalk t/xlate v))

(deftest colours-resolve-to-zero-to-one-rgba
  (testing "an Eido 0..255 colour vector becomes a 0..1 :color/rgba"
    (is (= [:color/rgba 1.0 0.0 0.0 1.0] (t/xlate [:color/rgb 255 0 0])))
    (is (= [:color/rgba 0.0 0.0 0.0 1.0] (t/xlate [:color/rgb 0 0 0]))))
  (testing "a bare named-colour keyword on a fill resolves at node level"
    (is (= [:color/rgba 1.0 0.0 0.0 1.0]
           (:style/fill (t/xlate {:node/type :shape/rect :style/fill :red}))))))

(deftest path-verbs-shorten
  (is (= [:move 1.0 2.0] (t/xlate [:move-to [1.0 2.0]])))
  (is (= [:line 3.0 4.0] (t/xlate [:line-to [3.0 4.0]])))
  (is (= [:quad 1.0 2.0 3.0 4.0] (t/xlate [:quad-to [1.0 2.0] [3.0 4.0]])))
  (is (= [:curve 1.0 2.0 3.0 4.0 5.0 6.0]
         (t/xlate [:curve-to [1.0 2.0] [3.0 4.0] [5.0 6.0]]))))

(deftest transforms-keep-affine-and-convert-rotation
  (testing "rotate radians become Clojo degrees, kept only affine ops"
    (let [node (t/xlate {:node/type :group
                         :node/transform [[:transform/rotate (/ Math/PI 2)]
                                          [:transform/translate 5 6]
                                          [:transform/shear 1 1]]})]
      (is (= [[:rotate 90.0] [:translate 5 6]] (:node/transform node))))))

(deftest shape-key-fixups
  (testing "rect/xy becomes rect/origin"
    (is (= {:node/type :shape/rect :rect/origin [1 2]}
           (t/xlate {:node/type :shape/rect :rect/xy [1 2]}))))
  (testing "ellipse rx/ry become ellipse/radii"
    (is (= {:node/type :shape/ellipse :ellipse/radii [3 4]}
           (t/xlate {:node/type :shape/ellipse :ellipse/rx 3 :ellipse/ry 4})))))

(deftest semantic-fills-become-tagged-vectors
  (let [hatch (t/xlate {:fill/type :hatch :hatch/angle 30 :hatch/spacing 8})]
    (is (= :fill/hatch (first hatch)))
    (is (= 30 (:angle (second hatch))))
    (is (= 8 (:spacing (second hatch)))))
  (let [stip (t/xlate {:fill/type :stipple :stipple/radius 3 :stipple/seed 1})]
    (is (= :fill/stipple (first stip)))
    (is (= 3 (:radius (second stip))))))

(deftest gradients-become-tagged-vectors
  (let [g (t/xlate {:gradient/type :linear :gradient/from [0 0]
                    :gradient/to [1 1] :gradient/stops []})]
    (is (= :gradient/linear (first g)))
    (is (= [0 0] (:from (second g))))))

(deftest symmetry-takes-its-clojo-node-type
  (let [r (x {:node/type :symmetry :symmetry/type :radial :symmetry/n 6
              :symmetry/center [5 5] :group/children []})]
    (is (= :item/symmetry (:node/type r)))
    (is (= :radial (:symmetry/type r)))
    (is (= 6 (:symmetry/count r)))))

(deftest translate-routes-canvas-and-paint
  (testing "a canvas scene returns a translated :canvas scene"
    (let [{:keys [kind scene]} (t/translate
                                 {:image/size [20 20]
                                  :image/background [:color/rgb 255 255 255]
                                  :image/nodes [{:node/type :shape/circle
                                                 :circle/center [10 10]
                                                 :circle/radius 5
                                                 :style/fill [:color/rgb 0 0 0]}]})]
      (is (= :canvas kind))
      (is (= [20 20] (:image/size scene)))
      (is (= [:color/rgba 1.0 1.0 1.0 1.0] (:image/background scene)))))
  (testing "a single paint surface returns a :paint program"
    (let [{:keys [kind program]} (t/translate
                                    {:image/size [30 30]
                                     :image/background [:color/rgb 255 255 255]
                                     :image/nodes
                                     [{:node/type :paint/surface
                                       :paint/size [30 30]
                                       :paint/strokes
                                       [{:paint/points [[0 0] [10 10]]
                                         :paint/brush :ink
                                         :paint/color [:color/rgb 0 0 0]}]}]})]
      (is (= :paint kind))
      (is (= [30 30] (:paint/size program)))
      (is (= 1 (count (:paint/strokes program)))))))

(deftest still-of-collapses-animations
  (is (= {:a 1} (t/still-of [{:a 1} {:a 2}])))
  (is (= {:a 1} (t/still-of {:frames [{:a 1} {:a 2}]})))
  (is (= {:a 1} (t/still-of {:scene {:a 1}}))))
