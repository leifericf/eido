(ns eido.path.warp-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.path.warp :as warp]))

(deftest wave-warp-test
  (testing "wave warp displaces y coordinates"
    (let [cmds [[:move-to [0.0 50.0]] [:line-to [100.0 50.0]]]
          result (warp/warp-commands cmds {:type :wave :axis :y
                                           :amplitude 10 :wavelength 50})]
      (is (= 2 (count result)))
      ;; Y values should differ from original (unless at wave node)
      (let [[_ [_ y1]] (first result)
            [_ [_ y2]] (second result)]
        (is (not= y1 y2))))))

(deftest twist-warp-test
  (testing "twist warp rotates points around center"
    (let [cmds [[:move-to [100.0 0.0]] [:line-to [100.0 100.0]]]
          result (warp/warp-commands cmds {:type :twist :center [0 0] :amount 0.5})]
      (is (= 2 (count result)))
      ;; Points should be displaced from original
      (let [[_ p1] (first result)]
        (is (not= [100.0 0.0] p1))))))

(deftest identity-warp-test
  (testing "wave with amplitude 0 is identity"
    (let [cmds [[:move-to [50.0 50.0]] [:line-to [100.0 100.0]]]
          result (warp/warp-commands cmds {:type :wave :axis :y
                                           :amplitude 0 :wavelength 50})]
      (is (= cmds result)))))

(deftest warp-preserves-close-test
  (testing "close commands pass through"
    (let [cmds [[:move-to [0.0 0.0]] [:line-to [100.0 0.0]] [:close]]
          result (warp/warp-commands cmds {:type :wave :axis :y
                                           :amplitude 5 :wavelength 50})]
      (is (= :close (first (last result)))))))

(deftest fisheye-warp-test
  (testing "fisheye warp displaces points"
    (let [cmds [[:move-to [110.0 100.0]] [:line-to [100.0 110.0]]]
          result (warp/warp-commands cmds {:type :fisheye :center [100 100]
                                           :strength 0.5 :radius 50})]
      (is (= 2 (count result))))))

(deftest warp-unknown-type-test
  (testing "nil or unknown warp type throws ExceptionInfo"
    (is (thrown? clojure.lang.ExceptionInfo
          (warp/warp-commands [[:move-to [0 0]]] {})))
    (is (thrown? clojure.lang.ExceptionInfo
          (warp/warp-commands [[:move-to [0 0]]] {:type :nonexistent})))))
