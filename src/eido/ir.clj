(ns eido.ir
  "Eido's intermediate representation.

  Two levels:
    1. Semantic IR — containers, draw items, fill/effect descriptors.
       Preserves artist intent (hatch fills, effects, fields) as data.
    2. Concrete ops — leaf records consumed by render.clj and svg.clj.
       Low-level drawing instructions (RectOp, PathOp, BufferOp).

  The pipeline flows: scene map → semantic IR → lower → concrete ops → render.
  See eido.ir.lower for the lowering step.")

;; --- concrete op records ---
;;
;; Records provide O(1) field access vs keyword lookup on maps.
;; They implement IPersistentMap, so existing destructuring and
;; keyword access in render.clj and svg.clj work unchanged.

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

(defrecord BufferOp  [op composite filter opacity transforms clip ops])

;; --- semantic IR ---
;;
;; Plain maps. The semantic IR preserves higher-level visual intent
;; before lowering to concrete ops.

;; --- container ---

(defn container
  "Creates a semantic IR container from size, background, and draw items."
  [size background items]
  {:ir/version    1
   :ir/size       size
   :ir/background background
   :ir/resources  {:framebuffer {:resource/kind :image
                                 :resource/size size}}
   :ir/passes     [{:pass/id    :draw-main
                    :pass/type  :draw-geometry
                    :pass/target :framebuffer
                    :pass/items items}]
   :ir/outputs    {:default :framebuffer}})

;; --- geometry constructors ---

(defn rect-geometry [xy size & {:keys [corner-radius]}]
  (cond-> {:geometry/type :rect
           :rect/xy       xy
           :rect/size     size}
    corner-radius (assoc :rect/corner-radius corner-radius)))

(defn circle-geometry [center radius]
  {:geometry/type  :circle
   :circle/center  center
   :circle/radius  radius})

(defn ellipse-geometry [center rx ry]
  {:geometry/type   :ellipse
   :ellipse/center  center
   :ellipse/rx      rx
   :ellipse/ry      ry})

(defn arc-geometry [center rx ry start extent & {:keys [mode]}]
  {:geometry/type :arc
   :arc/center    center
   :arc/rx        rx
   :arc/ry        ry
   :arc/start     start
   :arc/extent    extent
   :arc/mode      (or mode :open)})

(defn line-geometry [from to]
  {:geometry/type :line
   :line/from     from
   :line/to       to})

(defn path-geometry [commands & {:keys [fill-rule]}]
  (cond-> {:geometry/type  :path
           :path/commands  commands}
    fill-rule (assoc :path/fill-rule fill-rule)))

;; --- path command compilation ---

(defn compile-command
  "Flattens a scene path command to concrete IR format.
  [:move-to [x y]] → [:move-to x y]"
  [command]
  (let [cmd (nth command 0)]
    (case cmd
      :move-to  (let [[x y] (nth command 1)] [:move-to x y])
      :line-to  (let [[x y] (nth command 1)] [:line-to x y])
      :curve-to (let [[cx1 cy1] (nth command 1)
                      [cx2 cy2] (nth command 2)
                      [x y]     (nth command 3)]
                  [:curve-to cx1 cy1 cx2 cy2 x y])
      :quad-to  (let [[cpx cpy] (nth command 1)
                      [x y]     (nth command 2)]
                  [:quad-to cpx cpy x y])
      :close    [:close])))

;; --- draw item ---

(defn draw-item
  "Creates a semantic draw item from geometry and optional style properties."
  [geometry & {:keys [fill stroke opacity transforms clip effects pre-transforms]}]
  (cond-> {:item/geometry geometry}
    fill           (assoc :item/fill fill)
    stroke         (assoc :item/stroke stroke)
    opacity        (assoc :item/opacity opacity)
    transforms     (assoc :item/transforms transforms)
    clip           (assoc :item/clip clip)
    effects        (assoc :item/effects effects)
    pre-transforms (assoc :item/pre-transforms pre-transforms)))

(defn generator-item
  "Creates a semantic draw item from a generator descriptor map.
  The generator-map should have :item/generator with :generator/type."
  [generator-map]
  generator-map)

;; --- pass constructors ---

(defn effect-pass
  "Creates an effect pass that applies a filter to a resource."
  [id effect & {:keys [input target]
                :or {input :framebuffer target :framebuffer}}]
  {:pass/id     id
   :pass/type   :effect-pass
   :pass/input  input
   :pass/target target
   :pass/effect effect})

(defn program-pass
  "Creates a program pass that evaluates a program over a resource."
  [id program & {:keys [input target]
                 :or {input :framebuffer target :framebuffer}}]
  {:pass/id      id
   :pass/type    :program-pass
   :pass/input   input
   :pass/target  target
   :pass/program program})

;; --- pipeline ---

(defn pipeline
  "Creates a multi-pass IR container.
  passes: vector of pass maps (draw-geometry, effect-pass, program-pass)."
  [size background passes]
  {:ir/version    1
   :ir/size       size
   :ir/background background
   :ir/resources  {:framebuffer {:resource/kind :image
                                 :resource/size size}}
   :ir/passes     passes
   :ir/outputs    {:default :framebuffer}})
