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
