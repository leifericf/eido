(ns eido.io.plotter-test
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [eido.io.plotter :as plotter]))

(defn- tmp-dir []
  (let [d (java.io.File/createTempFile "eido-plotter-" "")]
    (.delete d)
    (.mkdirs d)
    (str d)))

(defn- delete-dir! [path]
  (let [dir (io/file path)]
    (doseq [f (reverse (file-seq dir))]
      (.delete f))))

(def two-pen-scene
  {:image/size [200 200]
   :image/background [:color/rgb 255 255 255]
   :image/nodes
   [{:node/type :shape/circle
     :circle/center [50 50] :circle/radius 30
     :style/stroke {:color [:color/rgb 255 0 0] :width 2}}
    {:node/type :shape/circle
     :circle/center [150 150] :circle/radius 30
     :style/stroke {:color [:color/rgb 0 0 255] :width 2}}]})

(def one-pen-scene
  {:image/size [200 200]
   :image/background [:color/rgb 255 255 255]
   :image/nodes
   [{:node/type :shape/circle
     :circle/center [50 50] :circle/radius 30
     :style/stroke {:color [:color/rgb 0 0 0] :width 2}}
    {:node/type :shape/circle
     :circle/center [150 150] :circle/radius 30
     :style/stroke {:color [:color/rgb 0 0 0] :width 2}}]})

(deftest export-layers-two-pens-test
  (let [dir (tmp-dir)]
    (try
      (let [results (plotter/export-layers two-pen-scene dir {})]
        (testing "produces one file per pen"
          (is (= 2 (count results))))
        (testing "each file exists"
          (doseq [{:keys [file]} results]
            (is (.exists (io/file file)))))
        (testing "each file is valid SVG"
          (doseq [{:keys [file]} results]
            (let [svg (slurp file)]
              (is (str/starts-with? svg "<svg"))
              (is (str/ends-with? (str/trim svg) "</svg>")))))
        (testing "preview file exists"
          (is (.exists (io/file dir "preview.svg")))))
      (finally
        (delete-dir! dir)))))

(deftest export-layers-single-pen-test
  (let [dir (tmp-dir)]
    (try
      (let [results (plotter/export-layers one-pen-scene dir {})]
        (testing "single pen produces one file"
          (is (= 1 (count results))))
        (testing "file is valid SVG"
          (let [svg (slurp (:file (first results)))]
            (is (str/starts-with? svg "<svg"))
            (is (str/ends-with? (str/trim svg) "</svg>")))))
      (finally
        (delete-dir! dir)))))

(deftest export-layers-no-preview-test
  (let [dir (tmp-dir)]
    (try
      (plotter/export-layers one-pen-scene dir {:preview false})
      (testing "no preview when disabled"
        (is (not (.exists (io/file dir "preview.svg")))))
      (finally
        (delete-dir! dir)))))
