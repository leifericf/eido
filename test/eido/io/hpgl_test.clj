(ns eido.io.hpgl-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [eido.engine.compile :as compile]
    [eido.io.hpgl :as hpgl]))

(def base-scene
  {:image/size [400 400]
   :image/background [:color/rgb 255 255 255]
   :image/nodes []})

(defn- count-substring [s sub]
  (count (re-seq (re-pattern (java.util.regex.Pattern/quote sub)) s)))

(deftest header-footer-test
  (testing "HPGL preamble (IN, PA) and pen-park footer (PU0,0; SP0;)"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [50 50]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir    (compile/compile scene)
          out   (hpgl/write-hpgl ir {})]
      (is (str/includes? out "IN;") "initialize plotter")
      (is (str/includes? out "PA;") "absolute coordinates")
      (is (str/includes? out "PU0,0;") "park pen at origin")
      (is (str/includes? out "SP0;") "deselect pen at end"))))

(deftest pen-numbering-per-color-test
  (testing "each unique stroke color gets a sequential SP pen number"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [30 30]
                    :style/stroke {:color [:color/rgb 255 0 0] :width 1}}
                   {:node/type :shape/rect
                    :rect/xy [100 100] :rect/size [30 30]
                    :style/stroke {:color [:color/rgb 0 0 255] :width 1}}])
          ir    (compile/compile scene)
          out   (hpgl/write-hpgl ir {})]
      (is (str/includes? out "SP1;"))
      (is (str/includes? out "SP2;")))))

(deftest pen-up-down-pattern-test
  (testing "each polyline emits a PU then PD"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/line
                    :line/from [10 10] :line/to [50 50]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir    (compile/compile scene)
          out   (hpgl/write-hpgl ir {})]
      (is (re-find #"PU\d+,\d+;PD\d+,\d+;" out)
          "single line emits PU<x,y>;PD<x,y>;"))))

(deftest y-flip-test
  (testing "Y is flipped relative to bounds height"
    ;; 400-tall scene, default scale 40 → rect at y=10..60 in scene
    ;; flips to y=390..340 in scene units, then ×40 plotter units.
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [50 50]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir    (compile/compile scene)
          out   (hpgl/write-hpgl ir {:optimize-travel false :scale 1})]
      (is (re-find #"\b390[;,]" out) "scene y=10 flipped to y=390 at scale 1")
      (is (re-find #"\b340[;,]" out) "scene y=60 flipped to y=340 at scale 1"))))

(deftest scale-multiplies-coords-test
  (testing ":scale multiplies coordinates"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/line
                    :line/from [10 10] :line/to [20 20]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir    (compile/compile scene)
          out   (hpgl/write-hpgl ir {:scale 10 :optimize-travel false})]
      ;; x=10 ×10 = 100; x=20 ×10 = 200
      (is (str/includes? out "PU100,") "scaled start x")
      (is (str/includes? out "200,") "scaled end x"))))

(deftest optimize-travel-preserves-pen-down-count-test
  (testing "travel optimization preserves PD count (only reorders polylines)"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/line
                    :line/from [10 10] :line/to [20 20]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}
                   {:node/type :shape/line
                    :line/from [100 100] :line/to [200 200]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir    (compile/compile scene)
          opt   (hpgl/write-hpgl ir {:optimize-travel true})
          plain (hpgl/write-hpgl ir {:optimize-travel false})]
      (is (= (count-substring opt "PD")
             (count-substring plain "PD"))))))

(deftest no-opts-arity-test
  (testing "single-arity write-hpgl works"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [50 50]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir    (compile/compile scene)]
      (is (string? (hpgl/write-hpgl ir))))))

(deftest empty-scene-test
  (testing "empty scene produces well-formed (header + footer only) HPGL"
    (let [ir (compile/compile base-scene)
          out (hpgl/write-hpgl ir {})]
      (is (str/includes? out "IN;"))
      (is (str/includes? out "SP0;"))
      (is (zero? (count-substring out "SP1;"))
          "no pen change for empty scene"))))
