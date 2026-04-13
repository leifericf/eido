(ns eido.paint.surface-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.paint.surface :as surface]))

(deftest create-surface-test
  (testing "creates surface with correct tile grid dimensions"
    (let [s (surface/create-surface 200 200)]
      (is (= 200 (:surface/width s)))
      (is (= 200 (:surface/height s)))
      (is (= 4 (:surface/cols s)))
      (is (= 4 (:surface/rows s)))))

  (testing "rounds up tile grid for non-multiple sizes"
    (let [s (surface/create-surface 100 65)]
      (is (= 2 (:surface/cols s)))
      (is (= 2 (:surface/rows s))))))

(deftest sparse-allocation-test
  (testing "tiles are nil before first access"
    (let [s (surface/create-surface 200 200)]
      (is (nil? (surface/tile-at s 0 0)))
      (is (nil? (surface/tile-at s 1 1)))))

  (testing "get-tile! allocates on demand"
    (let [s (surface/create-surface 200 200)]
      (is (some? (surface/get-tile! s 0 0)))
      (is (some? (surface/tile-at s 0 0)))
      (is (nil? (surface/tile-at s 1 1))))))

(deftest pixel-access-test
  (testing "unallocated tile returns zeros"
    (let [s (surface/create-surface 200 200)]
      (is (= [0.0 0.0 0.0 0.0] (surface/get-pixel s 10 10)))))

  (testing "writing and reading a pixel"
    (let [s    (surface/create-surface 200 200)
          tile (surface/get-tile! s 0 0)
          idx  (surface/pixel-idx 10 10)]
      (aset ^floats tile idx (float 0.5))
      (aset ^floats tile (inc idx) (float 0.3))
      (aset ^floats tile (+ idx 2) (float 0.1))
      (aset ^floats tile (+ idx 3) (float 0.8))
      (let [[r g b a] (surface/get-pixel s 10 10)]
        (is (< (Math/abs (- r 0.5)) 0.001))
        (is (< (Math/abs (- g 0.3)) 0.001))
        (is (< (Math/abs (- b 0.1)) 0.001))
        (is (< (Math/abs (- a 0.8)) 0.001))))))

(deftest deposit-height-bounds-test
  (testing "deposit-height! silently skips pixels outside the surface"
    (let [s (surface/create-surface 100 100)]
      (surface/deposit-height! s -1 50 0.5)
      (surface/deposit-height! s 50 -1 0.5)
      (surface/deposit-height! s 100 50 0.5)
      (surface/deposit-height! s 50 100 0.5)
      (is true "out-of-bounds deposits should be silently skipped")
      (surface/deposit-height! s 50 50 0.3)
      (is true "in-bounds deposit works after out-of-bounds attempts"))))

(deftest compose-to-image-test
  (testing "produces BufferedImage of correct size"
    (let [s   (surface/create-surface 200 150)
          img (surface/compose-to-image s)]
      (is (= 200 (.getWidth img)))
      (is (= 150 (.getHeight img)))))

  (testing "painted pixel appears in composed image"
    (let [s    (surface/create-surface 100 100)
          tile (surface/get-tile! s 0 0)
          idx  (surface/pixel-idx 5 5)]
      ;; Write a fully opaque red pixel (premultiplied)
      (aset ^floats tile idx (float 1.0))       ;; r
      (aset ^floats tile (inc idx) (float 0.0))  ;; g
      (aset ^floats tile (+ idx 2) (float 0.0))  ;; b
      (aset ^floats tile (+ idx 3) (float 1.0))  ;; a
      (let [img  (surface/compose-to-image s)
            argb (.getRGB img 5 5)
            a    (bit-and (bit-shift-right argb 24) 0xFF)
            r    (bit-and (bit-shift-right argb 16) 0xFF)]
        (is (= 255 a) "alpha should be fully opaque")
        (is (= 255 r) "red channel should be 255")))))
