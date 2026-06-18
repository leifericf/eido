(ns eido.phane-bake-test
  "Baking the native Phane boundary for distribution (clj-zig ADR 36): the
  boundary, pinned to a frozen Phane, bakes from a local checkout into a
  resource tree, and the baked library renders on its own. Tagged
  ^:integration — needs a zig toolchain, a sibling phane checkout, JDK 22+."
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]
    [clj-zig.bake :as bake]
    [clj-zig.cache :as cache]
    [clj-zig.ffm :as ffm]))

(defn- scratch []
  (str (java.nio.file.Files/createTempDirectory
         "eido-bake" (make-array java.nio.file.attribute.FileAttribute 0))))

(def ^:private graph
  (str "{:phane/version 1 :graph/id :g :graph/nodes "
       "{:art {:op/id :canvas/render "
       "       :canvas/scene {:image/size [8 8] :image/background [:color/rgb 0 0 0] "
       "                      :image/nodes [{:node/type :shape/circle :circle/center [4 4] "
       "                                     :circle/radius 3 :style/fill [:color/rgb 1 1 1]}]}} "
       " :out {:op/id :image/write :graph/input :art :asset/path \"o.png\"}} "
       ":graph/output :out}"))

(deftest ^:integration bakes-the-boundary-and-the-baked-library-renders
  (require 'eido.phane)
  (let [out               (scratch)
        results           (bake/bake! {:ns 'eido.phane :out out :targets :host})
        info              (:clj-zig/info (meta (resolve 'eido.phane/render-edn-raw)))
        {:keys [library]} (first results)]
    (testing "the boundary bakes into one loadable native library"
      (is (= 1 (count results)))
      (is (.exists (io/file library))))
    (testing "the build pins Phane for a reproducible, consumer-resolvable key"
      (let [inputs (#'bake/function-inputs info)]
        (is (seq (:modules inputs)) "the pinned fingerprint enters the hash")
        (is (seq (:module-roots inputs)) "the local checkout resolves for compile")))
    (testing "the baked library renders a graph to artifact bytes on its own"
      (let [render (ffm/bind (:spec info) library)
            framed (render (.getBytes graph "UTF-8") (.getBytes "." "UTF-8"))]
        (is (bytes? framed))
        (is (zero? (aget ^bytes framed 0)) "status byte 0 = :ok")))))
