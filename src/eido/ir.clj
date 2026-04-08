(ns eido.ir
  "Internal IR op records for compiled scene representation.
  Records provide O(1) field access vs keyword lookup on maps.
  They implement IPersistentMap, so existing destructuring and
  keyword access in render.clj and svg.clj work unchanged.")

;; --- leaf op records ---

(defrecord RectOp    [op x y w h corner-radius
                      fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash
                      transforms clip])

(defrecord CircleOp  [op cx cy r
                      fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash
                      transforms clip])

(defrecord ArcOp     [op cx cy rx ry start extent mode
                      fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash
                      transforms clip])

(defrecord LineOp    [op x1 y1 x2 y2
                      fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash
                      transforms clip])

(defrecord EllipseOp [op cx cy rx ry
                      fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash
                      transforms clip])

(defrecord PathOp    [op commands fill-rule
                      fill stroke-color stroke-width opacity
                      stroke-cap stroke-join stroke-dash
                      transforms clip])

;; --- composite op record ---

(defrecord BufferOp  [op composite filter opacity transforms clip ops])
