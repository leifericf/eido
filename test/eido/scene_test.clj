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
