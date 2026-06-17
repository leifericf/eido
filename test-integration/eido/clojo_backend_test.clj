(ns eido.clojo-backend-test
  "The :clojo renderer behind eido.core/render's backend switch. Tagged
  ^:integration — it drives the native backend, so it needs a zig toolchain,
  a sibling clojo checkout, and JDK 22+."
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.core :as eido])
  (:import
    [java.io File]))

;; An ordinary Eido scene (0..255 colour channels). The :clojo renderer
;; translates it to Clojo grammar (Phase 4) before rendering.
(def ^:private clojo-scene
  {:image/size       [16 16]
   :image/background [:color/rgb 0 0 0]
   :image/nodes      [{:node/type     :shape/circle
                       :circle/center [8 8]
                       :circle/radius 5
                       :style/fill    [:color/rgb 255 255 255]}]})

(deftest clojo-renderer-returns-encoded-bytes
  (let [r (eido/render clojo-scene {:renderer :clojo})]
    (is (= :ok (:status r)))
    (is (= "image/png" (:media-type r)))
    (is (bytes? (:bytes r)))
    (is (= [-119 80 78 71] (mapv int (take 4 (:bytes r)))))))

(deftest clojo-renderer-writes-a-file-with-output
  (let [out (File/createTempFile "eido-clojo" ".png")]
    (.deleteOnExit out)
    (let [ret (eido/render clojo-scene {:renderer :clojo :output (.getPath out)})]
      (is (= (.getPath out) ret))
      (is (pos? (.length out)))
      (with-open [in (java.io.FileInputStream. out)]
        (let [sig (byte-array 4)]
          (.read in sig)
          (is (= [-119 80 78 71] (mapv int sig))))))))

(deftest clojo-renderer-rejects-animations
  (testing "an animation through the clojo renderer is a clear error, not a crash"
    (is (thrown? clojure.lang.ExceptionInfo
                 (eido/render [clojo-scene clojo-scene] {:renderer :clojo :fps 12})))))

(deftest default-renderer-is-unchanged
  (testing "without :renderer, a normal eido scene still renders via Java2D"
    (let [img (eido/render {:image/size       [8 8]
                            :image/background [:color/rgb 0 0 0]
                            :image/nodes      [{:node/type     :shape/circle
                                                :circle/center [4 4]
                                                :circle/radius 3
                                                :style/fill    [:color/rgb 255 0 0]}]})]
      (is (instance? java.awt.image.BufferedImage img)))))
