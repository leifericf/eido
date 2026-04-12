(ns ^{:stability :provisional} eido.paint.surface
  "Tiled raster surface for the paint engine.

  A surface is a sparse grid of tiles, each a flat float-array of
  premultiplied RGBA values. Tiles are allocated on demand when first
  touched and tracked via dirty flags for efficient compositing."
  (:import
    [java.awt.image BufferedImage]))

;; --- constants ---

(def ^:const tile-size
  "Side length of each square tile in pixels."
  64)

(def ^:const tile-channels
  "Number of float channels per pixel (premultiplied RGBA)."
  4)

(def ^:const tile-len
  "Total floats per tile."
  (* tile-size tile-size tile-channels))

;; --- tile helpers ---

(defn- make-tile
  "Allocates a fresh tile — a zero-filled float array."
  ^floats []
  (float-array tile-len))

(defn- tile-index
  "Returns the flat index into the tile grid for tile coordinates [tx ty]."
  ^long [^long cols ^long tx ^long ty]
  (+ tx (* ty cols)))

;; --- surface creation ---

(defn create-surface
  "Creates a paint surface of the given pixel dimensions.
  Returns a map with sparse tile storage and dirty tracking.
  Tiles are allocated lazily on first write."
  [^long w ^long h]
  (let [cols (long (Math/ceil (/ (double w) tile-size)))
        rows (long (Math/ceil (/ (double h) tile-size)))
        n    (* cols rows)]
    {:surface/width  w
     :surface/height h
     :surface/cols   cols
     :surface/rows   rows
     :surface/tiles  (object-array n)   ;; sparse: nil until touched
     :surface/dirty  (boolean-array n)}))

;; --- tile access ---

(defn get-tile!
  "Returns the tile at tile coordinates [tx ty], allocating it if needed.
  Marks the tile dirty."
  ^floats [{:keys [^objects surface/tiles ^booleans surface/dirty
                   ^long surface/cols]} ^long tx ^long ty]
  (let [idx (tile-index cols tx ty)
        ^floats tile (aget tiles idx)]
    (if tile
      (do (aset dirty idx true)
          tile)
      (let [new-tile (make-tile)]
        (aset tiles idx new-tile)
        (aset dirty idx true)
        new-tile))))

(defn tile-at
  "Returns the tile at [tx ty] or nil if not yet allocated.
  Does not allocate or mark dirty."
  ^floats [{:keys [^objects surface/tiles ^long surface/cols]} ^long tx ^long ty]
  (aget tiles (tile-index cols tx ty)))

;; --- pixel access (mainly for testing) ---

(defn pixel-idx
  "Returns the float-array index for local pixel (lx, ly) within a tile."
  ^long [^long lx ^long ly]
  (* (+ (* ly tile-size) lx) tile-channels))

(defn get-pixel
  "Reads the premultiplied RGBA value at surface pixel (px, py).
  Returns [r g b a] as floats, or [0 0 0 0] if the tile is unallocated."
  [{:keys [^objects surface/tiles ^long surface/cols] :as surface}
   ^long px ^long py]
  (let [tx (quot px tile-size)
        ty (quot py tile-size)
        ^floats tile (tile-at surface tx ty)]
    (if tile
      (let [lx (rem px tile-size)
            ly (rem py tile-size)
            i  (pixel-idx lx ly)]
        [(aget tile i)
         (aget tile (unchecked-inc-int i))
         (aget tile (unchecked-add-int i 2))
         (aget tile (unchecked-add-int i 3))])
      [0.0 0.0 0.0 0.0])))

;; --- compositing ---

(defn- clamp-byte ^long [^double v]
  (long (Math/max 0.0 (Math/min 255.0 v))))

(defn compose-to-image
  "Composites all allocated tiles into a BufferedImage.
  Converts from premultiplied RGBA floats to 8-bit ARGB ints."
  ^BufferedImage [{:keys [^long surface/width ^long surface/height
                          ^long surface/cols ^long surface/rows
                          ^objects surface/tiles]}]
  (let [img (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
    (dotimes [ty rows]
      (dotimes [tx cols]
        (let [idx (tile-index cols tx ty)
              ^floats tile (aget tiles idx)]
          (when tile
            (let [ox (* tx tile-size)
                  oy (* ty tile-size)
                  max-x (min tile-size (- width ox))
                  max-y (min tile-size (- height oy))]
              (dotimes [ly max-y]
                (dotimes [lx max-x]
                  (let [fi (pixel-idx lx ly)
                        pr (aget tile fi)
                        pg (aget tile (unchecked-inc-int fi))
                        pb (aget tile (unchecked-add-int fi 2))
                        pa (aget tile (unchecked-add-int fi 3))]
                    ;; Un-premultiply for ARGB output
                    (when (> pa 0.0)
                      (let [inv-a (/ 1.0 (double pa))
                            r (clamp-byte (* (double pr) inv-a 255.0))
                            g (clamp-byte (* (double pg) inv-a 255.0))
                            b (clamp-byte (* (double pb) inv-a 255.0))
                            a (clamp-byte (* (double pa) 255.0))
                            argb (unchecked-int
                                   (bit-or (bit-shift-left a 24)
                                           (bit-shift-left r 16)
                                           (bit-shift-left g 8)
                                           b))]
                        (.setRGB img (+ ox lx) (+ oy ly) argb)))))))))))
    img))

(comment
  (def s (create-surface 200 200))
  (get-tile! s 0 0)
  (get-pixel s 0 0)
  (compose-to-image s))
