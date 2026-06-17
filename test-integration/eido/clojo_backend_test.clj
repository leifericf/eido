(ns eido.clojo-backend-test
  "eido.core/render through the native Clojo backend. Tagged ^:integration —
  it drives the backend, so it needs a zig toolchain, a sibling clojo
  checkout, and JDK 22+."
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.core :as eido])
  (:import
    [java.io File]))

;; An ordinary Eido scene (0..255 colour channels). render translates it to
;; Clojo grammar before rendering through the native backend.
(def ^:private clojo-scene
  {:image/size       [16 16]
   :image/background [:color/rgb 0 0 0]
   :image/nodes      [{:node/type     :shape/circle
                       :circle/center [8 8]
                       :circle/radius 5
                       :style/fill    [:color/rgb 255 255 255]}]})

(deftest clojo-renderer-returns-encoded-bytes
  (let [r (eido/render clojo-scene)]
    (is (= :ok (:status r)))
    (is (= "image/png" (:media-type r)))
    (is (bytes? (:bytes r)))
    (is (= [-119 80 78 71] (mapv int (take 4 (:bytes r)))))))

(deftest clojo-renderer-writes-a-file-with-output
  (let [out (File/createTempFile "eido-clojo" ".png")]
    (.deleteOnExit out)
    (let [ret (eido/render clojo-scene {:output (.getPath out)})]
      (is (= (.getPath out) ret))
      (is (pos? (.length out)))
      (with-open [in (java.io.FileInputStream. out)]
        (let [sig (byte-array 4)]
          (.read in sig)
          (is (= [-119 80 78 71] (mapv int sig))))))))

(deftest clojo-renderer-renders-animations-to-gif
  (testing "a sequence of frames renders to an animated GIF"
    (let [r (eido/render [clojo-scene clojo-scene clojo-scene] {:fps 12})]
      (is (= :ok (:status r)))
      (is (= "image/gif" (:media-type r)))
      (is (bytes? (:bytes r)))
      (testing "the bytes open with the GIF signature"
        (is (= [71 73 70 56] (mapv int (take 4 (:bytes r)))))))))
