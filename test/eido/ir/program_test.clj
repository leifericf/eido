(ns eido.ir.program-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.ir.program :as program]))

(deftest literal-test
  (is (= 42 (program/evaluate {} 42)))
  (is (= 3.14 (program/evaluate {} 3.14))))

(deftest variable-test
  (is (= 10 (program/evaluate {:x 10} :x)))
  (is (= [0.5 0.5] (program/evaluate {:uv [0.5 0.5]} :uv))))

(deftest arithmetic-test
  (is (= 5.0 (program/evaluate {} [:+ 2 3])))
  (is (= 6.0 (program/evaluate {:a 10 :b 4} [:- :a :b])))
  (is (= 12.0 (program/evaluate {} [:* 3 4])))
  (is (= 2.5 (program/evaluate {} [:/ 5 2]))))

(deftest nested-arithmetic-test
  (is (= 14.0 (program/evaluate {} [:+ [:* 3 4] 2])))
  (is (= 7.0 (program/evaluate {:x 3 :y 4} [:+ :x :y]))))

(deftest math-functions-test
  (is (= 5.0 (program/evaluate {} [:abs -5])))
  (is (= 3.0 (program/evaluate {} [:sqrt 9])))
  (is (< (Math/abs (- 8.0 (program/evaluate {} [:pow 2 3]))) 0.001))
  (is (= 1.0 (program/evaluate {} [:mod 7 3]))))

(deftest trig-test
  (is (< (Math/abs (- 0.0 (program/evaluate {} [:sin 0]))) 0.001))
  (is (< (Math/abs (- 1.0 (program/evaluate {} [:cos 0]))) 0.001)))

(deftest vector-constructors-test
  (is (= [1 2] (program/evaluate {} [:vec2 1 2])))
  (is (= [1 2 3] (program/evaluate {} [:vec3 1 2 3])))
  (is (= [1 2 3 4] (program/evaluate {} [:vec4 1 2 3 4]))))

(deftest component-access-test
  (is (= 10 (program/evaluate {:p [10 20]} [:x :p])))
  (is (= 20 (program/evaluate {:p [10 20]} [:y :p]))))

(deftest mix-test
  (is (= 5.0 (program/evaluate {} [:mix 0 10 0.5])))
  (is (= 0.0 (program/evaluate {} [:mix 0 10 0.0])))
  (is (= 10.0 (program/evaluate {} [:mix 0 10 1.0]))))

(deftest clamp-test
  (is (= 0.5 (program/evaluate {} [:clamp 0.5 0.0 1.0])))
  (is (= 0.0 (program/evaluate {} [:clamp -0.5 0.0 1.0])))
  (is (= 1.0 (program/evaluate {} [:clamp 1.5 0.0 1.0]))))

(deftest select-test
  (is (= 10 (program/evaluate {} [:select 1.0 10 20])))
  (is (= 20 (program/evaluate {} [:select 0.0 10 20])))
  (is (= 20 (program/evaluate {} [:select -1.0 10 20]))))

(deftest field-noise-test
  (testing "field/noise evaluates at position"
    (let [result (program/evaluate
                   {:uv [0.5 0.5]}
                   [:field/noise {:field/type :field/noise
                                  :field/scale 1.0
                                  :field/variant :raw
                                  :field/seed 42}
                    :uv])]
      (is (double? result))
      (is (<= -1.0 result 1.0)))))

(deftest color-rgb-test
  (is (= {:r 255 :g 128 :b 0 :a 1.0}
         (program/evaluate {} [:color/rgb 255 128 0]))))

(deftest program-run-test
  (testing "run evaluates a program map"
    (let [prog {:program/inputs {:uv :vec2}
                :program/body   [:mix 0.0 1.0
                                      [:field/noise
                                       {:field/type :field/noise
                                        :field/scale 0.1
                                        :field/variant :fbm
                                        :field/seed 42}
                                       :uv]]}
          result (program/run prog {:uv [5.0 5.0]})]
      (is (double? result)))))

(deftest complex-expression-test
  (testing "nested expression with multiple ops"
    (let [expr [:clamp
                [:+ 0.5
                     [:* 0.5
                         [:field/noise
                          {:field/type :field/noise
                           :field/scale 0.05
                           :field/variant :fbm
                           :field/seed 42}
                          :pos]]]
                0.0 1.0]
          result (program/evaluate {:pos [10.0 20.0]} expr)]
      (is (<= 0.0 result 1.0)))))
