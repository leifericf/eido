(ns eido.paint.smudge-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.paint.smudge :as smudge]))

(deftest make-smudge-state-test
  (testing "initializes with brush color"
    (let [state @(smudge/make-smudge-state
                   {:smudge/mode :smear :smudge/amount 0.5}
                   {:r 200 :g 100 :b 50 :a 1.0})]
      (is (< (Math/abs (- (:pickup-r state) (/ 200.0 255.0))) 0.01))
      (is (= :smear (:mode state)))
      (is (= 0.5 (:amount state))))))

(deftest update-smudge-no-surface-test
  (testing "without surface color, mixes pickup with brush color"
    (let [ss (smudge/make-smudge-state
               {:smudge/mode :smear :smudge/amount 0.5 :smudge/length 0.7}
               {:r 255 :g 0 :b 0 :a 1.0})
          ;; Empty surface — no paint to sample
          surface {:surface/width 100 :surface/height 100
                   :surface/cols 2 :surface/rows 2
                   :surface/tiles (object-array 4)
                   :surface/dirty (boolean-array 4)}
          [r g b] (smudge/update-smudge! ss surface 50 50
                    {:r 255 :g 0 :b 0 :a 1.0})]
      ;; Should be a mix of pickup (red) and brush (red) = still red
      (is (> r 0.5) "should be reddish"))))
