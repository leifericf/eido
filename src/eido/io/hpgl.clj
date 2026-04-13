(ns ^{:stability :provisional} eido.io.hpgl
  "HPGL (Hewlett-Packard Graphics Language) writer for vintage and
  current pen plotters.

  HPGL is the lingua franca of 1980s/90s pen plotters (HP DraftPro,
  HP DesignJet, Roland DXY/PNC, many CAD-era plotters still in
  service) and is also accepted by various AxiDraw-adjacent
  controllers via shims. It's plain ASCII: short two-letter
  commands separated by semicolons, with `PU` for pen-up moves,
  `PD` for pen-down draws, and `SP n` to select pen n.

  Each stroke-color group in the IR becomes a `SP` pen change plus
  a sequence of `PU x,y;PD x,y,x,y,...;` polyline draws. Pens are
  numbered sequentially from 1 in first-seen-stroke order. The
  Y-axis is flipped relative to scene height because HPGL convention
  is Y-up, while Eido's IR is SVG-style Y-down.

  Coordinates are emitted as integer plotter units and the user
  controls the scene→plotter scale via `:scale`. Default 40 — a
  common HP plotter resolution of 40 plotter units per millimeter
  (0.025mm per unit). Pass `:scale 1` for raw scene units.

  See also eido.io.polyline/extract-grouped-polylines, which
  produces the substrate this writer consumes."
  (:require
    [clojure.string :as str]
    [eido.io.polyline :as polyline]))

;; --- coordinate transforms ---

(defn- transform-xy
  "Applies scale and Y-flip to a scene point. Y-flip puts (0,0) at
  bottom-left (HPGL convention) from the IR's top-left (SVG
  convention)."
  [{:keys [scale bounds-h]} [x y]]
  [(Math/round (* (double scale) (double x)))
   (Math/round (* (double scale) (- (double bounds-h) (double y))))])

;; --- command emitters ---

(defn- header-lines []
  ["IN;"        ; initialize plotter — clears state, raises pen
   "PA;"])      ; absolute coordinates

(defn- footer-lines []
  ["PU0,0;"     ; raise pen and park at origin
   "SP0;"])     ; deselect pen (return to carousel)

(defn- polyline-command
  "Emits a single polyline as a HPGL move sequence:
    PU x,y;        ; pen up, move to start
    PD x,y,...;    ; pen down, draw through remaining points"
  [opts poly]
  (let [pts     (mapv #(transform-xy opts %) poly)
        [sx sy] (first pts)
        rest-pts (rest pts)]
    (if (seq rest-pts)
      (str "PU" sx "," sy ";"
           "PD" (str/join "," (mapcat (fn [[x y]] [x y]) rest-pts)) ";")
      (str "PU" sx "," sy ";"))))

(defn- group-lines [opts pen-num {:keys [polylines]}]
  (cons (str "SP" pen-num ";")
        (map #(polyline-command opts %) polylines)))

;; --- public API ---

(def ^:private defaults
  {:flatness        0.5
   :segments        64
   :optimize-travel true
   :scale           40})

(defn write-hpgl
  "Writes a compiled IR's stroke geometry as HPGL.

  Returns a string; callers are responsible for writing it to disk.
  Each unique stroke color becomes a `SP` pen change (1-indexed in
  first-seen order); polylines inside a group are ordered to
  minimize pen-up travel.

  Options (defaults shown):
    :scale 40              — plotter units per scene unit. Default
                             40 matches the 40-unit-per-mm
                             resolution of classic HP plotters; use
                             `:scale 1` for raw scene units.
    :flatness 0.5          — curve subdivision tolerance
    :segments 64           — circle/ellipse/arc segment count
    :optimize-travel true  — reorder polylines within each group

  Coordinate system: Y is flipped relative to scene height so that
  (0,0) is bottom-left (HPGL convention) rather than top-left (SVG
  convention)."
  ([ir] (write-hpgl ir {}))
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
            (concat (header-lines)
                    (mapcat (fn [i group] (group-lines opts (inc i) group))
                            (range) groups)
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
  (spit "/tmp/eido-smoke.hpgl" (write-hpgl ir {}))
  )
