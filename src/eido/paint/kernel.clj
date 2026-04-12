(ns ^{:stability :provisional} eido.paint.kernel
  "Hot-path dab rasterization kernel for the paint engine.

  Renders a single dab (a positioned brush stamp) onto a tiled surface
  using premultiplied-alpha blending. Inner loops use mutable float
  arrays and primitive math for performance."
  (:require
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
    :dab/tip            — tip spec map (optional, uses defaults)"
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
                  coverage (tip/evaluate-tip tip-spec nx ny angle)]
              (when (> coverage 0.0)
                (let [alpha     (* coverage opacity ca)
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
                      ;; Source-over: out = src + dst * (1 - src_a)
                      inv-src-a (- 1.0 src-a)
                      out-r     (+ src-r (* dst-r inv-src-a))
                      out-g     (+ src-g (* dst-g inv-src-a))
                      out-b     (+ src-b (* dst-b inv-src-a))
                      out-a     (+ src-a (* dst-a inv-src-a))]
                  (aset tile fi (float out-r))
                  (aset tile (unchecked-inc-int fi) (float out-g))
                  (aset tile (unchecked-add-int fi 2) (float out-b))
                  (aset tile (unchecked-add-int fi 3) (float out-a)))))
            (recur (unchecked-inc px))))
        (recur (unchecked-inc py))))))

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
