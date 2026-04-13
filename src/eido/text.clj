(ns eido.text
  "Text-to-path conversion for artistic typography.
  Converts text nodes into standard Eido path nodes via Java 2D font APIs.
  No external dependencies — uses JDK built-in Font, GlyphVector, PathIterator."
  (:import
    [java.awt Font GraphicsEnvironment]
    [java.awt.font FontRenderContext GlyphVector LineMetrics]
    [java.awt.geom PathIterator AffineTransform]))

;; --- font resolution ---

(defn- font-style
  "Derives java.awt.Font style int from font-spec :font/weight and :font/style."
  [{:font/keys [weight style]}]
  (bit-or
    (if (= weight :bold) Font/BOLD Font/PLAIN)
    (if (= style :italic) Font/ITALIC Font/PLAIN)))

(defn- load-font-from-file
  "Loads a TrueType/OpenType font from a file path."
  ^Font [^String path size style]
  (with-open [is (java.io.FileInputStream. path)]
    (-> (Font/createFont Font/TRUETYPE_FONT is)
        (.deriveFont (int style) (float size)))))

(defn- create-font
  "Creates a java.awt.Font from a font-spec map."
  ^Font [font-spec]
  (let [size  (:font/size font-spec)
        style (font-style font-spec)]
    (if-let [file (:font/file font-spec)]
      (load-font-from-file file size style)
      (Font. ^String (:font/family font-spec) (int style) (int size)))))

(def resolve-font
  "Resolves a font-spec map to a java.awt.Font. Memoized."
  (memoize create-font))

;; --- Java 2D Shape -> Eido path commands ---

(def ^:private ^FontRenderContext frc
  (FontRenderContext.
    (AffineTransform.)
    java.awt.RenderingHints/VALUE_TEXT_ANTIALIAS_ON
    java.awt.RenderingHints/VALUE_FRACTIONALMETRICS_ON))

(defn- shape->commands
  "Walks a java.awt.Shape's PathIterator and returns Eido path commands."
  [^java.awt.Shape shape]
  (let [iter (.getPathIterator shape nil)
        coords (double-array 6)]
    (loop [cmds (transient [])]
      (if (.isDone iter)
        (persistent! cmds)
        (let [seg (.currentSegment iter coords)]
          (.next iter)
          (recur
            (conj! cmds
              (case seg
                0 #_SEG_MOVETO  [:move-to [(aget coords 0) (aget coords 1)]]
                1 #_SEG_LINETO  [:line-to [(aget coords 0) (aget coords 1)]]
                2 #_SEG_QUADTO  [:quad-to
                                 [(aget coords 0) (aget coords 1)]
                                 [(aget coords 2) (aget coords 3)]]
                3 #_SEG_CUBICTO [:curve-to
                                 [(aget coords 0) (aget coords 1)]
                                 [(aget coords 2) (aget coords 3)]
                                 [(aget coords 4) (aget coords 5)]]
                4 #_SEG_CLOSE   [:close]))))))))

;; --- text -> path data ---

(defn text->path-commands
  "Converts a string + font-spec to Eido path commands for the entire text.
  Returns at the origin [0 0]; caller positions via :text/origin."
  [^String content font-spec]
  (let [font ^Font (resolve-font font-spec)
        gv   (.createGlyphVector font frc content)
        outline (.getOutline gv (float 0) (float 0))]
    (shape->commands outline)))

(defn text->glyph-paths
  "Converts a string + font-spec to per-glyph path data.
  Returns [{:commands [...] :position [x y]} ...], one entry per glyph.
  Commands are relative to each glyph's own origin (not layout position).
  :position gives the glyph's layout position within the string."
  [^String content font-spec]
  (let [font ^Font (resolve-font font-spec)
        gv   ^GlyphVector (.createGlyphVector font frc content)
        n    (.getNumGlyphs gv)]
    (mapv (fn [i]
            (let [pos   (.getGlyphPosition gv (int i))
                  gx    (.getX pos)
                  gy    (.getY pos)
                  ;; get outline at negative position to produce
                  ;; commands relative to glyph's own origin
                  shape (.getGlyphOutline gv (int i)
                          (float (- gx)) (float (- gy)))]
              {:commands (shape->commands shape)
               :position [gx gy]}))
          (range n))))

(defn text-advance
  "Returns the total advance width of text rendered with the given font-spec."
  ^double [^String content font-spec]
  (let [font ^Font (resolve-font font-spec)
        gv   ^GlyphVector (.createGlyphVector font frc content)]
    (.getWidth (.getLogicalBounds gv))))

;; --- path walking (for text-on-path) ---

(defn- bezier-point
  "Evaluates a cubic bezier at parameter t."
  [p0 p1 p2 p3 t]
  (let [u  (- 1.0 t)
        u2 (* u u)
        u3 (* u2 u)
        t2 (* t t)
        t3 (* t2 t)]
    [(+ (* u3 (p0 0)) (* 3 u2 t (p1 0)) (* 3 u t2 (p2 0)) (* t3 (p3 0)))
     (+ (* u3 (p0 1)) (* 3 u2 t (p1 1)) (* 3 u t2 (p2 1)) (* t3 (p3 1)))]))

(defn- bezier-tangent
  "Evaluates the tangent of a cubic bezier at parameter t."
  [p0 p1 p2 p3 t]
  (let [u  (- 1.0 t)
        u2 (* u u)
        t2 (* t t)]
    [(+ (* -3 u2 (p0 0)) (* 3 (- 1 (* 4 t) (- (* 3 t2))) (p1 0))
        (* 3 (- (* 2 t) (* 3 t2)) (p2 0)) (* 3 t2 (p3 0)))
     (+ (* -3 u2 (p0 1)) (* 3 (- 1 (* 4 t) (- (* 3 t2))) (p1 1))
        (* 3 (- (* 2 t) (* 3 t2)) (p2 1)) (* 3 t2 (p3 1)))]))

(defn- quad->cubic
  "Converts a quadratic bezier (p0, cp, p1) to cubic (p0, c1, c2, p1)."
  [p0 cp p1]
  (let [c1 [(+ (p0 0) (* 2/3 (- (cp 0) (p0 0))))
             (+ (p0 1) (* 2/3 (- (cp 1) (p0 1))))]
        c2 [(+ (p1 0) (* 2/3 (- (cp 0) (p1 0))))
             (+ (p1 1) (* 2/3 (- (cp 1) (p1 1))))]]
    [p0 c1 c2 p1]))

(defn- segment-arc-length
  "Approximates arc length of a cubic bezier by recursive subdivision."
  ([p0 p1 p2 p3] (segment-arc-length p0 p1 p2 p3 0.5))
  ([p0 p1 p2 p3 tolerance]
   (let [chord (Math/sqrt (+ (Math/pow (- (p3 0) (p0 0)) 2)
                              (Math/pow (- (p3 1) (p0 1)) 2)))
         mid01 [(/ (+ (p0 0) (p1 0)) 2) (/ (+ (p0 1) (p1 1)) 2)]
         mid12 [(/ (+ (p1 0) (p2 0)) 2) (/ (+ (p1 1) (p2 1)) 2)]
         mid23 [(/ (+ (p2 0) (p3 0)) 2) (/ (+ (p2 1) (p3 1)) 2)]
         net   (+ (Math/sqrt (+ (Math/pow (- (mid01 0) (p0 0)) 2)
                                 (Math/pow (- (mid01 1) (p0 1)) 2)))
                  (Math/sqrt (+ (Math/pow (- (mid12 0) (mid01 0)) 2)
                                 (Math/pow (- (mid12 1) (mid01 1)) 2)))
                  (Math/sqrt (+ (Math/pow (- (mid23 0) (mid12 0)) 2)
                                 (Math/pow (- (mid23 1) (mid12 1)) 2)))
                  (Math/sqrt (+ (Math/pow (- (p3 0) (mid23 0)) 2)
                                 (Math/pow (- (p3 1) (mid23 1)) 2))))]
     (if (< (- net chord) tolerance)
       (/ (+ chord net) 2.0)
       (let [mid (bezier-point p0 p1 p2 p3 0.5)
             ;; de Casteljau split
             a01 mid01 a12 mid12 a23 mid23
             b01 [(/ (+ (a01 0) (a12 0)) 2) (/ (+ (a01 1) (a12 1)) 2)]
             b12 [(/ (+ (a12 0) (a23 0)) 2) (/ (+ (a12 1) (a23 1)) 2)]
             c01 [(/ (+ (b01 0) (b12 0)) 2) (/ (+ (b01 1) (b12 1)) 2)]]
         (+ (segment-arc-length p0 a01 b01 c01 tolerance)
            (segment-arc-length c01 b12 a23 p3 tolerance)))))))

(defn- line-length [[x0 y0] [x1 y1]]
  (Math/sqrt (+ (Math/pow (- x1 x0) 2) (Math/pow (- y1 y0) 2))))

(defn- parse-path-segments
  "Parses path commands into segments: [{:type :line|:cubic :points [...] :length n} ...]"
  [commands]
  (loop [cmds commands
         pos  nil
         segs (transient [])]
    (if-not (seq cmds)
      (persistent! segs)
      (let [[cmd & args] (first cmds)]
        (case cmd
          :move-to (recur (rest cmds) (first args) segs)
          :line-to (let [p1 (first args)
                         len (line-length pos p1)]
                     (recur (rest cmds) p1
                            (conj! segs {:type :line :points [pos p1] :length len})))
          :quad-to (let [[cp p1] args
                         [a b c d] (quad->cubic pos cp p1)
                         len (segment-arc-length a b c d)]
                     (recur (rest cmds) p1
                            (conj! segs {:type :cubic :points [a b c d] :length len})))
          :curve-to (let [[c1 c2 p1] args
                          len (segment-arc-length pos c1 c2 p1)]
                      (recur (rest cmds) p1
                             (conj! segs {:type :cubic :points [pos c1 c2 p1] :length len})))
          :close (recur (rest cmds) pos segs))))))

(defn- point-on-segment
  "Returns {:point [x y] :angle rad} at distance d along a single segment."
  [{:keys [type points length]} d]
  (let [t (if (pos? length) (/ d length) 0.0)]
    (case type
      :line (let [[p0 p1] points
                  x (+ (p0 0) (* t (- (p1 0) (p0 0))))
                  y (+ (p0 1) (* t (- (p1 1) (p0 1))))
                  angle (Math/atan2 (- (p1 1) (p0 1)) (- (p1 0) (p0 0)))]
              {:point [x y] :angle angle})
      :cubic (let [[p0 p1 p2 p3] points
                   pt (bezier-point p0 p1 p2 p3 t)
                   tg (bezier-tangent p0 p1 p2 p3 t)]
               {:point pt :angle (Math/atan2 (tg 1) (tg 0))}))))

(defn path-length
  "Returns total arc length of path commands."
  [commands]
  (reduce + 0.0 (map :length (parse-path-segments commands))))

(defn point-at
  "Returns {:point [x y] :angle rad} at a given distance along path commands."
  [commands distance]
  (let [segs (parse-path-segments commands)]
    (loop [remaining distance
           ss segs]
      (if-not (seq ss)
        (when-let [last-seg (last segs)]
          (point-on-segment last-seg (:length last-seg)))
        (let [seg (first ss)]
          (if (<= remaining (:length seg))
            (point-on-segment seg remaining)
            (recur (- remaining (:length seg)) (rest ss))))))))

;; --- flatten curves (for 3D extrusion) ---

(defn flatten-commands
  "Approximates curves as line segments. flatness controls subdivision."
  ([commands] (flatten-commands commands 1.0))
  ([commands flatness]
   (let [^java.awt.geom.PathIterator
         iter (.getPathIterator
                (let [^java.awt.geom.GeneralPath p (java.awt.geom.GeneralPath.)]
                  (doseq [command commands]
                    (case (nth command 0)
                      :move-to  (let [[x y] (nth command 1)]
                                  (.moveTo p (double x) (double y)))
                      :line-to  (let [[x y] (nth command 1)]
                                  (.lineTo p (double x) (double y)))
                      :quad-to  (let [[cx cy] (nth command 1)
                                      [x y]   (nth command 2)]
                                  (.quadTo p (double cx) (double cy)
                                             (double x) (double y)))
                      :curve-to (let [[c1x c1y] (nth command 1)
                                      [c2x c2y] (nth command 2)
                                      [x y]     (nth command 3)]
                                  (.curveTo p (double c1x) (double c1y)
                                              (double c2x) (double c2y)
                                              (double x) (double y)))
                      :close    (.closePath p)))
                  p)
                nil (double flatness))
         coords (double-array 6)]
     (loop [cmds (transient [])]
       (if (.isDone iter)
         (persistent! cmds)
         (let [seg (.currentSegment iter coords)]
           (.next iter)
           (recur
             (conj! cmds
               (case seg
                 0 [:move-to [(aget coords 0) (aget coords 1)]]
                 1 [:line-to [(aget coords 0) (aget coords 1)]]
                 4 [:close])))))))))

(defn glyph-contours
  "Splits path commands into separate closed contours.
  Returns [[cmd ...] [cmd ...] ...], one vector per contour."
  [commands]
  (loop [cmds commands
         current []
         result []]
    (if-not (seq cmds)
      (if (seq current)
        (conj result current)
        result)
      (let [[cmd] (first cmds)]
        (if (and (= cmd :move-to) (seq current))
          (recur (rest cmds) [(first cmds)] (conj result current))
          (recur (rest cmds) (conj current (first cmds)) result))))))

;; --- node expansion: text nodes -> group/path nodes ---

(defn- apply-spacing
  "Adjusts glyph positions by adding cumulative spacing."
  [glyph-data spacing]
  (if (or (nil? spacing) (zero? spacing))
    glyph-data
    (map-indexed
      (fn [i g]
        (update-in g [:position 0] + (* i spacing)))
      glyph-data)))

(defn- align-offset
  "Returns x-offset for alignment given total width."
  [align width]
  (case (or align :left)
    :left   0.0
    :center (- (/ width 2.0))
    :right  (- width)))

(defn- with-group-attrs
  "Copies pass-through group-level attributes
  (:node/opacity, :node/transform, :group/composite, :group/filter)
  from `source` onto `group`."
  [group source]
  (cond-> group
    (:node/opacity source)    (assoc :node/opacity (:node/opacity source))
    (:node/transform source)  (assoc :node/transform (:node/transform source))
    (:group/composite source) (assoc :group/composite (:group/composite source))
    (:group/filter source)    (assoc :group/filter (:group/filter source))))

(defn text-node->group
  "Expands a :shape/text node into a :group with a single :shape/path child."
  [node]
  (let [commands (text->path-commands (:text/content node) (:text/font node))
        [ox oy]  (:text/origin node)
        width    (text-advance (:text/content node) (:text/font node))
        ax       (align-offset (:text/align node) width)]
    (-> {:node/type      :group
         :group/children [{:node/type     :shape/path
                           :path/commands commands
                           :path/fill-rule :even-odd}]}
        (cond->
          (:style/fill node)   (assoc-in [:group/children 0 :style/fill] (:style/fill node))
          (:style/stroke node) (assoc-in [:group/children 0 :style/stroke] (:style/stroke node)))
        (with-group-attrs node)
        (assoc :node/transform
               (into (or (:node/transform node) [])
                     [[:transform/translate (+ ox ax) oy]])))))

(defn text-glyphs-node->group
  "Expands a :shape/text-glyphs node into a :group with one :shape/path per glyph."
  [node]
  (let [glyph-data (-> (text->glyph-paths (:text/content node) (:text/font node))
                       (apply-spacing (:text/spacing node)))
        [ox oy]    (:text/origin node)
        width      (text-advance (:text/content node) (:text/font node))
        ax         (align-offset (:text/align node) width)
        overrides  (into {} (map (juxt :glyph/index identity)) (:text/glyphs node))
        children   (mapv
                     (fn [i {:keys [commands position]}]
                       (let [ovr (get overrides i)
                             [gx gy] position]
                         (cond-> {:node/type      :shape/path
                                  :path/commands  commands
                                  :path/fill-rule :even-odd
                                  :node/transform [[:transform/translate
                                                     (+ ox ax gx)
                                                     (+ oy gy)]]}
                           (or (:style/fill ovr) (:style/fill node))
                           (assoc :style/fill (or (:style/fill ovr) (:style/fill node)))
                           (or (:style/stroke ovr) (:style/stroke node))
                           (assoc :style/stroke (or (:style/stroke ovr) (:style/stroke node)))
                           (:node/opacity ovr)
                           (assoc :node/opacity (:node/opacity ovr))
                           (:node/transform ovr)
                           (update :node/transform into (:node/transform ovr)))))
                     (range)
                     glyph-data)]
    (-> {:node/type :group
         :group/children children}
        (with-group-attrs node))))

(defn- glyph-advances
  "Pre-computes advance width for each glyph from position data.
  The last glyph uses the font's em size as fallback."
  [glyph-data ^Font font]
  (let [n (count glyph-data)]
    (mapv (fn [i]
            (if (< i (dec n))
              (- (get-in (nth glyph-data (inc i)) [:position 0])
                 (get-in (nth glyph-data i) [:position 0]))
              (double (.getSize2D font))))
          (range n))))

(defn text-on-path-node->group
  "Expands a :shape/text-on-path node into a :group with glyphs along a path."
  [node]
  (let [glyph-data (text->glyph-paths (:text/content node) (:text/font node))
        font       ^Font (resolve-font (:text/font node))
        metrics    (.getLineMetrics font (:text/content node) frc)
        ascent     (.getAscent metrics)
        path-cmds  (:text/path node)
        offset     (or (:text/offset node) 0.0)
        spacing    (or (:text/spacing node) 0.0)
        advances   (glyph-advances glyph-data font)
        fill       (:style/fill node)
        stk        (:style/stroke node)
        {:keys [result]}
        (reduce
          (fn [{:keys [dist result]} [i {:keys [commands]}]]
            (let [advance    (double (nth advances i))
                  center-dist (+ dist (/ advance 2.0))
                  loc         (point-at path-cmds center-dist)
                  next-dist   (+ dist advance spacing)]
              (if loc
                (let [{:keys [point angle]} loc
                      [px py] point]
                  {:dist   next-dist
                   :result (conj result
                             (cond-> {:node/type      :shape/path
                                      :path/commands  commands
                                      :path/fill-rule :even-odd
                                      :node/transform
                                      [[:transform/translate px py]
                                       [:transform/rotate angle]
                                       [:transform/translate
                                         (- (/ advance 2.0))
                                         (- (/ ascent 2.0))]]}
                               fill (assoc :style/fill fill)
                               stk  (assoc :style/stroke stk)))})
                {:dist next-dist :result result})))
          {:dist (double offset) :result []}
          (map-indexed vector glyph-data))]
    (-> {:node/type      :group
         :group/children result}
        (with-group-attrs node))))

(comment
  ;; Explore available fonts
  (seq (.getAvailableFontFamilyNames (GraphicsEnvironment/getLocalGraphicsEnvironment)))

  ;; Basic text to path commands
  (text->path-commands "A" {:font/family "SansSerif" :font/size 72})

  ;; Per-glyph data
  (text->glyph-paths "Hello" {:font/family "Serif" :font/size 48})

  ;; Advance width
  (text-advance "Hello" {:font/family "SansSerif" :font/size 48})

  ;; Point on a straight line path
  (point-at [[:move-to [0 0]] [:line-to [100 0]]] 50.0)

  ;; Flatten curves
  (flatten-commands (text->path-commands "O" {:font/family "SansSerif" :font/size 72}))
  )
