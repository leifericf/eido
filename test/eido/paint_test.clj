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

(deftest circle-points-test
  (testing "generates correct number of points"
    (let [pts (paint/circle-points [100 100] {:radius 50 :n 20})]
      (is (= 20 (count pts)))
      (is (every? #(= 6 (count %)) pts))))

  (testing "points lie on circle"
    (let [pts (paint/circle-points [0 0] {:radius 50 :n 12})]
      (doseq [[x y _ _ _ _] pts]
        (let [d (Math/sqrt (+ (* x x) (* y y)))]
          (is (< (Math/abs (- d 50.0)) 0.1))))))

  (testing "arc subset"
    (let [pts (paint/circle-points [0 0] {:radius 50 :n 10 :start 0.0 :end Math/PI})]
      (is (= 10 (count pts)))
      (is (every? #(>= (second %) -1.0) pts)))))

(deftest wave-points-test
  (testing "generates points between from and to"
    (let [pts (paint/wave-points [0 0] {:to [100 0] :n 15})]
      (is (= 15 (count pts)))
      (is (< (Math/abs (ffirst pts)) 1.0) "starts near from")
      (is (< (Math/abs (- (first (last pts)) 100.0)) 1.0) "ends near to"))))

(deftest line-points-test
  (testing "generates straight line"
    (let [pts (paint/line-points [0 0] {:to [100 0] :n 11})]
      (is (= 11 (count pts)))
      (is (every? #(< (Math/abs (second %)) 0.01) pts)))))

(deftest fill-rect-test
  (testing "generates stroke descriptors"
    (let [strokes (paint/fill-rect [10 10 200 100] {:density 10 :brush :chalk})]
      (is (= 10 (count strokes)))
      (is (every? #(= :chalk (:paint/brush %)) strokes))
      (is (every? #(some? (:paint/points %)) strokes)))))

(deftest fill-ellipse-test
  (testing "generates stroke descriptors"
    (let [strokes (paint/fill-ellipse [100 100] {:rx 50 :ry 30 :density 8 :brush :oil})]
      (is (= 8 (count strokes)))
      (is (every? #(= :oil (:paint/brush %)) strokes)))))

(deftest auto-pressure-test
  (testing "taper mode creates bell-shaped pressure"
    (let [pts [[0 0] [50 0] [100 0]]
          curve (paint/auto-pressure pts {:mode :taper})]
      (is (= 3 (count curve)))
      (is (every? (fn [[t p]] (and (<= 0.0 t 1.0) (<= 0.01 p 1.0))) curve))
      (is (> (second (nth curve 1)) (second (first curve))))))

  (testing "single point returns default"
    (is (= [[0.0 1.0]] (paint/auto-pressure [[50 50]])))))

(deftest stroke-from-path-test
  (testing "creates stroke descriptor with auto pressure"
    (let [desc (paint/stroke-from-path
                 [[:move-to [100 100]] [:line-to [200 100]]]
                 {:brush :chalk :color [:color/rgb 60 40 30] :radius 12})]
      (is (some? (:paint/pressure desc)))
      (is (= [:color/rgb 60 40 30] (:paint/color desc)))
      (is (= 12 (:paint/radius desc))))))

(deftest make-surface-edge-cases-test
  (testing "accepts size vector"
    (is (some? (paint/make-surface [100 100]))))

  (testing "accepts :paint/size in config map"
    (is (some? (paint/make-surface {:paint/size [100 100]}))))

  (testing "accepts :size in config map (user-facing key from paint-surface)"
    (is (some? (paint/make-surface {:size [100 100]}))))

  (testing "throws descriptive error on nil"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"size"
          (paint/make-surface nil))))

  (testing "throws descriptive error on empty map"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"size"
          (paint/make-surface {}))))

  (testing "throws descriptive error on 0 or negative dimensions"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"positive"
          (paint/make-surface [0 100])))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"positive"
          (paint/make-surface [100 0])))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"positive"
          (paint/make-surface [-10 100])))))

(deftest point-generator-degenerate-n-test
  (testing "circle-points with n=1 returns single center point, not NaN"
    (let [pts (paint/circle-points [50 50] {:n 1 :radius 10})]
      (is (= 1 (count pts)))
      (is (every? #(Double/isFinite %) (take 2 (first pts))))))

  (testing "line-points with n=1 returns single from point, not NaN"
    (let [pts (paint/line-points [7 8] {:n 1 :to [100 200]})]
      (is (= 1 (count pts)))
      (is (= [7.0 8.0] [(nth (first pts) 0) (nth (first pts) 1)]))))

  (testing "wave-points with n=1 returns single from point, not NaN"
    (let [pts (paint/wave-points [3 4] {:n 1 :to [100 200]})]
      (is (= 1 (count pts)))
      (is (every? #(Double/isFinite %) (take 2 (first pts))))))

  (testing "n=0 and negative n return empty"
    (is (= [] (paint/circle-points [0 0] {:n 0})))
    (is (= [] (paint/line-points [0 0] {:n 0})))
    (is (= [] (paint/wave-points [0 0] {:n 0})))
    (is (= [] (paint/line-points [0 0] {:n -5})))))

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
