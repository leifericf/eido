(ns eido.scene-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.core :as eido]
    [eido.scene :as scene])
  (:import
    [java.awt.image BufferedImage]))

;; --- grid tests ---

(deftest grid-basic-test
  (testing "produces correct col/row for each cell"
    (let [nodes (scene/grid 3 2 (fn [c r] {:col c :row r}))]
      (is (= 6 (count nodes)))
      (is (= {:col 0 :row 0} (first nodes)))
      (is (= {:col 2 :row 1} (last nodes))))))

(deftest grid-row-major-order-test
  (testing "visits cells in row-major order"
    (let [nodes (scene/grid 2 2 (fn [c r] [c r]))]
      (is (= [[0 0] [1 0] [0 1] [1 1]] nodes)))))

(deftest grid-nil-skip-test
  (testing "nil return from f skips that cell"
    (let [nodes (scene/grid 3 3 (fn [c r] (when (= c r) {:diag true})))]
      (is (= 3 (count nodes)))
      (is (every? #(= {:diag true} %) nodes)))))

(deftest grid-zero-dimensions-test
  (testing "zero cols or rows returns empty vector"
    (is (= [] (scene/grid 0 5 (fn [_ _] :x))))
    (is (= [] (scene/grid 5 0 (fn [_ _] :x))))))

;; --- distribute tests ---

(deftest distribute-basic-test
  (testing "distributes 3 points along horizontal line"
    (let [nodes (scene/distribute 3 [0 0] [100 0]
                  (fn [x y t] {:x x :y y :t t}))]
      (is (= 3 (count nodes)))
      (is (= {:x 0.0 :y 0.0 :t 0.0} (first nodes)))
      (is (= {:x 50.0 :y 0.0 :t 0.5} (second nodes)))
      (is (= {:x 100.0 :y 0.0 :t 1.0} (nth nodes 2))))))

(deftest distribute-single-test
  (testing "n=1 places at midpoint"
    (let [nodes (scene/distribute 1 [0 0] [100 100]
                  (fn [x y t] {:x x :y y :t t}))]
      (is (= 1 (count nodes)))
      (is (= {:x 50.0 :y 50.0 :t 0.5} (first nodes))))))

(deftest distribute-two-endpoints-test
  (testing "n=2 places at endpoints"
    (let [nodes (scene/distribute 2 [0 0] [0 100]
                  (fn [x y _t] {:x x :y y}))]
      (is (= [{:x 0.0 :y 0.0} {:x 0.0 :y 100.0}] nodes)))))

(deftest distribute-zero-test
  (testing "n=0 returns empty vector"
    (is (= [] (scene/distribute 0 [0 0] [100 100]
                (fn [x y t] {:x x}))))))

;; --- radial tests ---

(defn- approx=
  "Checks approximate equality for floating-point values."
  [expected actual tolerance]
  (< (Math/abs (- (double expected) (double actual))) tolerance))

(deftest radial-cardinal-test
  (testing "4 points produce cardinal positions starting from top"
    (let [nodes (scene/radial 4 100 100 50
                  (fn [x y _a] {:x x :y y}))]
      (is (= 4 (count nodes)))
      ;; top (12 o'clock)
      (is (approx= 100.0 (:x (nth nodes 0)) 0.01))
      (is (approx= 50.0 (:y (nth nodes 0)) 0.01))
      ;; right (3 o'clock)
      (is (approx= 150.0 (:x (nth nodes 1)) 0.01))
      (is (approx= 100.0 (:y (nth nodes 1)) 0.01))
      ;; bottom (6 o'clock)
      (is (approx= 100.0 (:x (nth nodes 2)) 0.01))
      (is (approx= 150.0 (:y (nth nodes 2)) 0.01))
      ;; left (9 o'clock)
      (is (approx= 50.0 (:x (nth nodes 3)) 0.01))
      (is (approx= 100.0 (:y (nth nodes 3)) 0.01)))))

(deftest radial-single-test
  (testing "n=1 places at top"
    (let [nodes (scene/radial 1 0 0 10
                  (fn [x y _a] {:x x :y y}))]
      (is (= 1 (count nodes)))
      (is (approx= 0.0 (:x (first nodes)) 0.01))
      (is (approx= -10.0 (:y (first nodes)) 0.01)))))

(deftest radial-zero-test
  (testing "n=0 returns empty vector"
    (is (= [] (scene/radial 0 0 0 10 (fn [x y a] :x))))))

(deftest radial-angle-passed-test
  (testing "angle parameter starts at 0 and increases"
    (let [nodes (scene/radial 4 0 0 10
                  (fn [_x _y a] {:angle a}))]
      (is (approx= 0.0 (:angle (nth nodes 0)) 0.01))
      (is (approx= (* 0.5 Math/PI) (:angle (nth nodes 1)) 0.01))
      (is (approx= Math/PI (:angle (nth nodes 2)) 0.01)))))

;; --- polygon tests ---

(deftest polygon-triangle-test
  (testing "creates a closed triangle path"
    (let [node (scene/polygon [[0 0] [100 0] [50 100]])]
      (is (= :shape/path (:node/type node)))
      (is (= [[:move-to [0 0]]
              [:line-to [100 0]]
              [:line-to [50 100]]
              [:close]]
             (:path/commands node))))))

(deftest polygon-empty-test
  (testing "empty points returns path with no commands"
    (let [node (scene/polygon [])]
      (is (= [] (:path/commands node))))))

(deftest triangle-test
  (testing "creates a triangle path from 3 points"
    (let [node (scene/triangle [0 0] [100 0] [50 80])]
      (is (= :shape/path (:node/type node)))
      (is (= 4 (count (:path/commands node)))))))

;; --- smooth-path tests ---

(deftest smooth-path-basic-test
  (testing "creates a smooth path through points"
    (let [node (scene/smooth-path [[0 0] [100 50] [200 0] [300 50]])]
      (is (= :shape/path (:node/type node)))
      (is (= :move-to (first (first (:path/commands node)))))
      ;; Should have curve-to commands
      (is (every? #{:move-to :curve-to} (map first (:path/commands node)))))))

(deftest smooth-path-two-points-test
  (testing "two points produce a simple line"
    (let [node (scene/smooth-path [[0 0] [100 100]])]
      (is (= [[:move-to [0 0]] [:line-to [100 100]]]
             (:path/commands node))))))

(deftest smooth-path-one-point-test
  (testing "one point produces just a move-to"
    (let [node (scene/smooth-path [[50 50]])]
      (is (= [[:move-to [50 50]]]
             (:path/commands node))))))

;; --- regular-polygon tests ---

(deftest regular-polygon-triangle-test
  (testing "n=3 produces a triangle"
    (let [node (scene/regular-polygon [100 100] 50 3)]
      (is (= :shape/path (:node/type node)))
      (is (= 4 (count (:path/commands node))))
      (is (= :move-to (first (first (:path/commands node)))))
      (is (= :close (first (last (:path/commands node))))))))

(deftest regular-polygon-hexagon-test
  (testing "n=6 produces a hexagon with 7 commands"
    (let [node (scene/regular-polygon [0 0] 10 6)]
      ;; move-to + 5 line-to + close = 7
      (is (= 7 (count (:path/commands node)))))))

(deftest regular-polygon-first-vertex-at-top-test
  (testing "first vertex is at the top (12 o'clock)"
    (let [node (scene/regular-polygon [100 100] 50 4)
          [_ [x y]] (first (:path/commands node))]
      (is (approx= 100.0 x 0.01))
      (is (approx= 50.0 y 0.01)))))

;; --- star tests ---

(deftest star-basic-test
  (testing "5-pointed star has 10 vertices"
    (let [node (scene/star [100 100] 50 25 5)]
      (is (= :shape/path (:node/type node)))
      ;; move-to + 9 line-to + close = 11
      (is (= 11 (count (:path/commands node)))))))

(deftest star-tip-at-top-test
  (testing "first tip is at the top"
    (let [node (scene/star [100 100] 50 25 5)
          [_ [x y]] (first (:path/commands node))]
      (is (approx= 100.0 x 0.01))
      (is (approx= 50.0 y 0.01)))))

(deftest star-inner-outer-radii-test
  (testing "alternates between outer and inner radii"
    (let [node (scene/star [0 0] 100 50 4)
          pts  (map second (:path/commands node))
          ;; Remove the :close command (has no point)
          pts  (butlast pts)
          dists (mapv (fn [[x y]] (Math/sqrt (+ (* x x) (* y y)))) pts)]
      ;; Even indices (0,2,4,6) should be at outer radius, odd at inner
      (is (approx= 100.0 (nth dists 0) 0.01))
      (is (approx= 50.0 (nth dists 1) 0.01))
      (is (approx= 100.0 (nth dists 2) 0.01))
      (is (approx= 50.0 (nth dists 3) 0.01)))))

;; --- integration: scene helpers → render ---

(defn- pixel-rgb
  [^BufferedImage img x y]
  (let [argb (.getRGB img x y)]
    [(bit-and (bit-shift-right argb 16) 0xFF)
     (bit-and (bit-shift-right argb 8) 0xFF)
     (bit-and argb 0xFF)]))

(deftest grid-render-integration-test
  (testing "grid output renders as a scene"
    (let [nodes (scene/grid 2 2
                  (fn [c r]
                    {:node/type :shape/rect
                     :rect/xy [(* c 50) (* r 50)]
                     :rect/size [40 40]
                     :style/fill {:color [:color/rgb 255 0 0]}}))
          scene {:image/size [200 200]
                 :image/background [:color/rgb 255 255 255]
                 :image/nodes nodes}
          img (eido/render scene)]
      (is (instance? BufferedImage img))
      (is (= [255 0 0] (pixel-rgb img 20 20))
          "first cell rect should be red")
      (is (= [255 255 255] (pixel-rgb img 45 45))
          "gap between cells should be background"))))

;; --- convenience helper tests ---

(deftest circle-node-test
  (testing "creates circle node without fill"
    (let [n (scene/circle-node [100 200] 50)]
      (is (= :shape/circle (:node/type n)))
      (is (= [100 200] (:circle/center n)))
      (is (= 50 (:circle/radius n)))
      (is (nil? (:style/fill n)))))
  (testing "creates circle node with fill"
    (let [n (scene/circle-node [100 200] 50 [:color/name "red"])]
      (is (= [:color/name "red"] (:style/fill n))))))

(deftest rect-node-test
  (testing "creates rect node"
    (let [n (scene/rect-node [10 20] [100 50] [:color/name "blue"])]
      (is (= :shape/rect (:node/type n)))
      (is (= [10 20] (:rect/xy n)))
      (is (= [100 50] (:rect/size n)))
      (is (= [:color/name "blue"] (:style/fill n))))))

(deftest line-node-test
  (testing "creates line node with stroke"
    (let [n (scene/line-node [0 0] [100 100] [:color/name "black"] 2)]
      (is (= :shape/line (:node/type n)))
      (is (= {:color [:color/name "black"] :width 2} (:style/stroke n))))))

(deftest polar->xy-test
  (testing "angle 0 gives point to the right"
    (let [[x y] (scene/polar->xy [100 100] 50 0)]
      (is (< (Math/abs (- x 150)) 0.01))
      (is (< (Math/abs (- y 100)) 0.01))))
  (testing "angle PI/2 gives point below"
    (let [[x y] (scene/polar->xy [100 100] 50 (/ Math/PI 2))]
      (is (< (Math/abs (- x 100)) 0.01))
      (is (< (Math/abs (- y 150)) 0.01)))))

(deftest ring-test
  (testing "creates n nodes in a circle"
    (let [nodes (scene/ring 4 [200 200] 100
                  {:node/type :shape/circle :circle/center [0 0] :circle/radius 10})]
      (is (= 4 (count nodes)))
      (is (every? #(:node/transform %) nodes)))))

(deftest points->path-test
  (testing "converts points to path commands"
    (let [cmds (scene/points->path [[0 0] [100 0] [100 100]])]
      (is (= :move-to (ffirst cmds)))
      (is (= 3 (count cmds)))))
  (testing "closed path adds :close"
    (let [cmds (scene/points->path [[0 0] [100 0] [100 100]] true)]
      (is (= :close (first (last cmds)))))))

;; --- with-margin ---

(deftest with-margin-test
  (testing "wraps nodes in clipped group"
    (let [scene {:image/size [400 300]
                 :image/background :white
                 :image/nodes [{:node/type :shape/circle
                                :circle/center [200 150]
                                :circle/radius 50}]}
          result (scene/with-margin scene 20)]
      (is (= 1 (count (:image/nodes result))))
      (is (= :group (:node/type (first (:image/nodes result)))))
      (is (some? (:group/clip (first (:image/nodes result)))))))
  (testing "clip rect is inset by margin"
    (let [scene {:image/size [400 300]
                 :image/background :white
                 :image/nodes []}
          result (scene/with-margin scene 30)
          clip (:group/clip (first (:image/nodes result)))]
      (is (= [30.0 30.0] (:rect/xy clip)))
      (is (= [340.0 240.0] (:rect/size clip))))))

;; --- paper ---

(deftest paper-test
  (testing "returns base scene map for A4"
    (let [p (scene/paper :a4)]
      (is (= [21.0 29.7] (:image/size p)))
      (is (= :cm (:image/units p)))
      (is (= 300 (:image/dpi p)))))
  (testing "landscape swaps dimensions"
    (let [p (scene/paper :a4 :landscape true)]
      (is (= [29.7 21.0] (:image/size p)))))
  (testing "custom DPI overrides default"
    (let [p (scene/paper :a4 :dpi 150)]
      (is (= 150 (:image/dpi p)))))
  (testing "letter size returns inches"
    (let [p (scene/paper :letter)]
      (is (= [8.5 11.0] (:image/size p)))
      (is (= :in (:image/units p))))))

;; --- with-units ---

(deftest with-units-size-test
  (testing "converts cm size to pixels at 300 DPI"
    (let [scene {:image/size [10.0 20.0]
                 :image/units :cm
                 :image/dpi 300
                 :image/background :white
                 :image/nodes []}
          result (scene/with-units scene)
          [w h]  (:image/size result)
          factor (/ 300 2.54)]
      (is (== (Math/round (* 10.0 factor)) w))
      (is (== (Math/round (* 20.0 factor)) h))))
  (testing "converts mm size to pixels"
    (let [scene {:image/size [100.0 200.0]
                 :image/units :mm
                 :image/dpi 300
                 :image/background :white
                 :image/nodes []}
          result (scene/with-units scene)
          [w h]  (:image/size result)
          factor (/ 300 25.4)]
      (is (== (Math/round (* 100.0 factor)) w))
      (is (== (Math/round (* 200.0 factor)) h))))
  (testing "converts inch size to pixels"
    (let [scene {:image/size [8.5 11.0]
                 :image/units :in
                 :image/dpi 300
                 :image/background :white
                 :image/nodes []}
          result (scene/with-units scene)]
      (is (= [2550 3300] (:image/size result))))))

(deftest with-units-strips-units-key-test
  (testing "removes :image/units, retains :image/dpi"
    (let [result (scene/with-units
                   {:image/size [10.0 10.0]
                    :image/units :cm
                    :image/dpi 300
                    :image/background :white
                    :image/nodes []})]
      (is (nil? (:image/units result)))
      (is (= 300 (:image/dpi result))))))

(deftest with-units-point-keys-test
  (testing "scales circle center and radius"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :shape/circle
                   :circle/center [5.0 5.0]
                   :circle/radius 2.0}]}
          result (scene/with-units scene)
          node   (first (:image/nodes result))]
      (is (= [500.0 500.0] (:circle/center node)))
      (is (= 200.0 (:circle/radius node))))))

(deftest with-units-rect-test
  (testing "scales rect position and size"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [1.0 2.0]
                   :rect/size [3.0 4.0]
                   :rect/corner-radius 0.5}]}
          result (scene/with-units scene)
          node   (first (:image/nodes result))]
      (is (= [100.0 200.0] (:rect/xy node)))
      (is (= [300.0 400.0] (:rect/size node)))
      (is (= 50.0 (:rect/corner-radius node))))))

(deftest with-units-preserves-non-spatial-test
  (testing "opacity and colors are not scaled"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background [:color/rgb 255 128 0]
                 :image/nodes
                 [{:node/type :shape/circle
                   :circle/center [5.0 5.0]
                   :circle/radius 2.0
                   :node/opacity 0.7
                   :style/fill [:color/rgb 200 0 0]}]}
          result (scene/with-units scene)
          node   (first (:image/nodes result))]
      (is (= 0.7 (:node/opacity node)))
      (is (= [:color/rgb 200 0 0] (:style/fill node)))
      (is (= [:color/rgb 255 128 0] (:image/background result))))))

(deftest with-units-stroke-test
  (testing "scales stroke width and dash"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :shape/line
                   :line/from [1.0 1.0]
                   :line/to [9.0 9.0]
                   :style/stroke {:color [:color/rgb 0 0 0]
                                  :width 0.1
                                  :dash [0.5 0.2]}}]}
          result (scene/with-units scene)
          node   (first (:image/nodes result))
          stroke (:style/stroke node)]
      (is (= [100.0 100.0] (:line/from node)))
      (is (= [900.0 900.0] (:line/to node)))
      (is (= 10.0 (:width stroke)))
      (is (= [50.0 20.0] (:dash stroke)))
      (is (= [:color/rgb 0 0 0] (:color stroke))))))

(deftest with-units-path-commands-test
  (testing "scales path command points"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :shape/path
                   :path/commands [[:move-to [1.0 2.0]]
                                   [:line-to [3.0 4.0]]
                                   [:curve-to [5.0 5.0] [6.0 6.0] [7.0 8.0]]
                                   [:close]]}]}
          result (scene/with-units scene)
          cmds   (:path/commands (first (:image/nodes result)))]
      (is (= [:move-to [100.0 200.0]] (nth cmds 0)))
      (is (= [:line-to [300.0 400.0]] (nth cmds 1)))
      (is (= [:curve-to [500.0 500.0] [600.0 600.0] [700.0 800.0]] (nth cmds 2)))
      (is (= [:close] (nth cmds 3))))))

(deftest with-units-transform-test
  (testing "scales translate but not rotate or scale"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :group
                   :node/transform [[:transform/translate 2.0 3.0]
                                    [:transform/rotate 1.5]
                                    [:transform/scale 2.0 2.0]]
                   :group/children
                   [{:node/type :shape/circle
                     :circle/center [1.0 1.0]
                     :circle/radius 0.5}]}]}
          result (scene/with-units scene)
          node   (first (:image/nodes result))
          xforms (:node/transform node)
          child  (first (:group/children node))]
      (is (= [:transform/translate 200.0 300.0] (nth xforms 0)))
      (is (= [:transform/rotate 1.5] (nth xforms 1)))
      (is (= [:transform/scale 2.0 2.0] (nth xforms 2)))
      (is (= [100.0 100.0] (:circle/center child)))
      (is (= 50.0 (:circle/radius child))))))

(deftest with-units-nested-groups-test
  (testing "recurses into nested groups"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :group
                   :group/children
                   [{:node/type :group
                     :group/children
                     [{:node/type :shape/circle
                       :circle/center [5.0 5.0]
                       :circle/radius 1.0}]}]}]}
          result (scene/with-units scene)
          inner  (-> result :image/nodes first :group/children first
                     :group/children first)]
      (is (= [500.0 500.0] (:circle/center inner)))
      (is (= 100.0 (:circle/radius inner))))))

(deftest with-units-font-size-test
  (testing "scales font size"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :shape/text
                   :text/content "Hello"
                   :text/font {:font/family "SansSerif" :font/size 0.5}
                   :text/origin [1.0 2.0]}]}
          result (scene/with-units scene)
          node   (first (:image/nodes result))]
      (is (= [100.0 200.0] (:text/origin node)))
      (is (= 50.0 (get-in node [:text/font :font/size]))))))

(deftest with-units-bounds-keys-test
  (testing "scales bounds [x y w h]"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :flow-field
                   :flow/bounds [0.0 0.0 10.0 10.0]}]}
          result (scene/with-units scene)
          node   (first (:image/nodes result))]
      (is (= [0.0 0.0 1000.0 1000.0] (:flow/bounds node))))))

(deftest with-units-effect-test
  (testing "scales shadow dx/dy/blur but not opacity"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [1.0 1.0]
                   :rect/size [5.0 5.0]
                   :effect/shadow {:dx 0.1 :dy 0.1 :blur 0.2
                                   :color [:color/rgb 0 0 0] :opacity 0.5}}]}
          result (scene/with-units scene)
          shadow (:effect/shadow (first (:image/nodes result)))]
      (is (= 10.0 (:dx shadow)))
      (is (= 10.0 (:dy shadow)))
      (is (= 20.0 (:blur shadow)))
      (is (= 0.5 (:opacity shadow)))
      (is (= [:color/rgb 0 0 0] (:color shadow))))))

(deftest with-units-scatter-test
  (testing "scales scatter positions and jitter"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :scatter
                   :scatter/shape {:node/type :shape/circle
                                   :circle/center [0 0]
                                   :circle/radius 0.1}
                   :scatter/positions [[1.0 2.0] [3.0 4.0]]
                   :scatter/jitter 0.5}]}
          result (scene/with-units scene)
          node   (first (:image/nodes result))]
      (is (= [[100.0 200.0] [300.0 400.0]] (:scatter/positions node)))
      (is (= 50.0 (:scatter/jitter node)))
      (is (= 10.0 (:circle/radius (:scatter/shape node)))))))

(deftest with-units-gradient-fill-test
  (testing "scales gradient spatial values inside fill"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/xy [0.0 0.0]
                   :rect/size [10.0 10.0]
                   :style/fill {:gradient/type :linear
                                :gradient/from [0.0 0.0]
                                :gradient/to [10.0 10.0]
                                :gradient/stops [[0 [:color/rgb 0 0 0]]
                                                 [1 [:color/rgb 255 255 255]]]}}]}
          result (scene/with-units scene)
          fill   (:style/fill (first (:image/nodes result)))]
      (is (= [0.0 0.0] (:gradient/from fill)))
      (is (= [1000.0 1000.0] (:gradient/to fill)))
      (is (= [[0 [:color/rgb 0 0 0]] [1 [:color/rgb 255 255 255]]]
             (:gradient/stops fill))))))

(deftest with-units-group-clip-test
  (testing "scales clip shape inside group"
    (let [scene {:image/size [10.0 10.0]
                 :image/units :in
                 :image/dpi 100
                 :image/background :white
                 :image/nodes
                 [{:node/type :group
                   :group/clip {:node/type :shape/rect
                                :rect/xy [1.0 1.0]
                                :rect/size [8.0 8.0]}
                   :group/children []}]}
          result (scene/with-units scene)
          clip   (:group/clip (first (:image/nodes result)))]
      (is (= [100.0 100.0] (:rect/xy clip)))
      (is (= [800.0 800.0] (:rect/size clip))))))
