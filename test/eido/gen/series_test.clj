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
