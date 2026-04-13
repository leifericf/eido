(ns eido.io.polyline-test
  (:require
    [clojure.edn :as edn]
    [clojure.test :refer [deftest is testing]]
    [eido.engine.compile :as compile]
    [eido.io.polyline :as polyline]))

(defn- compile-scene [scene]
  (compile/compile scene))

(def base-scene
  {:image/size [400 400]
   :image/background [:color/rgb 255 255 255]
   :image/nodes []})

;; --- rect ---

(deftest rect-polyline-test
  (testing "rect produces closed 4-corner polyline"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [50 60]
                    :rect/size [100 80]}])
          result (polyline/extract-polylines (compile-scene scene) {})
          polys  (:polylines result)]
      (is (= 1 (count polys)))
      (is (= 5 (count (first polys))) "closed rect has 5 points (start repeated)")
      (is (= (first (first polys)) (last (first polys))) "first = last for closed")
      (is (= [50 60] (mapv int (first (first polys))))))))

;; --- circle ---

(deftest circle-polyline-test
  (testing "circle produces closed polygon"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/circle
                    :circle/center [200 200]
                    :circle/radius 50}])
          result (polyline/extract-polylines (compile-scene scene) {:segments 32})
          polys  (:polylines result)]
      (is (= 1 (count polys)))
      (is (= 33 (count (first polys))) "32 segments + closing point")
      (is (= (first (first polys)) (last (first polys)))))))

;; --- line ---

(deftest line-polyline-test
  (testing "line produces 2-point polyline"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/line
                    :line/from [10 20]
                    :line/to [300 400]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          result (polyline/extract-polylines (compile-scene scene) {})
          polys  (:polylines result)]
      (is (= 1 (count polys)))
      (is (= 2 (count (first polys))))
      (is (= [10 20] (first (first polys))))
      (is (= [300 400] (second (first polys)))))))

;; --- path with curves ---

(deftest path-polyline-test
  (testing "path with curves is flattened to line segments"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/path
                    :path/commands [[:move-to [0 0]]
                                    [:curve-to [50 100] [150 100] [200 0]]
                                    [:close]]}])
          result (polyline/extract-polylines (compile-scene scene) {:flatness 1.0})
          polys  (:polylines result)]
      (is (= 1 (count polys)))
      (is (> (count (first polys)) 3) "curve should produce many points"))))

;; --- multiple shapes ---

(deftest multiple-shapes-test
  (testing "multiple shapes produce multiple polylines"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10]
                    :rect/size [50 50]}
                   {:node/type :shape/circle
                    :circle/center [200 200]
                    :circle/radius 30}
                   {:node/type :shape/line
                    :line/from [0 0]
                    :line/to [400 400]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          result (polyline/extract-polylines (compile-scene scene) {})
          polys  (:polylines result)]
      (is (= 3 (count polys))))))

;; --- ellipse ---

(deftest ellipse-polyline-test
  (testing "ellipse produces closed polygon"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/ellipse
                    :ellipse/center [200 200]
                    :ellipse/rx 80
                    :ellipse/ry 40}])
          result (polyline/extract-polylines (compile-scene scene) {:segments 32})
          polys  (:polylines result)]
      (is (= 1 (count polys)))
      (is (= 33 (count (first polys))) "32 segments + closing point")
      (let [poly (first polys)]
        (is (< (Math/abs (- (ffirst poly) (first (last poly)))) 0.01)
            "first and last x should match")))))

;; --- arc ---

(deftest arc-polyline-test
  (testing "arc produces polyline"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/arc
                    :arc/center [200 200]
                    :arc/rx 80
                    :arc/ry 80
                    :arc/start 0
                    :arc/extent 180}])
          result (polyline/extract-polylines (compile-scene scene) {:segments 32})
          polys  (:polylines result)]
      (is (= 1 (count polys)))
      (is (= 33 (count (first polys))) "32 segments + endpoint"))))

;; --- bounds ---

(deftest bounds-test
  (testing "bounds matches scene size"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [0 0]
                    :rect/size [10 10]}])
          result (polyline/extract-polylines (compile-scene scene) {})]
      (is (= [400 400] (:bounds result))))))

;; --- EDN serialization ---

(deftest edn-roundtrip-test
  (testing "polylines serialize and deserialize via EDN"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 20]
                    :rect/size [100 200]}])
          result  (polyline/extract-polylines (compile-scene scene) {})
          edn-str (polyline/polylines->edn result)
          parsed  (edn/read-string edn-str)]
      (is (string? edn-str))
      (is (= (:bounds result) (:bounds parsed)))
      (is (= (count (:polylines result)) (count (:polylines parsed)))))))

;; --- group/buffer ---

(deftest group-polyline-test
  (testing "groups produce polylines from child ops"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :group
                    :group/children
                    [{:node/type :shape/rect
                      :rect/xy [0 0]
                      :rect/size [50 50]}
                     {:node/type :shape/circle
                      :circle/center [100 100]
                      :circle/radius 20}]}])
          result (polyline/extract-polylines (compile-scene scene) {})
          polys  (:polylines result)]
      (is (= 2 (count polys))))))

;; --- grouped polylines ---

(deftest grouped-single-color-test
  (testing "single stroke color produces one group"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10]
                    :rect/size [50 50]
                    :style/stroke {:color [:color/rgb 255 0 0] :width 1}}
                   {:node/type :shape/circle
                    :circle/center [200 200]
                    :circle/radius 30
                    :style/stroke {:color [:color/rgb 255 0 0] :width 2}}])
          result (polyline/extract-grouped-polylines (compile-scene scene) {})
          groups (:groups result)]
      (is (= 1 (count groups)) "same color = one group regardless of width")
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (:stroke (first groups))))
      (is (= 2 (count (:polylines (first groups))))))))

(deftest grouped-multi-color-test
  (testing "distinct stroke colors produce distinct groups"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10]
                    :rect/size [50 50]
                    :style/stroke {:color [:color/rgb 255 0 0] :width 1}}
                   {:node/type :shape/circle
                    :circle/center [200 200]
                    :circle/radius 30
                    :style/stroke {:color [:color/rgb 0 0 255] :width 1}}])
          result (polyline/extract-grouped-polylines (compile-scene scene) {})
          groups (:groups result)
          strokes (set (map :stroke groups))]
      (is (= 2 (count groups)))
      (is (contains? strokes {:r 255 :g 0 :b 0 :a 1.0}))
      (is (contains? strokes {:r 0 :g 0 :b 255 :a 1.0})))))

(deftest grouped-nil-stroke-test
  (testing "ops without stroke color go into a nil-stroke group"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10]
                    :rect/size [50 50]
                    :style/fill [:color/rgb 200 200 200]}])
          result (polyline/extract-grouped-polylines (compile-scene scene) {})
          groups (:groups result)]
      (is (= 1 (count groups)))
      (is (nil? (:stroke (first groups))))
      (is (= 1 (count (:polylines (first groups))))))))

(deftest grouped-bounds-test
  (testing "bounds matches scene size"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [0 0]
                    :rect/size [10 10]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          result (polyline/extract-grouped-polylines (compile-scene scene) {})]
      (is (= [400 400] (:bounds result))))))

(deftest grouped-preserves-order-test
  (testing "groups appear in first-seen stroke order"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [0 0] :rect/size [10 10]
                    :style/stroke {:color [:color/rgb 0 0 255] :width 1}}
                   {:node/type :shape/rect
                    :rect/xy [20 20] :rect/size [10 10]
                    :style/stroke {:color [:color/rgb 255 0 0] :width 1}}
                   {:node/type :shape/rect
                    :rect/xy [40 40] :rect/size [10 10]
                    :style/stroke {:color [:color/rgb 0 0 255] :width 1}}])
          result (polyline/extract-grouped-polylines (compile-scene scene) {})
          groups (:groups result)]
      (is (= {:r 0 :g 0 :b 255 :a 1.0} (:stroke (first groups)))
          "blue appears first")
      (is (= 2 (count (:polylines (first groups)))) "blue has two rects")
      (is (= {:r 255 :g 0 :b 0 :a 1.0} (:stroke (second groups)))))))

;; --- travel optimization ---

(deftest optimize-travel-noop-test
  (testing "empty and single-polyline inputs pass through"
    (is (= [] (polyline/optimize-travel-polylines [])))
    (is (= [[[0 0] [1 1]]]
           (polyline/optimize-travel-polylines [[[0 0] [1 1]]])))))

(deftest optimize-travel-reorders-test
  (testing "greedy NN reorders from origin"
    ;; Three polylines starting at (100 0), (10 0), (50 0).
    ;; From [0 0], NN order is (10) → (50) → (100).
    (let [polys  [[[100 0] [101 0]]
                  [[10 0]  [11 0]]
                  [[50 0]  [51 0]]]
          result (polyline/optimize-travel-polylines polys)]
      (is (= [10 0]  (first (nth result 0))))
      (is (= [50 0]  (first (nth result 1))))
      (is (= [100 0] (first (nth result 2)))))))

(deftest optimize-travel-preserves-count-test
  (testing "optimize preserves polyline count and contents"
    (let [polys  [[[3 3] [4 4]] [[1 1] [2 2]] [[5 5] [6 6]]]
          result (polyline/optimize-travel-polylines polys)]
      (is (= (count polys) (count result)))
      (is (= (set polys) (set result))))))
