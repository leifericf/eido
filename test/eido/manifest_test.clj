(ns eido.manifest-test
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]
    [eido.core :as eido]
    [eido.manifest :as manifest]))

(def test-scene
  {:image/size [100 100]
   :image/background [:color/rgb 200 200 200]
   :image/nodes
   [{:node/type :shape/circle
     :circle/center [50 50]
     :circle/radius 30
     :style/fill [:color/rgb 255 0 0]}]})

(defn- tmp-path [ext]
  (str (java.io.File/createTempFile "eido-test-" ext)))

;; --- manifest-path ---

(deftest manifest-path-test
  (testing "replaces extension with .edn"
    (is (= "out.edn" (manifest/manifest-path "out.png")))
    (is (= "dir/edition-0.edn" (manifest/manifest-path "dir/edition-0.tiff")))
    (is (= "foo.edn" (manifest/manifest-path "foo.svg")))
    (is (= "a/b/c.edn" (manifest/manifest-path "a/b/c.jpg")))))

;; --- eido-version ---

(deftest eido-version-test
  (testing "reads version.edn from classpath"
    (let [v (manifest/eido-version)]
      (is (map? v))
      (is (string? (:tag v)))
      (is (string? (:sha v))))))

;; --- write + read roundtrip ---

(deftest write-read-roundtrip-test
  (let [out-path (tmp-path ".png")]
    (try
      (eido/render test-scene {:output out-path :emit-manifest? true
                               :seed 42 :params {:color :red}})
      (let [m-path (manifest/manifest-path out-path)
            m      (manifest/read-manifest m-path)]
        (testing "manifest file is written"
          (is (.exists (io/file m-path))))
        (testing "manifest version"
          (is (= 1 (:eido/manifest-version m))))
        (testing "contains scene map"
          (is (= test-scene (:scene m))))
        (testing "contains seed and params"
          (is (= 42 (:seed m)))
          (is (= {:color :red} (:params m))))
        (testing "contains eido version"
          (is (map? (:eido/version m))))
        (testing "contains timestamp"
          (is (string? (:timestamp m))))
        (testing "contains output paths"
          (is (= [out-path] (:output-paths m))))
        (testing "render-opts excludes internal keys"
          (is (not (contains? (:render-opts m) :emit-manifest?)))
          (is (not (contains? (:render-opts m) :seed)))
          (is (not (contains? (:render-opts m) :output)))))
      (finally
        (io/delete-file out-path true)
        (io/delete-file (manifest/manifest-path out-path) true)))))

;; --- render-from-manifest ---

(deftest render-from-manifest-test
  (let [out-path   (tmp-path ".png")
        repro-path (tmp-path ".png")]
    (try
      (eido/render test-scene {:output out-path :emit-manifest? true})
      (eido/render-from-manifest
        (manifest/manifest-path out-path)
        {:output repro-path})
      (testing "reproduced file exists"
        (is (.exists (io/file repro-path))))
      (testing "reproduced file is identical"
        (let [bytes1 (java.nio.file.Files/readAllBytes (.toPath (io/file out-path)))
              bytes2 (java.nio.file.Files/readAllBytes (.toPath (io/file repro-path)))]
          (is (= (seq bytes1) (seq bytes2)))))
      (finally
        (io/delete-file out-path true)
        (io/delete-file repro-path true)
        (io/delete-file (manifest/manifest-path out-path) true)))))

;; --- no manifest when not requested ---

(deftest no-manifest-by-default-test
  (let [out-path (tmp-path ".png")]
    (try
      (eido/render test-scene {:output out-path})
      (testing "no manifest written without :emit-manifest?"
        (is (not (.exists (io/file (manifest/manifest-path out-path))))))
      (finally
        (io/delete-file out-path true)))))
