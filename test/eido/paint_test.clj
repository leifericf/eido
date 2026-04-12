(ns eido.paint-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.paint :as paint]))

(deftest resolve-brush-test
  (testing "resolves keyword to preset"
    (let [b (paint/resolve-brush :chalk)]
      (is (= :brush/dab (:brush/type b)))
      (is (some? (get-in b [:brush/tip :tip/hardness])))))

  (testing "passes through map specs"
    (let [spec {:brush/type :brush/dab :brush/tip {:tip/shape :ellipse}}
          b    (paint/resolve-brush spec)]
      (is (= spec b))))

  (testing "nil returns default"
    (is (some? (paint/resolve-brush nil)))))

(deftest brush-constructor-test
  (testing "single-arg preset"
    (is (= :brush/dab (:brush/type (paint/brush :ink)))))

  (testing "preset with overrides"
    (let [b (paint/brush :chalk {:brush/paint {:paint/opacity 0.99}})]
      (is (= 0.99 (get-in b [:brush/paint :paint/opacity])))
      ;; Other paint params from chalk should still be present
      (is (some? (get-in b [:brush/paint :paint/spacing]))))))

(deftest render-stroke-test
  (testing "explicit points render without error"
    (let [s (paint/make-surface [100 100])]
      (paint/render-stroke! s
        {:stroke/brush :pencil
         :stroke/color [:color/rgb 100 50 25]
         :stroke/radius 8.0
         :stroke/points [[20 50 0.8 0 0 0] [80 50 0.6 0 0 0]]})
      ;; Check that some paint was deposited
      (let [img (paint/compose s)
            argb (.getRGB img 50 50)
            a (bit-and (bit-shift-right argb 24) 0xFF)]
        (is (> a 0) "paint should be deposited along stroke")))))

(deftest all-presets-resolve-test
  (testing "every preset resolves to a valid brush spec"
    (doseq [[k _] paint/presets]
      (let [b (paint/resolve-brush k)]
        (is (#{:brush/dab :brush/deform} (:brush/type b))
            (str "preset " k " should have valid :brush/type"))
        (is (some? (:brush/paint b))
            (str "preset " k " should have :brush/paint")))))

  (testing "preset count is at least 30"
    (is (>= (count paint/presets) 30)
        (str "expected 30+ presets, got " (count paint/presets)))))

(deftest all-presets-render-test
  (testing "every preset renders without error"
    (doseq [[k _] paint/presets]
      (let [s (paint/make-surface [100 100])]
        ;; For deform presets, lay down paint first
        (when (= :brush/deform (:brush/type (paint/resolve-brush k)))
          (paint/render-stroke! s
            {:stroke/brush :ink
             :stroke/color [:color/rgb 100 50 25]
             :stroke/radius 20.0
             :stroke/points [[50 50 1.0 0 0 0]]}))
        (paint/render-stroke! s
          {:stroke/brush k
           :stroke/color [:color/rgb 100 50 25]
           :stroke/radius 8.0
           :stroke/seed 42
           :stroke/points [[20 50 0.8 0 0 0] [80 50 0.6 0 0 0]]})
        (is (some? (paint/compose s))
            (str "preset " k " should render without error"))))))

(deftest auto-pressure-test
  (testing "taper mode creates bell-shaped pressure"
    (let [pts [[0 0] [50 0] [100 0]]
          curve (paint/auto-pressure pts {:mode :taper})]
      (is (= 3 (count curve)))
      (is (every? (fn [[t p]] (and (<= 0.0 t 1.0) (<= 0.01 p 1.0))) curve))
      ;; Middle should be higher than endpoints
      (is (> (second (nth curve 1)) (second (first curve))))))

  (testing "curvature mode varies with angle changes"
    (let [pts [[0 0] [50 50] [100 0]]  ;; sharp turn
          curve (paint/auto-pressure pts {:mode :curvature})]
      (is (= 3 (count curve)))
      (is (every? (fn [[t p]] (<= 0.01 p 1.0)) curve))))

  (testing "single point returns default"
    (is (= [[0.0 1.0]] (paint/auto-pressure [[50 50]])))))

(deftest auto-speed-test
  (testing "longer segments have higher speed"
    (let [curve (paint/auto-speed [[0 0] [10 0] [100 0]])]
      (is (= 3 (count curve)))
      ;; Last segment (90px) should be faster than first (10px)
      (is (> (second (nth curve 1)) (second (first curve)))))))

(deftest dynamics-profile-test
  (testing "named profiles return sensor specs"
    (doseq [k [:calligraphy :expressive :steady :feathered :bold]]
      (is (seq (paint/dynamics-profile k))
          (str "profile " k " should return entries"))))

  (testing "unknown profile returns empty"
    (is (= [] (paint/dynamics-profile :nonexistent)))))

(deftest stroke-from-path-test
  (testing "creates stroke descriptor with auto pressure"
    (let [desc (paint/stroke-from-path
                 [[:move-to [100 100]] [:line-to [200 100]]]
                 {:brush :chalk :color [:color/rgb 60 40 30] :radius 12})]
      (is (some? (:paint/pressure desc)))
      (is (= [:color/rgb 60 40 30] (:paint/color desc)))
      (is (= 12 (:paint/radius desc)))))

  (testing "dynamics are wired into brush"
    (let [desc (paint/stroke-from-path
                 [[:move-to [0 0]] [:line-to [100 0]]]
                 {:brush :ink :dynamics :calligraphy})]
      (is (seq (get-in desc [:paint/brush :brush/dynamics]))))))

(deftest render-strokes-test
  (testing "multiple strokes accumulate"
    (let [s (paint/make-surface [100 100])]
      (paint/render-strokes! s
        [{:stroke/brush :ink
          :stroke/color [:color/rgb 0 0 0]
          :stroke/radius 5.0
          :stroke/points [[10 50 1.0 0 0 0] [90 50 1.0 0 0 0]]}
         {:stroke/brush :ink
          :stroke/color [:color/rgb 255 0 0]
          :stroke/radius 5.0
          :stroke/points [[50 10 1.0 0 0 0] [50 90 1.0 0 0 0]]}])
      (let [img (paint/compose s)
            ;; Center where strokes cross
            argb (.getRGB img 50 50)
            a (bit-and (bit-shift-right argb 24) 0xFF)]
        (is (> a 0) "intersection should have paint")))))
