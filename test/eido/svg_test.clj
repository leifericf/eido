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

(deftest svg-line-test
  (testing "renders line op"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :line :x1 10 :y1 20 :x2 90 :y2 80
                         :fill nil
                         :stroke-color {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-width 2
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"<line x1=\"10\" y1=\"20\" x2=\"90\" y2=\"80\"" out))
      (is (re-find #"stroke=\"rgb\(255,0,0\)\"" out)))))

(deftest svg-ellipse-test
  (testing "renders ellipse op"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :ellipse :cx 50 :cy 60 :rx 40 :ry 20
                         :fill {:r 0 :g 255 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"<ellipse cx=\"50\" cy=\"60\" rx=\"40\" ry=\"20\"" out))
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

(deftest svg-path-quad-to-test
  (testing "renders quad-to as Q command"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :path
                         :commands [[:move-to 0 0]
                                    [:quad-to 50 80 100 0]]
                         :fill nil :stroke-color nil :stroke-width nil
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"Q 50 80 100 0" out)))))

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

(deftest svg-fill-alpha-test
  (testing "renders fill with alpha as rgba()"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill {:r 255 :g 0 :b 0 :a 0.5}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"fill=\"rgba\(255,0,0,0\.5\)\"" out))))
  (testing "fill with alpha 1.0 stays rgb()"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-color nil :stroke-width nil
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"fill=\"rgb\(255,0,0\)\"" out)))))

(deftest svg-stroke-alpha-test
  (testing "renders stroke with alpha as rgba()"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill nil
                         :stroke-color {:r 0 :g 255 :b 0 :a 0.3}
                         :stroke-width 2
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"stroke=\"rgba\(0,255,0,0\.3\)\"" out)))))

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

(deftest svg-stroke-cap-join-test
  (testing "renders stroke-linecap and stroke-linejoin"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill nil
                         :stroke-color {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-width 3
                         :stroke-cap :round :stroke-join :bevel
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"stroke-linecap=\"round\"" out))
      (is (re-find #"stroke-linejoin=\"bevel\"" out)))))

(deftest svg-stroke-dash-test
  (testing "renders stroke-dasharray"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops [{:op :rect :x 0 :y 0 :w 10 :h 10
                         :fill nil
                         :stroke-color {:r 255 :g 0 :b 0 :a 1.0}
                         :stroke-width 2
                         :stroke-dash [5 3]
                         :opacity 1.0 :transforms []}]}
          out (svg/render ir)]
      (is (re-find #"stroke-dasharray=\"5 3\"" out)))))

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

(deftest svg-scale-test
  (testing "scale multiplies width and height but keeps viewBox"
    (let [ir {:ir/size [200 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops []}
          out (svg/render ir {:scale 2})]
      (is (re-find #"width=\"400\"" out))
      (is (re-find #"height=\"200\"" out))
      (is (re-find #"viewBox=\"0 0 200 100\"" out))))

  (testing "scale 1 is same as no scale"
    (let [ir {:ir/size [200 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops []}]
      (is (= (svg/render ir) (svg/render ir {:scale 1})))))

  (testing "fractional scale"
    (let [ir {:ir/size [100 100]
              :ir/background {:r 0 :g 0 :b 0 :a 1.0}
              :ir/ops []}
          out (svg/render ir {:scale 1.5})]
      (is (re-find #"width=\"150\"" out))
      (is (re-find #"height=\"150\"" out)))))

;; --- animated SVG ---

(def ^:private sample-irs
  [{:ir/size [100 100]
    :ir/background {:r 0 :g 0 :b 0 :a 1.0}
    :ir/ops [{:op :circle :cx 50 :cy 50 :r 20
              :fill {:r 255 :g 0 :b 0 :a 1.0}
              :stroke-color nil :stroke-width nil
              :opacity 1.0 :transforms []}]}
   {:ir/size [100 100]
    :ir/background {:r 0 :g 0 :b 0 :a 1.0}
    :ir/ops [{:op :circle :cx 50 :cy 50 :r 40
              :fill {:r 0 :g 255 :b 0 :a 1.0}
              :stroke-color nil :stroke-width nil
              :opacity 1.0 :transforms []}]}])

(deftest render-animated-test
  (testing "produces valid SVG wrapper"
    (let [out (svg/render-animated sample-irs 10)]
      (is (re-find #"<svg" out))
      (is (re-find #"xmlns=\"http://www.w3.org/2000/svg\"" out))
      (is (re-find #"</svg>" out))))

  (testing "uses first IR dimensions"
    (let [out (svg/render-animated sample-irs 10)]
      (is (re-find #"width=\"100\"" out))
      (is (re-find #"height=\"100\"" out))
      (is (re-find #"viewBox=\"0 0 100 100\"" out))))

  (testing "contains one group per frame"
    (let [out (svg/render-animated sample-irs 10)]
      (is (= 2 (count (re-seq #"<g " out))))))

  (testing "frames contain their ops"
    (let [out (svg/render-animated sample-irs 10)]
      (is (re-find #"r=\"20\"" out))
      (is (re-find #"r=\"40\"" out))))

  (testing "contains SMIL animation elements"
    (let [out (svg/render-animated sample-irs 10)]
      (is (re-find #"<animate" out))
      (is (re-find #"attributeName=\"visibility\"" out))))

  (testing "scale option works"
    (let [out (svg/render-animated sample-irs 10 {:scale 2})]
      (is (re-find #"width=\"200\"" out))
      (is (re-find #"height=\"200\"" out))
      (is (re-find #"viewBox=\"0 0 100 100\"" out))))

  (testing "single frame produces valid SVG"
    (let [out (svg/render-animated [(first sample-irs)] 10)]
      (is (re-find #"<svg" out))
      (is (re-find #"</svg>" out))))

  (testing "uses total duration, not per-frame duration"
    (let [out (svg/render-animated sample-irs 10)]
      ;; 2 frames at 10fps = 0.2s total
      (is (re-find #"dur=\"0\.2s\"" out))
      ;; Should NOT have per-frame dur of 0.1s
      (is (not (re-find #"dur=\"0\.1s\"" out)))))

  (testing "keyTimes partition frames correctly"
    (let [out (svg/render-animated sample-irs 10)]
      ;; Frame 0: visible then hidden at 0.5 (1/2)
      (is (re-find #"values=\"visible;hidden\".*keyTimes=\"0;0\.5\"" out))
      ;; Frame 1: hidden then visible at 0.5 (1/2)
      (is (re-find #"values=\"hidden;visible\".*keyTimes=\"0;0\.5\"" out)))))
