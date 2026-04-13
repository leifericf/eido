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

(deftest optimize-travel-drops-empty-polylines-test
  (testing "polylines with no first point are dropped, no crash"
    (is (= [] (polyline/optimize-travel-polylines [[]])))
    (is (= [] (polyline/optimize-travel-polylines [[] [] []])))
    (let [result (polyline/optimize-travel-polylines
                   [[[0 0] [1 1]] [] [[5 5] [6 6]]])]
      (is (= 2 (count result)) "empty polylines filtered out")
      (is (= [0 0] (first (first result))) "NN starts from origin"))))

(deftest optimize-travel-handles-nil-test
  (testing "nil input yields empty result"
    (is (= [] (polyline/optimize-travel-polylines nil)))))

;; --- transforms ---

(deftest translate-bakes-into-polyline-test
  (testing ":transform/translate moves polyline points"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [0 0] :rect/size [10 10]
                    :node/transform [[:transform/translate 50 60]]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          poly (first (:polylines (polyline/extract-polylines
                                    (compile-scene scene))))]
      (is (= [50.0 60.0] (first poly)))
      (is (= [60.0 60.0] (second poly)))
      (is (= [50.0 60.0] (last poly)) "closed rect still closes"))))

(deftest transform-on-group-propagates-test
  (testing "transforms on a group apply to child polylines"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :group
                    :node/transform [[:transform/translate 30 40]]
                    :group/children
                    [{:node/type :shape/rect
                      :rect/xy [0 0] :rect/size [10 10]
                      :style/stroke {:color [:color/rgb 0 0 0] :width 1}}]}])
          poly (first (:polylines (polyline/extract-polylines
                                    (compile-scene scene))))]
      (is (= [30.0 40.0] (first poly))))))

(deftest scale-bakes-into-polyline-test
  (testing ":transform/scale scales polyline points"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [0 0] :rect/size [10 10]
                    :node/transform [[:transform/scale 2 3]]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          poly (first (:polylines (polyline/extract-polylines
                                    (compile-scene scene))))]
      (is (= [0.0 0.0] (first poly)))
      (is (= [20.0 0.0] (second poly)) "x scaled by 2")
      (is (= [20.0 30.0] (nth poly 2)) "y scaled by 3"))))

(deftest opacity-zero-skipped-test
  (testing "shapes with :node/opacity 0 are excluded from polylines"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [30 30]
                    :node/opacity 0
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}
                   {:node/type :shape/rect
                    :rect/xy [50 50] :rect/size [30 30]
                    :node/opacity 1
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          result (polyline/extract-polylines (compile-scene scene))]
      (is (= 1 (count (:polylines result)))
          "only the opaque rect produces a polyline"))))

(deftest rotate-bakes-into-polyline-test
  (testing ":transform/rotate rotates polyline points (radians)"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/line
                    :line/from [0 0] :line/to [10 0]
                    :node/transform [[:transform/rotate (/ Math/PI 2)]]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          poly (first (:polylines (polyline/extract-polylines
                                    (compile-scene scene))))
          ;; cos(π/2) ≈ 0, sin(π/2) = 1 → (10, 0) → (~0, 10)
          [x y] (second poly)]
      (is (< (Math/abs (double x)) 1e-9))
      (is (< (Math/abs (- (double y) 10.0)) 1e-9)))))

;; --- drop reporting ---

(deftest dropped-absent-for-stroke-only-test
  (testing "stroke-only scene produces no :dropped key"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [50 50]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          result (polyline/extract-polylines (compile-scene scene))]
      (is (not (contains? result :dropped))))))

(deftest dropped-counts-fills-test
  (testing "fills are counted in :dropped"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [50 50]
                    :style/fill {:color [:color/rgb 200 0 0]}}
                   {:node/type :shape/circle
                    :circle/center [200 200] :circle/radius 30
                    :style/fill {:color [:color/rgb 0 200 0]}}])
          result (polyline/extract-polylines (compile-scene scene))]
      (is (= {:fills 2} (:dropped result))))))

(deftest dropped-also-on-grouped-test
  (testing "extract-grouped-polylines reports drops the same way"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [50 50]
                    :style/fill {:color [:color/rgb 200 0 0]}}])
          result (polyline/extract-grouped-polylines (compile-scene scene))]
      (is (= {:fills 1} (:dropped result))))))

(deftest summarize-drops-public-helper-test
  (testing "summarize-drops is a usable standalone helper"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/circle
                    :circle/center [200 200] :circle/radius 30
                    :style/fill {:color [:color/rgb 200 0 0]}}])
          ir (compile-scene scene)]
      (is (= {:fills 1} (polyline/summarize-drops ir))))))

;; --- clipping ---

(deftest clip-line-through-rect-test
  (testing "horizontal line clipped by narrow vertical rect"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :group
                    :group/clip {:node/type :shape/rect
                                 :rect/xy [40 0] :rect/size [20 400]}
                    :group/children
                    [{:node/type :shape/line
                      :line/from [0 50] :line/to [400 50]
                      :style/stroke {:color [:color/rgb 0 0 0] :width 1}}]}])
          polys (:polylines (polyline/extract-polylines (compile-scene scene)))]
      (is (= 1 (count polys)) "single line clipped to inside band")
      (let [[[x1 _] [x2 _]] (first polys)]
        (is (< (Math/abs (- (double x1) 40.0)) 1e-6))
        (is (< (Math/abs (- (double x2) 60.0)) 1e-6))))))

(deftest clip-line-entirely-inside-test
  (testing "line entirely inside the clip is preserved"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :group
                    :group/clip {:node/type :shape/rect
                                 :rect/xy [10 10] :rect/size [380 380]}
                    :group/children
                    [{:node/type :shape/line
                      :line/from [40 50] :line/to [60 50]
                      :style/stroke {:color [:color/rgb 0 0 0] :width 1}}]}])
          polys (:polylines (polyline/extract-polylines (compile-scene scene)))]
      (is (= 1 (count polys)))
      (is (= [[40.0 50.0] [60.0 50.0]] (first polys))))))

(deftest clip-line-entirely-outside-test
  (testing "line entirely outside the clip is dropped"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :group
                    :group/clip {:node/type :shape/rect
                                 :rect/xy [200 200] :rect/size [50 50]}
                    :group/children
                    [{:node/type :shape/line
                      :line/from [10 10] :line/to [40 40]
                      :style/stroke {:color [:color/rgb 0 0 0] :width 1}}]}])
          polys (:polylines (polyline/extract-polylines (compile-scene scene)))]
      (is (= 0 (count polys))))))

(deftest clip-by-circle-test
  (testing "rect outline clipped by smaller circle is empty (outline outside)"
    ;; A 50×50 rect's outline at (10,10)-(60,60) clipped by a circle
    ;; centered at (35,35) radius 5 — the circle is inside the rect's
    ;; interior, so the outline never enters it.
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :group
                    :group/clip {:node/type :shape/circle
                                 :circle/center [35 35] :circle/radius 5}
                    :group/children
                    [{:node/type :shape/rect
                      :rect/xy [10 10] :rect/size [50 50]
                      :style/stroke {:color [:color/rgb 0 0 0] :width 1}}]}])
          polys (:polylines (polyline/extract-polylines (compile-scene scene)))]
      (is (= 0 (count polys))))))

(deftest clip-circle-through-rect-test
  (testing "circle outline crossing a narrow band produces multiple arcs"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :group
                    :group/clip {:node/type :shape/rect
                                 :rect/xy [0 190] :rect/size [400 20]}
                    :group/children
                    [{:node/type :shape/circle
                      :circle/center [200 200] :circle/radius 100
                      :style/stroke {:color [:color/rgb 0 0 0] :width 1}}]}])
          polys (:polylines (polyline/extract-polylines (compile-scene scene)))]
      ;; A closed circle through a band cuts into ≥ 2 arcs.
      (is (>= (count polys) 2)))))

(deftest clip-by-arbitrary-path-test
  (testing "diagonal line clipped by diamond-shaped path"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :group
                    :group/clip {:node/type :shape/path
                                 :path/commands [[:move-to [200 100]]
                                                 [:line-to [300 200]]
                                                 [:line-to [200 300]]
                                                 [:line-to [100 200]]
                                                 [:close]]}
                    :group/children
                    [{:node/type :shape/line
                      :line/from [50 200] :line/to [350 200]
                      :style/stroke {:color [:color/rgb 0 0 0] :width 1}}]}])
          polys (:polylines (polyline/extract-polylines (compile-scene scene)))]
      (is (= 1 (count polys)))
      (let [[[x1 _] [x2 _]] (first polys)]
        (is (< (Math/abs (- (double x1) 100.0)) 1.0))
        (is (< (Math/abs (- (double x2) 300.0)) 1.0))))))

(deftest clip-respects-group-transforms-test
  (testing "transforms on a clipped group apply to clip and geometry alike"
    ;; Clip is a 20-wide rect at (40, 0); contents is a horizontal line
    ;; through y=50. Translating the entire group by (100, 0) should
    ;; move both — the visible portion ends up at x=140..160.
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :group
                    :node/transform [[:transform/translate 100 0]]
                    :group/clip {:node/type :shape/rect
                                 :rect/xy [40 0] :rect/size [20 400]}
                    :group/children
                    [{:node/type :shape/line
                      :line/from [0 50] :line/to [400 50]
                      :style/stroke {:color [:color/rgb 0 0 0] :width 1}}]}])
          polys (:polylines (polyline/extract-polylines (compile-scene scene)))]
      (is (= 1 (count polys)))
      (let [[[x1 _] [x2 _]] (first polys)]
        (is (< (Math/abs (- (double x1) 140.0)) 1e-6))
        (is (< (Math/abs (- (double x2) 160.0)) 1e-6))))))

;; --- segments edge cases ---

(deftest circle-with-degenerate-segments-test
  (testing "segments <= 2 is coerced to a minimum (3) rather than crashing"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/circle
                    :circle/center [200 200]
                    :circle/radius 50}])]
      (doseq [s [0 1 2 -3]]
        (let [result (polyline/extract-polylines (compile-scene scene)
                       {:segments s})
              poly   (first (:polylines result))]
          (is (some? poly) (str ":segments " s " still produces a polyline"))
          (is (>= (count poly) 4) "at least triangle + closing point")
          (is (every? some? poly) "no nil points"))))))

(deftest ellipse-with-degenerate-segments-test
  (testing "ellipse coerces segments to minimum"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/ellipse
                    :ellipse/center [200 200]
                    :ellipse/rx 80
                    :ellipse/ry 40}])
          result (polyline/extract-polylines (compile-scene scene)
                   {:segments 0})
          poly   (first (:polylines result))]
      (is (every? some? poly) "no nil points"))))
