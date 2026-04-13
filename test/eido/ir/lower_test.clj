(ns eido.ir.lower-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.ir :as ir]
    [eido.ir.lower :as lower]))

;; --- resolve-fill ---

(deftest resolve-fill-nil-test
  (is (nil? (lower/resolve-fill nil))))

(deftest resolve-fill-solid-test
  (let [f (lower/resolve-fill {:fill/type :fill/solid
                               :color [:color/rgb 255 0 0]})]
    (testing "resolves to color map"
      (is (= 255 (:r f)))
      (is (= 0 (:g f)))
      (is (= 0 (:b f))))))

(deftest resolve-fill-gradient-test
  (let [f (lower/resolve-fill
            {:fill/type      :fill/gradient
             :gradient/type  :linear
             :gradient/stops [[0.0 [:color/rgb 255 0 0]]
                              [1.0 [:color/rgb 0 0 255]]]
             :gradient/from  [0 0]
             :gradient/to    [100 0]})]
    (testing "gradient structure preserved"
      (is (= :linear (:gradient/type f)))
      (is (= 2 (count (:gradient/stops f))))
      (is (= [0 0] (:gradient/from f)))
      (is (= [100 0] (:gradient/to f))))
    (testing "stop colors resolved"
      (let [[_pos c] (first (:gradient/stops f))]
        (is (= 255 (:r c)))))))

;; --- lower-simple-item (via lower-item for each geometry type) ---

(defn- make-item [geometry-type geometry-props]
  (merge {:item/geometry (merge {:geometry/type geometry-type}
                                geometry-props)
          :item/fill     {:fill/type :fill/solid
                          :color     [:color/rgb 100 100 100]}
          :item/opacity  1.0
          :item/transforms []}))

(deftest lower-rect-item-test
  (let [item (make-item :rect {:rect/xy [10 20] :rect/size [100 50]})
        ops  (#'lower/lower-item item)]
    (testing "produces one op"
      (is (= 1 (count ops))))
    (testing "op is a rect op"
      (is (= :rect (:op (first ops)))))
    (testing "position correct"
      (let [op (first ops)]
        (is (= 10 (:x op)))
        (is (= 20 (:y op)))
        (is (= 100 (:w op)))
        (is (= 50 (:h op)))))))

(deftest lower-circle-item-test
  (let [item (make-item :circle {:circle/center [50 50]
                                 :circle/radius 25})
        ops  (#'lower/lower-item item)]
    (testing "produces one circle op"
      (is (= 1 (count ops)))
      (is (= :circle (:op (first ops)))))
    (testing "center and radius correct"
      (let [op (first ops)]
        (is (= 50 (:cx op)))
        (is (= 50 (:cy op)))
        (is (= 25 (:r op)))))))

(deftest lower-ellipse-item-test
  (let [item (make-item :ellipse {:ellipse/center [100 100]
                                  :ellipse/rx 40 :ellipse/ry 20})
        ops  (#'lower/lower-item item)]
    (is (= 1 (count ops)))
    (is (= :ellipse (:op (first ops))))))

(deftest lower-line-item-test
  (let [item (make-item :line {:line/from [0 0] :line/to [100 100]})
        ops  (#'lower/lower-item item)]
    (is (= 1 (count ops)))
    (is (= :line (:op (first ops))))))

(deftest lower-path-item-test
  (let [item (make-item :path {:path/commands [[:move-to [0 0]]
                                               [:line-to [100 0]]
                                               [:close]]})
        ops  (#'lower/lower-item item)]
    (is (= 1 (count ops)))
    (is (= :path (:op (first ops))))))

(deftest lower-arc-item-test
  (let [item (make-item :arc {:arc/center [50 50]
                              :arc/rx 30 :arc/ry 30
                              :arc/start 0 :arc/extent 180})
        ops  (#'lower/lower-item item)]
    (is (= 1 (count ops)))
    (is (= :arc (:op (first ops))))))

;; --- group items ---

(deftest lower-group-item-test
  (let [child1 (make-item :rect {:rect/xy [0 0] :rect/size [10 10]})
        child2 (make-item :circle {:circle/center [50 50]
                                   :circle/radius 5})
        group  {:item/group     [child1 child2]
                :item/opacity   0.5
                :item/transforms []}
        ops    (#'lower/lower-item group)]
    (testing "flat group produces child ops"
      (is (= 2 (count ops))))
    (testing "opacity multiplied into children"
      (is (every? #(= 0.5 (:opacity %)) ops)))))

;; --- end-to-end lower ---

(deftest lower-container-test
  (let [items [{:item/geometry {:geometry/type :rect
                                :rect/xy [0 0]
                                :rect/size [800 600]}
                :item/fill     {:fill/type :fill/solid
                                :color     [:color/rgb 200 0 0]}
                :item/opacity  1.0
                :item/transforms []}]
        container (ir/container [800 600] {:r 255 :g 255 :b 255 :a 1.0} items)
        result    (lower/lower container)]
    (testing "produces concrete IR with expected keys"
      (is (vector? (:ir/ops result)))
      (is (= [800 600] (:ir/size result)))
      (is (:ir/background result)))
    (testing "one op for one item"
      (is (= 1 (count (:ir/ops result)))))
    (testing "op is a rect op"
      (is (= :rect (:op (first (:ir/ops result))))))))
