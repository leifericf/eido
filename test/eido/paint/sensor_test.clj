(ns eido.paint.sensor-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.paint.sensor :as sensor]))

(deftest curve-lookup-test
  (testing "nil curve returns 1.0"
    (is (= 1.0 (sensor/curve-lookup nil 0.5))))

  (testing "exact curve points"
    (let [curve [[0.0 0.0] [0.5 1.0] [1.0 0.5]]]
      (is (< (Math/abs (- (sensor/curve-lookup curve 0.0) 0.0)) 0.001))
      (is (< (Math/abs (- (sensor/curve-lookup curve 0.5) 1.0)) 0.001))
      (is (< (Math/abs (- (sensor/curve-lookup curve 1.0) 0.5)) 0.001))))

  (testing "interpolated values"
    (let [curve [[0.0 0.0] [1.0 1.0]]]
      (is (< (Math/abs (- (sensor/curve-lookup curve 0.5) 0.5)) 0.001))
      (is (< (Math/abs (- (sensor/curve-lookup curve 0.25) 0.25)) 0.001))))

  (testing "clamps to [0, 1]"
    (let [curve [[0.0 0.2] [1.0 0.8]]]
      (is (< (Math/abs (- (sensor/curve-lookup curve -0.5) 0.2)) 0.001))
      (is (< (Math/abs (- (sensor/curve-lookup curve 1.5) 0.8)) 0.001)))))

(deftest apply-dynamics-test
  (testing "empty dynamics returns base unchanged"
    (is (= {:paint/opacity 0.8}
           (sensor/apply-dynamics {:paint/opacity 0.8} [] {}))))

  (testing "single dynamic modulates value"
    (let [result (sensor/apply-dynamics
                   {:paint/opacity 1.0}
                   [{:sensor/input :pressure
                     :sensor/target :paint/opacity
                     :sensor/curve [[0.0 0.5] [1.0 1.0]]}]
                   {:sensor/pressure 0.0})]
      (is (< (Math/abs (- (:paint/opacity result) 0.5)) 0.01)
          "zero pressure should give 0.5 modulation")))

  (testing "multiple dynamics compound"
    (let [result (sensor/apply-dynamics
                   {:paint/opacity 1.0 :paint/radius 10.0}
                   [{:sensor/input :pressure
                     :sensor/target :paint/opacity
                     :sensor/curve [[0.0 0.5] [1.0 1.0]]}
                    {:sensor/input :pressure
                     :sensor/target :paint/radius
                     :sensor/curve [[0.0 0.3] [1.0 1.0]]}]
                   {:sensor/pressure 1.0})]
      (is (< (Math/abs (- (:paint/opacity result) 1.0)) 0.01))
      (is (< (Math/abs (- (:paint/radius result) 10.0)) 0.01)))))

(deftest compute-sensor-inputs-test
  (testing "extracts pressure from sample"
    (let [inputs (sensor/compute-sensor-inputs
                   {:x 100.0 :y 50.0 :pressure 0.7}
                   0.5 nil)]
      (is (< (Math/abs (- (:sensor/pressure inputs) 0.7)) 0.001))
      (is (< (Math/abs (- (:sensor/distance inputs) 0.5)) 0.001)))))
