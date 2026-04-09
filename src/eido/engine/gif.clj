(ns eido.engine.gif
  (:import
    [java.awt.image BufferedImage]
    [java.io File]
    [javax.imageio IIOImage ImageIO ImageTypeSpecifier]
    [javax.imageio.metadata IIOMetadataNode]
    [javax.imageio.stream FileImageOutputStream]))

(defn- frame-metadata
  "Creates GIF frame metadata with delay and optional loop extension."
  [writer ^BufferedImage img delay-cs loop? first-frame?]
  (let [type-spec (ImageTypeSpecifier/createFromRenderedImage img)
        meta      (.getDefaultImageMetadata writer type-spec nil)
        format    (.getNativeMetadataFormatName meta)
        root      (.getAsTree meta format)
        gce       (IIOMetadataNode. "GraphicControlExtension")]
    (.setAttribute gce "disposalMethod" "none")
    (.setAttribute gce "userInputFlag" "FALSE")
    (.setAttribute gce "transparentColorFlag" "FALSE")
    (.setAttribute gce "delayTime" (str delay-cs))
    (.setAttribute gce "transparentColorIndex" "0")
    (.appendChild root gce)
    (when (and first-frame? loop?)
      (let [app-exts (IIOMetadataNode. "ApplicationExtensions")
            app-ext  (IIOMetadataNode. "ApplicationExtension")]
        (.setAttribute app-ext "applicationID" "NETSCAPE")
        (.setAttribute app-ext "authenticationCode" "2.0")
        (.setUserObject app-ext (byte-array [(byte 0x1) (byte 0) (byte 0)]))
        (.appendChild app-exts app-ext)
        (.appendChild root app-exts)))
    (.setFromTree meta format root)
    meta))

(defn write-animated-gif
  "Writes a sequence of BufferedImages as an animated GIF.
  delay-ms: inter-frame delay in milliseconds.
  loop?: whether to loop infinitely."
  [images ^String path delay-ms loop?]
  (let [writer   (.next (ImageIO/getImageWritersByFormatName "gif"))
        delay-cs (max 1 (quot delay-ms 10))]
    (with-open [out (FileImageOutputStream. (File. path))]
      (.setOutput writer out)
      (.prepareWriteSequence writer nil)
      (doseq [[i img] (map-indexed vector images)]
        (let [meta (frame-metadata writer img delay-cs loop? (zero? i))]
          (.writeToSequence writer (IIOImage. img nil meta) nil)))
      (.endWriteSequence writer))
    (.dispose writer))
  path)

(comment
  (require '[eido.core :as eido])
  (let [scenes (for [i (range 10)]
                 {:image/size [100 100]
                  :image/background [:color/rgb 0 0 0]
                  :image/nodes
                  [{:node/type :shape/circle
                    :circle/center [50 50]
                    :circle/radius (+ 10 (* i 4))
                    :style/fill {:color [:color/rgb 255 0 0]}}]})
        images (mapv eido/render scenes)]
    (write-animated-gif images "/tmp/eido-test.gif" 100 true))
  )
