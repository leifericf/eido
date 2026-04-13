(ns eido.paint.wet-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.paint.surface :as surface]
    [eido.paint.wet :as wet]))

(deftest diffuse-tile-test
  (testing "diffusion spreads wetness from a point source"
    (let [ts  8
          wet (float-array (* ts ts))]
      ;; Single wet pixel at center
      (aset wet (+ (* 4 ts) 4) (float 1.0))
      ;; Run a few iterations
      (dotimes [_ 5]
        (wet/diffuse-tile! wet ts 0.5))
      ;; Center should still be wet
      (is (> (aget wet (+ (* 4 ts) 4)) 0.0) "center should be wet")
      ;; Neighbors should have received some wetness
      (is (> (aget wet (+ (* 4 ts) 5)) 0.0) "right neighbor should be wet")
      (is (> (aget wet (+ (* 3 ts) 4)) 0.0) "upper neighbor should be wet")
      ;; Far corners should still be dry
      (is (< (aget wet 0) 0.01) "far corner should be dry")))

  (testing "diffusion does not exceed [0, 1]"
    (let [ts  4
          wet (float-array (* ts ts) (float 0.5))]
      (dotimes [_ 10]
        (wet/diffuse-tile! wet ts 0.5))
      (doseq [i (range (* ts ts))]
        (is (>= (aget wet i) 0.0))
        (is (<= (aget wet i) 1.0))))))

(deftest deposit-wetness-bounds-test
  (testing "deposit-wetness! silently skips pixels outside the surface"
    (let [s (surface/create-surface 100 100)]
      (wet/deposit-wetness! s -1 50 0.5)
      (wet/deposit-wetness! s 50 -1 0.5)
      (wet/deposit-wetness! s 100 50 0.5)
      (wet/deposit-wetness! s 50 100 0.5)
      (wet/deposit-wetness! s -500 -500 0.5)
      (is true "out-of-bounds deposits should be silently skipped")
      ;; In-bounds still works.
      (wet/deposit-wetness! s 50 50 0.3)
      (is true "in-bounds deposit works after out-of-bounds attempts"))))

(deftest diffusion-conserves-test
  (testing "total wetness is approximately conserved"
    (let [ts  8
          wet (float-array (* ts ts))]
      (aset wet (+ (* 4 ts) 4) (float 1.0))
      (let [total-before (reduce + (map #(aget wet %) (range (* ts ts))))]
        (dotimes [_ 3]
          (wet/diffuse-tile! wet ts 0.5))
        (let [total-after (reduce + (map #(aget wet %) (range (* ts ts))))]
          ;; Should be approximately conserved (not exact due to boundary clamping)
          (is (< (Math/abs (- total-before total-after)) 0.1)
              "total wetness should be approximately conserved"))))))
