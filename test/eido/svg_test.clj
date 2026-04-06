(ns eido.svg-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.svg :as svg]))

(deftest svg-wrapper-test
  (testing "wraps content in svg element with dimensions"
    (let [ir {:ir/size [800 600]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops []}
          out (svg/render ir)]
      (is (re-find #"<svg" out))
      (is (re-find #"xmlns=\"http://www.w3.org/2000/svg\"" out))
      (is (re-find #"width=\"800\"" out))
      (is (re-find #"height=\"600\"" out))
      (is (re-find #"viewBox=\"0 0 800 600\"" out))
      (is (re-find #"</svg>" out)))))

(deftest svg-background-test
  (testing "emits background rect as first element"
    (let [ir {:ir/size [200 100]
              :ir/background {:r 128 :g 64 :b 32 :a 1.0}
              :ir/ops []}
          out (svg/render ir)]
      (is (re-find #"<rect x=\"0\" y=\"0\" width=\"200\" height=\"100\" fill=\"rgb\(128,64,32\)\"" out)))))

(deftest svg-rect-test
  (testing "renders rect op"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 10 :y 20 :w 50 :h 30
                         :fill {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"<rect x=\"10\" y=\"20\" width=\"50\" height=\"30\"" out))
      (is (re-find #"fill=\"rgb\(255,0,0\)\"" out)))))

(deftest svg-circle-test
  (testing "renders circle op"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :circle :cx 50 :cy 60 :r 25
                         :fill {:r 0 :g 255 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"<circle cx=\"50\" cy=\"60\" r=\"25\"" out))
      (is (re-find #"fill=\"rgb\(0,255,0\)\"" out)))))

(deftest svg-path-test
  (testing "renders path op with d attribute"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :path
                         :commands [[:move-to 10 20]
                                    [:line-to 50 60]
                                    [:close]]
                         :fill {:r 0 :g 0 :b 255 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"<path d=\"M 10 20 L 50 60 Z\"" out)))))

(deftest svg-path-curve-test
  (testing "renders curve-to as C command"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :path
                         :commands [[:move-to 0 0]
                                    [:curve-to 10 20 30 40 50 60]]
                         :fill nil :stroke-color nil :stroke-width nil
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"C 10 20 30 40 50 60" out)))))

(deftest svg-no-fill-test
  (testing "nil fill produces fill=none"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill nil
                         :stroke-color {:r 0 :g 0 :b 0 :a 1.0}
                         :stroke-width 2
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"fill=\"none\"" out)))))

(deftest svg-stroke-test
  (testing "renders stroke attributes"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill nil
                         :stroke-color {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-width 3
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"stroke=\"rgb\(255,0,0\)\"" out))
      (is (re-find #"stroke-width=\"3\"" out)))))

(deftest svg-opacity-test
  (testing "renders opacity when not 1.0"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 0.5 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"opacity=\"0.5\"" out)))))

(deftest svg-opacity-omitted-at-one-test
  (testing "omits opacity when 1.0"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (not (re-find #"opacity=" out))))))

(deftest svg-transform-translate-test
  (testing "renders translate transform"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill nil :stroke-color nil :stroke-width nil
                         :opacity 1.0
                         :transforms [[:translate 10 20]]}]}
          out (svg/render ir)]
      (is (re-find #"transform=\"translate\(10,20\)\"" out)))))

(deftest svg-transform-rotate-test
  (testing "converts radians to degrees for rotate"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill nil :stroke-color nil :stroke-width nil
                         :opacity 1.0
                         :transforms [[:rotate Math/PI]]}]}
          out (svg/render ir)]
      (is (re-find #"rotate\(180" out)))))

(deftest svg-transform-combined-test
  (testing "combines multiple transforms"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill nil :stroke-color nil :stroke-width nil
                         :opacity 1.0
                         :transforms [[:translate 10 20] [:scale 2 3]]}]}
          out (svg/render ir)]
      (is (re-find #"translate\(10,20\)" out))
      (is (re-find #"scale\(2,3\)" out)))))

(deftest svg-empty-scene-test
  (testing "scene with no ops produces just background"
    (let [ir {:ir/size [50 50]
              :ir/background {:r 255 :g 255 :b 255 :a 1.0}
              :ir/ops []}
          out (svg/render ir)]
      (is (re-find #"<svg" out))
      (is (re-find #"<rect x=\"0\" y=\"0\"" out))
      (is (re-find #"</svg>" out)))))
