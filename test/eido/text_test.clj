(ns eido.text-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.text :as text]))

(def sans {:font/family "SansSerif" :font/size 48})
(def serif {:font/family "Serif" :font/size 72})

(deftest resolve-font-test
  (testing "resolves system fonts"
    (let [font (text/resolve-font sans)]
      (is (instance? java.awt.Font font))
      (is (= "SansSerif" (.getFamily font)))
      (is (= 48 (.getSize font)))))

  (testing "bold and italic"
    (let [font (text/resolve-font {:font/family "SansSerif" :font/size 24
                                    :font/weight :bold :font/style :italic})]
      (is (.isBold font))
      (is (.isItalic font))))

  (testing "caches fonts"
    (is (identical? (text/resolve-font sans)
                    (text/resolve-font sans)))))

(deftest text->path-commands-test
  (testing "produces non-empty commands for single char"
    (let [cmds (text/text->path-commands "A" sans)]
      (is (vector? cmds))
      (is (pos? (count cmds)))
      (is (= :move-to (first (first cmds))))))

  (testing "produces commands for multi-char string"
    (let [cmds (text/text->path-commands "Hello" sans)]
      (is (pos? (count cmds)))))

  (testing "empty string produces empty commands"
    (let [cmds (text/text->path-commands "" sans)]
      (is (empty? cmds)))))

(deftest text->glyph-paths-test
  (testing "returns one entry per glyph"
    (let [glyphs (text/text->glyph-paths "Hello" sans)]
      (is (= 5 (count glyphs)))))

  (testing "each glyph has commands and position"
    (let [[g] (text/text->glyph-paths "A" sans)]
      (is (vector? (:commands g)))
      (is (vector? (:position g)))
      (is (= 2 (count (:position g))))))

  (testing "glyph positions increase left to right"
    (let [glyphs (text/text->glyph-paths "AB" sans)
          [x0] (:position (first glyphs))
          [x1] (:position (second glyphs))]
      (is (< x0 x1)))))

(deftest text-advance-test
  (testing "returns positive width"
    (is (pos? (text/text-advance "Hello" sans))))

  (testing "longer text has greater advance"
    (is (< (text/text-advance "Hi" sans)
            (text/text-advance "Hello World" sans)))))

(deftest point-at-test
  (testing "straight line midpoint"
    (let [{:keys [point angle]} (text/point-at [[:move-to [0 0]]
                                                 [:line-to [100 0]]] 50.0)]
      (is (< (Math/abs (- (point 0) 50.0)) 0.01))
      (is (< (Math/abs (point 1)) 0.01))
      (is (< (Math/abs angle) 0.01))))

  (testing "start of path"
    (let [{:keys [point]} (text/point-at [[:move-to [10 20]]
                                           [:line-to [110 20]]] 0.0)]
      (is (< (Math/abs (- (point 0) 10.0)) 0.01))
      (is (< (Math/abs (- (point 1) 20.0)) 0.01))))

  (testing "past end clamps to end"
    (let [{:keys [point]} (text/point-at [[:move-to [0 0]]
                                           [:line-to [100 0]]] 200.0)]
      (is (< (Math/abs (- (point 0) 100.0)) 0.01)))))

(deftest path-length-test
  (testing "straight line"
    (is (< (Math/abs (- (text/path-length [[:move-to [0 0]]
                                            [:line-to [100 0]]]) 100.0))
            0.01)))

  (testing "two segments"
    (let [len (text/path-length [[:move-to [0 0]]
                                  [:line-to [100 0]]
                                  [:line-to [100 100]]])]
      (is (< (Math/abs (- len 200.0)) 0.01)))))

(deftest flatten-commands-test
  (testing "curves become line-to commands"
    (let [cmds (text/text->path-commands "O" sans)
          flat (text/flatten-commands cmds)]
      (is (every? #{:move-to :line-to :close} (map first flat))))))

(deftest glyph-contours-test
  (testing "O has two contours (outer + inner)"
    (let [cmds (text/text->path-commands "O" sans)
          contours (text/glyph-contours cmds)]
      (is (= 2 (count contours)))))

  (testing "L has one contour"
    (let [cmds (text/text->path-commands "L" sans)
          contours (text/glyph-contours cmds)]
      (is (= 1 (count contours))))))

(deftest text-node->group-test
  (testing "expands to group with path child"
    (let [node {:node/type   :shape/text
                :text/content "Hi"
                :text/font   sans
                :text/origin [100 200]
                :style/fill  [:color/rgb 0 0 0]}
          g (text/text-node->group node)]
      (is (= :group (:node/type g)))
      (is (= 1 (count (:group/children g))))
      (is (= :shape/path (:node/type (first (:group/children g)))))
      (is (= :even-odd (:path/fill-rule (first (:group/children g)))))
      (is (= [:color/rgb 0 0 0] (:style/fill (first (:group/children g)))))))

  (testing "preserves stroke"
    (let [node {:node/type   :shape/text
                :text/content "X"
                :text/font   sans
                :text/origin [0 0]
                :style/stroke {:color [:color/rgb 255 0 0] :width 2}}
          g (text/text-node->group node)]
      (is (= {:color [:color/rgb 255 0 0] :width 2}
             (:style/stroke (first (:group/children g))))))))

(deftest text-glyphs-node->group-test
  (testing "creates one path per glyph"
    (let [node {:node/type    :shape/text-glyphs
                :text/content "ABC"
                :text/font    sans
                :text/origin  [0 50]
                :style/fill   [:color/rgb 0 0 0]}
          g (text/text-glyphs-node->group node)]
      (is (= :group (:node/type g)))
      (is (= 3 (count (:group/children g))))))

  (testing "per-glyph fill overrides"
    (let [node {:node/type    :shape/text-glyphs
                :text/content "AB"
                :text/font    sans
                :text/origin  [0 50]
                :text/glyphs  [{:glyph/index 0 :style/fill [:color/rgb 255 0 0]}]
                :style/fill   [:color/rgb 0 0 0]}
          g (text/text-glyphs-node->group node)
          [a b] (:group/children g)]
      (is (= [:color/rgb 255 0 0] (:style/fill a)))
      (is (= [:color/rgb 0 0 0] (:style/fill b))))))

(deftest text-on-path-node->group-test
  (testing "places glyphs along a path"
    (let [node {:node/type      :shape/text-on-path
                :text/content   "AB"
                :text/font      sans
                :text/path      [[:move-to [0 0]] [:line-to [500 0]]]
                :style/fill     [:color/rgb 0 0 0]}
          g (text/text-on-path-node->group node)]
      (is (= :group (:node/type g)))
      (is (= 2 (count (:group/children g))))
      (is (every? #(= :shape/path (:node/type %)) (:group/children g))))))
