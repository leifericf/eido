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
  Tiles are allocated lazily on first write.
  Wet planes are allocated on demand by the wet media module."
  [^long w ^long h]
  (let [cols (long (Math/ceil (/ (double w) tile-size)))
        rows (long (Math/ceil (/ (double h) tile-size)))
        n    (* cols rows)]
    {:surface/width         w
     :surface/height        h
     :surface/cols          cols
     :surface/rows          rows
     :surface/tiles         (object-array n)   ;; sparse: nil until touched
     :surface/dirty         (boolean-array n)
     :surface/wet-planes    (object-array n)
     :surface/height-planes (object-array n)})) ;; impasto height data

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

;; --- height plane access (for impasto) ---

(defn ensure-height-plane!
  "Ensures that a surface has a height plane allocated for the given tile.
  Returns the height float-array (one float per pixel)."
  ^floats [surface ^long tx ^long ty]
  (let [ts   (long tile-size)
        n    (* ts ts)
        cols (long (:surface/cols surface))
        idx  (+ tx (* ty cols))
        ^objects planes (:surface/height-planes surface)
        ^floats plane (aget planes idx)]
    (if plane
      plane
      (let [new-plane (float-array n)]
        (aset planes idx new-plane)
        new-plane))))

(defn deposit-height!
  "Deposits height at surface coordinates (px, py).
  height: height to add [0..1]. Silently skips pixels outside bounds."
  [surface ^long px ^long py ^double height]
  (let [sw (long (:surface/width surface))
        sh (long (:surface/height surface))]
    (when (and (>= px 0) (< px sw) (>= py 0) (< py sh))
      (let [ts  (long tile-size)
            tx  (quot px ts)
            ty  (quot py ts)
            lx  (rem px ts)
            ly  (rem py ts)
            ^floats plane (ensure-height-plane! surface tx ty)
            idx (+ (* ly ts) lx)
            old (double (aget plane idx))]
        (aset plane idx (float (Math/min 1.0 (+ old height))))))))

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

(defn- height-gradient
  "Computes directional height gradient at (lx,ly) within a height tile.
  Returns a lighting factor: >1.0 for highlights, <1.0 for shadows."
  ^double [^floats height-plane ^long lx ^long ly ^long ts]
  (let [idx (+ (* ly ts) lx)
        h   (double (aget height-plane idx))
        ;; Sample neighbors for gradient (light from upper-left)
        h-right (if (< lx (dec ts))
                  (double (aget height-plane (inc idx)))
                  h)
        h-down  (if (< ly (dec ts))
                  (double (aget height-plane (+ idx ts)))
                  h)
        ;; Gradient: positive = facing light, negative = away
        dx (- h h-right)
        dy (- h h-down)
        ;; Light direction: upper-left normalized
        light-dot (+ (* dx 0.707) (* dy 0.707))]
    (+ 1.0 (* light-dot 2.0))))

(defn compose-to-image
  "Composites all allocated tiles into a BufferedImage.
  Converts from premultiplied RGBA floats to 8-bit ARGB ints.
  Applies impasto directional lighting when height planes exist."
  ^BufferedImage [{:keys [^long surface/width ^long surface/height
                          ^long surface/cols ^long surface/rows
                          ^objects surface/tiles
                          ^objects surface/height-planes]}]
  (let [img (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
    (dotimes [ty rows]
      (dotimes [tx cols]
        (let [idx (tile-index cols tx ty)
              ^floats tile (aget tiles idx)]
          (when tile
            (let [ox (* tx tile-size)
                  oy (* ty tile-size)
                  max-x (min tile-size (- width ox))
                  max-y (min tile-size (- height oy))
                  ^floats hplane (when height-planes (aget height-planes idx))]
              (dotimes [ly max-y]
                (dotimes [lx max-x]
                  (let [fi (pixel-idx lx ly)
                        pr (double (aget tile fi))
                        pg (double (aget tile (unchecked-inc-int fi)))
                        pb (double (aget tile (unchecked-add-int fi 2)))
                        pa (double (aget tile (unchecked-add-int fi 3)))]
                    ;; Un-premultiply for ARGB output
                    (when (> pa 0.0)
                      (let [inv-a (/ 1.0 pa)
                            ;; Apply impasto directional lighting
                            light (if hplane
                                    (height-gradient hplane lx ly tile-size)
                                    1.0)
                            r (clamp-byte (* pr inv-a light 255.0))
                            g (clamp-byte (* pg inv-a light 255.0))
                            b (clamp-byte (* pb inv-a light 255.0))
                            a (clamp-byte (* pa 255.0))
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
