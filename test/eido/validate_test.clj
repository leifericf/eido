(ns eido.validate-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [eido.validate :as validate]))

(def valid-scene
  {:image/size [800 600]
   :image/background [:color/rgb 255 255 255]
   :image/nodes
   [{:node/type :shape/circle
     :circle/center [400 300]
     :circle/radius 100
     :style/fill {:color [:color/rgb 200 0 0]}}]})

(deftest validate-valid-scene-test
  (testing "valid scene returns nil"
    (is (nil? (validate/validate valid-scene)))))

(deftest validate-missing-top-level-key-test
  (testing "missing :image/size produces error"
    (let [errors (validate/validate (dissoc valid-scene :image/size))]
      (is (vector? errors))
      (is (pos? (count errors)))
      (is (re-find #"image/size" (:message (first errors)))))))

(deftest validate-bad-rgb-test
  (testing "out-of-range RGB value produces error"
    (let [errors (validate/validate
                   (assoc valid-scene :image/background [:color/rgb 300 0 0]))]
      (is (some? errors))
      (is (pos? (count errors))))))

(deftest validate-missing-rect-key-test
  (testing "rect missing :rect/xy produces error with path"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :shape/rect
                   :rect/size [50 50]}]}
          errors (validate/validate scene)]
      (is (some? errors))
      (is (some #(re-find #"rect/xy" (:message %)) errors)))))

(deftest validate-nested-group-error-test
  (testing "invalid node inside group produces deep path"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes
                 [{:node/type :group
                   :group/children
                   [{:node/type :shape/circle
                     :circle/center [50 50]
                     :circle/radius -5}]}]}
          errors (validate/validate scene)]
      (is (some? errors))
      (is (some #(seq (:path %)) errors)))))

(deftest validate-bad-opacity-test
  (testing "opacity > 1 produces error"
    (let [scene (assoc-in valid-scene [:image/nodes 0 :node/opacity] 2.0)
          errors (validate/validate scene)]
      (is (some? errors)))))

(deftest validate-unknown-node-type-test
  (testing "unknown node type says 'unknown node type' not 'missing required key'"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes [{:node/type :shape/polygon}]}
          errors (validate/validate scene)]
      (is (= 1 (count errors)))
      (is (re-find #"unknown node type" (:message (first errors))))
      (is (re-find #":shape/rect" (:message (first errors))))
      (is (not (re-find #"missing required key" (:message (first errors))))))))

(deftest validate-multiple-errors-test
  (testing "multiple problems produce multiple errors"
    (let [scene {:image/background [:color/rgb 0 0 0]
                 :image/nodes "not a vector"}
          errors (validate/validate scene)]
      (is (some? errors))
      (is (> (count errors) 1)))))

(deftest validate-empty-scene-test
  (testing "empty map produces errors"
    (let [errors (validate/validate {})]
      (is (some? errors))
      (is (>= (count errors) 3)))))

;; --- core integration ---

(deftest core-validate-test
  (testing "eido.core/validate returns nil for valid scene"
    (is (nil? ((requiring-resolve 'eido.core/validate) valid-scene)))))

(deftest compile-throws-on-invalid-test
  (testing "validate-scene! throws ex-info with :errors for invalid scene"
    (try
      ((requiring-resolve 'eido.compile/validate-scene!) {:bad "scene"})
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (str/starts-with? (.getMessage e) "Invalid scene"))
        (is (vector? (:errors (ex-data e))))
        (is (pos? (count (:errors (ex-data e)))))))))

;; --- color error deduplication ---

(deftest color-dedup-rgb-test
  (testing "missing RGB channel produces 1 error, not 8"
    (let [errors (validate/validate
                   {:image/size [100 100]
                    :image/background [:color/rgb 255 0]
                    :image/nodes []})]
      (is (= 1 (count errors)))
      (is (re-find #"0\.\.255" (:message (first errors)))))))

(deftest color-dedup-hsl-test
  (testing "out-of-range HSL hue produces 1 error mentioning 0..360"
    (let [errors (validate/validate
                   {:image/size [100 100]
                    :image/background [:color/hsl 400 0.5 0.5]
                    :image/nodes []})]
      (is (= 1 (count errors)))
      (is (re-find #"0\.\.360" (:message (first errors)))))))

;; --- format-errors ---

(deftest format-errors-test
  (testing "formats multiple errors as numbered list"
    (let [errors [{:message "at [:image/size]: positive number, got: -1"}
                  {:message "at [:image/nodes 0]: missing required key :rect/xy"}]
          formatted (validate/format-errors errors)]
      (is (string? formatted))
      (is (str/includes? formatted "2 validation errors"))
      (is (str/includes? formatted "1."))
      (is (str/includes? formatted "2."))
      (is (str/includes? formatted "positive number"))))

  (testing "formats single error without plural"
    (let [formatted (validate/format-errors [{:message "some error"}])]
      (is (str/includes? formatted "1 validation error:"))
      (is (not (str/includes? formatted "errors")))))

  (testing "empty errors returns no-errors message"
    (is (= "No errors." (validate/format-errors [])))))

;; --- explain ---

(deftest explain-test
  (testing "explain prints formatted errors and returns error vector"
    (let [scene {:image/size [100 100]
                 :image/background [:color/rgb 0 0 0]
                 :image/nodes [{:node/type :shape/rect}]}
          output (with-out-str
                   (let [result (validate/explain scene)]
                     (is (vector? result))
                     (is (pos? (count result)))))]
      (is (str/includes? output "validation error"))))

  (testing "explain returns nil for valid scene"
    (is (nil? (validate/explain valid-scene)))))

;; --- compile default clauses ---

(deftest compile-unknown-node-type-throws-test
  (testing "compile-node throws on unknown node type when validation skipped"
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Unknown node type"
          ((requiring-resolve 'eido.core/render)
           {:image/size [100 100]
            :image/background [:color/rgb 0 0 0]
            :image/nodes [{:node/type :shape/polygon}]
            :eido/validate false})))))
