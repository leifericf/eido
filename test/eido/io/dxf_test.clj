(ns eido.io.dxf-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [eido.engine.compile :as compile]
    [eido.io.dxf :as dxf]))

(def base-scene
  {:image/size [400 400]
   :image/background [:color/rgb 255 255 255]
   :image/nodes []})

(defn- count-substring [s sub]
  (count (re-seq (re-pattern (java.util.regex.Pattern/quote sub)) s)))

;; --- rgb->aci ---

(deftest rgb->aci-primaries-test
  (testing "pure primaries map to expected ACI"
    (is (= 1 (dxf/rgb->aci [255 0 0])))
    (is (= 2 (dxf/rgb->aci [255 255 0])))
    (is (= 3 (dxf/rgb->aci [0 255 0])))
    (is (= 4 (dxf/rgb->aci [0 255 255])))
    (is (= 5 (dxf/rgb->aci [0 0 255])))
    (is (= 6 (dxf/rgb->aci [255 0 255])))
    (is (= 7 (dxf/rgb->aci [255 255 255])))))

(deftest rgb->aci-nearest-test
  (testing "near-primaries match to closest ACI"
    (is (= 1 (dxf/rgb->aci [250 10 10])) "near-red → ACI 1")
    (is (= 5 (dxf/rgb->aci [10 10 250])) "near-blue → ACI 5")
    (is (= 8 (dxf/rgb->aci [60 60 60])) "dark gray → ACI 8")))

;; --- DXF structure ---

(deftest single-rect-dxf-test
  (testing "single-rect scene emits valid R12 structure"
    (let [scene  (assoc base-scene :image/nodes
                   [{:node/type :shape/rect
                     :rect/xy [10 10] :rect/size [50 50]
                     :style/stroke {:color [:color/rgb 255 0 0] :width 1}}])
          ir     (compile/compile scene)
          out    (dxf/write-dxf ir {})]
      (is (str/includes? out "AC1009") "uses DXF R12 version")
      (is (str/includes? out "$INSUNITS") "declares unit metadata")
      (is (str/includes? out "POLYLINE"))
      (is (str/includes? out "SEQEND"))
      (is (str/ends-with? out "EOF\n"))
      (is (= 4 (count-substring out "VERTEX"))
          "closed rect emits 4 vertices (no repeat of first)")
      (is (str/includes? out "\n70\n1\n")
          "includes closed-flag 70 = 1"))))

(deftest two-color-dxf-test
  (testing "two stroke colors produce two layers with matching ACI codes"
    (let [scene  (assoc base-scene :image/nodes
                   [{:node/type :shape/rect
                     :rect/xy [10 10] :rect/size [50 50]
                     :style/stroke {:color [:color/rgb 255 0 0] :width 1}}
                    {:node/type :shape/circle
                     :circle/center [200 200] :circle/radius 30
                     :style/stroke {:color [:color/rgb 0 0 255] :width 1}}])
          ir     (compile/compile scene)
          out    (dxf/write-dxf ir {})]
      (is (str/includes? out "pen-255-0-0"))
      (is (str/includes? out "pen-0-0-255"))
      (is (>= (count-substring out "\nLAYER\n") 3)
          "LAYER keyword appears for TABLE header and each entry"))))

(deftest nil-stroke-dxf-test
  (testing "fill-only ops get a pen-none layer"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [50 50]
                    :style/fill [:color/rgb 200 200 200]}])
          ir    (compile/compile scene)
          out   (dxf/write-dxf ir {})]
      (is (str/includes? out "pen-none")))))

(deftest section-order-test
  (testing "HEADER before TABLES before ENTITIES before EOF"
    (let [scene  (assoc base-scene :image/nodes
                   [{:node/type :shape/rect
                     :rect/xy [10 10] :rect/size [50 50]
                     :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir     (compile/compile scene)
          out    (dxf/write-dxf ir {})
          hdr    (str/index-of out "HEADER")
          tbl    (str/index-of out "TABLES")
          ents   (str/index-of out "ENTITIES")
          eof    (str/index-of out "EOF")]
      (is (and hdr tbl ents eof))
      (is (< hdr tbl ents eof)))))

(deftest alpha-disambiguates-layer-names-test
  (testing "same RGB with different alpha produces unique layer names"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/rect
                    :rect/xy [10 10] :rect/size [20 20]
                    :style/stroke {:color [:color/rgba 255 0 0 1.0] :width 1}}
                   {:node/type :shape/rect
                    :rect/xy [50 50] :rect/size [20 20]
                    :style/stroke {:color [:color/rgba 255 0 0 0.5] :width 1}}])
          ir    (compile/compile scene)
          out   (dxf/write-dxf ir {})
          ;; Isolate the TABLES/LAYER section so geometry references don't
          ;; pollute the count.
          tables (second (re-find
                           #"(?s)SECTION\n2\nTABLES\n(.*?)ENDSEC" out))]
      (is (str/includes? out "pen-255-0-0-a50")
          "semi-transparent adds -a<percent> suffix")
      (is (= 1 (count-substring tables "\npen-255-0-0\n"))
          "exactly one bare-RGB layer entry")
      (is (= 1 (count-substring tables "\npen-255-0-0-a50\n"))
          "exactly one alpha-suffixed layer entry"))))

(deftest scale-opt-test
  (testing ":scale multiplies coordinates"
    (let [scene (assoc base-scene :image/nodes
                  [{:node/type :shape/line
                    :line/from [10 20] :line/to [30 40]
                    :style/stroke {:color [:color/rgb 0 0 0] :width 1}}])
          ir    (compile/compile scene)
          out   (dxf/write-dxf ir {:scale 2.0 :optimize-travel false})]
      (is (str/includes? out "20.000000") "10*2 = 20")
      (is (str/includes? out "40.000000") "20*2 = 40")
      (is (str/includes? out "60.000000") "30*2 = 60")
      (is (str/includes? out "80.000000") "40*2 = 80"))))
