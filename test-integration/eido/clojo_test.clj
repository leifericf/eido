(ns eido.clojo-test
  "End-to-end bridge: a Clojo graph in EDN renders to artifact bytes through
  the Zig boundary, clj-zig importing the clojo module from the JVM. Tagged
  ^:integration — it needs a real zig toolchain, a sibling clojo checkout,
  and JDK 22+, so the default test lane skips it."
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.clojo :as clojo]))

(def ^:private circle-graph
  (str "{:clojo/version 1 :graph/id :g :graph/nodes "
       "{:art {:op/id :canvas/render "
       "       :canvas/scene {:image/size [16 16] "
       "                      :image/background [:color/rgb 0 0 0] "
       "                      :image/nodes [{:node/type :shape/circle "
       "                                     :circle/center [8 8] "
       "                                     :circle/radius 5 "
       "                                     :style/fill [:color/rgb 1 1 1]}]}} "
       " :out {:op/id :image/write :graph/input :art :asset/path \"mem.png\"}} "
       ":graph/output :out}"))

(deftest ^:integration renders-a-clojo-graph-to-png-bytes
  (let [r (clojo/render-edn circle-graph)]
    (is (= :ok (:status r)))
    (is (= "image/png" (:media-type r)))
    (is (= 16 (:width r)))
    (is (= 16 (:height r)))
    (is (bytes? (:bytes r)))
    (testing "the bytes open with the PNG signature"
      (is (= [-119 80 78 71] (mapv int (take 4 (:bytes r))))))))

(deftest ^:integration reports-diagnostics-for-an-invalid-graph
  (testing "a rejected graph returns diagnostics as data, never a JVM crash"
    (let [r (clojo/render-edn
              (str "{:clojo/version 1 :graph/id :g :graph/nodes "
                   "{:x {:op/id :nope/missing}} :graph/output :x}"))]
      (is (= :invalid (:status r)))
      (is (seq (:diagnostics r)))
      (is (zero? (alength ^bytes (:bytes r)))))))
