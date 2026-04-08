(ns eido.ir.transform-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.color :as color]
    [eido.ir :as ir]
    [eido.ir.fill :as fill]
    [eido.ir.lower :as lower]
    [eido.ir.transform :as transform]
    [eido.render :as render]))

;; --- helpers ---

(defn- pixel-rgb
  [^java.awt.image.BufferedImage img x y]
  (let [rgb (.getRGB img x y)]
    [(bit-and (bit-shift-right rgb 16) 0xff)
     (bit-and (bit-shift-right rgb 8) 0xff)
     (bit-and rgb 0xff)]))

(defn- render-semantic [ir-container]
  (render/render (lower/lower ir-container) {}))

;; --- constructor tests ---

(deftest distortion-constructor-test
  (let [d (transform/distortion :noise {:amplitude 5 :frequency 0.1 :seed 42})]
    (is (= :transform/distort (:transform/type d)))
    (is (= :noise (:distort/method d)))
    (is (= 5 (:amplitude d)))))

(deftest warp-constructor-test
  (let [w (transform/warp-transform :twist {:center [100 100] :amount 0.5})]
    (is (= :transform/warp (:transform/type w)))
    (is (= :twist (:warp/method w)))))

(deftest morph-constructor-test
  (let [target [[:move-to [0 0]] [:line-to [100 100]] [:close]]
        m (transform/morph-transform target 0.5)]
    (is (= :transform/morph (:transform/type m)))
    (is (= 0.5 (:morph/t m)))))

;; --- distort lowering tests ---

(deftest distort-on-path-test
  (testing "distort transform displaces path commands"
    (let [commands [[:move-to [50.0 50.0]]
                    [:line-to [150.0 50.0]]
                    [:line-to [150.0 150.0]]
                    [:close]]
          result (transform/apply-pre-transforms
                   (ir/path-geometry commands)
                   [(transform/distortion :noise {:amplitude 10 :frequency 0.1 :seed 42})])]
      (is (= :path (:geometry/type result)))
      ;; Commands should be displaced (not identical to input)
      (let [out-cmds (:path/commands result)]
        (is (= 4 (count out-cmds)))
        ;; First point should be displaced from [50 50]
        (let [[_ [nx ny]] (first out-cmds)]
          (is (or (not= 50.0 nx) (not= 50.0 ny))))))))

(deftest distort-on-rect-test
  (testing "distort on rect converts to path"
    (let [result (transform/apply-pre-transforms
                   (ir/rect-geometry [10 10] [80 60])
                   [(transform/distortion :wave {:axis :y :amplitude 5 :wavelength 20})])]
      (is (= :path (:geometry/type result))))))

(deftest distort-renders-test
  (testing "distorted path renders via semantic IR"
    (let [semantic (ir/container
                     [200 200]
                     (color/resolve-color [:color/rgb 255 255 255])
                     [(ir/draw-item
                        (ir/path-geometry
                          [[:move-to [20.0 100.0]]
                           [:line-to [180.0 100.0]]])
                        :fill (fill/solid [:color/rgb 0 0 0])
                        :stroke {:color [:color/rgb 200 0 0] :width 3}
                        :pre-transforms [(transform/distortion
                                           :noise {:amplitude 15 :frequency 0.05 :seed 42})])])
          img (render-semantic semantic)]
      (is (some? img)))))

;; --- warp lowering tests ---

(deftest warp-on-rect-test
  (testing "warp on rect converts to warped path"
    (let [result (transform/apply-pre-transforms
                   (ir/rect-geometry [10 10] [80 60])
                   [(transform/warp-transform :wave {:axis :y :amplitude 10 :wavelength 40})])]
      (is (= :path (:geometry/type result)))
      ;; Should have many points (rect gets subdivided for smooth warping)
      (is (> (count (:path/commands result)) 4)))))

(deftest warp-renders-test
  (testing "warped rect renders"
    (let [semantic (ir/container
                     [200 200]
                     (color/resolve-color [:color/rgb 255 255 255])
                     [(ir/draw-item
                        (ir/rect-geometry [30 30] [140 140])
                        :fill (fill/solid [:color/rgb 100 150 200])
                        :pre-transforms [(transform/warp-transform
                                           :wave {:axis :y :amplitude 10 :wavelength 40})])])
          img (render-semantic semantic)]
      (is (some? img)))))

;; --- morph lowering tests ---

(deftest morph-test
  (testing "morph interpolates between two paths"
    (let [source [[:move-to [0.0 0.0]]
                  [:line-to [100.0 0.0]]
                  [:line-to [100.0 100.0]]
                  [:close]]
          target [[:move-to [50.0 0.0]]
                  [:line-to [100.0 50.0]]
                  [:line-to [50.0 100.0]]
                  [:close]]
          result (transform/apply-pre-transforms
                   (ir/path-geometry source)
                   [(transform/morph-transform target 0.5)])]
      (is (= :path (:geometry/type result)))
      (is (pos? (count (:path/commands result)))))))

;; --- chained transforms ---

(deftest chained-transforms-test
  (testing "multiple transforms applied sequentially"
    (let [result (transform/apply-pre-transforms
                   (ir/rect-geometry [10 10] [80 60])
                   [(transform/warp-transform :wave {:axis :y :amplitude 5 :wavelength 30})
                    (transform/distortion :noise {:amplitude 3 :frequency 0.1 :seed 42})])]
      (is (= :path (:geometry/type result)))
      (is (pos? (count (:path/commands result)))))))
