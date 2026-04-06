(ns eido.gif-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [eido.gif :as gif])
  (:import
    [java.awt Color Graphics2D]
    [java.awt.image BufferedImage]
    [java.io File]
    [javax.imageio ImageIO ImageReader]
    [javax.imageio.stream FileImageInputStream]))

(defn- solid-image
  "Creates a BufferedImage filled with a single color."
  [w h ^Color color]
  (let [img (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        ^Graphics2D g (.createGraphics img)]
    (.setColor g color)
    (.fillRect g 0 0 w h)
    (.dispose g)
    img))

(defn- gif-frame-count
  "Reads an animated GIF and returns the number of frames."
  [^String path]
  (let [readers (ImageIO/getImageReadersByFormatName "gif")
        ^ImageReader reader (.next readers)]
    (with-open [stream (FileImageInputStream. (File. path))]
      (.setInput reader stream)
      (let [n (.getNumImages reader true)]
        (.dispose reader)
        n))))

(deftest write-animated-gif-test
  (testing "writes a valid GIF file"
    (let [path (str (File/createTempFile "eido-gif" ".gif"))
          images [(solid-image 50 50 Color/RED)
                  (solid-image 50 50 Color/GREEN)
                  (solid-image 50 50 Color/BLUE)]]
      (gif/write-animated-gif images path 100 true)
      (is (.exists (File. path)))
      (is (pos? (.length (File. path))))
      (.delete (File. path))))

  (testing "GIF has correct number of frames"
    (let [path (str (File/createTempFile "eido-gif" ".gif"))
          images [(solid-image 40 40 Color/RED)
                  (solid-image 40 40 Color/GREEN)
                  (solid-image 40 40 Color/BLUE)]]
      (gif/write-animated-gif images path 100 true)
      (is (= 3 (gif-frame-count path)))
      (.delete (File. path))))

  (testing "GIF is readable as an image"
    (let [path (str (File/createTempFile "eido-gif" ".gif"))
          images [(solid-image 80 60 Color/RED)
                  (solid-image 80 60 Color/GREEN)]]
      (gif/write-animated-gif images path 50 false)
      (let [img (ImageIO/read (File. path))]
        (is (= 80 (.getWidth img)))
        (is (= 60 (.getHeight img))))
      (.delete (File. path))))

  (testing "single frame GIF"
    (let [path (str (File/createTempFile "eido-gif" ".gif"))
          images [(solid-image 30 30 Color/WHITE)]]
      (gif/write-animated-gif images path 100 true)
      (is (= 1 (gif-frame-count path)))
      (.delete (File. path)))))
