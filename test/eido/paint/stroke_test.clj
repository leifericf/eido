(ns eido.paint.stroke-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.paint.stroke :as stroke]))

(deftest resample-straight-line-test
  (testing "even spacing on a straight line"
    (let [dabs (stroke/resample-stroke [[0 0] [100 0]] 10.0
                 {:color {:r 200 :g 50 :b 30 :a 1.0} :radius 8.0 :opacity 0.5})]
      (is (= 11 (count dabs)) "100px / 10px spacing = 11 dabs (inclusive)")
      (is (< (Math/abs (- (:dab/cx (first dabs)) 0.0)) 0.01))
      (is (< (Math/abs (- (:dab/cx (last dabs)) 100.0)) 0.01)))))

(deftest resample-spacing-uniformity-test
  (testing "consecutive dabs are evenly spaced"
    (let [dabs (stroke/resample-stroke [[0 0] [200 0]] 25.0
                 {:color {:r 0 :g 0 :b 0 :a 1.0} :radius 5.0})
          xs   (mapv :dab/cx dabs)
          gaps (mapv (fn [i] (- (nth xs (inc i)) (nth xs i)))
                     (range (dec (count xs))))]
      (doseq [g gaps]
        (is (< (Math/abs (- g 25.0)) 0.01)
            (str "gap should be 25.0, got " g))))))

(deftest resample-pressure-curve-test
  (testing "pressure modulates radius and opacity"
    (let [dabs (stroke/resample-stroke [[0 0] [100 0]] 50.0
                 {:color {:r 200 :g 50 :b 30 :a 1.0}
                  :radius 10.0 :opacity 1.0
                  :pressure [[0.0 0.5] [1.0 1.0]]})]
      ;; First dab at t=0 gets pressure 0.5
      (is (< (Math/abs (- (:dab/radius (first dabs)) 5.0)) 0.5)
          "radius at t=0 should be ~5 (10 * 0.5)")
      ;; Last dab at t=1 gets pressure 1.0
      (is (< (Math/abs (- (:dab/radius (last dabs)) 10.0)) 0.5)
          "radius at t=1 should be ~10 (10 * 1.0)"))))

(deftest resample-single-point-test
  (testing "single point produces one dab"
    (let [dabs (stroke/resample-stroke [[50 50]] 10.0
                 {:color {:r 0 :g 0 :b 0 :a 1.0} :radius 5.0})]
      (is (= 1 (count dabs)))
      (is (= 50.0 (:dab/cx (first dabs))))))

  (testing "empty points produce no dabs"
    (is (= [] (stroke/resample-stroke [] 10.0
                {:color {:r 0 :g 0 :b 0 :a 1.0} :radius 5.0})))))

(deftest explicit-points-conversion-test
  (testing "converts explicit points to path points + pressure"
    (let [{:keys [points pressure]}
          (stroke/explicit-points->stroke-points
            [[0 0 0.5 0 0 0] [100 0 1.0 0 0 0]])]
      (is (= 2 (count points)))
      (is (= [0.0 0.0] (first points)))
      (is (= [100.0 0.0] (second points)))
      (is (= 2 (count pressure)))
      (is (< (Math/abs (- (second (first pressure)) 0.5)) 0.01))
      (is (< (Math/abs (- (second (last pressure)) 1.0)) 0.01)))))

(deftest resample-zero-spacing-test
  (testing "zero spacing does not cause infinite loop"
    (let [dabs (stroke/resample-stroke [[0 0] [100 0]] 0.0
                 {:color {:r 0 :g 0 :b 0 :a 1.0} :radius 5.0})]
      (is (pos? (count dabs)) "should produce dabs with fallback spacing")))

  (testing "negative spacing does not cause infinite loop"
    (let [dabs (stroke/resample-stroke [[0 0] [100 0]] -5.0
                 {:color {:r 0 :g 0 :b 0 :a 1.0} :radius 5.0})]
      (is (pos? (count dabs)))))

  (testing "zero-length stroke returns empty"
    (is (nil? (stroke/resample-stroke [[50 50] [50 50]] 5.0
                {:color {:r 0 :g 0 :b 0 :a 1.0} :radius 5.0})))))

(deftest jitter-produces-variation-test
  (testing "jitter varies position, opacity, and size"
    (let [base (stroke/resample-stroke [[0 0] [100 0]] 10.0
                {:color {:r 200 :g 50 :b 30 :a 1.0} :radius 8.0 :opacity 0.5})
          jittered (stroke/resample-stroke [[0 0] [100 0]] 10.0
                    {:color {:r 200 :g 50 :b 30 :a 1.0} :radius 8.0 :opacity 0.5
                     :jitter {:jitter/position 0.2 :jitter/opacity 0.3 :jitter/size 0.15}
                     :seed 42})]
      (is (= (count base) (count jittered)) "same number of dabs")
      ;; Jittered Y coords should differ from 0.0
      (is (some #(> (Math/abs (double (:dab/cy %))) 0.01) jittered)
          "at least one dab should have Y offset from jitter")
      ;; Opacities should vary
      (let [ops (set (mapv :dab/opacity jittered))]
        (is (> (count ops) 1) "opacity should vary between dabs"))
      ;; Radii should vary
      (let [rs (set (mapv :dab/radius jittered))]
        (is (> (count rs) 1) "radius should vary between dabs"))))

  (testing "jitter is deterministic with same seed"
    (let [a (stroke/resample-stroke [[0 0] [100 0]] 10.0
              {:color {:r 200 :g 50 :b 30 :a 1.0} :radius 8.0 :opacity 0.5
               :jitter {:jitter/position 0.2} :seed 42})
          b (stroke/resample-stroke [[0 0] [100 0]] 10.0
              {:color {:r 200 :g 50 :b 30 :a 1.0} :radius 8.0 :opacity 0.5
               :jitter {:jitter/position 0.2} :seed 42})]
      (is (= (mapv :dab/cx a) (mapv :dab/cx b))
          "same seed produces same jitter")))

  (testing "no jitter when spec is nil"
    (let [dabs (stroke/resample-stroke [[0 0] [100 0]] 10.0
                {:color {:r 200 :g 50 :b 30 :a 1.0} :radius 8.0 :opacity 0.5})]
      (is (every? #(= 0.0 (:dab/cy %)) dabs)
          "no jitter without spec"))))

(deftest dab-speed-estimate-test
  (testing "dabs have :dab/speed field"
    (let [dabs (stroke/resample-stroke [[0 0] [100 0]] 10.0
                {:color {:r 0 :g 0 :b 0 :a 1.0} :radius 5.0})]
      (is (every? #(contains? % :dab/speed) dabs))
      (is (every? #(>= (double (:dab/speed %)) 0.0) dabs)))))

(deftest path-commands-to-points-test
  (testing "flattens bezier to point sequence"
    (let [pts (stroke/path-commands->points
                [[:move-to [0 0]] [:line-to [100 0]]]
                0.5)]
      (is (>= (count pts) 2))
      (is (= [0.0 0.0] (mapv double (first pts)))))))
