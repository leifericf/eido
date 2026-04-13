(ns ^{:stability :provisional} eido.io.gcode
  "GRBL-flavored G-code writer for pen-on-CNC, laser cutters, and
  2D CNC routers.

  Emits compiled IR as a streamed motion program. Each stroke-color
  group becomes a tool-change pause (M0) plus a batch of drawing
  moves; within each group, polylines are ordered to minimize
  pen-up travel. Y-axis is flipped relative to scene height because
  most CNC beds use Y-up while Eido's IR uses SVG-style Y-down.

  Targets GRBL dialect (Arduino-based desktop CNC and laser
  controllers). Marlin and other firmware dialects are out of scope
  for the MVP; most diverging commands (bed leveling, tool-length
  offsets, dwells, G2/G3 arcs) are unnecessary here.

  Distances are in millimeters. Pass :scale if your scene units
  differ. Pass :laser-mode true to use M4 dynamic power instead of
  M3 constant power.

  See also eido.io.polyline/extract-grouped-polylines, which
  produces the substrate this writer consumes."
  (:require
    [clojure.string :as str]
    [eido.io.polyline :as polyline]))

;; --- number formatting ---

(defn- fmt-coord ^String [x]
  (format "%.4f" (double x)))

(defn- fmt-int ^String [x]
  (str (int x)))

;; --- coordinate transforms ---

(defn- transform-xy
  "Applies scale and Y-flip to a scene-coord point.
  Y-flip makes the origin bottom-left (CNC convention) from the
  IR's top-left (SVG convention)."
  [{:keys [scale bounds-h]} [x y]]
  [(* (double scale) (double x))
   (* (double scale) (- (double bounds-h) (double y)))])

;; --- line emitters ---

(defn- header-lines [{:keys [z-up]}]
  ["G21 ; units = mm"
   "G90 ; absolute coordinates"
   "G17 ; XY plane"
   "M5 ; tool off"
   (str "G0 Z" (fmt-coord z-up) " ; raise to safe height")])

(defn- footer-lines []
  ["M5 ; tool off"
   "G0 X0 Y0 ; park"])

(defn- tool-change-lines [stroke]
  (let [label (if stroke
                (let [a (double (:a stroke 1.0))]
                  (if (< a 1.0)
                    (format "rgb-%d-%d-%d-a%d"
                            (:r stroke) (:g stroke) (:b stroke)
                            (int (Math/round (* 100.0 a))))
                    (format "rgb-%d-%d-%d"
                            (:r stroke) (:g stroke) (:b stroke))))
                "none")]
    ["M5 ; end of previous group"
     (str "M0 ; tool change: pen-" label)]))

(defn- tool-on-lines [{:keys [spindle-power laser-mode]}]
  [(str (if laser-mode "M4" "M3")
        " S" (fmt-int spindle-power)
        " ; tool on")])

(defn- polyline-lines
  "Emits a single polyline as a G-code move sequence.

    G0 X Y              ; rapid to start (pen still up)
    G1 Z<down> F<feed>  ; plunge
    G1 X Y F<feed>      ; draw through remaining points
    ...
    G1 Z<up> F<feed>    ; retract"
  [{:keys [feed z-up z-down] :as opts} poly]
  (let [pts     (mapv #(transform-xy opts %) poly)
        [sx sy] (first pts)
        rest-pts (rest pts)
        feed-s  (fmt-int feed)]
    (into
      [(format "G0 X%s Y%s" (fmt-coord sx) (fmt-coord sy))
       (format "G1 Z%s F%s" (fmt-coord z-down) feed-s)]
      (concat
        (map (fn [[x y]]
               (format "G1 X%s Y%s F%s"
                       (fmt-coord x) (fmt-coord y) feed-s))
             rest-pts)
        [(format "G1 Z%s F%s" (fmt-coord z-up) feed-s)]))))

(defn- group-lines [opts {:keys [stroke polylines]}]
  (concat
    (tool-change-lines stroke)
    (tool-on-lines opts)
    (mapcat #(polyline-lines opts %) polylines)))

;; --- public API ---

(def ^:private defaults
  {:feed            1000
   :z-up            5
   :z-down          0
   :spindle-power   1000
   :laser-mode      false
   :flatness        0.5
   :segments        64
   :optimize-travel true
   :scale           1.0})

(defn write-gcode
  "Writes a compiled IR's stroke geometry as GRBL-flavored G-code.

  Returns a string; callers are responsible for writing it to disk.
  Each unique stroke color becomes an M0-paused tool change;
  polylines inside a group are ordered to minimize pen-up travel.

  Options (defaults shown):
    :feed 1000             — cutting feed rate, mm/min
    :z-up 5                — safe retract height, mm
    :z-down 0              — engage height, mm
    :spindle-power 1000    — S value on M3/M4 (0-1000 typical)
    :laser-mode false      — when true, emits M4 (dynamic power)
    :flatness 0.5          — curve subdivision tolerance
    :segments 64           — circle/ellipse/arc segment count
    :optimize-travel true  — reorder polylines within each group
    :scale 1.0             — coordinate multiplier (scene → mm)

  Coordinate system: Y is flipped relative to scene height so that
  (0,0) is bottom-left (CNC convention) rather than top-left (SVG
  convention)."
  ([ir] (write-gcode ir {}))
  ([ir opts]
   (let [opts      (merge defaults opts)
         grouped   (polyline/extract-grouped-polylines
                     ir (select-keys opts [:flatness :segments]))
         [_ h]     (:bounds grouped)
         opts      (assoc opts :bounds-h h)
         groups    (if (:optimize-travel opts)
                     (mapv #(update % :polylines
                                    polyline/optimize-travel-polylines)
                           (:groups grouped))
                     (:groups grouped))]
     (str (str/join "\n"
            (concat (header-lines opts)
                    (mapcat #(group-lines opts %) groups)
                    (footer-lines)))
          "\n"))))

(comment
  (require '[eido.engine.compile :as c])
  (def ir (c/compile
            {:image/size       [200 200]
             :image/background [:color/rgb 255 255 255]
             :image/nodes
             [{:node/type :shape/rect
               :rect/xy [20 20] :rect/size [60 60]
               :style/stroke {:color [:color/rgb 255 0 0] :width 1}}
              {:node/type :shape/circle
               :circle/center [140 140] :circle/radius 30
               :style/stroke {:color [:color/rgb 0 0 255] :width 1}}]}))
  (spit "/tmp/eido-smoke.gcode" (write-gcode ir {}))
  )
