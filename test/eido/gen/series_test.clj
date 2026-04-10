(ns eido.gen.series-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [eido.gen.series :as series]))

;; --- edition-seed ---

(deftest edition-seed-determinism-test
  (testing "same inputs produce same seed"
    (is (= (series/edition-seed 12345 0)
           (series/edition-seed 12345 0)))))

(deftest edition-seed-different-editions-test
  (testing "different editions produce different seeds"
    (is (not= (series/edition-seed 12345 0)
              (series/edition-seed 12345 1)))))

(deftest edition-seed-different-masters-test
  (testing "different master seeds produce different edition seeds"
    (is (not= (series/edition-seed 100 5)
              (series/edition-seed 200 5)))))

(deftest edition-seed-uncorrelated-test
  (testing "nearby editions produce uncorrelated seeds"
    (let [seeds (mapv #(series/edition-seed 42 %) (range 10))]
      ;; Check that consecutive seeds differ significantly
      (is (> (count (set seeds)) 9)))))

;; --- series-params ---

(def test-spec
  {:density {:type :uniform :lo 10.0 :hi 50.0}
   :weight  {:type :gaussian :mean 5.0 :sd 1.0}
   :shape   {:type :choice :options [:circle :square :triangle]}
   :fancy   {:type :boolean :probability 0.3}})

(deftest series-params-determinism-test
  (testing "same inputs produce same params"
    (is (= (series/series-params test-spec 42 0)
           (series/series-params test-spec 42 0)))))

(deftest series-params-all-keys-present-test
  (testing "output contains all spec keys"
    (let [params (series/series-params test-spec 42 0)]
      (is (every? #(contains? params %) (keys test-spec))))))

(deftest series-params-uniform-range-test
  (testing "uniform values within [lo, hi)"
    (let [params (series/series-params test-spec 42 0)]
      (is (>= (:density params) 10.0))
      (is (< (:density params) 50.0)))))

(deftest series-params-choice-valid-test
  (testing "choice selects from options"
    (let [params (series/series-params test-spec 42 0)]
      (is (some #{(:shape params)} [:circle :square :triangle])))))

(deftest series-params-boolean-test
  (testing "boolean returns true or false"
    (let [params (series/series-params test-spec 42 0)]
      (is (boolean? (:fancy params))))))

(deftest series-params-different-editions-test
  (testing "different editions produce different params"
    (is (not= (series/series-params test-spec 42 0)
              (series/series-params test-spec 42 1)))))

;; --- series-range ---

(deftest series-range-count-test
  (testing "produces correct number of param maps"
    (is (= 10 (count (series/series-range test-spec 42 0 10))))))

(deftest series-range-determinism-test
  (testing "same inputs produce same sequence"
    (is (= (series/series-range test-spec 42 0 5)
           (series/series-range test-spec 42 0 5)))))

;; --- weighted-choice spec ---

(def spec-with-weights
  {:tier {:type :weighted-choice
          :options [:common :rare :epic]
          :weights [5 3 1]}})

(deftest series-params-weighted-choice-test
  (testing "weighted-choice selects from options"
    (let [params (series/series-params spec-with-weights 42 0)]
      (is (some #{(:tier params)} [:common :rare :epic])))))

;; --- seed-grid ---

(defn- simple-scene [params _edition]
  {:image/size [40 40]
   :image/background [:color/hsl (get params :hue 0) 0.5 0.5]
   :image/nodes []})

(deftest seed-grid-test
  (testing "returns a BufferedImage"
    (let [result (series/seed-grid
                   {:spec {:hue {:type :uniform :lo 0.0 :hi 360.0}}
                    :master-seed 42
                    :start 0 :end 4
                    :scene-fn simple-scene
                    :cols 2
                    :thumb-size [40 40]})]
      (is (instance? java.awt.image.BufferedImage result))))
  (testing "grid dimensions match cols x rows x thumb-size"
    (let [result (series/seed-grid
                   {:spec {:hue {:type :uniform :lo 0.0 :hi 360.0}}
                    :master-seed 42
                    :start 0 :end 6
                    :scene-fn simple-scene
                    :cols 3
                    :thumb-size [40 40]})]
      (is (= 120 (.getWidth result)))   ;; 3 cols x 40
      (is (= 80 (.getHeight result))))) ;; 2 rows x 40
  (testing "deterministic output"
    (let [opts {:spec {:hue {:type :uniform :lo 0.0 :hi 360.0}}
                :master-seed 42 :start 0 :end 4
                :scene-fn simple-scene :cols 2 :thumb-size [40 40]}
          a (series/seed-grid opts)
          b (series/seed-grid opts)
          pixels-a (.getRGB a 0 0 (.getWidth a) (.getHeight a) nil 0 (.getWidth a))
          pixels-b (.getRGB b 0 0 (.getWidth b) (.getHeight b) nil 0 (.getWidth b))]
      (is (java.util.Arrays/equals ^ints pixels-a ^ints pixels-b)))))

;; --- param-grid ---

(deftest param-grid-test
  (testing "returns a BufferedImage"
    (let [result (series/param-grid
                   {:base-params {:hue 180}
                    :row-param {:key :hue :values [0 90 180 270]}
                    :col-param {:key :hue :values [0 120 240]}
                    :seed 42
                    :scene-fn (fn [params] (simple-scene params nil))
                    :thumb-size [40 40]})]
      (is (instance? java.awt.image.BufferedImage result))))
  (testing "grid dimensions match rows x cols x thumb-size"
    (let [result (series/param-grid
                   {:base-params {:hue 180}
                    :row-param {:key :hue :values [0 90 180]}
                    :col-param {:key :hue :values [0 120]}
                    :seed 42
                    :scene-fn (fn [params] (simple-scene params nil))
                    :thumb-size [40 40]})]
      (is (= 80 (.getWidth result)))    ;; 2 cols x 40
      (is (= 120 (.getHeight result))))) ;; 3 rows x 40
  (testing "single-axis sweep (row only)"
    (let [result (series/param-grid
                   {:base-params {:hue 180}
                    :row-param {:key :hue :values [0 90 180 270]}
                    :seed 42
                    :scene-fn (fn [params] (simple-scene params nil))
                    :thumb-size [40 40]})]
      (is (= 40 (.getWidth result)))    ;; 1 col x 40
      (is (= 160 (.getHeight result))))) ;; 4 rows x 40
  (testing "deterministic output"
    (let [opts {:base-params {:hue 180}
                :row-param {:key :hue :values [0 90]}
                :col-param {:key :hue :values [0 120]}
                :seed 42
                :scene-fn (fn [params] (simple-scene params nil))
                :thumb-size [40 40]}
          a (series/param-grid opts)
          b (series/param-grid opts)
          pixels-a (.getRGB a 0 0 (.getWidth a) (.getHeight a) nil 0 (.getWidth a))
          pixels-b (.getRGB b 0 0 (.getWidth b) (.getHeight b) nil 0 (.getWidth b))]
      (is (java.util.Arrays/equals ^ints pixels-a ^ints pixels-b)))))

;; --- convenience helper tests ---

;; --- batch edition rendering ---

(deftest render-editions-test
  (testing "renders editions to output directory"
    (let [dir (str "/tmp/eido-test-editions-" (System/currentTimeMillis))
          spec {:hue {:type :uniform :lo 0.0 :hi 360.0}}
          scene-fn (fn [params edition]
                     {:image/size [100 100]
                      :image/background [:color/hsl (:hue params) 0.5 0.5]
                      :image/nodes []})
          results (series/render-editions
                    {:spec spec
                     :master-seed 42
                     :start 0
                     :end 3
                     :scene-fn scene-fn
                     :output-dir dir})]
      (is (= 3 (count results)))
      (is (every? :edition results))
      (is (every? :params results))
      (is (every? :file results))
      ;; Files exist
      (is (every? #(.exists (java.io.File. ^String (:file %))) results))
      ;; Metadata file exists
      (is (.exists (java.io.File. (str dir "/metadata.edn"))))
      ;; Clean up
      (doseq [f (.listFiles (java.io.File. dir))]
        (.delete f))
      (.delete (java.io.File. dir))))
  (testing "supports SVG format"
    (let [dir (str "/tmp/eido-test-svg-" (System/currentTimeMillis))
          results (series/render-editions
                    {:spec {:r {:type :uniform :lo 10.0 :hi 50.0}}
                     :master-seed 42
                     :start 0
                     :end 2
                     :scene-fn (fn [params _ed]
                                 {:image/size [50 50]
                                  :image/background [:color/rgb 255 255 255]
                                  :image/nodes []})
                     :output-dir dir
                     :format :svg})]
      (is (every? #(clojure.string/ends-with? (:file %) ".svg") results))
      ;; Clean up
      (doseq [f (.listFiles (java.io.File. dir))]
        (.delete f))
      (.delete (java.io.File. dir)))))

;; --- convenience helper tests ---

(deftest derive-traits-test
  (testing "categorizes values into buckets"
    (let [params {:density 42 :speed 5}
          buckets {:density [[20 "sparse"] [40 "medium"] [100 "dense"]]
                   :speed [[3 "slow"] [7 "medium"] [10 "fast"]]}
          traits (series/derive-traits params buckets)]
      (is (= "dense" (:density traits)))
      (is (= "medium" (:speed traits)))))
  (testing "passes through params without buckets"
    (let [traits (series/derive-traits {:hue 200 :density 15}
                                       {:density [[20 "sparse"] [100 "dense"]]})]
      (is (= 200 (:hue traits)))
      (is (= "sparse" (:density traits))))))

;; --- trait-summary ---

(deftest trait-summary-test
  (testing "all trait keys present and counts sum to n"
    (let [spec {:density {:type :uniform :lo 0 :hi 100}
                :speed   {:type :uniform :lo 0 :hi 10}}
          buckets {:density [[30 "low"] [70 "mid"] [100 "high"]]
                   :speed   [[5 "slow"] [10 "fast"]]}
          summary (series/trait-summary spec 42 100 buckets)]
      (is (contains? summary :density))
      (is (contains? summary :speed))
      (is (= 100 (reduce + (vals (:density summary)))))
      (is (= 100 (reduce + (vals (:speed summary)))))))
  (testing "deterministic"
    (let [spec {:x {:type :uniform :lo 0 :hi 1}}
          buckets {:x [[0.5 "lo"] [1.0 "hi"]]}]
      (is (= (series/trait-summary spec 42 50 buckets)
             (series/trait-summary spec 42 50 buckets))))))

;; --- seed-grid :seeds ---

(deftest seed-grid-seeds-test
  (testing ":seeds option renders specific editions"
    (let [scene-fn (fn [_params _ed]
                     {:image/size [10 10]
                      :image/background :white
                      :image/nodes []})
          img (series/seed-grid
                {:spec {} :master-seed 42
                 :seeds [0 5 10]
                 :scene-fn scene-fn
                 :cols 3 :thumb-size [10 10]})]
      (is (instance? java.awt.image.BufferedImage img))
      ;; 3 seeds, 3 cols = 1 row of 30x10
      (is (= 30 (.getWidth ^java.awt.image.BufferedImage img)))
      (is (= 10 (.getHeight ^java.awt.image.BufferedImage img))))))
