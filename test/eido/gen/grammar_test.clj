(ns eido.gen.grammar-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gen.grammar :as grammar]))

(def ^:private test-rules
  {"F" ["FF+[+F-F]-[-F+F]" "F+F" "F"]})

(def ^:private test-opts
  {:iterations 3 :angle 25 :length 8
   :origin [200 380] :heading -90
   :bounds [20 20 360 360] :seed 42})

(deftest solve-produces-string-test
  (testing "returns a non-empty expanded string"
    (let [result (grammar/solve "F" test-rules test-opts)]
      (is (string? result))
      (is (pos? (count result))))))

(deftest solve-within-bounds-test
  (testing "expansion stays within specified bounds"
    (let [bounds  [20 20 360 360]
          result  (grammar/solve "F" test-rules (assoc test-opts :bounds bounds))
          angle   (:angle test-opts)
          length  (:length test-opts)
          origin  (:origin test-opts)
          heading (:heading test-opts)
          [min-x min-y max-x max-y]
          (#'grammar/turtle-bounds result angle length origin heading)]
      (is (>= min-x 20) "min-x within bounds")
      (is (>= min-y 20) "min-y within bounds")
      (is (<= max-x 380) "max-x within bounds")
      (is (<= max-y 380) "max-y within bounds"))))

(deftest solve-seed-variety-test
  (testing "different seeds produce different expansions"
    (let [r1 (grammar/solve "F" test-rules (assoc test-opts :seed 1))
          r2 (grammar/solve "F" test-rules (assoc test-opts :seed 42))]
      (is (not= r1 r2)))))

(deftest solve-deterministic-test
  (testing "same seed produces same result"
    (is (= (grammar/solve "F" test-rules test-opts)
           (grammar/solve "F" test-rules test-opts)))))

(deftest solve-tight-bounds-test
  (testing "very tight bounds produce shorter expansions"
    (let [tight (grammar/solve "F" test-rules
                  (assoc test-opts :bounds [190 370 20 20]))
          loose (grammar/solve "F" test-rules
                  (assoc test-opts :bounds [0 0 800 800]))]
      (is (<= (count tight) (count loose))))))

(deftest grammar->path-cmds-test
  (testing "produces path commands"
    (let [cmds (grammar/grammar->path-cmds "F" test-rules test-opts)]
      (is (vector? cmds))
      (is (pos? (count cmds)))
      (is (= :move-to (first (first cmds)))))))

(deftest grammar->path-cmds-has-lines-test
  (testing "path commands include line-to segments"
    (let [cmds (grammar/grammar->path-cmds "F" test-rules test-opts)]
      (is (some #(= :line-to (first %)) cmds)))))
