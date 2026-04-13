(ns eido.io.gcode-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [eido.engine.compile :as compile]
    [eido.io.gcode :as gcode]))

(def base-scene
  {:image/size [400 400]
   :image/background [:color/rgb 255 255 255]
   :image/nodes []})

(defn- count-substring [s sub]
  (count (re-seq (re-pattern (java.util.regex.Pattern/quote sub)) s)))

(deftest header-footer-test
  (testing "GRBL preamble and parking footer appear"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [50 50]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir    (compile/compile scene)
          out   (gcode/write-gcode ir {})]
      (is (str/includes? out "G21"))
      (is (str/includes? out "G90"))
      (is (str/includes? out "G17"))
      (is (str/includes? out "G0 X0 Y0")
          "footer parks at origin"))))

(deftest single-rect-moves-test
  (testing "closed rect emits four G1 X Y drawing moves"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [50 50]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir    (compile/compile scene)
          out   (gcode/write-gcode ir {})]
      (is (= 4 (count-substring out "G1 X"))
          "four drawing moves for a 4-corner closed rect"))))

(deftest y-axis-flip-test
  (testing "Y coordinates are flipped relative to bounds height"
    ;; rect at [10 10] size [50 50] in a 400-tall scene.
    ;; y=10 flips to y=390; y=60 flips to y=340. No raw y=10 should
    ;; remain in output.
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [50 50]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir    (compile/compile scene)
          out   (gcode/write-gcode ir {:optimize-travel false})]
      (is (str/includes? out "Y390.0000"))
      (is (str/includes? out "Y340.0000"))
      (is (not (str/includes? out "Y10.0000"))
          "no raw y=10 remains after flip"))))

(deftest tool-change-between-groups-test
  (testing "M0 operator pause appears once per color group"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [30 30]
                    :style/stroke {:color [:color/rgb 255 0 0] :width 1}}
                   {:node/type :shape/rect
                    :rect/xy [100 100] :rect/size [30 30]
                    :style/stroke {:color [:color/rgb 0 0 255] :width 1}}])
          ir    (compile/compile scene)
          out   (gcode/write-gcode ir {})]
      (is (= 2 (count-substring out "M0"))
          "one M0 per color group")
      (is (str/includes? out "pen-rgb-255-0-0"))
      (is (str/includes? out "pen-rgb-0-0-255")))))

(deftest spindle-mode-test
  (testing "M3 by default; M4 when :laser-mode true"
    (let [scene  (assoc base-scene :image/nodes
                   [{:node/type :shape/line
                     :line/from [10 10] :line/to [30 30]
                     :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir     (compile/compile scene)
          out-m3 (gcode/write-gcode ir {})
          out-m4 (gcode/write-gcode ir {:laser-mode true})]
      (is (str/includes? out-m3 "M3 S1000"))
      (is (not (str/includes? out-m3 "M4 S")))
      (is (str/includes? out-m4 "M4 S1000"))
      (is (not (str/includes? out-m4 "M3 S"))))))

(deftest optimize-travel-preserves-move-count-test
  (testing "travel optimization preserves XY drawing move count"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/line
                    :line/from [10 10] :line/to [20 20]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}
                   {:node/type :shape/line
                    :line/from [100 100] :line/to [200 200]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir    (compile/compile scene)
          opt   (gcode/write-gcode ir {:optimize-travel true})
          plain (gcode/write-gcode ir {:optimize-travel false})]
      (is (= (count-substring opt "G1 X")
             (count-substring plain "G1 X"))))))
