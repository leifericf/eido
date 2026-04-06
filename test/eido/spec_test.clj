(ns eido.spec-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest is testing]]
    [eido.spec]))

;; --- primitive specs ---

(deftest rgb-val-test
  (testing "accepts valid RGB values"
    (is (s/valid? :eido.spec/rgb-val 0))
    (is (s/valid? :eido.spec/rgb-val 127))
    (is (s/valid? :eido.spec/rgb-val 255)))
  (testing "rejects out-of-range values"
    (is (not (s/valid? :eido.spec/rgb-val -1)))
    (is (not (s/valid? :eido.spec/rgb-val 256)))
    (is (not (s/valid? :eido.spec/rgb-val 1.5)))
    (is (not (s/valid? :eido.spec/rgb-val "red")))))

(deftest unit-val-test
  (testing "accepts 0-1 range"
    (is (s/valid? :eido.spec/unit-val 0))
    (is (s/valid? :eido.spec/unit-val 0.5))
    (is (s/valid? :eido.spec/unit-val 1.0)))
  (testing "rejects outside range"
    (is (not (s/valid? :eido.spec/unit-val -0.1)))
    (is (not (s/valid? :eido.spec/unit-val 1.1)))))

(deftest hue-val-test
  (testing "accepts 0-360"
    (is (s/valid? :eido.spec/hue-val 0))
    (is (s/valid? :eido.spec/hue-val 180))
    (is (s/valid? :eido.spec/hue-val 360)))
  (testing "rejects outside range"
    (is (not (s/valid? :eido.spec/hue-val -1)))
    (is (not (s/valid? :eido.spec/hue-val 361)))))

(deftest point-test
  (testing "accepts [x y] vector"
    (is (s/valid? :eido.spec/point [10 20]))
    (is (s/valid? :eido.spec/point [0.5 -3.7])))
  (testing "rejects wrong shape"
    (is (not (s/valid? :eido.spec/point [1])))
    (is (not (s/valid? :eido.spec/point [1 2 3])))
    (is (not (s/valid? :eido.spec/point "10,20")))))

(deftest pos-size-test
  (testing "accepts positive dimensions"
    (is (s/valid? :eido.spec/pos-size [100 200]))
    (is (s/valid? :eido.spec/pos-size [0.5 0.5])))
  (testing "rejects zero or negative"
    (is (not (s/valid? :eido.spec/pos-size [0 100])))
    (is (not (s/valid? :eido.spec/pos-size [-1 100])))))

;; --- color specs ---

(deftest color-rgb-test
  (testing "accepts valid rgb"
    (is (s/valid? :eido.spec/color [:color/rgb 255 0 0]))
    (is (s/valid? :eido.spec/color [:color/rgb 0 128 255])))
  (testing "rejects out-of-range"
    (is (not (s/valid? :eido.spec/color [:color/rgb 256 0 0])))
    (is (not (s/valid? :eido.spec/color [:color/rgb -1 0 0])))))

(deftest color-rgba-test
  (testing "accepts valid rgba"
    (is (s/valid? :eido.spec/color [:color/rgba 255 0 0 0.5])))
  (testing "rejects bad alpha"
    (is (not (s/valid? :eido.spec/color [:color/rgba 255 0 0 1.5])))))

(deftest color-hsl-test
  (testing "accepts valid hsl"
    (is (s/valid? :eido.spec/color [:color/hsl 0 1.0 0.5]))
    (is (s/valid? :eido.spec/color [:color/hsl 360 0 0])))
  (testing "rejects bad values"
    (is (not (s/valid? :eido.spec/color [:color/hsl 400 0 0])))))

(deftest color-hsla-test
  (testing "accepts valid hsla"
    (is (s/valid? :eido.spec/color [:color/hsla 120 0.8 0.5 0.7]))))

(deftest color-hsb-test
  (testing "accepts valid hsb"
    (is (s/valid? :eido.spec/color [:color/hsb 0 1.0 1.0]))
    (is (s/valid? :eido.spec/color [:color/hsb 360 0 0])))
  (testing "rejects bad values"
    (is (not (s/valid? :eido.spec/color [:color/hsb 400 0 0])))))

(deftest color-hsba-test
  (testing "accepts valid hsba"
    (is (s/valid? :eido.spec/color [:color/hsba 120 0.8 0.5 0.7]))))

(deftest color-hex-test
  (testing "accepts valid hex strings"
    (is (s/valid? :eido.spec/color [:color/hex "#FF0000"]))
    (is (s/valid? :eido.spec/color [:color/hex "#F00"]))
    (is (s/valid? :eido.spec/color [:color/hex "#FF000080"]))
    (is (s/valid? :eido.spec/color [:color/hex "#F008"])))
  (testing "rejects invalid hex"
    (is (not (s/valid? :eido.spec/color [:color/hex "#GG0000"])))
    (is (not (s/valid? :eido.spec/color [:color/hex "#12345"])))))

(deftest color-dispatch-test
  (testing "unknown color tag is invalid"
    (is (not (s/valid? :eido.spec/color [:color/cmyk 0 0 0 0])))))

;; --- transform specs ---

(deftest transform-translate-test
  (testing "accepts valid translate"
    (is (s/valid? :eido.spec/transform [:transform/translate 10 20])))
  (testing "rejects wrong arity"
    (is (not (s/valid? :eido.spec/transform [:transform/translate 10])))))

(deftest transform-rotate-test
  (testing "accepts valid rotate"
    (is (s/valid? :eido.spec/transform [:transform/rotate 3.14])))
  (testing "rejects non-numeric"
    (is (not (s/valid? :eido.spec/transform [:transform/rotate "90deg"])))))

(deftest transform-scale-test
  (testing "accepts valid scale"
    (is (s/valid? :eido.spec/transform [:transform/scale 2 3]))))

(deftest transform-shear-test
  (testing "accepts valid shear-x"
    (is (s/valid? :eido.spec/transform [:transform/shear-x 0.3])))
  (testing "accepts valid shear-y"
    (is (s/valid? :eido.spec/transform [:transform/shear-y 0.5])))
  (testing "rejects non-numeric"
    (is (not (s/valid? :eido.spec/transform [:transform/shear-x "bad"])))))

(deftest transforms-collection-test
  (testing "accepts vector of transforms"
    (is (s/valid? :node/transform [[:transform/translate 10 20]
                                    [:transform/rotate 1.57]])))
  (testing "rejects unknown transform"
    (is (not (s/valid? :node/transform [[:transform/skew 10 20]])))))

;; --- path command specs ---

(deftest path-command-move-to-test
  (testing "accepts move-to with point"
    (is (s/valid? :eido.spec/path-command [:move-to [10 20]])))
  (testing "rejects move-to without point vector"
    (is (not (s/valid? :eido.spec/path-command [:move-to 10 20])))))

(deftest path-command-line-to-test
  (testing "accepts line-to"
    (is (s/valid? :eido.spec/path-command [:line-to [50 60]]))))

(deftest path-command-curve-to-test
  (testing "accepts curve-to with 3 points"
    (is (s/valid? :eido.spec/path-command [:curve-to [10 20] [30 40] [50 60]]))))

(deftest path-command-quad-to-test
  (testing "accepts quad-to with 2 points"
    (is (s/valid? :eido.spec/path-command [:quad-to [10 20] [50 60]])))
  (testing "rejects quad-to with wrong arity"
    (is (not (s/valid? :eido.spec/path-command [:quad-to [10 20]])))))

(deftest path-command-close-test
  (testing "accepts close"
    (is (s/valid? :eido.spec/path-command [:close]))))

;; --- style specs ---

(deftest style-fill-test
  (testing "accepts fill with color"
    (is (s/valid? :style/fill {:color [:color/rgb 255 0 0]})))
  (testing "rejects fill without color"
    (is (not (s/valid? :style/fill {})))))

(deftest style-stroke-test
  (testing "accepts stroke with color and width"
    (is (s/valid? :style/stroke {:color [:color/rgb 0 0 0] :width 2})))
  (testing "rejects stroke without width"
    (is (not (s/valid? :style/stroke {:color [:color/rgb 0 0 0]}))))
  (testing "accepts stroke with cap and join"
    (is (s/valid? :style/stroke {:color [:color/rgb 0 0 0] :width 2
                                  :cap :round :join :bevel})))
  (testing "rejects invalid cap"
    (is (not (s/valid? :style/stroke {:color [:color/rgb 0 0 0] :width 2
                                       :cap :invalid}))))
  (testing "accepts stroke with dash pattern"
    (is (s/valid? :style/stroke {:color [:color/rgb 0 0 0] :width 2
                                  :dash [5 3]})))
  (testing "rejects empty dash vector"
    (is (not (s/valid? :style/stroke {:color [:color/rgb 0 0 0] :width 2
                                       :dash []})))))

(deftest opacity-test
  (testing "accepts valid opacity"
    (is (s/valid? :node/opacity 0.5)))
  (testing "rejects out of range"
    (is (not (s/valid? :node/opacity 1.5)))))

;; --- node specs ---

(deftest node-rect-test
  (testing "accepts valid rect"
    (is (s/valid? :eido.spec/node
          {:node/type :shape/rect
           :rect/xy [10 20]
           :rect/size [100 50]})))
  (testing "accepts rect with corner-radius"
    (is (s/valid? :eido.spec/node
          {:node/type :shape/rect
           :rect/xy [10 20]
           :rect/size [100 50]
           :rect/corner-radius 8})))
  (testing "rejects rect missing :rect/xy"
    (is (not (s/valid? :eido.spec/node
               {:node/type :shape/rect
                :rect/size [100 50]}))))
  (testing "rejects negative corner-radius"
    (is (not (s/valid? :eido.spec/node
               {:node/type :shape/rect
                :rect/xy [0 0]
                :rect/size [50 50]
                :rect/corner-radius -1})))))

(deftest node-circle-test
  (testing "accepts valid circle"
    (is (s/valid? :eido.spec/node
          {:node/type :shape/circle
           :circle/center [50 50]
           :circle/radius 20})))
  (testing "rejects negative radius"
    (is (not (s/valid? :eido.spec/node
               {:node/type :shape/circle
                :circle/center [50 50]
                :circle/radius -5})))))

(deftest node-path-test
  (testing "accepts valid path"
    (is (s/valid? :eido.spec/node
          {:node/type :shape/path
           :path/commands [[:move-to [0 0]]
                           [:line-to [100 100]]
                           [:close]]}))))

(deftest node-group-test
  (testing "accepts group with children"
    (is (s/valid? :eido.spec/node
          {:node/type :group
           :group/children
           [{:node/type :shape/rect
             :rect/xy [0 0]
             :rect/size [10 10]}]})))
  (testing "rejects group with invalid child"
    (is (not (s/valid? :eido.spec/node
               {:node/type :group
                :group/children
                [{:node/type :shape/rect}]})))))

(deftest node-ellipse-test
  (testing "accepts valid ellipse"
    (is (s/valid? :eido.spec/node
          {:node/type :shape/ellipse
           :ellipse/center [100 200]
           :ellipse/rx 80
           :ellipse/ry 40})))
  (testing "rejects ellipse missing :ellipse/rx"
    (is (not (s/valid? :eido.spec/node
               {:node/type :shape/ellipse
                :ellipse/center [100 200]
                :ellipse/ry 40}))))
  (testing "rejects negative radii"
    (is (not (s/valid? :eido.spec/node
               {:node/type :shape/ellipse
                :ellipse/center [100 200]
                :ellipse/rx -5
                :ellipse/ry 40})))))

(deftest node-arc-test
  (testing "accepts valid arc"
    (is (s/valid? :eido.spec/node
          {:node/type :shape/arc
           :arc/center [200 200]
           :arc/rx 80
           :arc/ry 80
           :arc/start 0
           :arc/extent 270})))
  (testing "accepts arc with mode"
    (is (s/valid? :eido.spec/node
          {:node/type :shape/arc
           :arc/center [200 200]
           :arc/rx 80
           :arc/ry 80
           :arc/start 0
           :arc/extent 270
           :arc/mode :pie})))
  (testing "rejects invalid mode"
    (is (not (s/valid? :eido.spec/node
               {:node/type :shape/arc
                :arc/center [200 200]
                :arc/rx 80
                :arc/ry 80
                :arc/start 0
                :arc/extent 270
                :arc/mode :invalid}))))
  (testing "rejects arc missing extent"
    (is (not (s/valid? :eido.spec/node
               {:node/type :shape/arc
                :arc/center [200 200]
                :arc/rx 80
                :arc/ry 80
                :arc/start 0})))))

(deftest node-line-test
  (testing "accepts valid line"
    (is (s/valid? :eido.spec/node
          {:node/type :shape/line
           :line/from [10 20]
           :line/to [100 200]})))
  (testing "rejects line missing :line/to"
    (is (not (s/valid? :eido.spec/node
               {:node/type :shape/line
                :line/from [10 20]})))))

(deftest node-unknown-type-test
  (testing "rejects unknown node type"
    (is (not (s/valid? :eido.spec/node
               {:node/type :shape/polygon})))))

(deftest node-optional-keys-test
  (testing "accepts optional style, opacity, transform"
    (is (s/valid? :eido.spec/node
          {:node/type :shape/rect
           :rect/xy [0 0]
           :rect/size [50 50]
           :style/fill {:color [:color/rgb 255 0 0]}
           :style/stroke {:color [:color/rgb 0 0 0] :width 2}
           :node/opacity 0.8
           :node/transform [[:transform/translate 10 20]]})))
  (testing "rejects bad opacity on otherwise valid node"
    (is (not (s/valid? :eido.spec/node
               {:node/type :shape/rect
                :rect/xy [0 0]
                :rect/size [50 50]
                :node/opacity 2.0})))))

;; --- scene spec ---

(deftest scene-valid-test
  (testing "accepts valid scene"
    (is (s/valid? :eido.spec/scene
          {:image/size [800 600]
           :image/background [:color/rgb 255 255 255]
           :image/nodes
           [{:node/type :shape/circle
             :circle/center [400 300]
             :circle/radius 100}]}))))

(deftest scene-missing-keys-test
  (testing "rejects missing :image/size"
    (is (not (s/valid? :eido.spec/scene
               {:image/background [:color/rgb 0 0 0]
                :image/nodes []}))))
  (testing "rejects missing :image/nodes"
    (is (not (s/valid? :eido.spec/scene
               {:image/size [100 100]
                :image/background [:color/rgb 0 0 0]})))))

(deftest scene-invalid-node-test
  (testing "rejects scene with invalid node"
    (is (not (s/valid? :eido.spec/scene
               {:image/size [100 100]
                :image/background [:color/rgb 0 0 0]
                :image/nodes
                [{:node/type :shape/rect}]})))))

(deftest scene-version-test
  (testing "scene without version is valid"
    (is (s/valid? :eido.spec/scene
          {:image/size [100 100]
           :image/background [:color/rgb 0 0 0]
           :image/nodes []})))

  (testing "scene with valid version is valid"
    (is (s/valid? :eido.spec/scene
          {:eido/version "1.0"
           :image/size [100 100]
           :image/background [:color/rgb 0 0 0]
           :image/nodes []})))

  (testing "scene with invalid version format is rejected"
    (is (not (s/valid? :eido.spec/scene
               {:eido/version "alpha"
                :image/size [100 100]
                :image/background [:color/rgb 0 0 0]
                :image/nodes []}))))

  (testing "scene with numeric version is rejected"
    (is (not (s/valid? :eido.spec/scene
               {:eido/version 1.0
                :image/size [100 100]
                :image/background [:color/rgb 0 0 0]
                :image/nodes []})))))
