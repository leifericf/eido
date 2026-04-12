(ns ^{:stability :provisional} eido.paint.kernel
  "Hot-path dab rasterization kernel for the paint engine.

  Renders a single dab (a positioned brush stamp) onto a tiled surface
  using premultiplied-alpha blending. Inner loops use mutable float
  arrays and primitive math for performance."
  (:require
    [eido.paint.blend :as blend]
    [eido.paint.grain :as grain]
    [eido.paint.substrate :as substrate]
    [eido.paint.surface :as surface]
    [eido.paint.tip :as tip]))

;; --- dab rasterization ---

(defn rasterize-dab!
  "Rasterizes a single dab onto the surface.
  Mutates tile arrays in place. Returns nil.

  dab is a map:
    :dab/cx, :dab/cy   — center position (surface coords)
    :dab/radius         — radius in pixels
    :dab/aspect         — aspect ratio (optional, default 1.0)
    :dab/angle          — rotation angle in radians (optional, default 0)
    :dab/hardness       — falloff sharpness [0..1]
    :dab/opacity        — dab opacity [0..1]
    :dab/color          — {:r 0-255 :g 0-255 :b 0-255 :a 0-1}
    :dab/tip            — tip spec map (optional, uses defaults)
    :dab/grain          — grain spec map (optional, no grain if nil)
    :dab/substrate      — substrate spec map (optional)"
  [surface dab]
  (let [cx       (double (:dab/cx dab))
        cy       (double (:dab/cy dab))
        radius   (double (:dab/radius dab))
        aspect   (double (get dab :dab/aspect 1.0))
        angle    (double (get dab :dab/angle 0.0))
        opacity  (double (get dab :dab/opacity 1.0))
        color    (:dab/color dab)
        cr       (/ (double (:r color)) 255.0)
        cg       (/ (double (:g color)) 255.0)
        cb       (/ (double (:b color)) 255.0)
        ca       (double (get color :a 1.0))
        tip-spec (or (:dab/tip dab)
                     {:tip/shape    :ellipse
                      :tip/hardness (get dab :dab/hardness 0.7)
                      :tip/aspect   aspect})
        grain-spec    (:dab/grain dab)
        substrate-spec (:dab/substrate dab)
        blend-mode    (get dab :dab/blend :source-over)
        ;; Bounding box — expand by aspect ratio for elliptical tips
        extent   (* radius (max 1.0 aspect))
        x0       (long (Math/floor (- cx extent)))
        y0       (long (Math/floor (- cy extent)))
        x1       (long (Math/ceil (+ cx extent)))
        y1       (long (Math/ceil (+ cy extent)))
        ;; Clamp to surface bounds
        sw       (long (:surface/width surface))
        sh       (long (:surface/height surface))
        x0       (max 0 x0)
        y0       (max 0 y0)
        x1       (min sw x1)
        y1       (min sh y1)
        inv-r    (if (> radius 0.0) (/ 1.0 radius) 0.0)
        ts       (long surface/tile-size)]
    (loop [py y0]
      (when (< py y1)
        (loop [px x0]
          (when (< px x1)
            (let [;; Offset from dab center, normalized by radius
                  nx (* (- (+ (double px) 0.5) cx) inv-r)
                  ny (* (- (+ (double py) 0.5) cy) inv-r)
                  ;; Evaluate tip SDF for coverage
                  tip-cov (tip/evaluate-tip tip-spec nx ny angle)]
              (when (> tip-cov 0.0)
                (let [;; Apply grain and substrate modulation
                      grain-val (if grain-spec
                                  (let [gmode (get grain-spec :grain/mode :world)]
                                    (if (= gmode :local)
                                      ;; Local mode: coordinates relative to dab center, rotated
                                      (let [dx (- (+ (double px) 0.5) cx)
                                            dy (- (+ (double py) 0.5) cy)
                                            cos-a (Math/cos (- angle))
                                            sin-a (Math/sin (- angle))
                                            lx (+ (* dx cos-a) (* dy sin-a))
                                            ly (+ (* (- dx) sin-a) (* dy cos-a))]
                                        (grain/evaluate-grain grain-spec lx ly))
                                      ;; World mode: surface coordinates
                                      (grain/evaluate-grain grain-spec
                                        (double (+ px 0.5)) (double (+ py 0.5)))))
                                  1.0)
                      sub-val   (if substrate-spec
                                  (substrate/evaluate-substrate substrate-spec
                                    (double (+ px 0.5)) (double (+ py 0.5)))
                                  1.0)
                      coverage  (* tip-cov grain-val sub-val)
                      alpha     (* coverage opacity ca)
                      ;; Premultiplied source
                      src-r     (* cr alpha)
                      src-g     (* cg alpha)
                      src-b     (* cb alpha)
                      src-a     alpha
                      ;; Get the tile
                      tx        (quot px ts)
                      ty        (quot py ts)
                      ^floats tile (surface/get-tile! surface tx ty)
                      lx        (rem px ts)
                      ly        (rem py ts)
                      fi        (surface/pixel-idx lx ly)
                      ;; Existing premultiplied values
                      dst-r     (double (aget tile fi))
                      dst-g     (double (aget tile (unchecked-inc-int fi)))
                      dst-b     (double (aget tile (unchecked-add-int fi 2)))
                      dst-a     (double (aget tile (unchecked-add-int fi 3)))
                      ;; Blend source onto destination
                      [out-r out-g out-b out-a]
                      (if (= blend-mode :source-over)
                        ;; Fast path: inline source-over (most common)
                        (let [inv-src-a (- 1.0 src-a)]
                          [(+ src-r (* dst-r inv-src-a))
                           (+ src-g (* dst-g inv-src-a))
                           (+ src-b (* dst-b inv-src-a))
                           (+ src-a (* dst-a inv-src-a))])
                        ;; Other modes via dispatcher
                        (blend/blend blend-mode
                          [src-r src-g src-b src-a]
                          [dst-r dst-g dst-b dst-a]))]
                  (aset tile fi (float out-r))
                  (aset tile (unchecked-inc-int fi) (float out-g))
                  (aset tile (unchecked-add-int fi 2) (float out-b))
                  (aset tile (unchecked-add-int fi 3) (float out-a)))))
            (recur (unchecked-inc px))))
        (recur (unchecked-inc py))))))

;; --- deform operations ---

(def ^:private ^:const push-scale
  "Push displacement scale factor relative to radius." 0.3)

(def ^:private ^:const swirl-scale
  "Swirl rotation angle scale factor." 0.5)

(defn- box-average-3x3
  "Returns [avg-r avg-g avg-b avg-a] for a 3x3 neighborhood in buf.
  Pure function — no mutation, uses primitive accumulators."
  [^floats buf lx ly w h]
  (let [lx (long lx) ly (long ly) w (long w) h (long h)]
  (loop [oy (long -1)
         sum-r 0.0 sum-g 0.0 sum-b 0.0 sum-a 0.0
         cnt (long 0)]
    (if (> oy 1)
      (when (pos? cnt)
        (let [n (double cnt)]
          [(/ sum-r n) (/ sum-g n) (/ sum-b n) (/ sum-a n)]))
      (let [[sr sg sb sa c]
            (loop [ox (long -1)
                   sr sum-r sg sum-g sb sum-b sa sum-a
                   c cnt]
              (if (> ox 1)
                [sr sg sb sa c]
                (let [nx (+ lx ox) ny (+ ly oy)]
                  (if (and (>= nx 0) (< nx w) (>= ny 0) (< ny h))
                    (let [bi (* (+ (* ny w) nx) 4)]
                      (recur (inc ox)
                             (+ sr (double (aget buf bi)))
                             (+ sg (double (aget buf (+ bi 1))))
                             (+ sb (double (aget buf (+ bi 2))))
                             (+ sa (double (aget buf (+ bi 3))))
                             (inc c)))
                    (recur (inc ox) sr sg sb sa c)))))]
        (recur (inc oy) sr sg sb sa c))))))

(defn- deform-blur-sharpen!
  "Applies blur or sharpen deform to the surface within the given region.
  Uses box-average-3x3 with immutable accumulators instead of atoms."
  [surface ^floats buf mode strength
   x0 y0 x1 y1 w h cx cy r2]
  (let [strength (double strength)
        cx (double cx) cy (double cy)
        r2 (double r2)
        x0 (long x0) y0 (long y0)
        x1 (long x1) y1 (long y1)
        w (long w) h (long h)
        ts (long surface/tile-size)]
    (loop [py y0]
      (when (< py y1)
        (loop [px x0]
          (when (< px x1)
            (let [dx (- (+ (double px) 0.5) cx)
                  dy (- (+ (double py) 0.5) cy)
                  dist2 (+ (* dx dx) (* dy dy))]
              (when (< dist2 r2)
                (let [falloff (- 1.0 (/ dist2 r2))
                      lx (- px x0)
                      ly (- py y0)]
                  (when-let [[avg-r avg-g avg-b avg-a]
                             (box-average-3x3 buf lx ly w h)]
                    (let [ci (* (+ (* ly w) lx) 4)
                          orig-r (double (aget buf ci))
                          orig-g (double (aget buf (+ ci 1)))
                          orig-b (double (aget buf (+ ci 2)))
                          orig-a (double (aget buf (+ ci 3)))
                          f (* strength falloff)
                          [nr ng nb na]
                          (if (= mode :blur)
                            [(+ orig-r (* f (- avg-r orig-r)))
                             (+ orig-g (* f (- avg-g orig-g)))
                             (+ orig-b (* f (- avg-b orig-b)))
                             (+ orig-a (* f (- avg-a orig-a)))]
                            ;; Sharpen: overshoot away from blur
                            [(+ orig-r (* f (- orig-r avg-r)))
                             (+ orig-g (* f (- orig-g avg-g)))
                             (+ orig-b (* f (- orig-b avg-b)))
                             orig-a])
                          tx (quot px ts)
                          ty (quot py ts)
                          ^floats tile (surface/get-tile! surface tx ty)
                          tlx (rem px ts)
                          tly (rem py ts)
                          fi  (surface/pixel-idx tlx tly)]
                      (aset tile fi (float (Math/max 0.0 nr)))
                      (aset tile (unchecked-inc-int fi) (float (Math/max 0.0 ng)))
                      (aset tile (unchecked-add-int fi 2) (float (Math/max 0.0 nb)))
                      (aset tile (unchecked-add-int fi 3) (float (Math/max 0.0 na))))))))
            (recur (unchecked-inc px))))
        (recur (unchecked-inc py))))))

(defn- deform-displacement!
  "Applies push or swirl deform to the surface within the given region.
  Reads displaced pixels from buf via bilinear interpolation."
  [surface ^floats buf mode strength angle
   x0 y0 x1 y1 w h cx cy r2 radius]
  (let [strength (double strength) angle (double angle)
        cx (double cx) cy (double cy)
        r2 (double r2) radius (double radius)
        x0 (long x0) y0 (long y0)
        x1 (long x1) y1 (long y1)
        w (long w) h (long h)
        ts (long surface/tile-size)]
    (loop [py y0]
      (when (< py y1)
        (loop [px x0]
          (when (< px x1)
            (let [dx (- (+ (double px) 0.5) cx)
                  dy (- (+ (double py) 0.5) cy)
                  dist2 (+ (* dx dx) (* dy dy))]
              (when (< dist2 r2)
                (let [falloff (- 1.0 (/ dist2 r2))
                      [sx sy]
                      (case mode
                        :push
                        (let [push (* strength falloff radius push-scale)
                              push-dx (* push (Math/cos angle))
                              push-dy (* push (Math/sin angle))]
                          [(- (double px) push-dx) (- (double py) push-dy)])
                        :swirl
                        (let [swirl-angle (* strength falloff swirl-scale)
                              cos-s (Math/cos swirl-angle)
                              sin-s (Math/sin swirl-angle)
                              rx (+ cx (* dx cos-s) (* (- dy) sin-s))
                              ry (+ cy (* dx sin-s) (* dy cos-s))]
                          [rx ry]))
                      bx (- (double sx) (double x0))
                      by (- (double sy) (double y0))]
                  (when (and (>= bx 0.0) (< bx (- w 1.0))
                             (>= by 0.0) (< by (- h 1.0)))
                    (let [ix (long (Math/floor bx))
                          iy (long (Math/floor by))
                          fx (- bx ix)
                          fy (- by iy)
                          i00 (* (+ (* iy w) ix) 4)
                          i10 (* (+ (* iy w) (inc ix)) 4)
                          i01 (* (+ (* (inc iy) w) ix) 4)
                          i11 (* (+ (* (inc iy) w) (inc ix)) 4)
                          lerp (fn [^long off]
                                 (let [v00 (double (aget buf (+ i00 off)))
                                       v10 (double (aget buf (+ i10 off)))
                                       v01 (double (aget buf (+ i01 off)))
                                       v11 (double (aget buf (+ i11 off)))]
                                   (+ (* v00 (- 1.0 fx) (- 1.0 fy))
                                      (* v10 fx (- 1.0 fy))
                                      (* v01 (- 1.0 fx) fy)
                                      (* v11 fx fy))))
                          sr (lerp 0) sg (lerp 1)
                          sb (lerp 2) sa (lerp 3)
                          ci  (* (+ (* (- py y0) w) (- px x0)) 4)
                          or' (double (aget buf ci))
                          og  (double (aget buf (+ ci 1)))
                          ob  (double (aget buf (+ ci 2)))
                          oa  (double (aget buf (+ ci 3)))
                          f   (* strength falloff)
                          nr  (+ or' (* f (- sr or')))
                          ng  (+ og (* f (- sg og)))
                          nb  (+ ob (* f (- sb ob)))
                          na  (+ oa (* f (- sa oa)))
                          ttx (quot px ts)
                          tty (quot py ts)
                          ^floats tile (surface/get-tile! surface ttx tty)
                          tlx (rem px ts)
                          tly (rem py ts)
                          fi  (surface/pixel-idx tlx tly)]
                      (aset tile fi (float nr))
                      (aset tile (unchecked-inc-int fi) (float ng))
                      (aset tile (unchecked-add-int fi 2) (float nb))
                      (aset tile (unchecked-add-int fi 3) (float na)))))))
            (recur (unchecked-inc px))))
        (recur (unchecked-inc py))))))

(defn- copy-region-to-buffer!
  "Copies surface pixels in the [x0,y0)→[x1,y1) region into a float buffer."
  [surface ^floats buf x0 y0 x1 y1 w]
  (let [x0 (long x0) y0 (long y0) x1 (long x1) y1 (long y1) w (long w)]
  (loop [py y0]
    (when (< py y1)
      (loop [px x0]
        (when (< px x1)
          (let [[r g b a] (surface/get-pixel surface px py)
                bi (* (+ (* (- py y0) w) (- px x0)) 4)]
            (aset buf bi (float r))
            (aset buf (+ bi 1) (float g))
            (aset buf (+ bi 2) (float b))
            (aset buf (+ bi 3) (float a)))
          (recur (unchecked-inc px))))
      (recur (unchecked-inc py))))))

(defn deform-dab!
  "Applies a deform operation on existing surface pixels within dab radius.
  Does not deposit paint — moves, blends, or transforms existing content.

  dab is a map with:
    :dab/cx, :dab/cy — center position
    :dab/radius — area of effect
    :dab/angle — stroke direction (for push mode)
    :dab/deform — {:deform/mode :push|:swirl|:blur|:sharpen
                   :deform/strength 0.5}"
  [surface dab]
  (let [cx       (double (:dab/cx dab))
        cy       (double (:dab/cy dab))
        radius   (double (:dab/radius dab))
        angle    (double (get dab :dab/angle 0.0))
        deform   (:dab/deform dab)
        mode     (get deform :deform/mode :push)
        strength (double (get deform :deform/strength 0.5))
        r2       (* radius radius)
        sw       (long (:surface/width surface))
        sh       (long (:surface/height surface))
        x0       (max 0 (long (Math/floor (- cx radius))))
        y0       (max 0 (long (Math/floor (- cy radius))))
        x1       (min sw (long (Math/ceil (+ cx radius))))
        y1       (min sh (long (Math/ceil (+ cy radius))))
        w        (- x1 x0)
        h        (- y1 y0)
        ^floats buf (float-array (* w h 4))]
    (copy-region-to-buffer! surface buf x0 y0 x1 y1 w)
    (if (#{:blur :sharpen} mode)
      (deform-blur-sharpen! surface buf mode strength
        x0 y0 x1 y1 w h cx cy r2)
      (deform-displacement! surface buf mode strength angle
        x0 y0 x1 y1 w h cx cy r2 radius))))

(comment
  (require '[eido.paint.surface :as surface])
  (let [s (surface/create-surface 100 100)]
    (rasterize-dab! s {:dab/cx 50.0 :dab/cy 50.0 :dab/radius 20.0
                       :dab/hardness 0.6 :dab/opacity 0.8
                       :dab/color {:r 200 :g 50 :b 30 :a 1.0}})
    (println "Center:" (surface/get-pixel s 50 50))
    (println "Edge:"   (surface/get-pixel s 69 50))
    (println "Outside:" (surface/get-pixel s 75 50)))

  ;; Elliptical dab
  (let [s (surface/create-surface 100 100)]
    (rasterize-dab! s {:dab/cx 50.0 :dab/cy 50.0 :dab/radius 15.0
                       :dab/aspect 2.0 :dab/angle 0.5
                       :dab/tip {:tip/shape :ellipse :tip/hardness 0.7 :tip/aspect 2.0}
                       :dab/opacity 0.8
                       :dab/color {:r 200 :g 50 :b 30 :a 1.0}})
    (let [img (surface/compose-to-image s)]
      (javax.imageio.ImageIO/write img "png" (java.io.File. "/tmp/ellipse-dab.png")))))
